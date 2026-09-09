package jp.example.expenseflow.feature.expense.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import jp.example.expenseflow.feature.auth.service.CurrentUser;
import jp.example.expenseflow.feature.auth.service.CurrentUserService;
import jp.example.expenseflow.feature.expense.domain.ExpenseCategory;
import jp.example.expenseflow.feature.expense.domain.ExpenseEvent;
import jp.example.expenseflow.feature.expense.domain.ExpenseEventAction;
import jp.example.expenseflow.feature.expense.domain.ExpenseRequest;
import jp.example.expenseflow.feature.expense.domain.ExpenseStatus;
import jp.example.expenseflow.feature.expense.repository.ExpenseEventRepository;
import jp.example.expenseflow.feature.expense.repository.ExpenseRequestRepository;
import jp.example.expenseflow.feature.expense.service.dto.ExpenseDetailView;
import jp.example.expenseflow.feature.expense.service.dto.ExpenseEventView;
import jp.example.expenseflow.feature.expense.service.dto.ExpenseForm;
import jp.example.expenseflow.feature.expense.service.dto.ExpenseListItem;
import jp.example.expenseflow.feature.expense.service.dto.ExpenseListPage;
import jp.example.expenseflow.feature.expense.service.dto.ApprovalForm;
import jp.example.expenseflow.feature.expense.service.dto.ApprovalListItem;
import jp.example.expenseflow.feature.expense.service.dto.ApprovalListPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    public static final int PAGE_SIZE = 20;
    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Tokyo");

    private static final DateTimeFormatter DISPLAY_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(DISPLAY_ZONE);
    private static final Map<ExpenseStatus, String> STATUS_LABELS = Map.of(
            ExpenseStatus.DRAFT, "下書き",
            ExpenseStatus.SUBMITTED, "申請中",
            ExpenseStatus.RETURNED, "差戻し",
            ExpenseStatus.APPROVED, "承認済み");
    private static final Map<ExpenseCategory, String> CATEGORY_LABELS = Map.of(
            ExpenseCategory.TRANSPORT, "交通費",
            ExpenseCategory.SUPPLIES, "消耗品費",
            ExpenseCategory.OTHER, "その他");
    private static final Map<ExpenseEventAction, String> EVENT_LABELS = Map.of(
            ExpenseEventAction.CREATE, "作成",
            ExpenseEventAction.UPDATE, "更新",
            ExpenseEventAction.SUBMIT, "申請",
            ExpenseEventAction.APPROVE, "承認",
            ExpenseEventAction.RETURN, "差戻し");

    private final ExpenseRequestRepository expenseRequestRepository;
    private final ExpenseEventRepository expenseEventRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public ExpenseService(ExpenseRequestRepository expenseRequestRepository,
                          ExpenseEventRepository expenseEventRepository,
                          CurrentUserService currentUserService,
                          Clock clock) {
        this.expenseRequestRepository = expenseRequestRepository;
        this.expenseEventRepository = expenseEventRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExpenseListPage findOwn(String username, String statusValue, String categoryValue,
                                   String fromValue, String toValue, String query, int pageNumber) {
        SearchCriteria criteria = parseSearch(statusValue, categoryValue, fromValue, toValue,
                query, pageNumber);
        CurrentUser currentUser = currentUserService.require(username);
        PageRequest pageRequest = PageRequest.of(criteria.pageNumber(), PAGE_SIZE,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        Page<ExpenseRequest> requests = criteria.query() == null
                ? expenseRequestRepository.findOwnPageWithoutQuery(
                        currentUser.id(), criteria.status(), criteria.category(), criteria.fromDate(),
                        criteria.toDate(), pageRequest)
                : expenseRequestRepository.findOwnPage(
                        currentUser.id(), criteria.status(), criteria.category(), criteria.fromDate(),
                        criteria.toDate(), escapeLike(criteria.query()), pageRequest);

        List<ExpenseListItem> items = requests.getContent().stream()
                .map(this::toListItem)
                .toList();
        return new ExpenseListPage(items, requests.getTotalElements(), requests.getTotalPages(),
                requests.getNumber(), requests.hasPrevious(), requests.hasNext(),
                criteria.statusValue(), criteria.categoryValue(), criteria.fromValue(),
                criteria.toValue(), criteria.query());
    }

    @Transactional(readOnly = true)
    public ApprovalListPage findApprovals(String username, int pageNumber) {
        if (pageNumber < 0) {
            throw new ExpenseQueryException("ページ番号が不正です");
        }
        CurrentUser currentUser = currentUserService.require(username);
        requireApprover(currentUser);
        PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE,
                Sort.by(Sort.Order.asc("submittedAt"), Sort.Order.asc("id")));
        Page<ExpenseRequest> requests = expenseRequestRepository.findApprovalPage(
                currentUser.departmentId(), currentUser.id(), ExpenseStatus.SUBMITTED, pageRequest);
        List<ApprovalListItem> items = requests.getContent().stream()
                .map(this::toApprovalListItem)
                .toList();
        return new ApprovalListPage(items, requests.getTotalElements(), requests.getTotalPages(),
                requests.getNumber(), requests.hasPrevious(), requests.hasNext());
    }

    @Transactional(readOnly = true)
    public boolean isApprover(String username) {
        return currentUserService.require(username).isApprover();
    }

    @Transactional(readOnly = true)
    public void authorizeApprovalTarget(String username, Long id) {
        CurrentUser currentUser = currentUserService.require(username);
        requireApprover(currentUser);
        requireApprovalTarget(id, currentUser);
    }

    @Transactional(readOnly = true)
    public ExpenseDetailView findVisible(String username, Long id) {
        CurrentUser currentUser = currentUserService.require(username);
        ExpenseRequest request = requireVisible(id, currentUser);
        return toDetailView(request, currentUser);
    }

    @Transactional(readOnly = true)
    public ExpenseForm prepareEdit(String username, Long id) {
        CurrentUser currentUser = currentUserService.require(username);
        ExpenseRequest request = requireOwned(id, currentUser);
        ensureEditable(request);
        return ExpenseForm.from(request);
    }

    @Transactional
    public Long create(String username, ExpenseForm form) {
        CurrentUser currentUser = currentUserService.require(username);
        Instant now = clock.instant();
        LocalDate today = dateAt(now);
        ParsedDetails details = parseDetails(form, today);
        ExpenseRequest request = ExpenseRequest.create(
                currentUser.user(), currentUser.department(), details.title(), details.purpose(),
                details.category(), details.expenseDate(), details.amount(), now, today);
        ExpenseRequest saved = expenseRequestRepository.saveAndFlush(request);
        expenseEventRepository.saveAndFlush(ExpenseEvent.record(
                saved, currentUser.user(), ExpenseEventAction.CREATE, null,
                ExpenseStatus.DRAFT, null, now));
        return saved.getId();
    }

    @Transactional
    public void update(String username, Long id, ExpenseForm form) {
        CurrentUser currentUser = currentUserService.require(username);
        ExpenseRequest request = requireOwned(id, currentUser);
        requireMatchingVersion(form == null ? null : form.getVersion(), request);
        ensureEditable(request);
        ExpenseStatus previousStatus = request.getStatus();
        Instant now = clock.instant();
        LocalDate today = dateAt(now);
        ParsedDetails details = parseDetails(form, today);
        request.updateDetails(details.title(), details.purpose(), details.category(),
                details.expenseDate(), details.amount(), now, today);
        expenseRequestRepository.saveAndFlush(request);
        expenseEventRepository.saveAndFlush(ExpenseEvent.record(
                request, currentUser.user(), ExpenseEventAction.UPDATE, previousStatus,
                previousStatus, null, now));
    }

    @Transactional
    public void delete(String username, Long id, Long version) {
        CurrentUser currentUser = currentUserService.require(username);
        ExpenseRequest request = requireOwned(id, currentUser);
        requireMatchingVersion(version, request);
        if (!request.isDeletable()) {
            throw new ExpenseConflictException();
        }
        expenseRequestRepository.delete(request);
        expenseRequestRepository.flush();
    }

    @Transactional
    public void submit(String username, Long id, Long version) {
        CurrentUser currentUser = currentUserService.require(username);
        ExpenseRequest request = requireOwned(id, currentUser);
        requireMatchingVersion(version, request);
        if (!request.isSubmittable()) {
            throw new ExpenseConflictException();
        }
        Instant now = clock.instant();
        ExpenseStatus previousStatus = request.submit(now);
        expenseRequestRepository.saveAndFlush(request);
        expenseEventRepository.saveAndFlush(ExpenseEvent.record(
                request, currentUser.user(), ExpenseEventAction.SUBMIT, previousStatus,
                ExpenseStatus.SUBMITTED, null, now));
    }

    @Transactional
    public void approve(String username, Long id, ApprovalForm form) {
        CurrentUser currentUser = currentUserService.require(username);
        ExpenseRequest request = requireApprovalTarget(id, currentUser);
        requireMatchingVersion(form == null ? null : form.getVersion(), request);
        if (request.getStatus() != ExpenseStatus.SUBMITTED) {
            throw new ExpenseConflictException();
        }
        String comment = parseApprovalComment(form, false);
        Instant now = clock.instant();
        ExpenseStatus previousStatus = request.approve(now);
        expenseRequestRepository.saveAndFlush(request);
        expenseEventRepository.saveAndFlush(ExpenseEvent.record(
                request, currentUser.user(), ExpenseEventAction.APPROVE, previousStatus,
                ExpenseStatus.APPROVED, comment, now));
    }

    @Transactional
    public void returnToApplicant(String username, Long id, ApprovalForm form) {
        CurrentUser currentUser = currentUserService.require(username);
        ExpenseRequest request = requireApprovalTarget(id, currentUser);
        requireMatchingVersion(form == null ? null : form.getVersion(), request);
        if (request.getStatus() != ExpenseStatus.SUBMITTED) {
            throw new ExpenseConflictException();
        }
        String comment = parseApprovalComment(form, true);
        Instant now = clock.instant();
        ExpenseStatus previousStatus = request.returnForRevision(now);
        expenseRequestRepository.saveAndFlush(request);
        expenseEventRepository.saveAndFlush(ExpenseEvent.record(
                request, currentUser.user(), ExpenseEventAction.RETURN, previousStatus,
                ExpenseStatus.RETURNED, comment, now));
    }

    private ExpenseRequest requireVisible(Long id, CurrentUser currentUser) {
        ExpenseRequest request = requireExisting(id);
        boolean owner = Objects.equals(request.getApplicant().getId(), currentUser.id());
        boolean sameDepartmentApprover = currentUser.isApprover()
                && Objects.equals(request.getDepartment().getId(), currentUser.departmentId())
                && request.getStatus() != ExpenseStatus.DRAFT;
        if (!owner && !sameDepartmentApprover) {
            throw new ExpenseNotFoundException();
        }
        return request;
    }

    private ExpenseRequest requireOwned(Long id, CurrentUser currentUser) {
        ExpenseRequest request = requireExisting(id);
        if (!Objects.equals(request.getApplicant().getId(), currentUser.id())) {
            throw new ExpenseNotFoundException();
        }
        return request;
    }

    private ExpenseRequest requireApprovalTarget(Long id, CurrentUser currentUser) {
        requireApprover(currentUser);
        ExpenseRequest request = requireExisting(id);
        boolean sameDepartment = Objects.equals(request.getDepartment().getId(), currentUser.departmentId());
        boolean ownRequest = Objects.equals(request.getApplicant().getId(), currentUser.id());
        if (!sameDepartment || ownRequest || request.getStatus() == ExpenseStatus.DRAFT) {
            throw new ExpenseNotFoundException();
        }
        return request;
    }

    private void requireApprover(CurrentUser currentUser) {
        if (!currentUser.isApprover()) {
            throw new ExpenseForbiddenException();
        }
    }

    private ExpenseRequest requireExisting(Long id) {
        if (id == null || id <= 0) {
            throw new ExpenseNotFoundException();
        }
        return expenseRequestRepository.findWithRelationsById(id)
                .orElseThrow(ExpenseNotFoundException::new);
    }

    private void ensureEditable(ExpenseRequest request) {
        if (!request.isEditable()) {
            throw new ExpenseConflictException();
        }
    }

    private void requireMatchingVersion(Long suppliedVersion, ExpenseRequest request) {
        if (suppliedVersion == null) {
            throw new ExpenseQueryException("versionは必須です");
        }
        if (suppliedVersion < 0) {
            throw new ExpenseQueryException("versionが不正です");
        }
        if (suppliedVersion.longValue() != request.getVersion()) {
            throw new ExpenseConflictException();
        }
    }

    private ParsedDetails parseDetails(ExpenseForm form, LocalDate today) {
        ExpenseForm actualForm = form == null ? new ExpenseForm() : form;
        Map<String, String> errors = new LinkedHashMap<>();

        String title = trimToEmpty(actualForm.getTitle());
        if (title.isEmpty() || title.length() > 100) {
            errors.put("title", "件名は1〜100文字で入力してください");
        }

        String purpose = trimToEmpty(actualForm.getPurpose());
        if (purpose.isEmpty() || purpose.length() > 500) {
            errors.put("purpose", "用途は1〜500文字で入力してください");
        }

        String categoryValue = trimToEmpty(actualForm.getCategory());
        ExpenseCategory category = parseCategory(categoryValue, errors);

        String expenseDateValue = trimToEmpty(actualForm.getExpenseDate());
        LocalDate expenseDate = parseDate(expenseDateValue, errors);
        if (expenseDate != null && expenseDate.isAfter(today)) {
            errors.put("expenseDate", "利用日は今日以前の日付を入力してください");
        }

        String amountValue = trimToEmpty(actualForm.getAmount());
        BigDecimal amount = parseAmount(amountValue, errors);

        if (!errors.isEmpty()) {
            throw new ExpenseInputException(actualForm, errors);
        }
        return new ParsedDetails(title, purpose, category, expenseDate, amount);
    }

    private ExpenseCategory parseCategory(String value, Map<String, String> errors) {
        if (value.isEmpty()) {
            errors.put("category", "分類を選択してください");
            return null;
        }
        try {
            return ExpenseCategory.valueOf(value);
        } catch (IllegalArgumentException exception) {
            errors.put("category", "分類の指定が不正です");
            return null;
        }
    }

    private LocalDate parseDate(String value, Map<String, String> errors) {
        if (value.isEmpty()) {
            errors.put("expenseDate", "利用日を入力してください");
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeException exception) {
            errors.put("expenseDate", "利用日の日付形式が不正です");
            return null;
        }
    }

    private BigDecimal parseAmount(String value, Map<String, String> errors) {
        if (value.isEmpty() || !value.matches("[0-9]+")) {
            errors.put("amount", "金額は半角数字の整数で入力してください");
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(value);
            ExpenseRequest.validateAmount(amount);
            return amount;
        } catch (IllegalArgumentException exception) {
            errors.put("amount", "金額は1〜1,000,000円の整数で入力してください");
            return null;
        }
    }

    private SearchCriteria parseSearch(String statusValue, String categoryValue, String fromValue,
                                       String toValue, String query, int pageNumber) {
        if (pageNumber < 0) {
            throw new ExpenseQueryException("ページ番号が不正です");
        }
        String normalizedStatus = trimToNull(statusValue);
        ExpenseStatus status = parseStatus(normalizedStatus);
        String normalizedCategory = trimToNull(categoryValue);
        ExpenseCategory category = parseCategoryForSearch(normalizedCategory);
        String normalizedFrom = trimToNull(fromValue);
        LocalDate fromDate = parseDateForSearch(normalizedFrom, "利用開始日");
        String normalizedTo = trimToNull(toValue);
        LocalDate toDate = parseDateForSearch(normalizedTo, "利用終了日");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ExpenseQueryException("利用開始日は利用終了日以前にしてください");
        }
        String normalizedQuery = trimToNull(query);
        if (normalizedQuery != null && normalizedQuery.length() > 100) {
            throw new ExpenseQueryException("件名検索は100文字以内で入力してください");
        }
        return new SearchCriteria(status, category, fromDate, toDate, pageNumber,
                normalizedStatus, normalizedCategory, normalizedFrom, normalizedTo,
                normalizedQuery);
    }

    private ExpenseStatus parseStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ExpenseStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ExpenseQueryException("状態の指定が不正です");
        }
    }

    private ExpenseCategory parseCategoryForSearch(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ExpenseCategory.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ExpenseQueryException("分類の指定が不正です");
        }
    }

    private LocalDate parseDateForSearch(String value, String label) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeException exception) {
            throw new ExpenseQueryException(label + "の日付形式が不正です");
        }
    }

    private ExpenseListItem toListItem(ExpenseRequest request) {
        return new ExpenseListItem(
                request.getId(),
                request.getTitle(),
                request.getCategory().name(),
                CATEGORY_LABELS.get(request.getCategory()),
                request.getExpenseDate().toString(),
                request.getAmount().toPlainString(),
                request.getStatus().name(),
                STATUS_LABELS.get(request.getStatus()),
                formatInstant(request.getUpdatedAt()));
    }

    private ApprovalListItem toApprovalListItem(ExpenseRequest request) {
        return new ApprovalListItem(
                request.getId(),
                request.getApplicant().getDisplayName(),
                request.getApplicant().getUsername(),
                request.getTitle(),
                CATEGORY_LABELS.get(request.getCategory()),
                request.getExpenseDate().toString(),
                request.getAmount().toPlainString(),
                formatInstant(request.getSubmittedAt()),
                request.getVersion());
    }

    private ExpenseDetailView toDetailView(ExpenseRequest request, CurrentUser currentUser) {
        boolean owner = Objects.equals(request.getApplicant().getId(), currentUser.id());
        boolean sameDepartment = Objects.equals(request.getDepartment().getId(), currentUser.departmentId());
        boolean canApprove = currentUser.isApprover() && !owner && sameDepartment
                && request.getStatus() == ExpenseStatus.SUBMITTED;
        List<ExpenseEventView> events = expenseEventRepository
                .findByExpenseIdForDisplay(request.getId()).stream()
                .map(this::toEventView)
                .collect(Collectors.toUnmodifiableList());
        return new ExpenseDetailView(
                request.getId(),
                request.getApplicant().getDisplayName(),
                request.getApplicant().getUsername(),
                request.getDepartment().getName(),
                request.getTitle(),
                request.getPurpose(),
                CATEGORY_LABELS.get(request.getCategory()),
                request.getExpenseDate().toString(),
                request.getAmount().toPlainString(),
                request.getStatus().name(),
                STATUS_LABELS.get(request.getStatus()),
                request.getVersion(),
                formatInstant(request.getCreatedAt()),
                formatInstant(request.getUpdatedAt()),
                formatInstant(request.getSubmittedAt()),
                events,
                owner && request.isEditable(),
                owner && request.isDeletable(),
                owner && request.isSubmittable(),
                canApprove);
    }

    private ExpenseEventView toEventView(ExpenseEvent event) {
        return new ExpenseEventView(
                formatInstant(event.getOccurredAt()),
                event.getActor().getDisplayName(),
                EVENT_LABELS.getOrDefault(event.getAction(), event.getAction().name()),
                event.getFromStatus() == null ? "—" : STATUS_LABELS.get(event.getFromStatus()),
                STATUS_LABELS.get(event.getToStatus()),
                event.getComment());
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "—" : DISPLAY_TIME_FORMAT.format(instant);
    }

    private LocalDate dateAt(Instant instant) {
        return LocalDate.ofInstant(instant, DISPLAY_ZONE);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String normalized = trimToEmpty(value);
        return normalized.isEmpty() ? null : normalized;
    }

    private String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private String parseApprovalComment(ApprovalForm form, boolean required) {
        ApprovalForm actualForm = form == null ? new ApprovalForm() : form;
        String comment = trimToEmpty(actualForm.getComment());
        Map<String, String> errors = new LinkedHashMap<>();
        if (required && comment.isEmpty()) {
            errors.put("comment", "差戻し理由は1〜500文字で入力してください");
        } else if (comment.length() > 500) {
            errors.put("comment", required
                    ? "差戻し理由は1〜500文字で入力してください"
                    : "承認コメントは500文字以内で入力してください");
        }
        if (!errors.isEmpty()) {
            throw new ApprovalInputException(actualForm, errors);
        }
        return comment.isEmpty() ? null : comment;
    }

    public static Map<ExpenseStatus, String> statusLabels() {
        return STATUS_LABELS;
    }

    public static Map<ExpenseCategory, String> categoryLabels() {
        return CATEGORY_LABELS;
    }

    private record ParsedDetails(String title, String purpose, ExpenseCategory category,
                                 LocalDate expenseDate, BigDecimal amount) {
    }

    private record SearchCriteria(ExpenseStatus status, ExpenseCategory category,
                                  LocalDate fromDate, LocalDate toDate, int pageNumber,
                                  String statusValue, String categoryValue, String fromValue,
                                  String toValue, String query) {
    }
}

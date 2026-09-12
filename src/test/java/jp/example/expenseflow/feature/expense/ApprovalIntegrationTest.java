package jp.example.expenseflow.feature.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jp.example.expenseflow.feature.auth.domain.AppUser;
import jp.example.expenseflow.feature.auth.domain.Department;
import jp.example.expenseflow.feature.auth.domain.UserRole;
import jp.example.expenseflow.feature.auth.repository.AppUserRepository;
import jp.example.expenseflow.feature.auth.repository.DepartmentRepository;
import jp.example.expenseflow.feature.expense.domain.ExpenseCategory;
import jp.example.expenseflow.feature.expense.domain.ExpenseEvent;
import jp.example.expenseflow.feature.expense.domain.ExpenseEventAction;
import jp.example.expenseflow.feature.expense.domain.ExpenseRequest;
import jp.example.expenseflow.feature.expense.domain.ExpenseStatus;
import jp.example.expenseflow.feature.expense.repository.ExpenseEventRepository;
import jp.example.expenseflow.feature.expense.repository.ExpenseRequestRepository;
import jp.example.expenseflow.feature.expense.service.dto.ApprovalForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApprovalIntegrationTest.FixedClockConfiguration.class)
@Testcontainers
class ApprovalIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-08T15:00:00Z");
    private static final String PASSWORD = "test-password";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    ExpenseRequestRepository expenseRequestRepository;

    @Autowired
    ExpenseEventRepository expenseEventRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private Department sales;
    private Department development;
    private AppUser employee;
    private AppUser approver;
    private AppUser secondApprover;
    private AppUser developmentEmployee;
    private AppUser developmentApprover;

    @BeforeEach
    void setUpUsers() {
        expenseEventRepository.deleteAllInBatch();
        expenseRequestRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        departmentRepository.deleteAllInBatch();

        sales = departmentRepository.save(new Department("営業部"));
        development = departmentRepository.save(new Department("開発部"));
        employee = saveUser("test.employee", "テスト社員", sales, UserRole.EMPLOYEE);
        approver = saveUser("test.approver", "テスト承認者", sales, UserRole.APPROVER);
        secondApprover = saveUser("test.approver.2", "テスト承認者2", sales, UserRole.APPROVER);
        developmentEmployee = saveUser("test.employee.dev", "開発社員", development, UserRole.EMPLOYEE);
        developmentApprover = saveUser("test.approver.dev", "開発承認者", development, UserRole.APPROVER);
    }

    @AfterEach
    void removeFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_approval_event_trigger ON expense_events");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_approval_event()");
    }

    @Test
    void approvalPageShowsOnlySameDepartmentOthersInSubmittedOrder() throws Exception {
        for (int number = 1; number <= 21; number++) {
            insertSubmittedFixture(String.format("承認待ち%02d", number), employee,
                    FIXED_INSTANT.plusSeconds(number));
        }
        insertSubmittedFixture("承認者本人の申請", approver, FIXED_INSTANT.plusSeconds(30));
        insertSubmittedFixture("他部署の申請", developmentEmployee, FIXED_INSTANT.plusSeconds(31));
        insertStatusFixture("下書き", employee, ExpenseStatus.DRAFT);
        insertStatusFixture("差戻し済み", employee, ExpenseStatus.RETURNED);
        insertStatusFixture("承認済み", employee, ExpenseStatus.APPROVED);

        MockHttpSession session = loginAs(approver.getUsername());
        mockMvc.perform(get("/approvals").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("承認待ち01")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("承認待ち20")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("承認待ち21"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("承認者本人の申請"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("他部署の申請"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("page=1")));

        mockMvc.perform(get("/approvals").param("page", "1").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("承認待ち21")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("承認待ち01"))));

        for (int number : List.of(10, 107374182, 107374183, Integer.MAX_VALUE)) {
            MvcResult result = mockMvc.perform(get("/approvals").session(session)
                            .param("page", Integer.toString(number)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            ((long) number + 1) + " / 2"))).andReturn();
            var page = (jp.example.expenseflow.feature.expense.service.dto.ApprovalListPage)
                    result.getModelAndView().getModel().get("page");
            assertThat(page.getItems()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(21);
            assertThat(page.getNumber()).isEqualTo(number);
            assertThat(page.isHasNext()).isFalse();
        }
        for (String invalidPage : List.of("-1", "not-a-number")) {
            mockMvc.perform(get("/approvals").session(session).param("page", invalidPage))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void employeeCannotOpenApprovalPageOrApprove() throws Exception {
        Long id = insertSubmittedFixture("社員が触れない申請", employee, FIXED_INSTANT);
        MockHttpSession session = loginAs(employee.getUsername());

        mockMvc.perform(get("/approvals").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/approvals").session(session).param("page", "2147483647"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/expenses/{id}/approve", id)
                        .session(session)
                        .with(csrf())
                        .param("version", "0"))
                .andExpect(status().isForbidden());

        assertThat(findRequest(id).getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
    }

    @Test
    void approverCanApproveAndStoresOptionalComment() throws Exception {
        Long id = insertSubmittedFixture("承認する申請", employee, FIXED_INSTANT);
        MockHttpSession session = loginAs(approver.getUsername());

        mockMvc.perform(get("/expenses/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("承認する")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("差し戻す")));

        mockMvc.perform(post("/expenses/{id}/approve", id)
                        .session(session)
                        .with(csrf())
                        .param("version", "0")
                        .param("comment", "  確認済み  "))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/expenses/" + id));

        ExpenseRequest approved = findRequest(id);
        assertThat(approved.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
        assertThat(approved.getVersion()).isEqualTo(1L);
        List<ExpenseEvent> events = expenseEventRepository.findByExpenseIdForDisplay(id);
        assertThat(events).hasSize(3);
        assertThat(events.get(2).getAction()).isEqualTo(ExpenseEventAction.APPROVE);
        assertThat(events.get(2).getComment()).isEqualTo("確認済み");
    }

    @Test
    void returnRequiresReasonAndEmployeeCanEditAndResubmit() throws Exception {
        Long id = insertSubmittedFixture("差戻し対象", employee, FIXED_INSTANT);
        MockHttpSession approverSession = loginAs(approver.getUsername());

        mockMvc.perform(post("/expenses/{id}/return", id)
                        .session(approverSession)
                        .with(csrf())
                        .param("version", "0")
                        .param("comment", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "差戻し理由は1〜500文字で入力してください")));
        assertThat(findRequest(id).getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);

        mockMvc.perform(post("/expenses/{id}/return", id)
                        .session(approverSession)
                        .with(csrf())
                        .param("version", "0")
                        .param("comment", "  利用目的を補足してください  "))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/expenses/" + id));

        ExpenseRequest returned = findRequest(id);
        assertThat(returned.getStatus()).isEqualTo(ExpenseStatus.RETURNED);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id).get(2).getComment())
                .isEqualTo("利用目的を補足してください");

        MockHttpSession employeeSession = loginAs(employee.getUsername());
        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(employeeSession)
                        .with(csrf())
                        .param("title", "差戻し修正済み")
                        .param("purpose", "補足した用途")
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-08")
                        .param("amount", "2500")
                        .param("version", "1"))
                .andExpect(status().isSeeOther());
        mockMvc.perform(post("/expenses/{id}/submit", id)
                        .session(employeeSession)
                        .with(csrf())
                        .param("version", "2"))
                .andExpect(status().isSeeOther());

        ExpenseRequest resubmitted = findRequest(id);
        assertThat(resubmitted.getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
        assertThat(resubmitted.getSubmittedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(5);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id).get(4).getAction())
                .isEqualTo(ExpenseEventAction.SUBMIT);
    }

    @Test
    void fullWidthWhitespaceReturnReasonIsRejectedWithoutChangingHistory() throws Exception {
        Long id = insertSubmittedFixture("全角空白理由", employee, FIXED_INSTANT);
        MockHttpSession session = loginAs(approver.getUsername());

        mockMvc.perform(post("/expenses/{id}/return", id)
                        .session(session)
                        .with(csrf())
                        .param("version", "0")
                        .param("comment", "\u3000\u3000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "差戻し理由は1〜500文字で入力してください")));

        ExpenseRequest unchanged = findRequest(id);
        assertThat(unchanged.getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
        assertThat(unchanged.getVersion()).isEqualTo(0L);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(2);
    }

    @Test
    void approvalTargetAccessRejectsSelfOtherDepartmentAndDraft() throws Exception {
        Long selfId = insertSubmittedFixture("承認者本人", approver, FIXED_INSTANT);
        Long otherDepartmentId = insertSubmittedFixture("他部署対象", developmentEmployee, FIXED_INSTANT);
        Long draftId = insertStatusFixture("下書き対象", employee, ExpenseStatus.DRAFT);
        MockHttpSession session = loginAs(approver.getUsername());

        mockMvc.perform(post("/expenses/{id}/approve", selfId)
                        .session(session).with(csrf()).param("version", "0"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/expenses/{id}/return", otherDepartmentId)
                        .session(session).with(csrf()).param("version", "0").param("comment", "理由"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/expenses/{id}", draftId).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/expenses/{id}/approve", draftId)
                        .session(session).with(csrf()).param("version", "0"))
                .andExpect(status().isNotFound());

        assertThat(findRequest(selfId).getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
        assertThat(findRequest(otherDepartmentId).getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
    }

    @Test
    void approvalVersionAndCommentInputsReturnMeaningfulBadRequest() throws Exception {
        Long missingVersionId = insertSubmittedFixture("version必須", employee, FIXED_INSTANT);
        MockHttpSession session = loginAs(approver.getUsername());

        mockMvc.perform(post("/expenses/{id}/approve", missingVersionId)
                        .session(session).with(csrf()).param("comment", "確認"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("versionは必須です")));
        mockMvc.perform(post("/expenses/{id}/approve", missingVersionId)
                        .session(session).with(csrf()).param("version", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "versionの形式が不正です")));
        mockMvc.perform(post("/expenses/{id}/return", missingVersionId)
                        .session(session).with(csrf()).param("version", "0")
                        .param("comment", "x".repeat(501)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("x".repeat(501))));

        Long staleId = insertSubmittedFixture("古いversion", employee, FIXED_INSTANT);
        mockMvc.perform(post("/expenses/{id}/approve", staleId)
                        .session(session).with(csrf()).param("version", "0"))
                .andExpect(status().isSeeOther());
        mockMvc.perform(post("/expenses/{id}/return", staleId)
                        .session(session).with(csrf()).param("version", "0").param("comment", "理由"))
                .andExpect(status().isConflict());
        assertThat(findRequest(staleId).getStatus()).isEqualTo(ExpenseStatus.APPROVED);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(staleId)).hasSize(3);
    }

    @Test
    void onlySubmittedCanBeApprovedOrReturned() throws Exception {
        Long returnedId = insertStatusFixture("差戻し済み", employee, ExpenseStatus.RETURNED);
        Long approvedId = insertStatusFixture("承認済み", employee, ExpenseStatus.APPROVED);
        MockHttpSession session = loginAs(approver.getUsername());

        mockMvc.perform(post("/expenses/{id}/approve", returnedId)
                        .session(session).with(csrf()).param("version", "0"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/expenses/{id}/return", approvedId)
                        .session(session).with(csrf()).param("version", "0").param("comment", "理由"))
                .andExpect(status().isConflict());

        assertThat(findRequest(returnedId).getStatus()).isEqualTo(ExpenseStatus.RETURNED);
        assertThat(findRequest(approvedId).getStatus()).isEqualTo(ExpenseStatus.APPROVED);
    }

    @Test
    void approvalEventFailureRollsBackStatusVersionAndHistory() throws Exception {
        Long id = insertSubmittedFixture("承認ロールバック", employee, FIXED_INSTANT);
        installFailingApprovalTrigger();
        try {
            mockMvc.perform(post("/expenses/{id}/approve", id)
                            .session(loginAs(approver.getUsername()))
                            .with(csrf()).param("version", "0"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "処理に失敗しました")));
        } finally {
            removeFailureTrigger();
        }

        ExpenseRequest unchanged = findRequest(id);
        assertThat(unchanged.getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
        assertThat(unchanged.getVersion()).isEqualTo(0L);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(2);
    }

    @Test
    void returnEventFailureRollsBackStatusVersionAndHistory() throws Exception {
        Long id = insertSubmittedFixture("差戻しロールバック", employee, FIXED_INSTANT);
        installFailingApprovalTrigger();
        try {
            mockMvc.perform(post("/expenses/{id}/return", id)
                            .session(loginAs(approver.getUsername())).with(csrf())
                            .param("version", "0").param("comment", "保存に失敗する差戻し理由"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("照合ID")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("test approval event failure"))));
        } finally {
            removeFailureTrigger();
        }
        ExpenseRequest unchanged = findRequest(id);
        assertThat(unchanged.getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
        assertThat(unchanged.getVersion()).isZero();
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id))
                .extracting(ExpenseEvent::getAction)
                .containsExactly(ExpenseEventAction.CREATE, ExpenseEventAction.SUBMIT);
    }

    @Test
    void concurrentApprovalsAllowOneCommitAndOneOptimisticConflict() throws Exception {
        Long id = insertSubmittedFixture("承認競合", employee, FIXED_INSTANT);
        List<DecisionOutcome> outcomes = runConcurrentDecisions(id,
                new Decision(ExpenseEventAction.APPROVE, approver.getId()),
                new Decision(ExpenseEventAction.APPROVE, secondApprover.getId()));

        assertOneSuccessAndOneOptimisticConflict(outcomes);
        ExpenseRequest current = findRequest(id);
        assertThat(current.getStatus()).isEqualTo(ExpenseStatus.APPROVED);
        assertThat(current.getVersion()).isEqualTo(1L);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(3);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id).get(2).getAction())
                .isEqualTo(ExpenseEventAction.APPROVE);
    }

    @Test
    void concurrentApprovalAndReturnAllowOneCommitAndOneOptimisticConflict() throws Exception {
        Long id = insertSubmittedFixture("承認差戻し競合", employee, FIXED_INSTANT);
        List<DecisionOutcome> outcomes = runConcurrentDecisions(id,
                new Decision(ExpenseEventAction.APPROVE, approver.getId()),
                new Decision(ExpenseEventAction.RETURN, secondApprover.getId()));

        assertOneSuccessAndOneOptimisticConflict(outcomes);
        ExpenseRequest current = findRequest(id);
        assertThat(current.getStatus()).isIn(ExpenseStatus.APPROVED, ExpenseStatus.RETURNED);
        assertThat(current.getVersion()).isEqualTo(1L);
        List<ExpenseEvent> events = expenseEventRepository.findByExpenseIdForDisplay(id);
        assertThat(events).hasSize(3);
        assertThat(events.get(2).getAction()).isIn(ExpenseEventAction.APPROVE, ExpenseEventAction.RETURN);
    }

    private void assertOneSuccessAndOneOptimisticConflict(List<DecisionOutcome> outcomes) {
        assertThat(outcomes).filteredOn(DecisionOutcome::succeeded).hasSize(1);
        List<DecisionOutcome> failures = outcomes.stream()
                .filter(outcome -> !outcome.succeeded())
                .toList();
        assertThat(failures).hasSize(1);
        assertThat(hasOptimisticLockCause(failures.get(0).failure())).isTrue();
    }

    private List<DecisionOutcome> runConcurrentDecisions(Long id, Decision first,
                                                         Decision second) throws Exception {
        CyclicBarrier bothReadVersionZero = new CyclicBarrier(2);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<DecisionOutcome>> futures = List.of(
                    executor.submit(() -> decideInIndependentTransaction(
                            id, first, bothReadVersionZero, transactionTemplate)),
                    executor.submit(() -> decideInIndependentTransaction(
                            id, second, bothReadVersionZero, transactionTemplate)));
            return futures.stream().map(future -> getWithTimeout(future)).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private DecisionOutcome decideInIndependentTransaction(Long id, Decision decision,
                                                           CyclicBarrier barrier,
                                                           TransactionTemplate transactionTemplate) {
        try {
            ExpenseStatus savedStatus = transactionTemplate.execute(transactionStatus -> {
                ExpenseRequest request = findRequest(id);
                assertThat(request.getVersion()).isZero();
                awaitBarrier(barrier);
                ExpenseStatus previousStatus = request.getStatus();
                ExpenseStatus nextStatus;
                if (decision.action() == ExpenseEventAction.APPROVE) {
                    nextStatus = request.approve(FIXED_INSTANT);
                    nextStatus = ExpenseStatus.APPROVED;
                } else {
                    nextStatus = request.returnForRevision(FIXED_INSTANT);
                    nextStatus = ExpenseStatus.RETURNED;
                }
                expenseRequestRepository.saveAndFlush(request);
                AppUser actor = appUserRepository.findById(decision.actorId()).orElseThrow();
                expenseEventRepository.saveAndFlush(ExpenseEvent.record(request, actor,
                        decision.action(), previousStatus, nextStatus,
                        decision.action() == ExpenseEventAction.RETURN ? "競合確認" : null,
                        FIXED_INSTANT));
                return nextStatus;
            });
            return DecisionOutcome.success(savedStatus);
        } catch (Throwable failure) {
            return DecisionOutcome.failure(failure);
        }
    }

    private void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("承認競合テストの同期に失敗しました", exception);
        }
    }

    private DecisionOutcome getWithTimeout(Future<DecisionOutcome> future) {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("承認競合テストがタイムアウトしました", exception);
        }
    }

    private boolean hasOptimisticLockCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof org.springframework.dao.OptimisticLockingFailureException
                    || current instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private AppUser saveUser(String username, String displayName, Department department,
                             UserRole role) {
        return appUserRepository.save(new AppUser(department, username, displayName,
                passwordEncoder.encode(PASSWORD), role, true));
    }

    private MockHttpSession loginAs(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/expenses"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Long createViaHttp(MockHttpSession session, String title, String purpose,
                               String category, String expenseDate, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/expenses")
                        .session(session)
                        .with(csrf())
                        .param("title", title)
                        .param("purpose", purpose)
                        .param("category", category)
                        .param("expenseDate", expenseDate)
                        .param("amount", amount))
                .andExpect(status().isSeeOther())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return Long.valueOf(URI.create(location).getPath()
                .substring(location.lastIndexOf('/') + 1));
    }

    private Long createAndSubmitViaHttp(String username, String title) throws Exception {
        MockHttpSession session = loginAs(username);
        Long id = createViaHttp(session, title, "用途", "OTHER", "2026-09-08", "100");
        mockMvc.perform(post("/expenses/{id}/submit", id)
                        .session(session).with(csrf()).param("version", "0"))
                .andExpect(status().isSeeOther());
        return id;
    }

    private ExpenseRequest findRequest(Long id) {
        return expenseRequestRepository.findWithRelationsById(id).orElseThrow();
    }

    private Long insertSubmittedFixture(String title, AppUser applicant, Instant submittedAt) {
        ExpenseRequest request = ExpenseRequest.create(applicant,
                applicant == developmentEmployee ? development : sales,
                title, "fixture用途", ExpenseCategory.OTHER,
                LocalDate.of(2026, 9, 8), new java.math.BigDecimal("100"),
                FIXED_INSTANT, LocalDate.of(2026, 9, 9));
        request = expenseRequestRepository.saveAndFlush(request);
        expenseEventRepository.saveAndFlush(ExpenseEvent.record(request, applicant,
                ExpenseEventAction.CREATE, null, ExpenseStatus.DRAFT, null, FIXED_INSTANT));
        jdbcTemplate.update("update expense_requests set status = ?, submitted_at = ?, updated_at = ? where id = ?",
                ExpenseStatus.SUBMITTED.name(), Timestamp.from(submittedAt), Timestamp.from(submittedAt),
                request.getId());
        AppUser actor = applicant == developmentEmployee ? developmentApprover : approver;
        expenseEventRepository.saveAndFlush(ExpenseEvent.record(request, actor,
                ExpenseEventAction.SUBMIT, ExpenseStatus.DRAFT, ExpenseStatus.SUBMITTED,
                null, submittedAt));
        return request.getId();
    }

    private Long insertStatusFixture(String title, AppUser applicant, ExpenseStatus status) {
        if (status == ExpenseStatus.DRAFT) {
            ExpenseRequest request = ExpenseRequest.create(applicant,
                    applicant == developmentEmployee ? development : sales,
                    title, "fixture用途", ExpenseCategory.OTHER,
                    LocalDate.of(2026, 9, 8), new java.math.BigDecimal("100"),
                    FIXED_INSTANT, LocalDate.of(2026, 9, 9));
            request = expenseRequestRepository.saveAndFlush(request);
            expenseEventRepository.saveAndFlush(ExpenseEvent.record(request, applicant,
                    ExpenseEventAction.CREATE, null, ExpenseStatus.DRAFT, null, FIXED_INSTANT));
            return request.getId();
        }
        Long id = insertSubmittedFixture(title, applicant, FIXED_INSTANT);
        jdbcTemplate.update("update expense_requests set status = ?, updated_at = ? where id = ?",
                status.name(), Timestamp.from(FIXED_INSTANT), id);
        ExpenseEventAction action = status == ExpenseStatus.RETURNED
                ? ExpenseEventAction.RETURN : ExpenseEventAction.APPROVE;
        AppUser actor = applicant == developmentEmployee ? developmentApprover : approver;
        expenseEventRepository.saveAndFlush(ExpenseEvent.record(findRequest(id), actor, action,
                ExpenseStatus.SUBMITTED, status,
                status == ExpenseStatus.RETURNED ? "修正してください" : null, FIXED_INSTANT));
        return id;
    }

    private void installFailingApprovalTrigger() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fail_approval_event() RETURNS trigger AS $$
                BEGIN
                    IF NEW.action IN ('APPROVE', 'RETURN') THEN
                        RAISE EXCEPTION 'test approval event failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_approval_event_trigger
                BEFORE INSERT ON expense_events
                FOR EACH ROW EXECUTE FUNCTION fail_approval_event()
                """);
    }

    private record Decision(ExpenseEventAction action, Long actorId) {
    }

    private record DecisionOutcome(ExpenseStatus status, Throwable failure) {

        static DecisionOutcome success(ExpenseStatus status) {
            return new DecisionOutcome(status, null);
        }

        static DecisionOutcome failure(Throwable failure) {
            return new DecisionOutcome(null, failure);
        }

        boolean succeeded() {
            return failure == null;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @org.springframework.context.annotation.Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }
}

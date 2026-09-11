package jp.example.expenseflow.feature.expense.domain;

import jp.example.expenseflow.feature.auth.domain.AppUser;
import jp.example.expenseflow.feature.auth.domain.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "expense_requests")
public class ExpenseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private AppUser applicant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpenseCategory category;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpenseStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    protected ExpenseRequest() {
    }

    public static ExpenseRequest create(AppUser applicant, Department department, String title,
                                        String purpose, ExpenseCategory category,
                                        LocalDate expenseDate, BigDecimal amount,
                                        Instant now, LocalDate today) {
        String normalizedTitle = normalize(title);
        String normalizedPurpose = normalize(purpose);
        validateDetails(normalizedTitle, normalizedPurpose, category, expenseDate, amount, today);
        if (applicant == null || department == null || now == null) {
            throw new IllegalArgumentException("申請者、部署、日時は必須です");
        }

        ExpenseRequest request = new ExpenseRequest();
        request.applicant = applicant;
        request.department = department;
        request.title = normalizedTitle;
        request.purpose = normalizedPurpose;
        request.category = category;
        request.expenseDate = expenseDate;
        request.amount = amount;
        request.status = ExpenseStatus.DRAFT;
        request.version = 0L;
        request.createdAt = now;
        request.updatedAt = now;
        return request;
    }

    public void updateDetails(String title, String purpose, ExpenseCategory category,
                              LocalDate expenseDate, BigDecimal amount, Instant now,
                              LocalDate today) {
        ensureStatus(ExpenseStatus.DRAFT, ExpenseStatus.RETURNED);
        String normalizedTitle = normalize(title);
        String normalizedPurpose = normalize(purpose);
        validateDetails(normalizedTitle, normalizedPurpose, category, expenseDate, amount, today);
        if (now == null) {
            throw new IllegalArgumentException("更新日時は必須です");
        }
        this.title = normalizedTitle;
        this.purpose = normalizedPurpose;
        this.category = category;
        this.expenseDate = expenseDate;
        this.amount = amount;
        this.updatedAt = now;
    }

    public ExpenseStatus submit(Instant now) {
        ensureStatus(ExpenseStatus.DRAFT, ExpenseStatus.RETURNED);
        if (now == null) {
            throw new IllegalArgumentException("申請日時は必須です");
        }
        ExpenseStatus previous = status;
        status = ExpenseStatus.SUBMITTED;
        submittedAt = now;
        updatedAt = now;
        return previous;
    }

    public ExpenseStatus approve(Instant now) {
        ensureStatus(ExpenseStatus.SUBMITTED);
        if (now == null) {
            throw new IllegalArgumentException("承認日時は必須です");
        }
        ExpenseStatus previous = status;
        status = ExpenseStatus.APPROVED;
        updatedAt = now;
        return previous;
    }

    public ExpenseStatus returnForRevision(Instant now) {
        ensureStatus(ExpenseStatus.SUBMITTED);
        if (now == null) {
            throw new IllegalArgumentException("差戻し日時は必須です");
        }
        ExpenseStatus previous = status;
        status = ExpenseStatus.RETURNED;
        updatedAt = now;
        return previous;
    }

    public boolean isEditable() {
        return status == ExpenseStatus.DRAFT || status == ExpenseStatus.RETURNED;
    }

    public boolean isDeletable() {
        return status == ExpenseStatus.DRAFT;
    }

    public boolean isSubmittable() {
        return isEditable();
    }

    private void ensureStatus(ExpenseStatus... allowed) {
        for (ExpenseStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("現在の状態では操作できません");
    }

    private static void validateDetails(String title, String purpose, ExpenseCategory category,
                                        LocalDate expenseDate, BigDecimal amount, LocalDate today) {
        if (title == null || title.isBlank() || title.length() > 100) {
            throw new IllegalArgumentException("件名は1〜100文字で入力してください");
        }
        if (purpose == null || purpose.isBlank() || purpose.length() > 500) {
            throw new IllegalArgumentException("用途は1〜500文字で入力してください");
        }
        if (category == null) {
            throw new IllegalArgumentException("分類を選択してください");
        }
        if (expenseDate == null || today == null || expenseDate.isAfter(today)) {
            throw new IllegalArgumentException("利用日は今日以前の日付を入力してください");
        }
        validateAmount(amount);
    }

    public static void validateAmount(BigDecimal amount) {
        if (amount == null
                || amount.compareTo(BigDecimal.ONE) < 0
                || amount.compareTo(new BigDecimal("1000000")) > 0
                || amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("金額は1〜1,000,000円の整数で入力してください");
        }
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip();
    }

    public Long getId() {
        return id;
    }

    public AppUser getApplicant() {
        return applicant;
    }

    public Department getDepartment() {
        return department;
    }

    public String getTitle() {
        return title;
    }

    public String getPurpose() {
        return purpose;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public ExpenseStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}

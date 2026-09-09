package jp.example.expenseflow.feature.expense.service.dto;

public class ApprovalListItem {

    private final Long id;
    private final String applicantName;
    private final String applicantUsername;
    private final String title;
    private final String categoryLabel;
    private final String expenseDate;
    private final String amount;
    private final String status;
    private final String statusLabel;
    private final String submittedAt;
    private final long version;

    public ApprovalListItem(Long id, String applicantName, String applicantUsername,
                           String title, String categoryLabel, String expenseDate,
                           String amount, String status, String statusLabel,
                           String submittedAt, long version) {
        this.id = id;
        this.applicantName = applicantName;
        this.applicantUsername = applicantUsername;
        this.title = title;
        this.categoryLabel = categoryLabel;
        this.expenseDate = expenseDate;
        this.amount = amount;
        this.status = status;
        this.statusLabel = statusLabel;
        this.submittedAt = submittedAt;
        this.version = version;
    }

    public Long getId() {
        return id;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public String getApplicantUsername() {
        return applicantUsername;
    }

    public String getTitle() {
        return title;
    }

    public String getCategoryLabel() {
        return categoryLabel;
    }

    public String getExpenseDate() {
        return expenseDate;
    }

    public String getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }

    public long getVersion() {
        return version;
    }
}

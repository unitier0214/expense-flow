package jp.example.expenseflow.feature.expense.service.dto;

import java.util.List;

public class ExpenseDetailView {

    private final Long id;
    private final String applicantName;
    private final String applicantUsername;
    private final String departmentName;
    private final String title;
    private final String purpose;
    private final String categoryLabel;
    private final String expenseDate;
    private final String amount;
    private final String status;
    private final String statusLabel;
    private final long version;
    private final String createdAt;
    private final String updatedAt;
    private final String submittedAt;
    private final List<ExpenseEventView> events;
    private final boolean canEdit;
    private final boolean canDelete;
    private final boolean canSubmit;
    private final boolean canApprove;

    public ExpenseDetailView(Long id, String applicantName, String applicantUsername,
                             String departmentName, String title, String purpose,
                             String categoryLabel, String expenseDate, String amount,
                             String status, String statusLabel, long version, String createdAt,
                             String updatedAt, String submittedAt, List<ExpenseEventView> events,
                             boolean canEdit, boolean canDelete, boolean canSubmit,
                             boolean canApprove) {
        this.id = id;
        this.applicantName = applicantName;
        this.applicantUsername = applicantUsername;
        this.departmentName = departmentName;
        this.title = title;
        this.purpose = purpose;
        this.categoryLabel = categoryLabel;
        this.expenseDate = expenseDate;
        this.amount = amount;
        this.status = status;
        this.statusLabel = statusLabel;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.submittedAt = submittedAt;
        this.events = events;
        this.canEdit = canEdit;
        this.canDelete = canDelete;
        this.canSubmit = canSubmit;
        this.canApprove = canApprove;
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

    public String getDepartmentName() {
        return departmentName;
    }

    public String getTitle() {
        return title;
    }

    public String getPurpose() {
        return purpose;
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

    public long getVersion() {
        return version;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }

    public List<ExpenseEventView> getEvents() {
        return events;
    }

    public boolean isCanEdit() {
        return canEdit;
    }

    public boolean isCanDelete() {
        return canDelete;
    }

    public boolean isCanSubmit() {
        return canSubmit;
    }

    public boolean isCanApprove() {
        return canApprove;
    }
}

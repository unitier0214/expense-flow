package jp.example.expenseflow.feature.expense.service.dto;

public class ExpenseListItem {

    private final Long id;
    private final String title;
    private final String category;
    private final String categoryLabel;
    private final String expenseDate;
    private final String amount;
    private final String status;
    private final String statusLabel;
    private final String updatedAt;

    public ExpenseListItem(Long id, String title, String category, String categoryLabel,
                           String expenseDate, String amount, String status,
                           String statusLabel, String updatedAt) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.categoryLabel = categoryLabel;
        this.expenseDate = expenseDate;
        this.amount = amount;
        this.status = status;
        this.statusLabel = statusLabel;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
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

    public String getUpdatedAt() {
        return updatedAt;
    }
}

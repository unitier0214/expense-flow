package jp.example.expenseflow.feature.expense.service.dto;

import jp.example.expenseflow.feature.expense.domain.ExpenseRequest;

public class ExpenseForm {

    private Long id;
    private String title;
    private String purpose;
    private String category;
    private String expenseDate;
    private String amount;
    private Long version;

    public ExpenseForm() {
    }

    public static ExpenseForm from(ExpenseRequest request) {
        ExpenseForm form = new ExpenseForm();
        form.id = request.getId();
        form.title = request.getTitle();
        form.purpose = request.getPurpose();
        form.category = request.getCategory().name();
        form.expenseDate = request.getExpenseDate().toString();
        form.amount = request.getAmount().toPlainString();
        form.version = request.getVersion();
        return form;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(String expenseDate) {
        this.expenseDate = expenseDate;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

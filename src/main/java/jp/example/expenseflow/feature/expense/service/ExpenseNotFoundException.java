package jp.example.expenseflow.feature.expense.service;

public class ExpenseNotFoundException extends RuntimeException {

    public ExpenseNotFoundException() {
        super("申請が見つかりません");
    }
}

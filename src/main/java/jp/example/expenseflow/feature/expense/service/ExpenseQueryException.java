package jp.example.expenseflow.feature.expense.service;

public class ExpenseQueryException extends RuntimeException {

    public ExpenseQueryException(String message) {
        super(message);
    }
}

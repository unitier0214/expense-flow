package jp.example.expenseflow.feature.expense.service;

public class ExpenseForbiddenException extends RuntimeException {

    public ExpenseForbiddenException() {
        super("この操作を行う権限がありません");
    }
}

package jp.example.expenseflow.feature.expense.service;

public class ExpenseConflictException extends RuntimeException {

    public ExpenseConflictException() {
        super("申請が別の操作で更新されています。最新情報を再読み込みしてください");
    }
}

package jp.example.expenseflow.feature.expense.service;

public class ExpenseExportLimitException extends RuntimeException {

    public ExpenseExportLimitException() {
        super("CSV出力の対象が1,000件を超えています。検索条件を追加して絞り込んでください。");
    }
}

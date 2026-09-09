package jp.example.expenseflow.feature.expense.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import jp.example.expenseflow.feature.expense.service.dto.ExpenseForm;

public class ExpenseInputException extends RuntimeException {

    private final ExpenseForm form;
    private final Map<String, String> fieldErrors;

    public ExpenseInputException(ExpenseForm form, Map<String, String> fieldErrors) {
        super("入力内容を確認してください");
        this.form = form;
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public ExpenseForm getForm() {
        return form;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}

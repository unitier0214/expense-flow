package jp.example.expenseflow.feature.expense.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import jp.example.expenseflow.feature.expense.service.dto.ApprovalForm;

public class ApprovalInputException extends RuntimeException {

    private final ApprovalForm form;
    private final Map<String, String> fieldErrors;

    public ApprovalInputException(ApprovalForm form, Map<String, String> fieldErrors) {
        super("入力内容を確認してください");
        this.form = form;
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    public ApprovalForm getForm() {
        return form;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}

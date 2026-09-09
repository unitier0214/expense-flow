package jp.example.expenseflow.feature.expense.service.dto;

public class ApprovalForm {

    private Long version;
    private String comment;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}

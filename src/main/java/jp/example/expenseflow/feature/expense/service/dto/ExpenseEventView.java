package jp.example.expenseflow.feature.expense.service.dto;

public class ExpenseEventView {

    private final String occurredAt;
    private final String actorName;
    private final String actionLabel;
    private final String fromStatusLabel;
    private final String toStatusLabel;
    private final String comment;

    public ExpenseEventView(String occurredAt, String actorName, String actionLabel,
                            String fromStatusLabel, String toStatusLabel, String comment) {
        this.occurredAt = occurredAt;
        this.actorName = actorName;
        this.actionLabel = actionLabel;
        this.fromStatusLabel = fromStatusLabel;
        this.toStatusLabel = toStatusLabel;
        this.comment = comment;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public String getActorName() {
        return actorName;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public String getFromStatusLabel() {
        return fromStatusLabel;
    }

    public String getToStatusLabel() {
        return toStatusLabel;
    }

    public String getComment() {
        return comment;
    }
}

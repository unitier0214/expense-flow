package jp.example.expenseflow.feature.expense.domain;

import jp.example.expenseflow.feature.auth.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "expense_events")
public class ExpenseEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false)
    private ExpenseRequest expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private AppUser actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpenseEventAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ExpenseStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ExpenseStatus toStatus;

    @Column(length = 500)
    private String comment;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ExpenseEvent() {
    }

    private ExpenseEvent(ExpenseRequest expense, AppUser actor, ExpenseEventAction action,
                         ExpenseStatus fromStatus, ExpenseStatus toStatus, String comment,
                         Instant occurredAt) {
        this.expense = expense;
        this.actor = actor;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.comment = comment;
        this.occurredAt = occurredAt;
    }

    public static ExpenseEvent record(ExpenseRequest expense, AppUser actor,
                                      ExpenseEventAction action, ExpenseStatus fromStatus,
                                      ExpenseStatus toStatus, String comment, Instant occurredAt) {
        if (expense == null || actor == null || action == null || toStatus == null || occurredAt == null) {
            throw new IllegalArgumentException("履歴に必要な値が不足しています");
        }
        return new ExpenseEvent(expense, actor, action, fromStatus, toStatus,
                normalizeComment(comment), occurredAt);
    }

    private static String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public Long getId() {
        return id;
    }

    public ExpenseRequest getExpense() {
        return expense;
    }

    public AppUser getActor() {
        return actor;
    }

    public ExpenseEventAction getAction() {
        return action;
    }

    public ExpenseStatus getFromStatus() {
        return fromStatus;
    }

    public ExpenseStatus getToStatus() {
        return toStatus;
    }

    public String getComment() {
        return comment;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}

package jp.example.expenseflow.feature.expense.service.dto;

import java.util.List;

public class ExpenseListPage {

    private final List<ExpenseListItem> items;
    private final long totalElements;
    private final int totalPages;
    private final int number;
    private final boolean hasPrevious;
    private final boolean hasNext;
    private final String status;
    private final String category;
    private final String from;
    private final String to;
    private final String q;

    public ExpenseListPage(List<ExpenseListItem> items, long totalElements, int totalPages,
                           int number, boolean hasPrevious, boolean hasNext, String status,
                           String category, String from, String to, String q) {
        this.items = items;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.number = number;
        this.hasPrevious = hasPrevious;
        this.hasNext = hasNext;
        this.status = status;
        this.category = category;
        this.from = from;
        this.to = to;
        this.q = q;
    }

    public List<ExpenseListItem> getItems() {
        return items;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getNumber() {
        return number;
    }

    public long getDisplayNumber() {
        return (long) number + 1;
    }

    public boolean isHasPrevious() {
        return hasPrevious;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public String getStatus() {
        return status;
    }

    public String getCategory() {
        return category;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getQ() {
        return q;
    }
}

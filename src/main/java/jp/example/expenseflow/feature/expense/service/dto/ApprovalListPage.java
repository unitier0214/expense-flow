package jp.example.expenseflow.feature.expense.service.dto;

import java.util.List;

public class ApprovalListPage {

    private final List<ApprovalListItem> items;
    private final long totalElements;
    private final int totalPages;
    private final int number;
    private final boolean hasPrevious;
    private final boolean hasNext;

    public ApprovalListPage(List<ApprovalListItem> items, long totalElements, int totalPages,
                            int number, boolean hasPrevious, boolean hasNext) {
        this.items = items;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.number = number;
        this.hasPrevious = hasPrevious;
        this.hasNext = hasNext;
    }

    public List<ApprovalListItem> getItems() {
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
}

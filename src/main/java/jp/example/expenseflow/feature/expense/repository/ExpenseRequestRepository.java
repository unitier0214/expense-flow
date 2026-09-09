package jp.example.expenseflow.feature.expense.repository;

import jp.example.expenseflow.feature.expense.domain.ExpenseRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRequestRepository extends JpaRepository<ExpenseRequest, Long> {
}

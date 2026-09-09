package jp.example.expenseflow.feature.expense.repository;

import jp.example.expenseflow.feature.expense.domain.ExpenseEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseEventRepository extends JpaRepository<ExpenseEvent, Long> {
}

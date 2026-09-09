package jp.example.expenseflow.feature.expense.repository;

import jp.example.expenseflow.feature.expense.domain.ExpenseEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseEventRepository extends JpaRepository<ExpenseEvent, Long> {

    @Query("""
            select e
            from ExpenseEvent e
            join fetch e.actor
            where e.expense.id = :expenseId
            order by e.occurredAt asc, e.id asc
            """)
    List<ExpenseEvent> findByExpenseIdForDisplay(@Param("expenseId") Long expenseId);
}

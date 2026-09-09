package jp.example.expenseflow.feature.expense.repository;

import jp.example.expenseflow.feature.expense.domain.ExpenseRequest;
import jp.example.expenseflow.feature.expense.domain.ExpenseCategory;
import jp.example.expenseflow.feature.expense.domain.ExpenseStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRequestRepository extends JpaRepository<ExpenseRequest, Long> {

    @EntityGraph(attributePaths = {"applicant", "department"})
    @Query(value = """
            select e
            from ExpenseRequest e
            where e.applicant.id = :applicantId
              and (:status is null or e.status = :status)
              and (:category is null or e.category = :category)
              and (:fromDate is null or e.expenseDate >= :fromDate)
              and (:toDate is null or e.expenseDate <= :toDate)
              and upper(e.title) like upper(concat('%', :q, '%')) escape '\\'
            """,
            countQuery = """
            select count(e)
            from ExpenseRequest e
            where e.applicant.id = :applicantId
              and (:status is null or e.status = :status)
              and (:category is null or e.category = :category)
              and (:fromDate is null or e.expenseDate >= :fromDate)
              and (:toDate is null or e.expenseDate <= :toDate)
              and upper(e.title) like upper(concat('%', :q, '%')) escape '\\'
            """)
    Page<ExpenseRequest> findOwnPage(@Param("applicantId") Long applicantId,
                                     @Param("status") ExpenseStatus status,
                                     @Param("category") ExpenseCategory category,
                                     @Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate,
                                     @Param("q") String q,
                                     Pageable pageable);

    @EntityGraph(attributePaths = {"applicant", "department"})
    @Query(value = """
            select e
            from ExpenseRequest e
            where e.applicant.id = :applicantId
              and (:status is null or e.status = :status)
              and (:category is null or e.category = :category)
              and (:fromDate is null or e.expenseDate >= :fromDate)
              and (:toDate is null or e.expenseDate <= :toDate)
            """,
            countQuery = """
            select count(e)
            from ExpenseRequest e
            where e.applicant.id = :applicantId
              and (:status is null or e.status = :status)
              and (:category is null or e.category = :category)
              and (:fromDate is null or e.expenseDate >= :fromDate)
              and (:toDate is null or e.expenseDate <= :toDate)
            """)
    Page<ExpenseRequest> findOwnPageWithoutQuery(@Param("applicantId") Long applicantId,
                                                  @Param("status") ExpenseStatus status,
                                                  @Param("category") ExpenseCategory category,
                                                  @Param("fromDate") LocalDate fromDate,
                                                  @Param("toDate") LocalDate toDate,
                                                  Pageable pageable);

    @EntityGraph(attributePaths = {"applicant", "department"})
    @Query("select e from ExpenseRequest e where e.id = :id")
    Optional<ExpenseRequest> findWithRelationsById(@Param("id") Long id);
}

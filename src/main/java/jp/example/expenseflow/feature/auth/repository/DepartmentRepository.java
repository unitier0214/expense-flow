package jp.example.expenseflow.feature.auth.repository;

import jp.example.expenseflow.feature.auth.domain.Department;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByName(String name);
}

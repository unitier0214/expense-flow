package jp.example.expenseflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import jp.example.expenseflow.feature.auth.domain.AppUser;
import jp.example.expenseflow.feature.auth.repository.AppUserRepository;
import jp.example.expenseflow.feature.auth.repository.DepartmentRepository;
import jp.example.expenseflow.feature.expense.domain.ExpenseEvent;
import jp.example.expenseflow.feature.expense.domain.ExpenseRequest;
import jp.example.expenseflow.feature.expense.domain.ExpenseStatus;
import jp.example.expenseflow.feature.expense.repository.ExpenseEventRepository;
import jp.example.expenseflow.feature.expense.repository.ExpenseRequestRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles({"test", "demo"})
@Testcontainers
class DemoDataInitializerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    DemoDataInitializer demoDataInitializer;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    DepartmentRepository departmentRepository;

    @Autowired
    ExpenseRequestRepository expenseRequestRepository;

    @Autowired
    ExpenseEventRepository expenseEventRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    void demoDataIsCreatedIdempotently() throws Exception {
        assertThat(departmentRepository.count()).isEqualTo(2);
        assertThat(appUserRepository.count()).isEqualTo(5);
        assertThat(expenseRequestRepository.count()).isEqualTo(45);
        assertThat(expenseEventRepository.count()).isEqualTo(101);

        long firstId = expenseRequestRepository.findByTitle("デモ申請-01").orElseThrow().getId();
        jdbcTemplate.update("update expense_requests set purpose = ? where id = ?",
                "利用者が確認したデモ用途", firstId);
        entityManager.clear();

        demoDataInitializer.run(new DefaultApplicationArguments());

        assertThat(departmentRepository.count()).isEqualTo(2);
        assertThat(appUserRepository.count()).isEqualTo(5);
        assertThat(expenseRequestRepository.count()).isEqualTo(45);
        assertThat(expenseEventRepository.count()).isEqualTo(101);
        ExpenseRequest first = expenseRequestRepository.findByTitle("デモ申請-01").orElseThrow();
        assertThat(first.getPurpose()).isEqualTo("利用者が確認したデモ用途");
        assertThat(expenseRequestRepository.findByTitle("デモ申請-01").orElseThrow().getStatus())
                .isEqualTo(ExpenseStatus.SUBMITTED);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(first.getId()))
                .extracting(ExpenseEvent::getAction)
                .containsExactly(
                        jp.example.expenseflow.feature.expense.domain.ExpenseEventAction.CREATE,
                        jp.example.expenseflow.feature.expense.domain.ExpenseEventAction.SUBMIT);

        AppUser employee = appUserRepository.findByUsername("demo.employee").orElseThrow();
        assertThat(employee.getDepartment().getName()).isEqualTo("営業部");
        assertThat(passwordEncoder.matches("demo-password", employee.getPasswordHash())).isTrue();
    }
}

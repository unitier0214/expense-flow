package jp.example.expenseflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import jp.example.expenseflow.feature.auth.domain.AppUser;
import jp.example.expenseflow.feature.auth.repository.AppUserRepository;
import jp.example.expenseflow.feature.auth.repository.DepartmentRepository;
import jp.example.expenseflow.feature.expense.domain.ExpenseEvent;
import jp.example.expenseflow.feature.expense.domain.ExpenseEventAction;
import jp.example.expenseflow.feature.expense.domain.ExpenseCategory;
import jp.example.expenseflow.feature.expense.domain.ExpenseRequest;
import jp.example.expenseflow.feature.expense.domain.ExpenseStatus;
import jp.example.expenseflow.feature.expense.repository.ExpenseEventRepository;
import jp.example.expenseflow.feature.expense.repository.ExpenseRequestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
    DemoSeedEntryRepository demoSeedEntryRepository;

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
        assertThat(demoSeedEntryRepository.count()).isEqualTo(45);

        long firstId = demoSeedEntryRepository.findById("expense-01").orElseThrow().getExpenseId();
        jdbcTemplate.update("update expense_requests set purpose = ? where id = ?",
                "利用者が確認したデモ用途", firstId);
        entityManager.clear();

        demoDataInitializer.run(new DefaultApplicationArguments());

        assertThat(departmentRepository.count()).isEqualTo(2);
        assertThat(appUserRepository.count()).isEqualTo(5);
        assertThat(expenseRequestRepository.count()).isEqualTo(45);
        assertThat(expenseEventRepository.count()).isEqualTo(101);
        assertThat(demoSeedEntryRepository.count()).isEqualTo(45);
        ExpenseRequest first = expenseRequestRepository.findById(firstId).orElseThrow();
        assertThat(first.getPurpose()).isEqualTo("利用者が確認したデモ用途");
        assertThat(first.getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(first.getId()))
                .extracting(ExpenseEvent::getAction)
                .containsExactly(
                        ExpenseEventAction.CREATE, ExpenseEventAction.SUBMIT);

        AppUser employee = appUserRepository.findByUsername("demo.employee").orElseThrow();
        ExpenseRequest duplicate = ExpenseRequest.create(employee, employee.getDepartment(),
                "デモ申請-01", "同名申請", ExpenseCategory.OTHER,
                LocalDate.of(2026, 9, 8), new BigDecimal("999"),
                Instant.parse("2026-09-11T00:00:00Z"), LocalDate.of(2026, 9, 11));
        duplicate = expenseRequestRepository.saveAndFlush(duplicate);
        expenseEventRepository.saveAndFlush(ExpenseEvent.record(duplicate, employee,
                ExpenseEventAction.CREATE, null, ExpenseStatus.DRAFT, null,
                Instant.parse("2026-09-11T00:00:00Z")));
        assertThat(expenseRequestRepository.count()).isEqualTo(46);

        jdbcTemplate.update("update expense_requests set title = ? where id = ?",
                "利用者が変更したデモ件名", firstId);
        long deletedSeedId = demoSeedEntryRepository.findById("expense-04").orElseThrow()
                .getExpenseId();
        jdbcTemplate.update("delete from expense_requests where id = ?", deletedSeedId);
        entityManager.clear();

        assertThat(expenseRequestRepository.count()).isEqualTo(45);
        assertThat(demoSeedEntryRepository.findById("expense-04")).isPresent();
        assertThat(demoSeedEntryRepository.findById("expense-04").orElseThrow().getExpenseId())
                .isEqualTo(deletedSeedId);

        demoDataInitializer.run(new DefaultApplicationArguments());
        demoDataInitializer.run(new DefaultApplicationArguments());

        assertThat(departmentRepository.count()).isEqualTo(2);
        assertThat(appUserRepository.count()).isEqualTo(5);
        assertThat(expenseRequestRepository.count()).isEqualTo(45);
        assertThat(expenseEventRepository.count()).isEqualTo(101);
        assertThat(demoSeedEntryRepository.count()).isEqualTo(45);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from expense_requests where title = ?", Integer.class,
                "デモ申請-01")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from expense_requests where title = ?", Integer.class,
                "利用者が変更したデモ件名")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from expense_requests where title = ?", Integer.class,
                "デモ申請-04")).isZero();

        assertThat(employee.getDepartment().getName()).isEqualTo("営業部");
        assertThat(passwordEncoder.matches("demo-password", employee.getPasswordHash())).isTrue();
    }
}

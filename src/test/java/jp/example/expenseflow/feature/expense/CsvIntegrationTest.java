package jp.example.expenseflow.feature.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import jp.example.expenseflow.feature.auth.domain.AppUser;
import jp.example.expenseflow.feature.auth.domain.Department;
import jp.example.expenseflow.feature.auth.domain.UserRole;
import jp.example.expenseflow.feature.auth.repository.AppUserRepository;
import jp.example.expenseflow.feature.auth.repository.DepartmentRepository;
import jp.example.expenseflow.feature.expense.domain.ExpenseCategory;
import jp.example.expenseflow.feature.expense.domain.ExpenseStatus;
import jp.example.expenseflow.feature.expense.repository.ExpenseEventRepository;
import jp.example.expenseflow.feature.expense.repository.ExpenseRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CsvIntegrationTest.FixedClockConfiguration.class)
@Testcontainers
class CsvIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-08T15:00:00Z");
    private static final String PASSWORD = "test-password";
    private static final String CSV_HEADER = "ID,件名,分類,利用日,金額,状態,更新日時";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

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

    private Department sales;
    private Department development;
    private AppUser employee;
    private AppUser otherEmployee;

    @BeforeEach
    void setUpUsers() {
        expenseEventRepository.deleteAllInBatch();
        expenseRequestRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        departmentRepository.deleteAllInBatch();

        sales = departmentRepository.save(new Department("営業部"));
        development = departmentRepository.save(new Department("開発部"));
        employee = saveUser("test.employee", "テスト社員", sales);
        otherEmployee = saveUser("test.other", "別社員", sales);
        saveUser("test.employee.dev", "開発社員", development);
    }

    @Test
    void unauthenticatedUserCannotExport() throws Exception {
        mockMvc.perform(get("/expenses/export.csv"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void exportUsesOwnSearchFiltersAndUpdatedOrder() throws Exception {
        insertExpense("対象旧", employee, sales, ExpenseStatus.SUBMITTED,
                FIXED_INSTANT.minusSeconds(60), ExpenseCategory.OTHER, "100");
        insertExpense("対象新", employee, sales, ExpenseStatus.SUBMITTED,
                FIXED_INSTANT, ExpenseCategory.OTHER, "200");
        insertExpense("対象外の別社員", otherEmployee, sales, ExpenseStatus.SUBMITTED,
                FIXED_INSTANT.plusSeconds(60), ExpenseCategory.OTHER, "300");
        insertExpense("対象外の状態", employee, sales, ExpenseStatus.DRAFT,
                FIXED_INSTANT.plusSeconds(120), ExpenseCategory.OTHER, "400");
        insertExpense("対象外の分類", employee, sales, ExpenseStatus.SUBMITTED,
                FIXED_INSTANT.plusSeconds(180), ExpenseCategory.TRANSPORT, "500");

        MockHttpSession session = loginAs(employee.getUsername());
        MvcResult result = mockMvc.perform(get("/expenses/export.csv")
                        .session(session)
                        .param("status", "SUBMITTED")
                        .param("category", "OTHER")
                        .param("from", "2026-09-08")
                        .param("to", "2026-09-09")
                        .param("q", "対象"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"expense-flow-expenses.csv\""))
                .andReturn();

        String csv = csvBody(result);
        assertThat(csv).startsWith("\uFEFF" + CSV_HEADER + "\r\n");
        assertThat(csv.indexOf("対象新")).isLessThan(csv.indexOf("対象旧"));
        assertThat(csv).contains("対象新", "対象旧", ",200,申請中,");
        assertThat(csv).doesNotContain("対象外の別社員", "対象外の状態", "対象外の分類");
    }

    @Test
    void exportWithNoMatchesReturnsBomAndHeaderOnly() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());

        MvcResult result = mockMvc.perform(get("/expenses/export.csv")
                        .session(session)
                        .param("q", "存在しない件名"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(csvBody(result)).isEqualTo("\uFEFF" + CSV_HEADER + "\r\n");
    }

    @Test
    void exportEscapesJapaneseQuotesCommaNewlineAndFormulaPrefix() throws Exception {
        insertExpense("日本語, \"引用\"\n改行", employee, sales, ExpenseStatus.DRAFT,
                FIXED_INSTANT, ExpenseCategory.OTHER, "1234");
        insertExpense("  =1+1", employee, sales, ExpenseStatus.DRAFT,
                FIXED_INSTANT.minusSeconds(60), ExpenseCategory.OTHER, "5678");

        MvcResult result = mockMvc.perform(get("/expenses/export.csv")
                        .session(loginAs(employee.getUsername())))
                .andExpect(status().isOk())
                .andReturn();

        String csv = csvBody(result);
        assertThat(csv).contains("\"日本語, \"\"引用\"\"\n改行\"");
        assertThat(csv).contains("'  =1+1");
        assertThat(csv).contains(",1234,下書き,");
        assertThat(csv).contains("ID,件名,分類,利用日,金額,状態,更新日時");
    }

    @Test
    void exportExactlyOneThousandRows() throws Exception {
        insertBatch(1000);

        MvcResult result = mockMvc.perform(get("/expenses/export.csv")
                        .session(loginAs(employee.getUsername())))
                .andExpect(status().isOk())
                .andReturn();

        String[] rows = csvBody(result).split("\\r\\n", -1);
        assertThat(rows[0]).isEqualTo("\uFEFF" + CSV_HEADER);
        assertThat(rows).hasSize(1002);
        assertThat(rows[1001]).isEmpty();
    }

    @Test
    void exportOverOneThousandRowsReturnsBadRequestInsteadOfTruncating() throws Exception {
        insertBatch(1001);

        mockMvc.perform(get("/expenses/export.csv")
                        .session(loginAs(employee.getUsername())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1,000件を超えています")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("絞り込んでください")));
    }

    @Test
    void expenseListCsvLinkKeepsSearchConditions() throws Exception {
        insertExpense("検索対象", employee, sales, ExpenseStatus.SUBMITTED,
                FIXED_INSTANT, ExpenseCategory.OTHER, "100");

        mockMvc.perform(get("/expenses")
                        .session(loginAs(employee.getUsername()))
                        .param("status", "SUBMITTED")
                        .param("category", "OTHER")
                        .param("from", "2026-09-08")
                        .param("to", "2026-09-09")
                        .param("q", "検索"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "/expenses/export.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("status=SUBMITTED")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("category=OTHER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("from=2026-09-08")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("to=2026-09-09")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("q=")));
    }

    private AppUser saveUser(String username, String displayName, Department department) {
        return appUserRepository.save(new AppUser(
                department, username, displayName, passwordEncoder.encode(PASSWORD),
                UserRole.EMPLOYEE, true));
    }

    private Long insertExpense(String title, AppUser applicant, Department department,
                               ExpenseStatus status, Instant updatedAt,
                               ExpenseCategory category, String amount) {
        Timestamp timestamp = Timestamp.from(updatedAt);
        return jdbcTemplate.queryForObject("""
                insert into expense_requests
                    (applicant_id, department_id, title, purpose, category, expense_date,
                     amount, status, version, created_at, updated_at, submitted_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id
                """, Long.class,
                applicant.getId(), department.getId(), title, "用途",
                category.name(), java.sql.Date.valueOf(LocalDate.of(2026, 9, 8)),
                new BigDecimal(amount), status.name(), 0L, timestamp, timestamp,
                status == ExpenseStatus.SUBMITTED ? timestamp : null);
    }

    private void insertBatch(int count) {
        jdbcTemplate.batchUpdate("""
                insert into expense_requests
                    (applicant_id, department_id, title, purpose, category, expense_date,
                     amount, status, version, created_at, updated_at, submitted_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                int number = index + 1;
                Timestamp timestamp = Timestamp.from(FIXED_INSTANT.minusSeconds(number));
                ps.setLong(1, employee.getId());
                ps.setLong(2, sales.getId());
                ps.setString(3, "CSV確認-" + number);
                ps.setString(4, "用途");
                ps.setString(5, ExpenseCategory.OTHER.name());
                ps.setDate(6, java.sql.Date.valueOf(LocalDate.of(2026, 9, 8)));
                ps.setBigDecimal(7, BigDecimal.valueOf(100L + number));
                ps.setString(8, ExpenseStatus.DRAFT.name());
                ps.setLong(9, 0L);
                ps.setTimestamp(10, timestamp);
                ps.setTimestamp(11, timestamp);
                ps.setNull(12, Types.TIMESTAMP);
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });
    }

    private MockHttpSession loginAs(String username) throws Exception {
        MvcResult loginResult = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) loginResult.getRequest().getSession(false);
    }

    private String csvBody(MvcResult result) throws Exception {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @org.springframework.context.annotation.Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }
}

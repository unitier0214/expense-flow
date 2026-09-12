package jp.example.expenseflow.feature.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import jp.example.expenseflow.feature.auth.domain.AppUser;
import jp.example.expenseflow.feature.auth.domain.Department;
import jp.example.expenseflow.feature.auth.domain.UserRole;
import jp.example.expenseflow.feature.auth.repository.AppUserRepository;
import jp.example.expenseflow.feature.auth.repository.DepartmentRepository;
import jp.example.expenseflow.feature.expense.domain.ExpenseCategory;
import jp.example.expenseflow.feature.expense.domain.ExpenseEvent;
import jp.example.expenseflow.feature.expense.domain.ExpenseEventAction;
import jp.example.expenseflow.feature.expense.domain.ExpenseRequest;
import jp.example.expenseflow.feature.expense.domain.ExpenseStatus;
import jp.example.expenseflow.feature.expense.repository.ExpenseEventRepository;
import jp.example.expenseflow.feature.expense.repository.ExpenseRequestRepository;
import jp.example.expenseflow.feature.expense.service.ExpenseService;
import jp.example.expenseflow.feature.expense.service.dto.ExpenseForm;
import jp.example.expenseflow.feature.expense.service.dto.ExpenseListPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
@Import(ExpenseIntegrationTest.FixedClockConfiguration.class)
@Testcontainers
class ExpenseIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-08T15:00:00Z");
    private static final String PASSWORD = "test-password";

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
    ExpenseService expenseService;

    @Autowired
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    MutableTestClock testClock;

    private Department sales;
    private Department development;
    private AppUser employee;
    private AppUser approver;
    private AppUser secondApprover;
    private AppUser developmentEmployee;
    private AppUser developmentApprover;

    @BeforeEach
    void setUpUsers() {
        testClock.setInstant(FIXED_INSTANT);
        expenseEventRepository.deleteAllInBatch();
        expenseRequestRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
        departmentRepository.deleteAllInBatch();

        sales = departmentRepository.save(new Department("営業部"));
        development = departmentRepository.save(new Department("開発部"));
        employee = saveUser("test.employee", "テスト社員", sales, UserRole.EMPLOYEE);
        approver = saveUser("test.approver", "テスト承認者", sales, UserRole.APPROVER);
        secondApprover = saveUser("test.approver.2", "テスト承認者2", sales, UserRole.APPROVER);
        developmentEmployee = saveUser("test.employee.dev", "開発社員", development, UserRole.EMPLOYEE);
        developmentApprover = saveUser("test.approver.dev", "開発承認者", development, UserRole.APPROVER);
    }

    @AfterEach
    void removeFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_expense_event_trigger ON expense_events");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_expense_event()");
    }

    @Test
    void employeeCanCreateEditSubmitAndSeeHistory() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());

        Long id = createViaHttp(session, "  東京出張  ", "  顧客訪問  ", "TRANSPORT", "2026-09-08", "1000");
        ExpenseRequest created = findRequest(id);
        assertThat(created.getApplicant().getId()).isEqualTo(employee.getId());
        assertThat(created.getDepartment().getId()).isEqualTo(sales.getId());
        assertThat(created.getTitle()).isEqualTo("東京出張");
        assertThat(created.getPurpose()).isEqualTo("顧客訪問");
        assertThat(created.getStatus()).isEqualTo(ExpenseStatus.DRAFT);
        assertThat(created.getAmount()).isEqualByComparingTo("1000");
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(1);

        mockMvc.perform(get("/expenses/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("東京出張")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026/09/09 00:00")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("作成")));

        ExpenseForm editForm = ExpenseForm.from(created);
        editForm.setTitle("東京出張（変更）");
        editForm.setAmount("2000");
        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(session)
                        .with(csrf())
                        .param("title", editForm.getTitle())
                        .param("purpose", editForm.getPurpose())
                        .param("category", editForm.getCategory())
                        .param("expenseDate", editForm.getExpenseDate())
                        .param("amount", editForm.getAmount())
                        .param("version", editForm.getVersion().toString()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/expenses/" + id));

        ExpenseRequest updated = findRequest(id);
        assertThat(updated.getTitle()).isEqualTo("東京出張（変更）");
        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(2);

        mockMvc.perform(post("/expenses/{id}/submit", id)
                        .session(session)
                        .with(csrf())
                        .param("version", Long.toString(updated.getVersion())))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/expenses/" + id));

        ExpenseRequest submitted = findRequest(id);
        assertThat(submitted.getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
        assertThat(submitted.getSubmittedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(submitted.getVersion()).isEqualTo(2L);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(3);
    }

    @Test
    void invalidFormReturns400AndKeepsOriginalInput() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());

        mockMvc.perform(post("/expenses")
                        .session(session)
                        .with(csrf())
                        .param("title", " ")
                        .param("purpose", "")
                        .param("category", "NOT_A_CATEGORY")
                        .param("expenseDate", "2026-09-10")
                        .param("amount", "1.5"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1.5")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("半角数字の整数")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("利用日は今日以前")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("分類の指定が不正")));

        assertThat(expenseRequestRepository.count()).isZero();
        assertThat(expenseEventRepository.count()).isZero();
    }

    @Test
    void fullWidthWhitespaceIsRejectedForCreateAndEditWithoutChangingData() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        String fullWidthWhitespace = "\u3000\u3000";

        mockMvc.perform(post("/expenses")
                        .session(session)
                        .with(csrf())
                        .param("title", fullWidthWhitespace)
                        .param("purpose", "用途")
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-09")
                        .param("amount", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "件名は1〜100文字で入力してください")));
        assertThat(expenseRequestRepository.count()).isZero();
        assertThat(expenseEventRepository.count()).isZero();

        Long id = createViaHttp(session, "空白確認", "元の用途", "OTHER", "2026-09-09", "100");
        ExpenseRequest before = findRequest(id);
        long versionBefore = before.getVersion();
        int eventCountBefore = expenseEventRepository.findByExpenseIdForDisplay(id).size();

        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(session)
                        .with(csrf())
                        .param("title", "空白確認")
                        .param("purpose", fullWidthWhitespace)
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-09")
                        .param("amount", "100")
                        .param("version", Long.toString(versionBefore)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "用途は1〜500文字で入力してください")));

        ExpenseRequest unchanged = findRequest(id);
        assertThat(unchanged.getTitle()).isEqualTo("空白確認");
        assertThat(unchanged.getPurpose()).isEqualTo("元の用途");
        assertThat(unchanged.getStatus()).isEqualTo(ExpenseStatus.DRAFT);
        assertThat(unchanged.getVersion()).isEqualTo(versionBefore);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id))
                .hasSize(eventCountBefore);

        assertThatThrownBy(() -> ExpenseRequest.create(employee, sales, fullWidthWhitespace,
                "用途", ExpenseCategory.OTHER, LocalDate.of(2026, 9, 9),
                new java.math.BigDecimal("100"), FIXED_INSTANT,
                LocalDate.of(2026, 9, 9)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("件名は1〜100文字で入力してください");
        assertThatThrownBy(() -> unchanged.updateDetails("空白確認", fullWidthWhitespace,
                ExpenseCategory.OTHER, LocalDate.of(2026, 9, 9),
                new java.math.BigDecimal("100"), FIXED_INSTANT,
                LocalDate.of(2026, 9, 9)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用途は1〜500文字で入力してください");
    }

    @Test
    void editValidationKeepsPathIdAndAllowsRecoveryOnTheOriginalRequest() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        Long id = createViaHttp(session, "編集復帰前", "用途", "OTHER", "2026-09-08", "100");
        Long otherId = createViaHttp(session, "別申請", "別用途", "OTHER", "2026-09-08", "200");

        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(session)
                        .with(csrf())
                        .param("title", "編集復帰前")
                        .param("purpose", "用途")
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-08")
                        .param("amount", "1.5")
                        .param("version", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/expenses/" + id + "/edit\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/expenses/" + id + "\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1.5")));

        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(session)
                        .with(csrf())
                        .param("id", otherId.toString())
                        .param("title", "編集復帰前")
                        .param("purpose", "用途")
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-08")
                        .param("amount", "not-a-number")
                        .param("version", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "versionの形式が不正です")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "action=\"/expenses/" + id + "/edit\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/expenses/" + id + "\"")));

        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(session)
                        .with(csrf())
                        .param("id", otherId.toString())
                        .param("title", "編集復帰後")
                        .param("purpose", "用途")
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-08")
                        .param("amount", "300")
                        .param("version", "0"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/expenses/" + id));

        assertThat(expenseRequestRepository.count()).isEqualTo(2);
        assertThat(findRequest(id).getTitle()).isEqualTo("編集復帰後");
        assertThat(findRequest(otherId).getTitle()).isEqualTo("別申請");
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(2);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(otherId)).hasSize(1);
    }

    @Test
    void invalidAmountFormatsAreRejectedWithoutRounding() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        for (String amount : List.of("1.5", "1.0", "1e3", "1,000", "abc", "0", "1000001")) {
            mockMvc.perform(post("/expenses")
                            .session(session)
                            .with(csrf())
                            .param("title", "金額確認" + amount)
                            .param("purpose", "入力形式確認")
                            .param("category", "OTHER")
                            .param("expenseDate", "2026-09-09")
                            .param("amount", amount))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(amount)));
        }
        assertThat(expenseRequestRepository.count()).isZero();
    }

    @Test
    void boundaryValuesAndJstTodayAreAccepted() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        Long minimumId = createViaHttp(session, "a".repeat(100), "b".repeat(500), "OTHER",
                "2026-09-09", "1");
        Long maximumId = createViaHttp(session, "上限確認", "上限確認", "OTHER", "2026-09-09",
                "1000000");

        assertThat(findRequest(minimumId).getTitle()).hasSize(100);
        assertThat(findRequest(minimumId).getPurpose()).hasSize(500);
        assertThat(findRequest(minimumId).getAmount()).isEqualByComparingTo("1");
        assertThat(findRequest(maximumId).getAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    void overlongTitleAndPurposeAreRejectedForCreateAndEditWithoutChangingData() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        Long id = createViaHttp(session, "変更前", "変更前の用途", "OTHER", "2026-09-09", "100");
        for (String field : List.of("title", "purpose")) {
            for (String endpoint : List.of("/expenses", "/expenses/" + id + "/edit")) {
                mockMvc.perform(post(endpoint).session(session).with(csrf())
                                .param("title", field.equals("title") ? "a".repeat(101) : "変更後")
                                .param("purpose", field.equals("purpose") ? "b".repeat(501) : "変更後の用途")
                                .param("category", "OTHER").param("expenseDate", "2026-09-09")
                                .param("amount", "100").param("version", "0"))
                        .andExpect(status().isBadRequest());
                assertThat(expenseRequestRepository.count()).isEqualTo(1);
                ExpenseRequest unchanged = findRequest(id);
                assertThat(unchanged.getTitle()).isEqualTo("変更前");
                assertThat(unchanged.getPurpose()).isEqualTo("変更前の用途");
                assertThat(unchanged.getVersion()).isZero();
                assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(1);
            }
        }
    }

    @Test
    void sameExpenseDateChangesFromFutureToTodayAtJstMidnight() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        testClock.setInstant(FIXED_INSTANT.minusSeconds(1)); // JST 2026-09-08 23:59:59
        mockMvc.perform(post("/expenses").session(session).with(csrf())
                        .param("title", "日付境界").param("purpose", "境界確認")
                        .param("category", "OTHER").param("expenseDate", "2026-09-09")
                        .param("amount", "100"))
                .andExpect(status().isBadRequest());
        assertThat(expenseRequestRepository.count()).isZero();
        assertThat(expenseEventRepository.count()).isZero();
        testClock.setInstant(FIXED_INSTANT); // JST 2026-09-09 00:00:00
        Long id = createViaHttp(session, "日付境界", "境界確認", "OTHER", "2026-09-09", "100");
        assertThat(findRequest(id).getExpenseDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(1);
    }

    @Test
    void mvcClientErrorsKeepStatusAndAllowHeader() throws Exception {
        mockMvc.perform(get("/css/missing.css"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("照合ID"))));
        mockMvc.perform(put("/expenses").session(loginAs(employee.getUsername())).with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Exception"))));
    }

    @Test
    void hugePagesKeepOwnFiltersTotalsAndPositiveDisplayNumber() throws Exception {
        for (int index = 0; index < 21; index++) {
            insertStatusFixture(ExpenseStatus.DRAFT);
        }
        insertStatusFixture(ExpenseStatus.RETURNED);
        createViaHttp(loginAs(developmentEmployee.getUsername()), "fixture-DRAFT", "他部署",
                "OTHER", "2026-09-08", "100");
        MockHttpSession session = loginAs(employee.getUsername());
        for (int number : List.of(10, 107374182, 107374183, Integer.MAX_VALUE)) {
            for (boolean filtered : List.of(false, true)) {
                var request = get("/expenses").session(session).param("page", Integer.toString(number));
                if (filtered) {
                    request.param("status", "DRAFT").param("category", "OTHER")
                            .param("from", "2026-09-08").param("to", "2026-09-08")
                            .param("q", "fixture-DRAFT");
                }
                MvcResult result = mockMvc.perform(request).andExpect(status().isOk())
                        .andExpect(content().string(org.hamcrest.Matchers.containsString(
                                ((long) number + 1) + " / 2"))).andReturn();
                ExpenseListPage page = (ExpenseListPage) result.getModelAndView().getModel().get("page");
                assertThat(page.getItems()).isEmpty();
                assertThat(page.getTotalElements()).isEqualTo(filtered ? 21 : 22);
                assertThat(page.getNumber()).isEqualTo(number);
                assertThat(page.isHasNext()).isFalse();
                if (filtered) {
                    assertThat(page.getQ()).isEqualTo("fixture-DRAFT");
                    assertThat(result.getResponse().getContentAsString()).contains("q=fixture-DRAFT");
                }
            }
        }
    }

    @Test
    void expensePostsRequireCsrf() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());

        mockMvc.perform(post("/expenses")
                        .session(session)
                        .param("title", "CSRF確認")
                        .param("purpose", "用途")
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-09")
                        .param("amount", "100"))
                .andExpect(status().isForbidden());

        assertThat(expenseRequestRepository.count()).isZero();
    }

    @Test
    void draftCanBeDeletedAndItsHistoryIsRemoved() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        Long id = createViaHttp(session, "削除確認", "用途", "OTHER", "2026-09-09", "100");
        long version = findRequest(id).getVersion();

        mockMvc.perform(post("/expenses/{id}/delete", id)
                        .session(session)
                        .with(csrf())
                        .param("version", Long.toString(version)))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/expenses"));

        assertThat(expenseRequestRepository.findById(id)).isEmpty();
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).isEmpty();
        mockMvc.perform(get("/expenses/{id}", id).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnedRequestCanBeEditedAndResubmittedButNotDeleted() throws Exception {
        Long id = insertStatusFixture(ExpenseStatus.RETURNED);
        MockHttpSession session = loginAs(employee.getUsername());

        mockMvc.perform(get("/expenses/{id}/edit", id).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("経費申請を編集")));

        mockMvc.perform(post("/expenses/{id}/delete", id)
                        .session(session)
                        .with(csrf())
                        .param("version", "0"))
                .andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("更新できません")));

        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(session)
                        .with(csrf())
                        .param("title", "差戻し修正")
                        .param("purpose", "理由を修正")
                        .param("category", "SUPPLIES")
                        .param("expenseDate", "2026-09-08")
                        .param("amount", "3000")
                        .param("version", "0"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/expenses/" + id));

        ExpenseRequest edited = findRequest(id);
        assertThat(edited.getStatus()).isEqualTo(ExpenseStatus.RETURNED);
        assertThat(edited.getVersion()).isEqualTo(1L);

        mockMvc.perform(post("/expenses/{id}/submit", id)
                        .session(session)
                        .with(csrf())
                        .param("version", "1"))
                .andExpect(status().isSeeOther());

        assertThat(findRequest(id).getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(4);
    }

    @Test
    void approvedRequestCannotBeEditedDeletedOrSubmitted() throws Exception {
        Long id = insertStatusFixture(ExpenseStatus.APPROVED);
        MockHttpSession session = loginAs(employee.getUsername());

        mockMvc.perform(get("/expenses/{id}/edit", id).session(session))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(session)
                        .with(csrf())
                        .param("title", "変更不可")
                        .param("purpose", "変更不可")
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-08")
                        .param("amount", "100")
                        .param("version", "0"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/expenses/{id}/delete", id)
                        .session(session)
                        .with(csrf())
                        .param("version", "0"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/expenses/{id}/submit", id)
                        .session(session)
                        .with(csrf())
                        .param("version", "0"))
                .andExpect(status().isConflict());

        assertThat(findRequest(id).getStatus()).isEqualTo(ExpenseStatus.APPROVED);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(2);
    }

    @Test
    void accessIsRestrictedByOwnerAndDepartment() throws Exception {
        MockHttpSession employeeSession = loginAs(employee.getUsername());
        Long draftId = createViaHttp(employeeSession, "本人下書き", "用途", "OTHER", "2026-09-08", "100");

        mockMvc.perform(get("/expenses/{id}", draftId).session(loginAs(approver.getUsername())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/expenses/{id}", draftId).session(loginAs(developmentApprover.getUsername())))
                .andExpect(status().isNotFound());

        ExpenseRequest draft = findRequest(draftId);
        mockMvc.perform(post("/expenses/{id}/submit", draftId)
                        .session(employeeSession)
                        .with(csrf())
                        .param("version", Long.toString(draft.getVersion())))
                .andExpect(status().isSeeOther());

        mockMvc.perform(get("/expenses/{id}", draftId).session(loginAs(approver.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("本人下書き")));
        mockMvc.perform(get("/expenses/{id}", draftId).session(loginAs(developmentApprover.getUsername())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/expenses/{id}", draftId).session(loginAs(developmentEmployee.getUsername())))
                .andExpect(status().isNotFound());

        Long submittedVersion = findRequest(draftId).getVersion();
        for (String path : List.of("edit", "delete", "submit")) {
            mockMvc.perform(post("/expenses/{id}/" + path, draftId)
                            .session(loginAs(approver.getUsername()))
                            .with(csrf())
                            .param("version", Long.toString(submittedVersion))
                            .param("title", "他人変更")
                            .param("purpose", "他人変更")
                            .param("category", "OTHER")
                            .param("expenseDate", "2026-09-08")
                            .param("amount", "100"))
                    .andExpect(status().isNotFound());
        }

        assertThat(findRequest(draftId).getStatus()).isEqualTo(ExpenseStatus.SUBMITTED);
    }

    @Test
    void approverCanCreateAndListOnlyOwnRequests() throws Exception {
        MockHttpSession session = loginAs(approver.getUsername());
        Long id = createViaHttp(session, "承認者本人の申請", "自分の経費", "OTHER", "2026-09-09", "500");

        assertThat(findRequest(id).getApplicant().getId()).isEqualTo(approver.getId());
        mockMvc.perform(get("/expenses").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("承認者本人の申請")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("テスト社員の申請"))));
    }

    @Test
    void unexpectedApplicantParametersCannotChangeServerOwnedFields() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        Long id = createViaHttp(session, "サーバー所有確認", "用途", "OTHER", "2026-09-08", "100",
                "applicant_id", approver.getId().toString(),
                "department_id", development.getId().toString(),
                "status", "APPROVED");

        ExpenseRequest request = findRequest(id);
        assertThat(request.getApplicant().getId()).isEqualTo(employee.getId());
        assertThat(request.getDepartment().getId()).isEqualTo(sales.getId());
        assertThat(request.getStatus()).isEqualTo(ExpenseStatus.DRAFT);

        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(session)
                        .with(csrf())
                        .param("title", "サーバー所有確認2")
                        .param("purpose", "用途")
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-08")
                        .param("amount", "100")
                        .param("version", Long.toString(request.getVersion()))
                        .param("status", "APPROVED")
                        .param("applicant_id", approver.getId().toString()))
                .andExpect(status().isSeeOther());
        assertThat(findRequest(id).getStatus()).isEqualTo(ExpenseStatus.DRAFT);
    }

    @Test
    void listUsesOwnFiltersLiteralLikeCharactersAndTwentyItemPages() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        for (int number = 1; number <= 21; number++) {
            createViaHttp(session, String.format("申請%02d", number), "ページ確認", "OTHER",
                    "2026-09-09", Integer.toString(number));
        }
        createViaHttp(session, "交通費100%精算", "ワイルドカード確認", "TRANSPORT", "2026-09-09", "100");
        createViaHttp(session, "交通費100X精算", "ワイルドカード確認", "TRANSPORT", "2026-09-09", "100");
        createViaHttp(session, "備品_購入", "アンダースコア確認", "SUPPLIES", "2026-09-09", "100");
        createViaHttp(session, "備品A購入", "アンダースコア確認", "SUPPLIES", "2026-09-09", "100");

        mockMvc.perform(get("/expenses").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("申請21")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("申請01"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("page=1")));

        mockMvc.perform(get("/expenses").param("page", "1").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("申請01")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("申請21"))));

        mockMvc.perform(get("/expenses").param("q", "100%").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("100%精算")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("100X精算"))));

        mockMvc.perform(get("/expenses").param("q", "備品_").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("備品_購入")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("備品A購入"))));

        mockMvc.perform(get("/expenses")
                        .param("status", "DRAFT")
                        .param("from", "2026-09-09")
                        .param("to", "2026-09-09")
                        .param("q", "申請")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("申請")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"q\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"申請\"")));
    }

    @Test
    void invalidSearchAndOutOfRangePageHaveDefinedResponses() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        mockMvc.perform(get("/expenses").param("status", "INVALID").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/expenses").param("category", "INVALID").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/expenses").param("from", "2026-09-10").param("to", "2026-09-09")
                        .session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/expenses").param("page", "-1").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/expenses").param("page", "not-a-number").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/expenses").param("q", "a".repeat(101)).session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/expenses").param("page", "10").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("条件に一致する申請はありません")));
    }

    @Test
    void staleVersionsAreRejectedWithoutChangingDataOrHistory() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        Long id = createViaHttp(session, "version確認", "用途", "OTHER", "2026-09-08", "100");
        ExpenseRequest initial = findRequest(id);

        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(session)
                        .with(csrf())
                        .param("title", "先に更新")
                        .param("purpose", "用途")
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-08")
                        .param("amount", "200")
                        .param("version", Long.toString(initial.getVersion())))
                .andExpect(status().isSeeOther());

        ExpenseRequest current = findRequest(id);
        assertThat(current.getVersion()).isEqualTo(1L);
        int eventCount = expenseEventRepository.findByExpenseIdForDisplay(id).size();

        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(session)
                        .with(csrf())
                        .param("title", "古い画面")
                        .param("purpose", "用途")
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-08")
                        .param("amount", "300")
                        .param("version", "0"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/expenses/{id}/delete", id)
                        .session(session)
                        .with(csrf())
                        .param("version", "0"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/expenses/{id}/submit", id)
                        .session(session)
                        .with(csrf())
                        .param("version", "0"))
                .andExpect(status().isConflict());

        ExpenseRequest after = findRequest(id);
        assertThat(after.getTitle()).isEqualTo("先に更新");
        assertThat(after.getStatus()).isEqualTo(ExpenseStatus.DRAFT);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(eventCount);
    }

    @Test
    void missingOrMalformedVersionsAreBadRequests() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        Long id = createViaHttp(session, "version必須", "用途", "OTHER", "2026-09-08", "100");

        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(session)
                        .with(csrf())
                        .param("title", "変更")
                        .param("purpose", "用途")
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-08")
                        .param("amount", "100"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/expenses/{id}/edit", id)
                        .session(session)
                        .with(csrf())
                        .param("title", "変更")
                        .param("purpose", "用途")
                        .param("category", "OTHER")
                        .param("expenseDate", "2026-09-08")
                        .param("amount", "100")
                        .param("version", "not-a-number"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/expenses/{id}/delete", id).session(session).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/expenses/{id}/submit", id).session(session).with(csrf())
                        .param("version", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentUpdatesAllowOneCommitAndOneConflict() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        Long id = createViaHttp(session, "同時更新", "用途", "OTHER", "2026-09-08", "100");
        CyclicBarrier bothReadVersionZero = new CyclicBarrier(2);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<ConcurrentUpdateOutcome>> futures = List.of(
                    executor.submit(() -> updateInIndependentTransaction(
                            id, "同時更新A", bothReadVersionZero, transactionTemplate)),
                    executor.submit(() -> updateInIndependentTransaction(
                            id, "同時更新B", bothReadVersionZero, transactionTemplate)));
            List<ConcurrentUpdateOutcome> outcomes = futures.stream()
                    .map(future -> getWithTimeout(future, "同時更新がタイムアウトしました"))
                    .toList();

            List<ConcurrentUpdateOutcome> successes = outcomes.stream()
                    .filter(ConcurrentUpdateOutcome::succeeded)
                    .toList();
            List<ConcurrentUpdateOutcome> failures = outcomes.stream()
                    .filter(outcome -> !outcome.succeeded())
                    .toList();
            assertThat(successes).hasSize(1);
            assertThat(failures).hasSize(1);
            assertThat(hasOptimisticLockCause(failures.get(0).failure()))
                    .as("失敗は楽観ロック競合でなければならない: " + failures.get(0).failure())
                    .isTrue();

            ExpenseRequest current = findRequest(id);
            assertThat(current.getVersion()).isEqualTo(1L);
            assertThat(current.getTitle()).isEqualTo(successes.get(0).title());
            List<ExpenseEvent> events = expenseEventRepository.findByExpenseIdForDisplay(id);
            assertThat(events).hasSize(2);
            assertThat(events.get(1).getAction()).isEqualTo(ExpenseEventAction.UPDATE);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void eventFailureRollsBackExpenseChange() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        Long id = createViaHttp(session, "ロールバック前", "用途", "OTHER", "2026-09-08", "100");
        installFailingEventTrigger();

        try {
            mockMvc.perform(post("/expenses/{id}/edit", id)
                            .session(session)
                            .with(csrf())
                            .param("title", "ロールバック後には残らない")
                            .param("purpose", "用途")
                            .param("category", "OTHER")
                            .param("expenseDate", "2026-09-08")
                            .param("amount", "200")
                            .param("version", "0"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("処理に失敗しました")))
                    .andExpect(content().string(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("ロールバック後には残らない"))));
        } finally {
            removeFailureTrigger();
        }

        ExpenseRequest unchanged = findRequest(id);
        assertThat(unchanged.getTitle()).isEqualTo("ロールバック前");
        assertThat(unchanged.getVersion()).isEqualTo(0L);
        assertThat(expenseEventRepository.findByExpenseIdForDisplay(id)).hasSize(1);
    }

    @Test
    void userInputIsHtmlEscaped() throws Exception {
        MockHttpSession session = loginAs(employee.getUsername());
        Long id = createViaHttp(session, "<script>alert(1)</script>", "<b>用途</b>", "OTHER",
                "2026-09-08", "100");

        mockMvc.perform(get("/expenses").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("<script>alert(1)</script>"))));
        mockMvc.perform(get("/expenses/{id}", id).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;b&gt;用途&lt;/b&gt;")));
    }

    private ConcurrentUpdateOutcome updateInIndependentTransaction(
            Long id, String title, CyclicBarrier barrier, TransactionTemplate transactionTemplate) {
        try {
            String savedTitle = transactionTemplate.execute(status -> {
                ExpenseRequest request = findRequest(id);
                assertThat(request.getVersion()).isZero();
                awaitBarrier(barrier);
                request.updateDetails(title, "用途", ExpenseCategory.OTHER,
                        LocalDate.of(2026, 9, 8), new java.math.BigDecimal("100"),
                        FIXED_INSTANT, LocalDate.of(2026, 9, 9));
                expenseRequestRepository.saveAndFlush(request);
                expenseEventRepository.saveAndFlush(ExpenseEvent.record(request, employee,
                        ExpenseEventAction.UPDATE, ExpenseStatus.DRAFT, ExpenseStatus.DRAFT,
                        null, FIXED_INSTANT));
                return request.getTitle();
            });
            return ConcurrentUpdateOutcome.success(savedTitle);
        } catch (Throwable failure) {
            return ConcurrentUpdateOutcome.failure(failure);
        }
    }

    private void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("同時更新テストの同期に失敗しました", exception);
        }
    }

    private ConcurrentUpdateOutcome getWithTimeout(Future<ConcurrentUpdateOutcome> future,
                                                   String message) {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(message, exception);
        }
    }

    private boolean hasOptimisticLockCause(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof org.springframework.dao.OptimisticLockingFailureException
                    || current instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record ConcurrentUpdateOutcome(String title, Throwable failure) {

        static ConcurrentUpdateOutcome success(String title) {
            return new ConcurrentUpdateOutcome(title, null);
        }

        static ConcurrentUpdateOutcome failure(Throwable failure) {
            return new ConcurrentUpdateOutcome(null, failure);
        }

        boolean succeeded() {
            return failure == null;
        }
    }

    private AppUser saveUser(String username, String displayName, Department department,
                             UserRole role) {
        return appUserRepository.save(new AppUser(department, username, displayName,
                passwordEncoder.encode(PASSWORD), role, true));
    }

    private MockHttpSession loginAs(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/expenses"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Long createViaHttp(MockHttpSession session, String title, String purpose,
                               String category, String expenseDate, String amount,
                               String... extraParameters) throws Exception {
        var request = post("/expenses")
                .session(session)
                .with(csrf())
                .param("title", title)
                .param("purpose", purpose)
                .param("category", category)
                .param("expenseDate", expenseDate)
                .param("amount", amount);
        for (int index = 0; index < extraParameters.length; index += 2) {
            request.param(extraParameters[index], extraParameters[index + 1]);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isSeeOther())
                .andReturn();
        return Long.valueOf(URI.create(result.getResponse().getHeader("Location")).getPath()
                .substring(result.getResponse().getHeader("Location").lastIndexOf('/') + 1));
    }

    private ExpenseRequest findRequest(Long id) {
        return expenseRequestRepository.findWithRelationsById(id).orElseThrow();
    }

    private Long insertStatusFixture(ExpenseStatus status) {
        ExpenseRequest request = ExpenseRequest.create(employee, sales, "fixture-" + status,
                "fixture", ExpenseCategory.OTHER, LocalDate.of(2026, 9, 8),
                new java.math.BigDecimal("100"), FIXED_INSTANT, LocalDate.of(2026, 9, 9));
        request = expenseRequestRepository.saveAndFlush(request);
        expenseEventRepository.saveAndFlush(ExpenseEvent.record(request, employee,
                ExpenseEventAction.CREATE, null, ExpenseStatus.DRAFT, null, FIXED_INSTANT));
        if (status != ExpenseStatus.DRAFT) {
            jdbcTemplate.update("update expense_requests set status = ?, submitted_at = ?, updated_at = ? where id = ?",
                    status.name(), Timestamp.from(FIXED_INSTANT), Timestamp.from(FIXED_INSTANT), request.getId());
            ExpenseEventAction action = status == ExpenseStatus.RETURNED
                    ? ExpenseEventAction.RETURN : ExpenseEventAction.APPROVE;
            expenseEventRepository.saveAndFlush(ExpenseEvent.record(request, approver, action,
                    ExpenseStatus.SUBMITTED, status, status == ExpenseStatus.RETURNED ? "修正してください" : null,
                    FIXED_INSTANT));
        }
        return request.getId();
    }

    private void installFailingEventTrigger() {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION fail_expense_event() RETURNS trigger AS $$
                BEGIN
                    IF NEW.action = 'UPDATE' THEN
                        RAISE EXCEPTION 'test event failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_expense_event_trigger
                BEFORE INSERT ON expense_events
                FOR EACH ROW EXECUTE FUNCTION fail_expense_event()
                """);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @org.springframework.context.annotation.Primary
        MutableTestClock fixedClock() {
            return new MutableTestClock(new AtomicReference<>(FIXED_INSTANT), ZoneOffset.UTC);
        }
    }

    static final class MutableTestClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        MutableTestClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId value) {
            return new MutableTestClock(instant, value);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}

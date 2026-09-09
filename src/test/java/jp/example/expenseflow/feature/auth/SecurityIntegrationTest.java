package jp.example.expenseflow.feature.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jp.example.expenseflow.config.DemoDataInitializer;
import jp.example.expenseflow.feature.auth.domain.AppUser;
import jp.example.expenseflow.feature.auth.domain.Department;
import jp.example.expenseflow.feature.auth.domain.UserRole;
import jp.example.expenseflow.feature.auth.repository.AppUserRepository;
import jp.example.expenseflow.feature.auth.repository.DepartmentRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SecurityIntegrationTest {

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
    PasswordEncoder passwordEncoder;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpUsers() {
        appUserRepository.deleteAllInBatch();
        departmentRepository.deleteAllInBatch();

        Department department = departmentRepository.save(new Department("テスト部"));
        appUserRepository.save(new AppUser(
                department,
                "test.employee",
                "テスト社員",
                passwordEncoder.encode("test-password"),
                UserRole.EMPLOYEE,
                true));
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("経費申請・承認システム")));
    }

    @Test
    void flywayCreatesTheExpectedSchema() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1'",
                Integer.class);

        Assertions.assertThat(appliedMigrations).isEqualTo(1);
        Assertions.assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'public' and table_name = 'expense_requests'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void normalProfileDoesNotRegisterDemoInitializer() {
        Assertions.assertThat(applicationContext.getBeansOfType(DemoDataInitializer.class)).isEmpty();
    }

    @Test
    void unauthenticatedUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/expenses"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
    }

    @Test
    void validLoginReachesExpensesPage() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "test.employee")
                        .param("password", "test-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/expenses"));
    }

    @Test
    void invalidLoginReturnsToLoginWithError() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "test.employee")
                        .param("password", "wrong-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void authenticatedUserCanLogoutWithCsrf() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("username", "test.employee")
                        .param("password", "test-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    void loginPostWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "test.employee")
                        .param("password", "test-password"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actuatorHealthIsPublicAndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void logoutPostWithoutCsrfIsRejected() throws Exception {
        mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden());
    }
}

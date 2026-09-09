package jp.example.expenseflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import jp.example.expenseflow.feature.auth.domain.AppUser;
import jp.example.expenseflow.feature.auth.repository.AppUserRepository;
import jp.example.expenseflow.feature.auth.repository.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
    PasswordEncoder passwordEncoder;

    @Test
    void demoDataIsCreatedIdempotently() throws Exception {
        assertThat(departmentRepository.count()).isEqualTo(2);
        assertThat(appUserRepository.count()).isEqualTo(5);

        demoDataInitializer.run(new DefaultApplicationArguments());

        assertThat(departmentRepository.count()).isEqualTo(2);
        assertThat(appUserRepository.count()).isEqualTo(5);

        AppUser employee = appUserRepository.findByUsername("demo.employee").orElseThrow();
        assertThat(employee.getDepartment().getName()).isEqualTo("営業部");
        assertThat(passwordEncoder.matches("demo-password", employee.getPasswordHash())).isTrue();
    }
}

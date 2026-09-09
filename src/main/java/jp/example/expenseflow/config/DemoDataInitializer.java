package jp.example.expenseflow.config;

import jp.example.expenseflow.feature.auth.domain.AppUser;
import jp.example.expenseflow.feature.auth.domain.Department;
import jp.example.expenseflow.feature.auth.domain.UserRole;
import jp.example.expenseflow.feature.auth.repository.AppUserRepository;
import jp.example.expenseflow.feature.auth.repository.DepartmentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoDataInitializer implements ApplicationRunner {

    private final DepartmentRepository departmentRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(DepartmentRepository departmentRepository,
                               AppUserRepository appUserRepository,
                               PasswordEncoder passwordEncoder) {
        this.departmentRepository = departmentRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Department sales = findOrCreateDepartment("営業部");
        Department development = findOrCreateDepartment("開発部");
        String passwordHash = passwordEncoder.encode("demo-password");

        findOrCreateUser("demo.employee", "デモ営業社員", sales, UserRole.EMPLOYEE, passwordHash);
        findOrCreateUser("demo.approver.sales.1", "デモ営業承認者1", sales, UserRole.APPROVER, passwordHash);
        findOrCreateUser("demo.approver.sales.2", "デモ営業承認者2", sales, UserRole.APPROVER, passwordHash);
        findOrCreateUser("demo.employee.dev", "デモ開発社員", development, UserRole.EMPLOYEE, passwordHash);
        findOrCreateUser("demo.approver.dev", "デモ開発承認者", development, UserRole.APPROVER, passwordHash);
    }

    private Department findOrCreateDepartment(String name) {
        return departmentRepository.findByName(name)
                .orElseGet(() -> departmentRepository.save(new Department(name)));
    }

    private void findOrCreateUser(String username, String displayName, Department department,
                                  UserRole role, String passwordHash) {
        if (appUserRepository.findByUsername(username).isEmpty()) {
            appUserRepository.save(new AppUser(department, username, displayName,
                    passwordHash, role, true));
        }
    }
}

package jp.example.expenseflow.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final ExpenseRequestRepository expenseRequestRepository;
    private final ExpenseEventRepository expenseEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataInitializer(DepartmentRepository departmentRepository,
                               AppUserRepository appUserRepository,
                               ExpenseRequestRepository expenseRequestRepository,
                               ExpenseEventRepository expenseEventRepository,
                               PasswordEncoder passwordEncoder,
                               Clock clock) {
        this.departmentRepository = departmentRepository;
        this.appUserRepository = appUserRepository;
        this.expenseRequestRepository = expenseRequestRepository;
        this.expenseEventRepository = expenseEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Department sales = findOrCreateDepartment("営業部");
        Department development = findOrCreateDepartment("開発部");
        String passwordHash = passwordEncoder.encode("demo-password");

        AppUser salesEmployee = findOrCreateUser(
                "demo.employee", "デモ営業社員", sales, UserRole.EMPLOYEE, passwordHash);
        AppUser salesApprover = findOrCreateUser(
                "demo.approver.sales.1", "デモ営業承認者1", sales, UserRole.APPROVER, passwordHash);
        findOrCreateUser("demo.approver.sales.2", "デモ営業承認者2", sales, UserRole.APPROVER, passwordHash);
        AppUser developmentEmployee = findOrCreateUser(
                "demo.employee.dev", "デモ開発社員", development, UserRole.EMPLOYEE, passwordHash);
        AppUser developmentApprover = findOrCreateUser(
                "demo.approver.dev", "デモ開発承認者", development, UserRole.APPROVER, passwordHash);

        createDemoExpenses(sales, development, salesEmployee, salesApprover,
                developmentEmployee, developmentApprover);
    }

    private Department findOrCreateDepartment(String name) {
        return departmentRepository.findByName(name)
                .orElseGet(() -> departmentRepository.save(new Department(name)));
    }

    private AppUser findOrCreateUser(String username, String displayName, Department department,
                                     UserRole role, String passwordHash) {
        return appUserRepository.findByUsername(username).orElseGet(() ->
                appUserRepository.save(new AppUser(department, username, displayName,
                        passwordHash, role, true)));
    }

    private void createDemoExpenses(Department sales, Department development,
                                    AppUser salesEmployee, AppUser salesApprover,
                                    AppUser developmentEmployee, AppUser developmentApprover) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneId.of("Asia/Tokyo"));
        for (int number = 1; number <= 45; number++) {
            String title = String.format("デモ申請-%02d", number);
            if (expenseRequestRepository.findByTitle(title).isPresent()) {
                continue;
            }
            boolean isSales = number <= 23;
            Department department = isSales ? sales : development;
            AppUser applicant = isSales ? salesEmployee : developmentEmployee;
            AppUser approver = isSales ? salesApprover : developmentApprover;
            ExpenseStatus targetStatus = statusFor(number);
            Instant createdAt = now.minusSeconds((46L - number) * 60L);
            ExpenseRequest request = ExpenseRequest.create(
                    applicant, department, title, "デモ用の経費申請です。",
                    categoryFor(number), today.minusDays(number % 14L),
                    BigDecimal.valueOf(1000L + number * 100L), createdAt, today);
            request = expenseRequestRepository.saveAndFlush(request);
            expenseEventRepository.saveAndFlush(ExpenseEvent.record(
                    request, applicant, ExpenseEventAction.CREATE, null,
                    ExpenseStatus.DRAFT, null, createdAt));

            if (targetStatus == ExpenseStatus.DRAFT) {
                continue;
            }
            Instant submittedAt = createdAt.plusSeconds(10);
            ExpenseStatus previousStatus = request.submit(submittedAt);
            expenseRequestRepository.saveAndFlush(request);
            expenseEventRepository.saveAndFlush(ExpenseEvent.record(
                    request, applicant, ExpenseEventAction.SUBMIT, previousStatus,
                    ExpenseStatus.SUBMITTED, null, submittedAt));

            if (targetStatus == ExpenseStatus.SUBMITTED) {
                continue;
            }
            Instant decidedAt = createdAt.plusSeconds(20);
            if (targetStatus == ExpenseStatus.RETURNED) {
                previousStatus = request.returnForRevision(decidedAt);
                expenseRequestRepository.saveAndFlush(request);
                expenseEventRepository.saveAndFlush(ExpenseEvent.record(
                        request, approver, ExpenseEventAction.RETURN, previousStatus,
                        ExpenseStatus.RETURNED, "デモ確認のため修正してください", decidedAt));
            } else {
                previousStatus = request.approve(decidedAt);
                expenseRequestRepository.saveAndFlush(request);
                expenseEventRepository.saveAndFlush(ExpenseEvent.record(
                        request, approver, ExpenseEventAction.APPROVE, previousStatus,
                        ExpenseStatus.APPROVED, "デモ確認済み", decidedAt));
            }
        }
    }

    private ExpenseStatus statusFor(int number) {
        return switch (number % 4) {
            case 0 -> ExpenseStatus.DRAFT;
            case 1 -> ExpenseStatus.SUBMITTED;
            case 2 -> ExpenseStatus.RETURNED;
            default -> ExpenseStatus.APPROVED;
        };
    }

    private ExpenseCategory categoryFor(int number) {
        return switch (number % 3) {
            case 0 -> ExpenseCategory.TRANSPORT;
            case 1 -> ExpenseCategory.SUPPLIES;
            default -> ExpenseCategory.OTHER;
        };
    }
}

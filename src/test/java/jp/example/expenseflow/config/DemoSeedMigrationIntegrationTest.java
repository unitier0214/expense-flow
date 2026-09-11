package jp.example.expenseflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DemoSeedMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void v2BackfillsDuplicateLegacyTitlesOnce() throws Exception {
        migrateToV1();
        long departmentId;
        long userId;
        long firstLegacyExpenseId;
        long duplicateLegacyExpenseId;
        try (Connection connection = connection()) {
            departmentId = insertDepartment(connection);
            userId = insertUser(connection, departmentId);
            firstLegacyExpenseId = insertLegacyExpense(connection, userId, departmentId);
            duplicateLegacyExpenseId = insertLegacyExpense(connection, userId, departmentId);
        }

        migrateToLatest();

        try (Connection connection = connection();
             PreparedStatement markerStatement = connection.prepareStatement(
                     "select seed_key, expense_id from demo_seed_entries");
             ResultSet markers = markerStatement.executeQuery()) {
            assertThat(markers.next()).isTrue();
            assertThat(markers.getString("seed_key")).isEqualTo("expense-01");
            assertThat(markers.getLong("expense_id")).isEqualTo(firstLegacyExpenseId);
            assertThat(markers.getLong("expense_id")).isNotEqualTo(duplicateLegacyExpenseId);
            assertThat(markers.next()).isFalse();
        }

        try (Connection connection = connection();
             PreparedStatement historyStatement = connection.prepareStatement(
                     "select count(*) from flyway_schema_history where success = true");
             ResultSet history = historyStatement.executeQuery()) {
            assertThat(history.next()).isTrue();
            assertThat(history.getInt(1)).isEqualTo(2);
        }
    }

    private void migrateToV1() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("1")
                .load()
                .migrate();
    }

    private void migrateToLatest() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    private long insertDepartment(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into departments(name) values (?) returning id")) {
            statement.setString(1, "移行確認部署");
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private long insertUser(Connection connection, long departmentId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into users(department_id, username, display_name, password_hash, "
                        + "role, enabled) values (?, ?, ?, ?, ?, ?) returning id")) {
            statement.setLong(1, departmentId);
            statement.setString(2, "legacy-seed-user");
            statement.setString(3, "移行確認ユーザー");
            statement.setString(4, "not-a-real-password");
            statement.setString(5, "EMPLOYEE");
            statement.setBoolean(6, true);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private long insertLegacyExpense(Connection connection, long userId, long departmentId)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into expense_requests(applicant_id, department_id, title, purpose, "
                        + "category, expense_date, amount, status, version, created_at, "
                        + "updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "timestamp with time zone '2026-09-08 00:00:00+00', "
                        + "timestamp with time zone '2026-09-08 00:00:00+00') returning id")) {
            statement.setLong(1, userId);
            statement.setLong(2, departmentId);
            statement.setString(3, "デモ申請-01");
            statement.setString(4, "移行前の申請");
            statement.setString(5, "OTHER");
            statement.setDate(6, java.sql.Date.valueOf("2026-09-08"));
            statement.setBigDecimal(7, new java.math.BigDecimal("100"));
            statement.setString(8, "DRAFT");
            statement.setLong(9, 0L);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }
}

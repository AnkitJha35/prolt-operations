package com.sense.prolt_operations.migration;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sense.prolt_operations.BaseIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

class UserTokenVersionMigrationTest extends BaseIntegrationTest {

    private void executeSql(String dbUrl, String sql) throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private Flyway configureFlyway(String dbUrl, String targetVersion) {
        var builder = Flyway.configure()
                .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                .schemas("public", "forecasting")
                .defaultSchema("public")
                .locations("classpath:db/migration")
                .baselineOnMigrate(false);
        if (targetVersion != null) {
            builder.target(targetVersion);
        }
        return builder.load();
    }

    private void insertUserV11(String dbUrl, String id, String username, String email, String fullName,
                               String passwordHash, String role, boolean enabled) throws Exception {
        String sql = """
            INSERT INTO public."User" ("id", "username", "email", "fullName", "passwordHash", "role", "hiddenModules", "readOnlyModules", "enabled", "createdAt", "updatedAt")
            VALUES (?, ?, ?, ?, ?, ?::"Role", ?, ?, ?, NOW(), NOW());
            """;
        try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, username);
            ps.setString(3, email);
            ps.setString(4, fullName);
            ps.setString(5, passwordHash);
            ps.setString(6, role);
            Array emptyArr = conn.createArrayOf("varchar", new String[0]);
            ps.setArray(7, emptyArr);
            ps.setArray(8, emptyArr);
            ps.setBoolean(9, enabled);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("V12 Migration: Existing users receive tokenVersion = 0, column is NOT NULL DEFAULT 0, and new inserts default to 0")
    void testV12Migration_ExistingUsersDefaultToZero_AndConstraintsEnforced() throws Exception {
        String dbName = "test_v12_migration_" + UUID.randomUUID().toString().replace("-", "");
        String defaultDbUrl = postgres.getJdbcUrl();
        executeSql(defaultDbUrl, "CREATE DATABASE " + dbName);

        String testDbUrl = defaultDbUrl.substring(0, defaultDbUrl.lastIndexOf('/') + 1) + dbName;

        try {
            // 1. Migrate up to V11
            Flyway flywayV11 = configureFlyway(testDbUrl, "11");
            flywayV11.migrate();
            assertThat(flywayV11.info().current().getVersion().getVersion()).isEqualTo("11");

            // 2. Insert test users at V11 (before tokenVersion column exists)
            String u1 = UUID.randomUUID().toString();
            String u2 = UUID.randomUUID().toString();
            insertUserV11(testDbUrl, u1, "test_v11_alice", "alice_v11@example.com", "Alice Smith", "hash1", "ADMIN", true);
            insertUserV11(testDbUrl, u2, "test_v11_bob", "bob_v11@example.com", "Bob Jones", "hash2", "CLIENT", false);

            // 3. Migrate to V12
            Flyway flywayV12 = configureFlyway(testDbUrl, "12");
            var result = flywayV12.migrate();
            assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(1);
            assertThat(flywayV12.info().current().getVersion().getVersion()).isEqualTo("12");

            // 4. Verify column metadata in information_schema
            try (Connection conn = DriverManager.getConnection(testDbUrl, postgres.getUsername(), postgres.getPassword());
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT is_nullable, column_default, data_type
                     FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'User'
                       AND column_name = 'tokenVersion';
                     """)) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("is_nullable")).isEqualTo("NO");
                    assertThat(rs.getString("column_default")).contains("0");
                    assertThat(rs.getString("data_type")).isEqualTo("integer");
                }
            }

            // 5. Verify existing users have tokenVersion = 0
            try (Connection conn = DriverManager.getConnection(testDbUrl, postgres.getUsername(), postgres.getPassword());
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT id, username, "tokenVersion", "enabled"
                     FROM public."User"
                     WHERE id IN (?, ?);
                     """)) {
                ps.setString(1, u1);
                ps.setString(2, u2);
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        assertThat(rs.getInt("tokenVersion")).isEqualTo(0);
                    }
                    assertThat(count).isEqualTo(2);
                }
            }

            // 6. Verify inserting new user at V12 without tokenVersion defaults to 0
            String u3 = UUID.randomUUID().toString();
            insertUserV11(testDbUrl, u3, "test_v12_charlie", "charlie_v12@example.com", "Charlie Brown", "hash3", "CLIENT", true);
            try (Connection conn = DriverManager.getConnection(testDbUrl, postgres.getUsername(), postgres.getPassword());
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT "tokenVersion", "enabled" FROM public."User" WHERE id = ?;
                     """)) {
                ps.setString(1, u3);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt("tokenVersion")).isEqualTo(0);
                    assertThat(rs.getBoolean("enabled")).isTrue();
                }
            }

            // 7. Verify idempotency - running migrate again is a no-op
            var secondResult = flywayV12.migrate();
            assertThat(secondResult.migrationsExecuted).isZero();
            assertThat(flywayV12.info().current().getVersion().getVersion()).isEqualTo("12");

        } finally {
            executeSql(defaultDbUrl, "DROP DATABASE IF EXISTS " + dbName);
        }
    }
}

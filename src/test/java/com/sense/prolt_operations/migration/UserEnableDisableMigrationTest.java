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

class UserEnableDisableMigrationTest extends BaseIntegrationTest {

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

    private void insertUserV10(String dbUrl, String id, String username, String email, String fullName,
                               String passwordHash, String role) throws Exception {
        String sql = """
            INSERT INTO public."User" ("id", "username", "email", "fullName", "passwordHash", "role", "hiddenModules", "readOnlyModules", "createdAt", "updatedAt")
            VALUES (?, ?, ?, ?, ?, ?::"Role", ?, ?, NOW(), NOW());
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
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("V11 Migration: Existing users remain enabled and column constraints are enforced")
    void testV11Migration_ExistingUsersRemainEnabled_AndConstraintsEnforced() throws Exception {
        String dbName = "test_v11_migration_" + UUID.randomUUID().toString().replace("-", "");
        String defaultDbUrl = postgres.getJdbcUrl();
        executeSql(defaultDbUrl, "CREATE DATABASE " + dbName);

        String testDbUrl = defaultDbUrl.substring(0, defaultDbUrl.lastIndexOf('/') + 1) + dbName;

        try {
            // 1. Migrate up to V10
            Flyway flywayV10 = configureFlyway(testDbUrl, "10");
            flywayV10.migrate();

            // 2. Insert test users at V10 (before enabled column exists)
            String u1 = UUID.randomUUID().toString();
            String u2 = UUID.randomUUID().toString();
            insertUserV10(testDbUrl, u1, "test_alice", "alice@example.com", "Alice Smith", "hash1", "ADMIN");
            insertUserV10(testDbUrl, u2, "test_bob", "bob@example.com", "Bob Jones", "hash2", "CLIENT");

            // 3. Migrate to V11
            Flyway flywayV11 = configureFlyway(testDbUrl, "11");
            var result = flywayV11.migrate();
            assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(1);

            // 4. Verify column metadata in information_schema
            try (Connection conn = DriverManager.getConnection(testDbUrl, postgres.getUsername(), postgres.getPassword());
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT is_nullable, column_default, data_type
                     FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'User'
                       AND column_name = 'enabled';
                     """)) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("is_nullable")).isEqualTo("NO");
                    assertThat(rs.getString("column_default")).contains("true");
                    assertThat(rs.getString("data_type")).isEqualTo("boolean");
                }
            }

            // 5. Verify existing users are enabled = true
            try (Connection conn = DriverManager.getConnection(testDbUrl, postgres.getUsername(), postgres.getPassword());
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT id, username, "enabled"
                     FROM public."User"
                     WHERE id IN (?, ?);
                     """)) {
                ps.setString(1, u1);
                ps.setString(2, u2);
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        assertThat(rs.getBoolean("enabled")).isTrue();
                    }
                    assertThat(count).isEqualTo(2);
                }
            }

            // 6. Verify inserting new user defaults to enabled = true
            String u3 = UUID.randomUUID().toString();
            insertUserV10(testDbUrl, u3, "test_charlie", "charlie@example.com", "Charlie Brown", "hash3", "CLIENT");
            try (Connection conn = DriverManager.getConnection(testDbUrl, postgres.getUsername(), postgres.getPassword());
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT "enabled" FROM public."User" WHERE id = ?;
                     """)) {
                ps.setString(1, u3);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean("enabled")).isTrue();
                }
            }
        } finally {
            executeSql(defaultDbUrl, "DROP DATABASE IF EXISTS " + dbName);
        }
    }
}

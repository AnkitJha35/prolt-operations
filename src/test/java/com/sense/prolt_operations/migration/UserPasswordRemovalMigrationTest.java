package com.sense.prolt_operations.migration;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sense.prolt_operations.BaseIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPasswordRemovalMigrationTest extends BaseIntegrationTest {

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

    private void insertUserV12(String dbUrl, String id, String username, String email, String fullName,
                               String passwordHash, String role, boolean enabled, int tokenVersion) throws Exception {
        String sql = """
            INSERT INTO public."User" ("id", "username", "email", "fullName", "passwordHash", "role", "hiddenModules", "readOnlyModules", "enabled", "tokenVersion", "createdAt", "updatedAt")
            VALUES (?, ?, ?, ?, ?, ?::"Role", ?, ?, ?, ?, NOW(), NOW());
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
            ps.setInt(10, tokenVersion);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("V13 Migration: Drops passwordHash column, preserves all existing user records, columns, and constraints")
    void testV13Migration_DropsPasswordHash_PreservesUserData() throws Exception {
        String dbName = "test_v13_migration_" + UUID.randomUUID().toString().replace("-", "");
        String defaultDbUrl = postgres.getJdbcUrl();
        executeSql(defaultDbUrl, "CREATE DATABASE " + dbName);

        String testDbUrl = defaultDbUrl.substring(0, defaultDbUrl.lastIndexOf('/') + 1) + dbName;

        try {
            // 1. Migrate up to V12
            Flyway flywayV12 = configureFlyway(testDbUrl, "12");
            flywayV12.migrate();
            assertThat(flywayV12.info().current().getVersion().getVersion()).isEqualTo("12");

            // 2. Insert test users at V12 (with passwordHash present)
            String u1 = UUID.randomUUID().toString();
            String u2 = UUID.randomUUID().toString();
            insertUserV12(testDbUrl, u1, "alice_v12", "alice_v12@probus.io", "Alice Wonderland", "hash_alice_123", "ADMIN", true, 2);
            insertUserV12(testDbUrl, u2, "bob_v12", "bob_v12@probus.io", "Bob Builder", "hash_bob_456", "CLIENT", false, 0);

            // Verify passwordHash exists in V12
            try (Connection conn = DriverManager.getConnection(testDbUrl, postgres.getUsername(), postgres.getPassword());
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT count(*) FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'User'
                       AND column_name = 'passwordHash';
                     """)) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }

            // 3. Migrate to V13
            Flyway flywayV13 = configureFlyway(testDbUrl, "13");
            var result = flywayV13.migrate();
            assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(1);
            assertThat(flywayV13.info().current().getVersion().getVersion()).isEqualTo("13");

            // 4. Verify passwordHash column NO LONGER exists
            try (Connection conn = DriverManager.getConnection(testDbUrl, postgres.getUsername(), postgres.getPassword());
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT count(*) FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name = 'User'
                       AND column_name = 'passwordHash';
                     """)) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(0);
                }
            }

            // 5. Verify user records preserved without data loss
            try (Connection conn = DriverManager.getConnection(testDbUrl, postgres.getUsername(), postgres.getPassword());
                 PreparedStatement ps = conn.prepareStatement("""
                     SELECT id, username, email, "fullName", role, enabled, "tokenVersion"
                     FROM public."User"
                     WHERE id IN (?, ?)
                     ORDER BY username ASC;
                     """)) {
                ps.setString(1, u1);
                ps.setString(2, u2);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("username")).isEqualTo("alice_v12");
                    assertThat(rs.getString("email")).isEqualTo("alice_v12@probus.io");
                    assertThat(rs.getString("fullName")).isEqualTo("Alice Wonderland");
                    assertThat(rs.getString("role")).isEqualTo("ADMIN");
                    assertThat(rs.getBoolean("enabled")).isTrue();
                    assertThat(rs.getInt("tokenVersion")).isEqualTo(2);

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("username")).isEqualTo("bob_v12");
                    assertThat(rs.getString("email")).isEqualTo("bob_v12@probus.io");
                    assertThat(rs.getString("fullName")).isEqualTo("Bob Builder");
                    assertThat(rs.getString("role")).isEqualTo("CLIENT");
                    assertThat(rs.getBoolean("enabled")).isFalse();
                    assertThat(rs.getInt("tokenVersion")).isEqualTo(0);
                }
            }

        } finally {
            executeSql(defaultDbUrl, "DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V13 Migration Preflight: Aborts with error if any user lacks an email address")
    void testV13Migration_AbortsIfUserHasNullEmail() throws Exception {
        String dbName = "test_v13_preflight_" + UUID.randomUUID().toString().replace("-", "");
        String defaultDbUrl = postgres.getJdbcUrl();
        executeSql(defaultDbUrl, "CREATE DATABASE " + dbName);

        String testDbUrl = defaultDbUrl.substring(0, defaultDbUrl.lastIndexOf('/') + 1) + dbName;

        try {
            // 1. Migrate up to V12
            Flyway flywayV12 = configureFlyway(testDbUrl, "12");
            flywayV12.migrate();

            // 2. Temporarily relax NOT NULL constraint on email to simulate corrupted data
            executeSql(testDbUrl, "ALTER TABLE public.\"User\" ALTER COLUMN \"email\" DROP NOT NULL");

            // 3. Insert user with NULL email
            String badUserId = UUID.randomUUID().toString();
            insertUserV12(testDbUrl, badUserId, "no_email_user", null, "No Email", "somehash", "CLIENT", true, 0);

            // 4. Migrate to V13 - must fail preflight assertion
            Flyway flywayV13 = configureFlyway(testDbUrl, "13");
            assertThatThrownBy(flywayV13::migrate)
                    .isInstanceOf(FlywayException.class)
                    .hasMessageContaining("Flyway V13 Preflight Failed: 1 user account(s) have missing or blank email addresses");

        } finally {
            executeSql(defaultDbUrl, "DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        }
    }
}

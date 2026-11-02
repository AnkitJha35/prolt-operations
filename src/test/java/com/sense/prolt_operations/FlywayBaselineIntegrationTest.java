package com.sense.prolt_operations;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayBaselineIntegrationTest extends BaseIntegrationTest {

    private void executeSqlOnDatabase(String dbUrl, String sql) throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    @Test
    @DisplayName("Case A: Migration from scratch on an empty database applies all migrations cleanly")
    void migrationFromScratch_appliesAllMigrationsCleanly() throws Exception {
        String scratchDbName = "scratch_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSqlOnDatabase(postgres.getJdbcUrl(), "CREATE DATABASE \"" + scratchDbName + "\"");
        String scratchDbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + scratchDbName);

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(scratchDbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .load();

            int applied = flyway.migrate().migrationsExecuted;
            assertThat(applied).isEqualTo(10);

            assertThat(flyway.info().current()).isNotNull();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("10");
            assertThat(flyway.info().current().getState().isApplied()).isTrue();

            // Verify User table is created and contains zero rows
            try (Connection conn = DriverManager.getConnection(scratchDbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM \"public\".\"User\"")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isZero();
            }
        } finally {
            executeSqlOnDatabase(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + scratchDbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("Case B: Baseline an existing database at version 5 preserves existing data and applies V6 through V10")
    void baselineExistingDatabase_preservesDataAndSetsBaselineRow() throws Exception {
        String existingDbName = "existing_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSqlOnDatabase(postgres.getJdbcUrl(), "CREATE DATABASE \"" + existingDbName + "\"");
        String existingDbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + existingDbName);

        try {
            // Step 1: Simulate pre-existing database by applying V1 DDL
            Flyway setupV1 = Flyway.configure()
                    .dataSource(existingDbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .target("1")
                    .load();
            setupV1.migrate();

            // Drop flyway schema history table so the database mimics a legacy pre-Flyway database
            executeSqlOnDatabase(existingDbUrl, "DROP TABLE IF EXISTS \"public\".\"flyway_schema_history\"");

            // Step 2: Insert one row of application data into pre-existing table
            String testHolidayId = "holiday-" + UUID.randomUUID().toString().substring(0, 8);
            String insertSql = String.format("""
                INSERT INTO "public"."HolidayCalendar" ("id", "month", "holidayDate", "holidayName", "holidayType", "state", "isActive", "createdAt", "updatedAt")
                VALUES ('%s', 'May', '2026-05-01', 'Pre-existing Foundation Day', 'National', 'All', true, NOW(), NOW())
                """, testHolidayId);
            executeSqlOnDatabase(existingDbUrl, insertSql);

            // Step 3: Run Flyway with baselineOnMigrate=true and baselineVersion=5
            Flyway baselineFlyway = Flyway.configure()
                    .dataSource(existingDbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion(MigrationVersion.fromVersion("5"))
                    .load();

            int executedMigrations = baselineFlyway.migrate().migrationsExecuted;
            // Migrations V6, V7, V8, V9, and V10 are executed because schema is baselined at version 5 and V6..V10 are pending
            assertThat(executedMigrations).isEqualTo(5);
            assertThat(baselineFlyway.info().current().getVersion().getVersion()).isEqualTo("10");

            // Step 4: Verify flyway_schema_history has the baseline row at version 5 and migration rows at 6, 7, 8, 9, 10
            try (Connection conn = DriverManager.getConnection(existingDbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT "version", "type", "success"
                     FROM "public"."flyway_schema_history"
                     WHERE "version" = '5'
                     """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("version")).isEqualTo("5");
                assertThat(rs.getString("type")).isEqualTo("BASELINE");
                assertThat(rs.getBoolean("success")).isTrue();
            }

            try (Connection conn = DriverManager.getConnection(existingDbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT "version", "type", "success"
                     FROM "public"."flyway_schema_history"
                     WHERE "version" = '6'
                     """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("version")).isEqualTo("6");
                assertThat(rs.getString("type")).isEqualTo("SQL");
                assertThat(rs.getBoolean("success")).isTrue();
            }

            try (Connection conn = DriverManager.getConnection(existingDbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT "version", "type", "success"
                     FROM "public"."flyway_schema_history"
                     WHERE "version" = '7'
                     """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("version")).isEqualTo("7");
                assertThat(rs.getString("type")).isEqualTo("SQL");
                assertThat(rs.getBoolean("success")).isTrue();
            }

            try (Connection conn = DriverManager.getConnection(existingDbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT "version", "type", "success"
                     FROM "public"."flyway_schema_history"
                     WHERE "version" = '8'
                     """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("version")).isEqualTo("8");
                assertThat(rs.getString("type")).isEqualTo("SQL");
                assertThat(rs.getBoolean("success")).isTrue();
            }

            try (Connection conn = DriverManager.getConnection(existingDbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT "version", "type", "success"
                     FROM "public"."flyway_schema_history"
                     WHERE "version" = '9'
                     """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("version")).isEqualTo("9");
                assertThat(rs.getString("type")).isEqualTo("SQL");
                assertThat(rs.getBoolean("success")).isTrue();
            }

            try (Connection conn = DriverManager.getConnection(existingDbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT "version", "type", "success"
                     FROM "public"."flyway_schema_history"
                     WHERE "version" = '10'
                     """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("version")).isEqualTo("10");
                assertThat(rs.getString("type")).isEqualTo("SQL");
                assertThat(rs.getBoolean("success")).isTrue();
            }

            // Step 5: Assert that existing application row is untouched
            try (Connection conn = DriverManager.getConnection(existingDbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(String.format("""
                     SELECT "holidayName" FROM "public"."HolidayCalendar" WHERE "id" = '%s'
                     """, testHolidayId))) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("holidayName")).isEqualTo("Pre-existing Foundation Day");
            }
        } finally {
            executeSqlOnDatabase(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + existingDbName + "\" WITH (FORCE)");
        }
    }
}

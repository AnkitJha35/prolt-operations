package com.sense.prolt_operations.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.internal.exception.FlywayMigrateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sense.prolt_operations.BaseIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsolatedMigrationRehearsalTest extends BaseIntegrationTest {

    private void executeSql(String dbUrl, String sql) throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private List<String> listApplicationTables(String dbUrl, String schema) throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(String.format("""
                 SELECT table_name
                 FROM information_schema.tables
                 WHERE table_schema = '%s' 
                   AND table_type = 'BASE TABLE'
                   AND table_name != 'flyway_schema_history'
                 ORDER BY table_name;
                 """, schema))) {
            while (rs.next()) {
                tables.add(rs.getString("table_name"));
            }
        }
        return tables;
    }

    @Test
    @DisplayName("Rehearsal 1: Prisma Partial Migration Trap - Demonstrates multi-schema baseline failure and missing table hazard")
    void rehearsal_prismaPartialMigration_baselineV5_failsAndLeavesTablesMissing() throws Exception {
        String dbName = "rehearsal_partial_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            // Step 1: Apply legacy Prisma migration 1 (creates 6 tables: Admin, Dataset, UploadHistory, DamRecord, GdamRecord, RtmRecord)
            Path prismaMig1 = Paths.get("../IEX-Dashboard/backend/prisma/migrations/20260622060941_initial_schema/migration.sql");
            if (Files.exists(prismaMig1)) {
                String sql1 = Files.readString(prismaMig1);
                executeSql(dbUrl, sql1);
            }

            // Apply legacy Prisma migration 2 (drops index on Dataset)
            Path prismaMig2 = Paths.get("../IEX-Dashboard/backend/prisma/migrations/20260622105237_remove_market_date_unique/migration.sql");
            if (Files.exists(prismaMig2)) {
                String sql2 = Files.readString(prismaMig2);
                executeSql(dbUrl, sql2);
            }

            // Verify only the initial 6 tables exist in public schema
            List<String> tablesBefore = listApplicationTables(dbUrl, "public");
            assertThat(tablesBefore).containsExactlyInAnyOrder(
                "Admin", "Dataset", "UploadHistory", "DamRecord", "GdamRecord", "RtmRecord"
            );

            // Facet A: Executing Flyway migrate with baselineOnMigrate=true on this legacy schema
            // FAILS because 'forecasting' schema did not exist in Prisma, so Flyway creates it, detects it as empty,
            // does NOT baseline, and attempts to run V1 which crashes on MarketType enum collision!
            Flyway flywayWithBaselineOnMigrate = Flyway.configure()
                    .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion(MigrationVersion.fromVersion("5"))
                    .load();

            assertThatThrownBy(flywayWithBaselineOnMigrate::migrate)
                    .isInstanceOf(FlywayMigrateException.class)
                    .hasMessageContaining("V1__Initial_Schema.sql failed")
                    .hasMessageContaining("type \"MarketType\" already exists");

            // Facet B: Even if an operator manually drops the failed history table and executes flyway.baseline() to force version 5:
            executeSql(dbUrl, "DROP TABLE IF EXISTS \"public\".\"flyway_schema_history\"");

            Flyway forcedBaselineFlyway = Flyway.configure()
                    .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .baselineVersion(MigrationVersion.fromVersion("5"))
                    .load();

            forcedBaselineFlyway.baseline();

            // Verify flyway_schema_history exists and has BASELINE row at version 5
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT version, type, success FROM \"public\".\"flyway_schema_history\"")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("version")).isEqualTo("5");
                assertThat(rs.getString("type")).isEqualTo("BASELINE");
                assertThat(rs.getBoolean("success")).isTrue();
            }

            // Verify the critical finding: 52 tables are completely MISSING!
            List<String> tablesAfter = listApplicationTables(dbUrl, "public");
            assertThat(tablesAfter).doesNotContain("region_state", "ctu_charges", "state_charges", "state_tariff", "User");
            assertThat(tablesAfter.size()).isEqualTo(6);

            // Querying a required Java entity table fails with PSQLException (table does not exist)
            assertThatThrownBy(() -> {
                try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                     Statement stmt = conn.createStatement()) {
                    stmt.executeQuery("SELECT * FROM \"public\".\"ctu_charges\" LIMIT 1");
                }
            }).hasMessageContaining("relation \"public.ctu_charges\" does not exist");

        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("Rehearsal 2: Full Canonical DB - Baseline v5 on database with all 58 tables preserves schema and data")
    void rehearsal_canonicalSchema_baselineV5_preservesAllTablesAndData() throws Exception {
        String dbName = "rehearsal_canonical_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            // Apply full V1 DDL representing a database where all 58 tables were synced (e.g. via prisma db push)
            Flyway setupFlyway = Flyway.configure()
                    .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .target("1")
                    .load();
            setupFlyway.migrate();

            // Drop flyway schema history table to simulate a pre-Flyway legacy database
            executeSql(dbUrl, "DROP TABLE IF EXISTS \"public\".\"flyway_schema_history\"");

            // Insert representative data into Resource Center tables using exact DDL column names
            executeSql(dbUrl, """
                INSERT INTO "public"."region_state" ("regional_grid", "region_code", "region_name", "state_name", "state_code", "state_or_ut", "updated_at")
                VALUES ('Northern', 'NR', 'Northern Region', 'Delhi', 'DL', 'ut', NOW());
                """);
            executeSql(dbUrl, """
                INSERT INTO "public"."ctu_charges" ("state", "month", "ctu_charges_rs_per_kwh", "updated_at")
                VALUES ('Delhi', 202605, 0.450000, NOW());
                """);

            // Verify exactly 58 application tables exist across public and forecasting schemas
            List<String> publicTables = listApplicationTables(dbUrl, "public");
            List<String> forecastingTables = listApplicationTables(dbUrl, "forecasting");
            assertThat(publicTables.size() + forecastingTables.size()).isEqualTo(58);

            // Execute Flyway with baseline-version=5
            Flyway flyway = Flyway.configure()
                    .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion(MigrationVersion.fromVersion("5"))
                    .load();

            int executed = flyway.migrate().migrationsExecuted;
            assertThat(executed).isZero(); // Baseline at 5 skipped all migrations cleanly

            // Verify data remains completely intact
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT ctu_charges_rs_per_kwh FROM \"public\".\"ctu_charges\" WHERE \"state\" = 'Delhi'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("0.450000");
            }

            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT state_name, state_code FROM \"public\".\"region_state\" WHERE \"state_code\" = 'DL'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("state_name")).isEqualTo("Delhi");
            }

        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("Rehearsal 3: Greenfield Database - Flyway migrate with baseline=false applies all migrations cleanly")
    void rehearsal_greenfieldDatabase_appliesAllMigrationsCleanly() throws Exception {
        String dbName = "rehearsal_fresh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .load();

            int executed = flyway.migrate().migrationsExecuted;
            assertThat(executed).isGreaterThanOrEqualTo(5);

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");
            assertThat(flyway.info().current().getState().isApplied()).isTrue();

            List<String> publicTables = listApplicationTables(dbUrl, "public");
            List<String> forecastingTables = listApplicationTables(dbUrl, "forecasting");
            assertThat(publicTables.size() + forecastingTables.size()).isEqualTo(58);

        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }
}

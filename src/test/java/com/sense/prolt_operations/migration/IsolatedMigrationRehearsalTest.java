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

import java.util.HashMap;
import java.util.Map;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

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

    private void validateHibernateSchema(String dbUrl) {
        Map<String, Object> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url", dbUrl);
        props.put("jakarta.persistence.jdbc.user", postgres.getUsername());
        props.put("jakarta.persistence.jdbc.password", postgres.getPassword());
        props.put("jakarta.persistence.jdbc.driver", "org.postgresql.Driver");
        props.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        props.put("hibernate.globally_quoted_identifiers", "true");
        props.put("hibernate.hbm2ddl.auto", "validate");

        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setPackagesToScan("com.sense.prolt_operations.entity");
        emfBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        emfBean.setJpaPropertyMap(props);
        emfBean.setDataSource(new DriverManagerDataSource(dbUrl, postgres.getUsername(), postgres.getPassword()));
        emfBean.afterPropertiesSet();
        try {
            EntityManagerFactory emf = emfBean.getObject();
            assertThat(emf).isNotNull();
            emf.close();
        } finally {
            emfBean.destroy();
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
    @DisplayName("Case 1: Fresh Empty Database - Run V1-V6, verify all 58 tables, bigint IDs, constraints, and Hibernate validation")
    void rehearsal_case1_freshEmptyDatabase_appliesV1ThroughV6_verifiesSchemaAndHibernate() throws Exception {
        String dbName = "rehearsal_case1_fresh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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
            assertThat(executed).isEqualTo(10);

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("10");
            assertThat(flyway.info().current().getState().isApplied()).isTrue();

            // Verify all 58 canonical tables exist
            List<String> publicTables = listApplicationTables(dbUrl, "public");
            List<String> forecastingTables = listApplicationTables(dbUrl, "forecasting");
            assertThat(publicTables.size() + forecastingTables.size()).isEqualTo(58);
            assertThat(publicTables).contains("dsm_charges", "exchange_dam_rate", "exchange_rtm_rate");

            // Verify exchange_dam_rate and exchange_rtm_rate IDs are bigint
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT column_name, data_type
                     FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name IN ('exchange_dam_rate', 'exchange_rtm_rate')
                       AND column_name = 'id'
                     """)) {
                int count = 0;
                while (rs.next()) {
                    assertThat(rs.getString("data_type")).isEqualTo("bigint");
                    count++;
                }
                assertThat(count).isEqualTo(2);
            }

            // Verify dsm_charges indexes
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT indexname
                     FROM pg_indexes
                     WHERE schemaname = 'public' AND tablename = 'dsm_charges'
                     """)) {
                List<String> indexes = new ArrayList<>();
                while (rs.next()) {
                    indexes.add(rs.getString("indexname"));
                }
                assertThat(indexes).contains("dsm_charges_pkey", "uq_dsm_charges_state_month");
            }

            // Run Hibernate schema validation across all 58 JPA entities
            validateHibernateSchema(dbUrl);

        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("Case 2: Existing-Schema Rehearsal - Baseline v5 on DB missing dsm_charges, apply V6, verify tables, data preservation, and Hibernate")
    void rehearsal_case2_existingSchemaMissingDsmCharges_baselineV5_appliesV6_preservesDataAndValidates() throws Exception {
        String dbName = "rehearsal_case2_exist_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            // Step 1: Create initial 58 tables via V1, then drop dsm_charges and history to mirror restored prod DB
            Flyway setupFlyway = Flyway.configure()
                    .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .target("1")
                    .load();
            setupFlyway.migrate();

            executeSql(dbUrl, "DROP TABLE IF EXISTS \"public\".\"dsm_charges\" CASCADE");
            executeSql(dbUrl, "DROP TABLE IF EXISTS \"public\".\"flyway_schema_history\"");

            // Insert representative pre-existing data into multiple tables
            executeSql(dbUrl, """
                INSERT INTO "public"."region_state" ("regional_grid", "region_code", "region_name", "state_name", "state_code", "state_or_ut", "updated_at")
                VALUES ('Northern', 'NR', 'Northern Region', 'Delhi', 'DL', 'ut', NOW());
                """);
            executeSql(dbUrl, """
                INSERT INTO "public"."ctu_charges" ("state", "month", "ctu_charges_rs_per_kwh", "updated_at")
                VALUES ('Delhi', 202605, 0.450000, NOW());
                """);
            executeSql(dbUrl, """
                INSERT INTO "public"."exchange_dam_rate" ("date", "intervalTime", "mcp", "mcv", "purchaseBid", "sellBid", "state", "updated_at")
                VALUES ('2026-06-01', '00:15:00', 3.50, 100.00, 120.00, 110.00, 'DELHI', NOW());
                """);

            // Verify dsm_charges is missing and 57 tables exist
            List<String> tablesBefore = listApplicationTables(dbUrl, "public");
            assertThat(tablesBefore).doesNotContain("dsm_charges");
            assertThat(tablesBefore.size() + listApplicationTables(dbUrl, "forecasting").size()).isEqualTo(57);

            // Step 2: Establish intended baseline state at version 5 and apply V6
            Flyway flyway = Flyway.configure()
                    .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion(MigrationVersion.fromVersion("5"))
                    .load();

            int executed = flyway.migrate().migrationsExecuted;
            assertThat(executed).isEqualTo(5); // V6, V7, V8, V9, and V10 applied cleanly

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("10");
            assertThat(flyway.info().current().getState().isApplied()).isTrue();

            // Verify dsm_charges is now created and total tables is 58
            List<String> tablesAfter = listApplicationTables(dbUrl, "public");
            assertThat(tablesAfter).contains("dsm_charges");
            assertThat(tablesAfter.size() + listApplicationTables(dbUrl, "forecasting").size()).isEqualTo(58);

            // Verify unique index on dsm_charges
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT indexname
                     FROM pg_indexes
                     WHERE schemaname = 'public' AND tablename = 'dsm_charges'
                     """)) {
                List<String> indexes = new ArrayList<>();
                while (rs.next()) {
                    indexes.add(rs.getString("indexname"));
                }
                assertThat(indexes).contains("dsm_charges_pkey", "uq_dsm_charges_state_month");
            }

            // Verify pre-existing data is untouched
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT ctu_charges_rs_per_kwh FROM \"public\".\"ctu_charges\" WHERE \"state\" = 'Delhi'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("0.450000");
                }
                try (ResultSet rs = stmt.executeQuery("SELECT state_name FROM \"public\".\"region_state\" WHERE \"state_code\" = 'DL'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("state_name")).isEqualTo("Delhi");
                }
                try (ResultSet rs = stmt.executeQuery("SELECT id, mcp FROM \"public\".\"exchange_dam_rate\" WHERE \"state\" = 'DELHI'")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("id")).isGreaterThan(0L);
                    assertThat(rs.getBigDecimal("mcp")).isEqualByComparingTo("3.50");
                }
            }

            // Step 3: Run Hibernate validation against the migrated existing schema
            validateHibernateSchema(dbUrl);

        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("Case 3: Migration Idempotency & Unique Constraint - Second migrate is a no-op, raw DDL is safe, constraint blocks duplicates")
    void rehearsal_case3_migrationIdempotency_andConstraintEnforcement() throws Exception {
        String dbName = "rehearsal_case3_idemp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
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

            flyway.migrate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("10");

            // Sub-test A: Second migrate() invocation is an absolute no-op
            int secondRun = flyway.migrate().migrationsExecuted;
            assertThat(secondRun).isZero();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("10");

            // Sub-test B: Raw DDL replay (CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS) is completely safe
            executeSql(dbUrl, """
                CREATE TABLE IF NOT EXISTS "public"."dsm_charges" (
                    "id" SERIAL NOT NULL,
                    "state" VARCHAR(100) NOT NULL,
                    "month" INTEGER NOT NULL,
                    "dsm_charges_rs_per_kwh" DECIMAL(12,6),
                    "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT "dsm_charges_pkey" PRIMARY KEY ("id")
                );
                """);
            executeSql(dbUrl, "CREATE UNIQUE INDEX IF NOT EXISTS \"uq_dsm_charges_state_month\" ON \"public\".\"dsm_charges\"(\"state\", \"month\");");

            // Sub-test C: CRUD verification on dsm_charges
            executeSql(dbUrl, """
                INSERT INTO "public"."dsm_charges" ("state", "month", "dsm_charges_rs_per_kwh")
                VALUES ('Maharashtra', 202604, 1.250000);
                """);

            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT state, month, dsm_charges_rs_per_kwh FROM \"public\".\"dsm_charges\" WHERE \"state\" = 'Maharashtra'")) {
                 assertThat(rs.next()).isTrue();
                 assertThat(rs.getString("state")).isEqualTo("Maharashtra");
                 assertThat(rs.getInt("month")).isEqualTo(202604);
                 assertThat(rs.getBigDecimal("dsm_charges_rs_per_kwh")).isEqualByComparingTo("1.250000");
            }

            // Sub-test D: Unique constraint blocks duplicate (state, month)
            assertThatThrownBy(() -> {
                executeSql(dbUrl, """
                    INSERT INTO "public"."dsm_charges" ("state", "month", "dsm_charges_rs_per_kwh")
                    VALUES ('Maharashtra', 202604, 2.500000);
                    """);
            }).hasMessageContaining("uq_dsm_charges_state_month");

        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("Scenario 4: Rehearsal of V7 case-insensitive username functional index and preflight duplicate abort")
    void testScenario4_caseInsensitiveUsernameV7Rehearsal() throws Exception {
        String dbName = "v7_rehearsal_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            // Part 1: Run migrations up to V6
            Flyway flywayV6 = Flyway.configure()
                    .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .target("6")
                    .load();
            flywayV6.migrate();
            assertThat(flywayV6.info().current().getVersion().getVersion()).isEqualTo("6");

            // Part 2: Insert two users with duplicate normalized usernames directly into User table
            String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
            executeSql(dbUrl, String.format("""
                INSERT INTO "public"."User" ("id", "username", "email", "passwordHash", "role", "createdAt", "updatedAt")
                VALUES ('user-id-1-%s', 'Ankit_%s', 'user1_%s@probus.io', 'hash1', 'CLIENT', NOW(), NOW()),
                       ('user-id-2-%s', 'ankit_%s', 'user2_%s@probus.io', 'hash2', 'CLIENT', NOW(), NOW());
                """, uid, uid, uid, uid, uid, uid));

            // Part 3: Running V7 migration now MUST fail cleanly due to preflight duplicate check
            Flyway flywayV7 = Flyway.configure()
                    .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .target("7")
                    .load();

            assertThatThrownBy(flywayV7::migrate)
                    .isInstanceOf(FlywayMigrateException.class)
                    .hasMessageContaining("duplicate normalized usernames in public.\"User\"");

            // Part 4: Clean up the duplicate record so preflight passes
            executeSql(dbUrl, String.format("DELETE FROM \"public\".\"User\" WHERE \"id\" = 'user-id-2-%s'", uid));

            // Part 5: V7 migration now succeeds cleanly
            // Need to repair flyway history table since previous migration failed
            flywayV7.repair();
            int migrated = flywayV7.migrate().migrationsExecuted;
            assertThat(migrated).isEqualTo(1);
            assertThat(flywayV7.info().current().getVersion().getVersion()).isEqualTo("7");

            // Part 6: Verify unique index uq_user_username_lower_trim exists in pg_indexes
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT indexname, indexdef FROM pg_indexes
                     WHERE tablename = 'User' AND indexname = 'uq_user_username_lower_trim'
                     """)) {
                assertThat(rs.next()).isTrue();
                String def = rs.getString("indexdef").toLowerCase();
                assertThat(def).contains("lower").contains("trim").contains("username");
            }

            // Part 7: Verify direct duplicate normalized insert now fails on uq_user_username_lower_trim
            assertThatThrownBy(() -> {
                executeSql(dbUrl, String.format("""
                    INSERT INTO "public"."User" ("id", "username", "email", "passwordHash", "role", "createdAt", "updatedAt")
                    VALUES ('user-id-3-%s', 'ANKIT_%s', 'user3_%s@probus.io', 'hash3', 'CLIENT', NOW(), NOW());
                    """, uid, uid, uid));
            }).hasMessageContaining("uq_user_username_lower_trim");

            // Migrate to V8 so all entity attributes (like fullName) are present
            Flyway flywayV8 = Flyway.configure()
                    .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .target("8")
                    .load();
            int v8Migrated = flywayV8.migrate().migrationsExecuted;
            assertThat(v8Migrated).isEqualTo(1);
            assertThat(flywayV8.info().current().getVersion().getVersion()).isEqualTo("8");

            // Part 8: Verify Hibernate validation succeeds on the schema after V8
            validateHibernateSchema(dbUrl);

        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("Scenario 5: Rehearsal of V8 case-insensitive email functional index, fullName column, and preflight duplicate abort")
    void testScenario5_caseInsensitiveEmailAndFullNameV8Rehearsal() throws Exception {
        String dbName = "v8_rehearsal_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            // Part 1: Run migrations up to V7
            Flyway flywayV7 = Flyway.configure()
                    .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .target("7")
                    .load();
            flywayV7.migrate();
            assertThat(flywayV7.info().current().getVersion().getVersion()).isEqualTo("7");

            // Part 2: Insert two users with duplicate normalized emails directly into User table
            String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
            executeSql(dbUrl, String.format("""
                INSERT INTO "public"."User" ("id", "username", "email", "passwordHash", "role", "createdAt", "updatedAt")
                VALUES ('user-id-v8-1-%s', 'user1_%s', 'dup_email_%s@probus.io', 'hash1', 'CLIENT', NOW(), NOW()),
                       ('user-id-v8-2-%s', 'user2_%s', 'DUP_EMAIL_%s@PROBUS.IO', 'hash2', 'CLIENT', NOW(), NOW());
                """, uid, uid, uid, uid, uid, uid));

            // Part 3: Running V8 migration now MUST fail cleanly due to preflight duplicate email check
            Flyway flywayV8 = Flyway.configure()
                    .dataSource(dbUrl, postgres.getUsername(), postgres.getPassword())
                    .schemas("public", "forecasting")
                    .defaultSchema("public")
                    .locations("classpath:db/migration")
                    .target("8")
                    .load();

            assertThatThrownBy(flywayV8::migrate)
                    .isInstanceOf(FlywayMigrateException.class)
                    .hasMessageContaining("duplicate normalized emails in public.\"User\"");

            // Part 4: Clean up the duplicate record so preflight passes
            executeSql(dbUrl, String.format("DELETE FROM \"public\".\"User\" WHERE \"id\" = 'user-id-v8-2-%s'", uid));

            // Part 5: V8 migration now succeeds cleanly after repair
            flywayV8.repair();
            int migrated = flywayV8.migrate().migrationsExecuted;
            assertThat(migrated).isEqualTo(1);
            assertThat(flywayV8.info().current().getVersion().getVersion()).isEqualTo("8");

            // Part 6: Verify unique index uq_user_email_lower_trim exists in pg_indexes
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT indexname, indexdef FROM pg_indexes
                     WHERE tablename = 'User' AND indexname = 'uq_user_email_lower_trim'
                     """)) {
                assertThat(rs.next()).isTrue();
                String def = rs.getString("indexdef").toLowerCase();
                assertThat(def).contains("lower").contains("trim").contains("email");
            }

            // Part 7: Verify legacy User_email_key is dropped from pg_indexes
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT COUNT(*) FROM pg_indexes
                     WHERE tablename = 'User' AND indexname = 'User_email_key'
                     """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }

            // Part 8: Verify fullName column exists in information_schema.columns and is nullable
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT column_name, is_nullable, character_maximum_length, data_type
                     FROM information_schema.columns
                     WHERE table_schema = 'public' AND table_name = 'User' AND column_name = 'fullName'
                     """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("is_nullable")).isEqualTo("YES");
                assertThat(rs.getInt("character_maximum_length")).isEqualTo(100);
            }

            // Part 9: Verify direct duplicate normalized email insert now fails on uq_user_email_lower_trim
            assertThatThrownBy(() -> {
                executeSql(dbUrl, String.format("""
                    INSERT INTO "public"."User" ("id", "username", "email", "passwordHash", "role", "createdAt", "updatedAt")
                    VALUES ('user-id-v8-3-%s', 'user3_%s', '  DUP_EMAIL_%s@probus.io  ', 'hash3', 'CLIENT', NOW(), NOW());
                    """, uid, uid, uid));
            }).hasMessageContaining("uq_user_email_lower_trim");

            // Part 10: Verify inserting user with non-null fullName succeeds
            executeSql(dbUrl, String.format("""
                INSERT INTO "public"."User" ("id", "username", "email", "fullName", "passwordHash", "role", "createdAt", "updatedAt")
                VALUES ('user-id-v8-4-%s', 'user4_%s', 'unique_%s@probus.io', 'Dr. Alice Smith', 'hash4', 'CLIENT', NOW(), NOW());
                """, uid, uid, uid));

            // Part 11: Verify Hibernate validation succeeds on the schema after V8
            validateHibernateSchema(dbUrl);

        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }
}

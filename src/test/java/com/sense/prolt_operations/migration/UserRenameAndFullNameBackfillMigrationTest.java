package com.sense.prolt_operations.migration;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.internal.exception.FlywayMigrateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sense.prolt_operations.BaseIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRenameAndFullNameBackfillMigrationTest extends BaseIntegrationTest {

    private static final String[][] APPROVED_USERS = {
        {"superadmin", "superadmin@probus.io", "SUPER_ADMIN"},
        {"Yash", "yash@probus.io", "ADMIN"},
        {"Saikat", "saikat@probus.io", "ADMIN"},
        {"Priyanka", "priyanka@probus.io", "ADMIN"},
        {"harshit", "harshit@probus.io", "ADMIN"},
        {"ankit_vaish", "ankit_vaish@probus.io", "CLIENT"},
        {"Gaurav", "gaurav@probus.io", "CLIENT"},
        {"Ankit_Jha", "ankit_jha@probus.io", "CLIENT"},
        {"Neeraj Sharma", "neeraj_sharma@probus.io", "CLIENT"},
        {"Anurag Shukla", "anurag_shukla@probus.io", "CLIENT"},
        {"Anand", "anand@probus.io", "CLIENT"},
        {"Saurabh Anoop", "saurabh_anoop@probus.io", "CLIENT"}
    };

    private static final Map<String, String[]> EXPECTED_MAPPING = Map.ofEntries(
        Map.entry("superadmin", new String[]{"superadmin", "System Administrator"}),
        Map.entry("Yash", new String[]{"yash", "Yash"}),
        Map.entry("Saikat", new String[]{"saikat", "Saikat"}),
        Map.entry("Priyanka", new String[]{"priyanka", "Priyanka"}),
        Map.entry("harshit", new String[]{"harshit", "Harshit"}),
        Map.entry("ankit_vaish", new String[]{"ankit_vaish", "Ankit Vaish"}),
        Map.entry("Gaurav", new String[]{"gaurav", "Gaurav"}),
        Map.entry("Ankit_Jha", new String[]{"ankit_jha", "Ankit Jha"}),
        Map.entry("Neeraj Sharma", new String[]{"neeraj_sharma", "Neeraj Sharma"}),
        Map.entry("Anurag Shukla", new String[]{"anurag_shukla", "Anurag Shukla"}),
        Map.entry("Anand", new String[]{"anand", "Anand"}),
        Map.entry("Saurabh Anoop", new String[]{"saurabh_anoop", "Saurabh Anoop"})
    );

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

    private void insertUser(String dbUrl, String id, String username, String email, String fullName,
                            String passwordHash, String role, String[] hiddenModules, String[] readOnlyModules) throws Exception {
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
            Array hiddenArr = conn.createArrayOf("varchar", hiddenModules != null ? hiddenModules : new String[0]);
            Array readOnlyArr = conn.createArrayOf("varchar", readOnlyModules != null ? readOnlyModules : new String[0]);
            ps.setArray(7, hiddenArr);
            ps.setArray(8, readOnlyArr);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // V9 TEST SCENARIOS
    // =========================================================================

    @Test
    @DisplayName("V9-01: All 12 approved source accounts normalized and fullName backfilled correctly")
    void v9_all12ApprovedAccountsNormalizedAndFullNamePopulated() throws Exception {
        String dbName = "v9_all12_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            // Apply migrations up to V8
            configureFlyway(dbUrl, "8").migrate();

            // Seed all 12 approved accounts with NULL fullName (as in V8 baseline)
            Map<String, String> userIds = new HashMap<>();
            for (String[] u : APPROVED_USERS) {
                String id = UUID.randomUUID().toString();
                userIds.put(u[0], id);
                insertUser(dbUrl, id, u[0], u[1], null, "hash_" + u[0], u[2], new String[]{"mod1"}, new String[]{"mod2"});
            }

            // Run migration V9
            configureFlyway(dbUrl, "9").migrate();

            // Verify all 12 accounts
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, username, \"fullName\" FROM public.\"User\"")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    String id = rs.getString("id");
                    String username = rs.getString("username");
                    String fullName = rs.getString("fullName");

                    // Find which source user had this id
                    String sourceUser = userIds.entrySet().stream()
                            .filter(e -> e.getValue().equals(id))
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElseThrow();

                    String[] expected = EXPECTED_MAPPING.get(sourceUser);
                    assertThat(username).isEqualTo(expected[0]);
                    assertThat(fullName).isEqualTo(expected[1]);
                }
                assertThat(count).isEqualTo(12);
            }
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V9-02: Subset of mapped accounts is handled safely")
    void v9_subsetOfMappedAccountsHandledSafely() throws Exception {
        String dbName = "v9_subset_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "8").migrate();

            // Seed only 3 accounts
            insertUser(dbUrl, UUID.randomUUID().toString(), "Yash", "yash@probus.io", null, "hash1", "ADMIN", null, null);
            insertUser(dbUrl, UUID.randomUUID().toString(), "Saikat", "saikat@probus.io", null, "hash2", "ADMIN", null, null);
            insertUser(dbUrl, UUID.randomUUID().toString(), "Priyanka", "priyanka@probus.io", null, "hash3", "ADMIN", null, null);

            // Run V9
            configureFlyway(dbUrl, "9").migrate();

            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT username, \"fullName\" FROM public.\"User\" ORDER BY username")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("username")).isEqualTo("priyanka");
                assertThat(rs.getString("fullName")).isEqualTo("Priyanka");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("username")).isEqualTo("saikat");
                assertThat(rs.getString("fullName")).isEqualTo("Saikat");

                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("username")).isEqualTo("yash");
                assertThat(rs.getString("fullName")).isEqualTo("Yash");

                assertThat(rs.next()).isFalse();
            }
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V9-03: Empty User table does not cause V9 to fail")
    void v9_emptyUserTableSucceeds() throws Exception {
        String dbName = "v9_empty_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "8").migrate();

            // Run V9 directly on empty table
            int executed = configureFlyway(dbUrl, "9").migrate().migrationsExecuted;
            assertThat(executed).isEqualTo(1);

            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM public.\"User\"")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V9-04: Unrelated user is not modified")
    void v9_unrelatedUserIsNotModified() throws Exception {
        String dbName = "v9_unrelated_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "8").migrate();

            String unrelatedId = UUID.randomUUID().toString();
            insertUser(dbUrl, unrelatedId, "Custom_Employee", "custom@probus.io", null, "custom_hash", "CLIENT", null, null);
            insertUser(dbUrl, UUID.randomUUID().toString(), "Yash", "yash@probus.io", null, "yash_hash", "ADMIN", null, null);

            configureFlyway(dbUrl, "9").migrate();

            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT username, \"fullName\" FROM public.\"User\" WHERE id = '" + unrelatedId + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("username")).isEqualTo("Custom_Employee");
                assertThat(rs.getString("fullName")).isNull();
            }
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V9-05: Target username collision causes safe failure")
    void v9_targetUsernameCollisionCausesSafeFailure() throws Exception {
        String dbName = "v9_collision_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "8").migrate();

            // Mapped user: "Neeraj Sharma" -> target "neeraj_sharma"
            insertUser(dbUrl, UUID.randomUUID().toString(), "Neeraj Sharma", "neeraj@probus.io", null, "hash1", "ADMIN", null, null);
            // Conflicting user already occupying target "neeraj_sharma" under different id
            insertUser(dbUrl, UUID.randomUUID().toString(), "neeraj_sharma", "conflict@probus.io", null, "hash2", "CLIENT", null, null);

            assertThatThrownBy(() -> configureFlyway(dbUrl, "9").migrate())
                    .isInstanceOf(FlywayMigrateException.class)
                    .hasMessageContaining("Target username collision detected");
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V9-06: Unexpected non-null fullName for mapped account causes safe abort and is not overwritten")
    void v9_unexpectedNonNullFullNameCausesSafeAbort() throws Exception {
        String dbName = "v9_existing_fn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "8").migrate();

            String yashId = UUID.randomUUID().toString();
            insertUser(dbUrl, yashId, "Yash", "yash@probus.io", "PreExisting Custom FullName", "hash1", "ADMIN", null, null);

            assertThatThrownBy(() -> configureFlyway(dbUrl, "9").migrate())
                    .isInstanceOf(FlywayMigrateException.class)
                    .hasMessageContaining("pre-existing non-null fullName");

            // Verify that row was rolled back and NOT modified
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT username, \"fullName\" FROM public.\"User\" WHERE id = '" + yashId + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("username")).isEqualTo("Yash");
                assertThat(rs.getString("fullName")).isEqualTo("PreExisting Custom FullName");
            }
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V9-06b: Whitespace-only existing fullName causes safe abort without modifying username or fullName")
    void v9_whitespaceOnlyNonNullFullNameCausesSafeAbort() throws Exception {
        String dbName = "v9_ws_fn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "8").migrate();

            String yashId = UUID.randomUUID().toString();
            insertUser(dbUrl, yashId, "Yash", "yash@probus.io", "   ", "hash1", "ADMIN", null, null);

            assertThatThrownBy(() -> configureFlyway(dbUrl, "9").migrate())
                    .isInstanceOf(FlywayMigrateException.class)
                    .hasMessageContaining("pre-existing non-null fullName");

            // Verify that row was rolled back and NOT modified
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT username, \"fullName\" FROM public.\"User\" WHERE id = '" + yashId + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("username")).isEqualTo("Yash");
                assertThat(rs.getString("fullName")).isEqualTo("   ");
            }
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V9-07 to 10: Protected user fields (id, email, passwordHash, role, modules, createdAt) remain unchanged")
    void v9_protectedUserFieldsRemainUnchanged() throws Exception {
        String dbName = "v9_protected_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "8").migrate();

            String userId = UUID.randomUUID().toString();
            String email = "priyanka.audit@probus.io";
            String passwordHash = "$2a$10$e8wVfQ0...testProtectedPasswordHash";
            String role = "ADMIN";
            String[] hidden = new String[]{"moduleA", "moduleB"};
            String[] readOnly = new String[]{"moduleC"};

            insertUser(dbUrl, userId, "Priyanka", email, null, passwordHash, role, hidden, readOnly);

            configureFlyway(dbUrl, "9").migrate();

            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM public.\"User\" WHERE id = '" + userId + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("id")).isEqualTo(userId);
                assertThat(rs.getString("username")).isEqualTo("priyanka");
                assertThat(rs.getString("email")).isEqualTo(email);
                assertThat(rs.getString("fullName")).isEqualTo("Priyanka");
                assertThat(rs.getString("passwordHash")).isEqualTo(passwordHash);
                assertThat(rs.getString("role")).isEqualTo(role);

                Array hArr = rs.getArray("hiddenModules");
                assertThat((String[]) hArr.getArray()).containsExactly("moduleA", "moduleB");

                Array rArr = rs.getArray("readOnlyModules");
                assertThat((String[]) rArr.getArray()).containsExactly("moduleC");
            }
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V9-11: Historical audit and username snapshot records remain untouched")
    void v9_historicalAuditAndUsernameSnapshotsRemainUntouched() throws Exception {
        String dbName = "v9_history_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "8").migrate();

            String userId = UUID.randomUUID().toString();
            insertUser(dbUrl, userId, "Priyanka", "priyanka@probus.io", null, "hash", "ADMIN", null, null);

            // Insert audit log snapshot with historical casing
            String auditId = UUID.randomUUID().toString();
            executeSql(dbUrl, String.format("""
                INSERT INTO public."UserAuditLog" ("id", "action", "targetUserId", "targetUser", "performedBy", "changes", "timestamp")
                VALUES ('%s', 'CREATE', '%s', 'Priyanka', 'superadmin', '{}', NOW());
                """, auditId, userId));

            // Insert savings calculator entry with createdBy snapshot
            String savingsId = UUID.randomUUID().toString();
            executeSql(dbUrl, String.format("""
                INSERT INTO public.savings_calculator_new_entries ("id", "client_name", "industry_name", "address", "state_code", "discom", "consumer_category", "voltage_level", "tod_consumptions", "createdBy", "updatedBy", "created_at", "updated_at")
                VALUES ('%s', 'Client X', 'Industry Y', 'Address Z', 'UP', 'PVVNL', 'HT-1', '11kV', '{}', 'Priyanka', 'Saikat', NOW(), NOW());
                """, savingsId));

            // Execute V9
            configureFlyway(dbUrl, "9").migrate();

            // Assert UserAuditLog was not modified
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT \"targetUser\", \"performedBy\" FROM public.\"UserAuditLog\" WHERE id = '" + auditId + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("targetUser")).isEqualTo("Priyanka");
                assertThat(rs.getString("performedBy")).isEqualTo("superadmin");
            }

            // Assert savings_calculator_new_entries was not modified
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT \"createdBy\", \"updatedBy\" FROM public.savings_calculator_new_entries WHERE id = '" + savingsId + "'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("createdBy")).isEqualTo("Priyanka");
                assertThat(rs.getString("updatedBy")).isEqualTo("Saikat");
            }
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V9-12: The functional username unique index remains valid after migration")
    void v9_functionalUsernameUniqueIndexRemainsValid() throws Exception {
        String dbName = "v9_index_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "8").migrate();
            insertUser(dbUrl, UUID.randomUUID().toString(), "Yash", "yash@probus.io", null, "hash", "ADMIN", null, null);

            configureFlyway(dbUrl, "9").migrate();

            // Attempting to insert duplicate normalized username (" YASH " or "yash") must fail on uq_user_username_lower_trim
            assertThatThrownBy(() -> insertUser(dbUrl, UUID.randomUUID().toString(), " YASH ", "dup@probus.io", "Other Name", "hash", "CLIENT", null, null))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_user_username_lower_trim");
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    // =========================================================================
    // V10 TEST SCENARIOS
    // =========================================================================

    @Test
    @DisplayName("V10-01: Zero users in table: migration succeeds")
    void v10_zeroUsersSucceeds() throws Exception {
        String dbName = "v10_zero_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "9").migrate();

            int executed = configureFlyway(dbUrl, "10").migrate().migrationsExecuted;
            assertThat(executed).isEqualTo(1);

            // Verify column is NOT NULL
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT is_nullable FROM information_schema.columns
                     WHERE table_schema = 'public' AND table_name = 'User' AND column_name = 'fullName'
                     """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("is_nullable")).isEqualTo("NO");
            }
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V10-02: All users have nonblank fullName: migration succeeds")
    void v10_allUsersHaveNonblankFullNameSucceeds() throws Exception {
        String dbName = "v10_valid_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "8").migrate();

            // Seed 12 users
            for (String[] u : APPROVED_USERS) {
                insertUser(dbUrl, UUID.randomUUID().toString(), u[0], u[1], null, "hash", u[2], null, null);
            }

            // Run V9 to backfill all fullNames
            configureFlyway(dbUrl, "9").migrate();

            // Run V10
            int executed = configureFlyway(dbUrl, "10").migrate().migrationsExecuted;
            assertThat(executed).isEqualTo(1);

            // Verify is_nullable is NO
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                     SELECT is_nullable FROM information_schema.columns
                     WHERE table_schema = 'public' AND table_name = 'User' AND column_name = 'fullName'
                     """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("is_nullable")).isEqualTo("NO");
            }
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V10-03: A NULL fullName: migration fails safely")
    void v10_nullFullNameFailsSafely() throws Exception {
        String dbName = "v10_null_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "9").migrate();

            // Insert user with NULL fullName
            insertUser(dbUrl, UUID.randomUUID().toString(), "null_user", "null@probus.io", null, "hash", "CLIENT", null, null);

            assertThatThrownBy(() -> configureFlyway(dbUrl, "10").migrate())
                    .isInstanceOf(FlywayMigrateException.class)
                    .hasMessageContaining("All users must have a valid fullName before enforcing NOT NULL");
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V10-04: An empty string fullName: migration fails safely")
    void v10_emptyFullNameFailsSafely() throws Exception {
        String dbName = "v10_empty_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "9").migrate();

            // Insert user with empty string fullName
            insertUser(dbUrl, UUID.randomUUID().toString(), "empty_user", "empty@probus.io", "", "hash", "CLIENT", null, null);

            assertThatThrownBy(() -> configureFlyway(dbUrl, "10").migrate())
                    .isInstanceOf(FlywayMigrateException.class)
                    .hasMessageContaining("All users must have a valid fullName before enforcing NOT NULL");
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V10-05: A whitespace-only fullName: migration fails safely")
    void v10_whitespaceFullNameFailsSafely() throws Exception {
        String dbName = "v10_whitespace_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "9").migrate();

            // Insert user with whitespace-only fullName
            insertUser(dbUrl, UUID.randomUUID().toString(), "space_user", "space@probus.io", "   ", "hash", "CLIENT", null, null);

            assertThatThrownBy(() -> configureFlyway(dbUrl, "10").migrate())
                    .isInstanceOf(FlywayMigrateException.class)
                    .hasMessageContaining("All users must have a valid fullName before enforcing NOT NULL");
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("V10-06 & 07: Successful migration makes column NOT NULL and existing fullName values remain unchanged")
    void v10_makesColumnNotNullAndPreservesExistingValues() throws Exception {
        String dbName = "v10_preserve_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        executeSql(postgres.getJdbcUrl(), "CREATE DATABASE \"" + dbName + "\"");
        String dbUrl = postgres.getJdbcUrl().replace("/Prolt_Operations", "/" + dbName);

        try {
            configureFlyway(dbUrl, "8").migrate();

            String u1 = UUID.randomUUID().toString();
            String u2 = UUID.randomUUID().toString();
            insertUser(dbUrl, u1, "Yash", "yash@probus.io", null, "h1", "ADMIN", null, null);
            insertUser(dbUrl, u2, "Saikat", "saikat@probus.io", null, "h2", "ADMIN", null, null);

            // V9 backfills
            configureFlyway(dbUrl, "9").migrate();

            // Record snapshot before V10
            Map<String, String> namesBefore = new HashMap<>();
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, \"fullName\" FROM public.\"User\"")) {
                while (rs.next()) {
                    namesBefore.put(rs.getString("id"), rs.getString("fullName"));
                }
            }

            // V10 enforces NOT NULL
            configureFlyway(dbUrl, "10").migrate();

            // Assert names match exactly
            try (Connection conn = DriverManager.getConnection(dbUrl, postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id, \"fullName\" FROM public.\"User\"")) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String fn = rs.getString("fullName");
                    assertThat(fn).isEqualTo(namesBefore.get(id));
                }
            }

            // Assert inserting NULL fullName fails at the PostgreSQL database level
            assertThatThrownBy(() -> insertUser(dbUrl, UUID.randomUUID().toString(), "new_user", "new@probus.io", null, "h3", "CLIENT", null, null))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("null value in column \"fullName\"");
        } finally {
            executeSql(postgres.getJdbcUrl(), "DROP DATABASE IF EXISTS \"" + dbName + "\" WITH (FORCE)");
        }
    }
}

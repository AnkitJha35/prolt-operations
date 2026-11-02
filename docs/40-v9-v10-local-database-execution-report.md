# Execution Report: Flyway V9 and V10 Local Database Execution

**Document Reference**: `docs/40-v9-v10-local-database-execution-report.md`  
**Execution Date**: 2026-11-02 14:14 IST  
**Environment**: Local Restored PostgreSQL (Container `postgres-server`)  
**Target JDBC URL**: `jdbc:postgresql://localhost:5432/prolt_operations`  
**Target Engine**: PostgreSQL 16.14 (Debian 16.14-1.pgdg13+1)  
**Execution Result**: **100% SUCCESSFUL — FLYWAY V9 & V10 FULLY APPLIED**  

---

## 1. Target Database Scope and Isolation Confirmation

Strict adherence to the authorized scope was maintained throughout all phases:
- **Target Database**: `prolt_operations` on `localhost:5432`.
- **Engine**: PostgreSQL 16.14 running on Docker container `postgres-server`.
- **Remote Production**: **NEVER CONTACTED** (0 network calls to production/remote).
- **Staging**: **NEVER CONTACTED**.
- **No Database Replace/Restore**: The database was mutated forward strictly via Flyway migrations. No tables were dropped or truncated.
- **No Git Commits**: Zero git commits were created. All changes remain unstaged in the local working tree.

---

## 2. Phase A: Read-Only Preflight Audit Results

Prior to creating backups or modifying database records, comprehensive preflight verification was conducted against `prolt_operations`:

### 2.1 Baseline Flyway Status
```sql
SELECT installed_rank, version, description, type, script, checksum, success 
FROM flyway_schema_history 
ORDER BY installed_rank;
```
| Installed Rank | Version | Description | Type | Script | Success |
|---|---|---|---|---|---|
| 1 | `5` | `<< Flyway Baseline >>` | BASELINE | `<< Flyway Baseline >>` | `true` |
| 2 | `6` | `create dsm charges if missing` | SQL | `V6__create_dsm_charges_if_missing.sql` | `true` |
| 3 | `7` | `case insensitive username uniqueness` | SQL | `V7__case_insensitive_username_uniqueness.sql` | `true` |
| 4 | `8` | `email case insensitive and full name` | SQL | `V8__email_case_insensitive_and_full_name.sql` | `true` |

- **Current Version**: Exactly `8`.
- **Pending Migrations**: `V9` and `V10` were pending.
- **Failed Migrations**: None (`success = true` for all rows).

### 2.2 User Records Baseline Audit
- **Total Accounts**: Exactly `12`.
- **Usernames Matching Approved Mapping**: Exactly `12` (0 missing, 0 unexpected).
- **`fullName` Values**: Exactly 12 `NULL` (0 non-null, 0 empty, 0 whitespace).
- **Uniqueness Check**: `0` duplicate target usernames under `LOWER(TRIM(username))`.
- **Email Integrity**: `12` non-null, whitespace-free emails, `0` duplicates under `LOWER(TRIM(email))`.

### 2.3 Baseline Protected Fields Checksum
```sql
SELECT 
    md5(string_agg(id || '|' || email || '|' || COALESCE("passwordHash", '') || '|' || role::text || '|' || COALESCE("hiddenModules"::text, '{}') || '|' || COALESCE("readOnlyModules"::text, '{}') || '|' || "createdAt"::text, ',' ORDER BY id)) AS protected_fields_md5,
    encode(sha256(string_agg(id || '|' || email || '|' || COALESCE("passwordHash", '') || '|' || role::text || '|' || COALESCE("hiddenModules"::text, '{}') || '|' || COALESCE("readOnlyModules"::text, '{}') || '|' || "createdAt"::text, ',' ORDER BY id)::bytea), 'hex') AS protected_fields_sha256
FROM public."User";
```
- **Baseline MD5**: `6305b08a6edb44a22b3c59e0e698c36f`
- **Baseline SHA-256**: `9d4f203aa3908dca37ebffdd83ea4c0bdd5ef2a527788cf5a1939e4427c7e8c0`

### 2.4 Baseline Historical Snapshot Checksums
- `UserAuditLog` MD5: `3f41aa8d3951bf985444c1b6dd6b17dc` (44 records)
- `savings_calculator_new_entries` user columns MD5: `acef6509e068e5239b935de1b0a781f0` (31 records)

---

## 3. Phase B: Pre-Migration Backup Verification

A full PostgreSQL custom-format backup was generated before any migration was executed:

- **Backup File**: `backups/prolt_operations_pre_v9_v10_20261102_140500.dump`
- **Format**: PostgreSQL Custom Format (`-Fc`, compressed)
- **File Size**: `1,037,656,733` bytes (~1,037 MB)
- **SHA-256**: `248f05bcb19b1616a9b4cc67d3e9bd08d457425fb23caf3ca23ad96241e997c7`
- **Archive Validation (`pg_restore --list`)**:
  - `TOC Entries`: 453
  - `Source Database`: `prolt_operations`
  - `Dump Version`: `1.16-0` (PostgreSQL 16.14)
  - Successfully read and verified without warnings or errors.
- **Existing Backups**:
  - `backups/prolt_operations_pre_v7_20261030_180304.dump` (Untouched)
  - `backups/prolt_operations_pre_v8_20261102_114557.dump` (Untouched)

---

## 4. Phase C: Flyway V9 Execution & Postflight Verification

### 4.1 V9 Execution Log Excerpt
Executed via Spring Boot runtime under profile `localvalidate` targeting version `9`:
```text
2026-11-02 14:09:26.238 [main] INFO  org.flywaydb.core.FlywayExecutor - Database: jdbc:postgresql://localhost:5432/prolt_operations?stringtype=unspecified (PostgreSQL 16.14)
2026-11-02 14:09:26.282 [main] INFO  o.f.core.internal.command.DbValidate - Successfully validated 11 migrations (execution time 00:00.021s)
2026-11-02 14:09:26.306 [main] INFO  o.f.core.internal.command.DbMigrate - Current version of schema "public": 8
2026-11-02 14:09:26.319 [main] INFO  o.f.core.internal.command.DbMigrate - Migrating schema "public" to version "9 - normalize usernames and backfill full name"
2026-11-02 14:09:26.356 [main] INFO  o.f.c.i.s.DefaultSqlScriptExecutor - DB: Flyway migration V9 completed successfully: 12 account(s) normalized and fullNames backfilled.
2026-11-02 14:09:26.368 [main] INFO  o.f.core.internal.command.DbMigrate - Successfully applied 1 migration to schema "public", now at version v9 (execution time 00:00.021s)
```

### 4.2 Account Mapping Verification (Before vs. After)

All 12 user accounts were verified against the approved mapping:

| # | Previous Username (`source_username`) | New Username (`target_username`) | Populated Full Name (`fullName`) | Verification Status |
|---|---|---|---|---|
| 1 | `superadmin` | `superadmin` | `System Administrator` | **MATCH** |
| 2 | `Yash` | `yash` | `Yash` | **MATCH** |
| 3 | `Saikat` | `saikat` | `Saikat` | **MATCH** |
| 4 | `Priyanka` | `priyanka` | `Priyanka` | **MATCH** |
| 5 | `harshit` | `harshit` | `Harshit` | **MATCH** |
| 6 | `ankit_vaish` | `ankit_vaish` | `Ankit Vaish` | **MATCH** |
| 7 | `Gaurav` | `gaurav` | `Gaurav` | **MATCH** |
| 8 | `Ankit_Jha` | `ankit_jha` | `Ankit Jha` | **MATCH** |
| 9 | `Neeraj Sharma` | `neeraj_sharma` | `Neeraj Sharma` | **MATCH** |
| 10 | `Anurag Shukla` | `anurag_shukla` | `Anurag Shukla` | **MATCH** |
| 11 | `Anand` | `anand` | `Anand` | **MATCH** |
| 12 | `Saurabh Anoop` | `saurabh_anoop` | `Saurabh Anoop` | **MATCH** |

### 4.3 Protected Fields Verification
```sql
SELECT 
    md5(string_agg(id || '|' || email || '|' || COALESCE("passwordHash", '') || '|' || role::text || '|' || COALESCE("hiddenModules"::text, '{}') || '|' || COALESCE("readOnlyModules"::text, '{}') || '|' || "createdAt"::text, ',' ORDER BY id)) AS protected_fields_md5,
    encode(sha256(string_agg(id || '|' || email || '|' || COALESCE("passwordHash", '') || '|' || role::text || '|' || COALESCE("hiddenModules"::text, '{}') || '|' || COALESCE("readOnlyModules"::text, '{}') || '|' || "createdAt"::text, ',' ORDER BY id)::bytea), 'hex') AS protected_fields_sha256
FROM public."User";
```
- **Post-V9 MD5**: `6305b08a6edb44a22b3c59e0e698c36f` (**EXACT MATCH** with baseline)
- **Post-V9 SHA-256**: `9d4f203aa3908dca37ebffdd83ea4c0bdd5ef2a527788cf5a1939e4427c7e8c0` (**EXACT MATCH** with baseline)
- All 12 `id`, `email`, `passwordHash`, `role`, `hiddenModules`, `readOnlyModules`, and `createdAt` values were 100% preserved.

### 4.4 Historical Snapshot & Unique Index Verification
- `UserAuditLog` MD5: `3f41aa8d3951bf985444c1b6dd6b17dc` (**EXACT MATCH**)
- `savings_calculator_new_entries` MD5: `acef6509e068e5239b935de1b0a781f0` (**EXACT MATCH**)
- Functional Unique Index: `uq_user_username_lower_trim` verified valid (`indisvalid = t`).

---

## 5. Phase D: Flyway V10 Execution & Schema Enforcement

### 5.1 V10 Execution Log Excerpt
Executed via Spring Boot runtime under profile `localvalidate` targeting version `10`:
```text
2026-11-02 14:10:48.827 [main] INFO  org.flywaydb.core.FlywayExecutor - Database: jdbc:postgresql://localhost:5432/prolt_operations?stringtype=unspecified (PostgreSQL 16.14)
2026-11-02 14:10:48.863 [main] INFO  o.f.core.internal.command.DbValidate - Successfully validated 11 migrations (execution time 00:00.021s)
2026-11-02 14:10:48.896 [main] INFO  o.f.core.internal.command.DbMigrate - Current version of schema "public": 9
2026-11-02 14:10:48.906 [main] INFO  o.f.core.internal.command.DbMigrate - Migrating schema "public" to version "10 - enforce user full name not null"
2026-11-02 14:10:48.939 [main] INFO  o.f.c.i.s.DefaultSqlScriptExecutor - DB: Flyway migration V10 completed successfully: NOT NULL constraint enforced on public."User"."fullName".
2026-11-02 14:10:48.950 [main] INFO  o.f.core.internal.command.DbMigrate - Successfully applied 1 migration to schema "public", now at version v10 (execution time 00:00.034s)
```

### 5.2 Schema Metadata Verification
```sql
SELECT column_name, is_nullable, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'User'
  AND column_name = 'fullName';
```
| Column Name | Is Nullable | Data Type | Max Length |
|---|---|---|---|
| `fullName` | **NO** | `character varying` | `100` |

### 5.3 Data Completeness Post-V10
```sql
SELECT 
    COUNT(*) as total_users,
    COUNT("fullName") as count_fullnames,
    COUNT(*) FILTER (WHERE "fullName" IS NULL) as null_fullnames,
    COUNT(*) FILTER (WHERE TRIM("fullName") = '') as empty_or_whitespace_fullnames
FROM public."User";
```
- **Total Users**: `12`
- **Count Non-Null `fullName`**: `12`
- **Null `fullName`**: `0`
- **Empty / Blank `fullName`**: `0`

---

## 6. Phase E: Application and Entity Verification

### 6.1 JPA Entity Update
Following successful V10 execution and schema verification, `src/main/java/com/sense/prolt_operations/entity/User.java` was updated:
```java
    @Column(name = "fullName", length = 100, nullable = false)
    private String fullName;
```

### 6.2 Hibernate Schema Validation (`ddl-auto=validate`)
Spring Boot was launched with `localvalidate` profile connecting to `localhost:5432/prolt_operations` with Flyway disabled:
```text
2026-11-02 14:12:09.117 [main] INFO  o.s.o.j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-11-02 14:12:12.866 [main] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port 8089 (http) with context path '/'
2026-11-02 14:12:12.880 [main] INFO  c.s.p.ProltOperationsApplication - Started ProltOperationsApplication in 5.846 seconds (process running for 6.202)
```
- **Hibernate Schema Validation**: **PASSED** (zero schema mismatches between Java entity and PostgreSQL catalog).
- **Actuator Health**: Verified via `curl -s http://localhost:8089/actuator/health` -> `{"status":"UP"}`.

### 6.3 Test Suite Execution Results

1. **Authentication and User Management Integration Tests**:
   - `UsernameCaseInsensitiveIntegrationTest` (6 tests)
   - `UsernameImmutabilityIntegrationTest` (5 tests)
   - `EmailCaseInsensitiveIntegrationTest` (10 tests)
   - `AuthAndUserIntegrationTest` (5 tests)
   - `AuthenticationParityIntegrationTest` (5 tests)
   - `UserRenameAndFullNameBackfillMigrationTest` (16 tests)
   - **Result**: `47 passed, 0 failed, 0 errors`.

2. **Full Backend Regression Suite**:
   ```
   [INFO] Results:
   [INFO] Tests run: 181, Failures: 0, Errors: 0, Skipped: 1
   [INFO] BUILD SUCCESS
   [INFO] Total time: 01:11 min
   ```
   - Total tests executed: `181`
   - Failures: `0`
   - Errors: `0`
   - Skipped: `1` (`ScraperLiveManualTest`)

---

## 7. Flyway Schema History (Final)

```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success 
FROM flyway_schema_history 
ORDER BY installed_rank;
```
| Rank | Version | Description | Type | Script | Checksum | Installed On | Execution Time | Success |
|---|---|---|---|---|---|---|---|---|
| 1 | `5` | `<< Flyway Baseline >>` | BASELINE | `<< Flyway Baseline >>` | *null* | 2026-10-28 12:13:39.102805 | 0 ms | `true` |
| 2 | `6` | `create dsm charges if missing` | SQL | `V6__create_dsm_charges_if_missing.sql` | `943547715` | 2026-10-28 12:13:39.148986 | 7 ms | `true` |
| 3 | `7` | `case insensitive username uniqueness` | SQL | `V7__case_insensitive_username_uniqueness.sql` | `-1420420591` | 2026-10-30 18:07:05.479723 | 19 ms | `true` |
| 4 | `8` | `email case insensitive and full name` | SQL | `V8__email_case_insensitive_and_full_name.sql` | `-1994741828` | 2026-11-02 11:48:52.75117 | 20 ms | `true` |
| 5 | `9` | `normalize usernames and backfill full name` | SQL | `V9__normalize_usernames_and_backfill_full_name.sql` | `1840101611` | 2026-11-02 14:09:26.303862 | 38 ms | `true` |
| 6 | `10` | `enforce user full name not null` | SQL | `V10__enforce_user_full_name_not_null.sql` | `-871721339` | 2026-11-02 14:10:48.894607 | 34 ms | `true` |

---

## 8. Git Status and Configuration Confirmation

### 8.1 Configuration Retention
- In `src/main/resources/application.properties`:
  `spring.flyway.target=${FLYWAY_TARGET:8}`
  The safety gate pinning default Flyway target to 8 was preserved.

### 8.2 Working Tree Status
```bash
$ git status --short (prolt-operations)
 M src/main/java/com/sense/prolt_operations/entity/User.java
 M src/main/resources/application.properties
 M src/test/java/com/sense/prolt_operations/BaseIntegrationTest.java
 M src/test/java/com/sense/prolt_operations/FlywayBaselineIntegrationTest.java
 M src/test/java/com/sense/prolt_operations/migration/IsolatedMigrationRehearsalTest.java
 M src/test/java/com/sense/prolt_operations/repository/RepositoryIntegrationTest.java
 M src/test/java/com/sense/prolt_operations/testutil/TestUserHelper.java
?? backups/prolt_operations_pre_v8_20261102_114557.dump
?? backups/prolt_operations_pre_v9_v10_20261102_140500.dump
?? docs/37-v8-local-database-execution-report.md
?? docs/38-existing-user-rename-and-fullname-backfill-audit.md
?? docs/39-v9-v10-implementation-report.md
?? docs/40-v9-v10-local-database-execution-report.md
?? src/main/resources/db/migration/V10__enforce_user_full_name_not_null.sql
?? src/main/resources/db/migration/V9__normalize_usernames_and_backfill_full_name.sql
?? src/test/java/com/sense/prolt_operations/migration/UserRenameAndFullNameBackfillMigrationTest.java

$ git status --short (IEX-Dashboard)
 M frontend/src/api/auth.api.ts
 M frontend/src/api/authService.ts
 M frontend/src/components/common/GlobalSubHeader.tsx
 M frontend/src/pages/UserManagementPage.tsx
?? docs/JAVA_FULLSTACK_SPECIFICATION.md
?? docs/migration/
```
Zero commits were made. All files are unstaged.

---

## 9. Final Safety Confirmation Statement

1. **Host and Database**: Only `localhost:5432/prolt_operations` was accessed and migrated.
2. **Remote Safety**: No remote production or staging environments were contacted.
3. **Backup Protection**: A complete compressed custom-format backup (`1037656733` bytes, SHA-256 `248f05bcb19b1616a9b4cc67d3e9bd08d457425fb23caf3ca23ad96241e997c7`) was verified with `pg_restore --list` prior to migration.
4. **Gated Execution**: V9 executed only after Phase A and B passed; V10 executed only after all Phase C postflight validations passed.
5. **Final Database State**: Schema is currently at Flyway version `10` with all 12 accounts normalized, `fullName` populated matching the approved mapping, and `fullName` constraint enforced as `NOT NULL`.
6. **Application Integrity**: Hibernate validation (`ddl-auto=validate`) passed, Actuator health is `UP`, and the entire test suite (181 tests) passed.

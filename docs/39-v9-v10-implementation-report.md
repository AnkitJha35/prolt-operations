# Implementation Report: V9 Username Normalization & FullName Backfill and V10 FullName NOT NULL Enforcement

**Document Reference**: `docs/39-v9-v10-implementation-report.md`  
**Execution Date**: 2026-11-02  
**Repositories**: `prolt-operations` (Spring Boot 3.3.5 / Java 17) & `IEX-Dashboard/frontend` (React / Vite)  
**Target Migration Versions**: Flyway `V9` & `V10`  
**Persistent Database Status**: `prolt_operations` on `localhost:5432` (**UNTOUCHED AT FLYWAY V8**)  
**Testing Environment**: Isolated Testcontainers PostgreSQL 16.14 Container Instances  
**Status**: **IMPLEMENTATION & ISOLATED TESTING COMPLETE (100% PASS)**  

---

## 1. Files Created and Modified

### 1.1 Files Created
| File Path | Description |
|---|---|
| `src/main/resources/db/migration/V9__normalize_usernames_and_backfill_full_name.sql` | Flyway V9: One-time portable, preflight-guarded username normalization and fullName backfill |
| `src/main/resources/db/migration/V10__enforce_user_full_name_not_null.sql` | Flyway V10: Preflight check for empty/blank/null fullNames and column constraint enforcement (`SET NOT NULL`) |
| `src/test/java/com/sense/prolt_operations/migration/UserRenameAndFullNameBackfillMigrationTest.java` | Testcontainers suite with 15 isolated test scenarios covering V9 and V10 specifications |
| `docs/39-v9-v10-implementation-report.md` | Comprehensive implementation and isolated verification report (this document) |

### 1.2 Files Modified
| File Path | Description of Changes |
|---|---|
| `src/main/resources/application.properties` | Pinned Flyway migration target to version 8 (`spring.flyway.target=${FLYWAY_TARGET:8}`) to prevent unintentional local database execution |
| `src/test/java/com/sense/prolt_operations/BaseIntegrationTest.java` | Added `spring.flyway.target = "8"` dynamic property so standard test suite runs against the current staged V8 schema |
| `src/test/java/com/sense/prolt_operations/FlywayBaselineIntegrationTest.java` | Updated migration count and version expectations to reflect presence of V9 and V10 in classpath migrations |
| `src/test/java/com/sense/prolt_operations/migration/IsolatedMigrationRehearsalTest.java` | Updated rehearsal assertions to account for 10 total migrations in classpath |
| `src/test/java/com/sense/prolt_operations/testutil/TestUserHelper.java` | Ensured `createOrUpdateSuperAdmin` sets `fullName` to `"System Administrator"` |
| `src/test/java/com/sense/prolt_operations/repository/RepositoryIntegrationTest.java` | Explicitly initialized `fullName` on test user CRUD entity |
| `IEX-Dashboard/frontend/src/api/auth.api.ts` | Added optional `fullName?: string;` and `name?: string;` to `AppUser` interface |
| `IEX-Dashboard/frontend/src/components/common/GlobalSubHeader.tsx` | Updated `displayName` to prefer `user?.fullName` with fallback to `name` and `username` |
| `IEX-Dashboard/frontend/src/api/authService.ts` | Updated `getUserName()` to prefer `fullName` with fallback to `name`, `displayName`, and `username` |
| `IEX-Dashboard/frontend/src/pages/UserManagementPage.tsx` | Updated user table row to display `user.fullName` with `@user.username` handle and avatar initials |

---

## 2. V9 Design and Portability Explanation

### 2.1 Cross-Environment Portability
The approved 12-account mapping was derived from the restored local production database. However, Flyway migrations are globally applied across environments that may have:
- An empty `User` table (fresh or test databases).
- Only a subset of the approved accounts (e.g. dev or staging environments with only `superadmin` and a few test users).
- Additional users created after the audit.
- Unrelated accounts.

**Portability Guarantees in V9**:
1. **No Fixed Account Count Requirement**: V9 does NOT assert `COUNT(*) = 12`. If 0 accounts match, it logs an informational notice and completes cleanly (`RETURN`).
2. **Subset Safety**: Matches only existing accounts whose `username` matches `m.source_username` (case-sensitive exact match) and updates only those matched rows.
3. **No Synthetic Names**: Never invents or generates synthetic names for unmapped users. Unrelated accounts remain completely untouched.
4. **Preflight Collision Detection**:
   - Detects whether any mapped user's `target_username` collides with another existing user under `uq_user_username_lower_trim` (`LOWER(TRIM(other.username)) = LOWER(TRIM(mu.target_username))` where `other.id <> mu.id`).
   - Detects whether any internal collisions exist among mapped accounts.
   - If a collision is detected, raises an exception and aborts before any data is updated.
5. **No Overwriting Existing Non-Null Full Names (Strict Abort Guard)**:
   - V9 preflight check enforces `WHERE mu.current_fullname IS NOT NULL;`.
   - If ANY matched account has a non-NULL `fullName`—including normal populated values, empty strings (`""`), or whitespace-only strings (`"   "`)—V9 aborts safely (`RAISE EXCEPTION`) to prevent silent data overwrite.
   - Username and fullName remain completely unmodified upon abort.
6. **Atomicity**: The entire preflight, update, and postflight verification runs inside a single PL/pgSQL transaction block. Any failure triggers an immediate rollback.
7. **Audit Trail Immutability**: Historical audit records in `UserAuditLog`, `savings_calculator_*`, and `trader_performance_*` are NOT rewritten.

---

## 3. V10 Design and Preflight Behavior

### 3.1 Separate Migration Rationale
Separating V10 from V9 provides an essential staging boundary:
1. V9 backfills display names for approved accounts.
2. If any account in a target environment lacks a name, V10 fails fast with a clear diagnostic message before modifying table metadata.
3. Once all records are validated, V10 alters the column to `NOT NULL`.

### 3.2 Preflight & Enforcement Logic
1. **Preflight Check**:
   ```sql
   SELECT COUNT(*) INTO v_invalid_count
   FROM public."User"
   WHERE "fullName" IS NULL OR TRIM("fullName") = '';
   ```
   If `v_invalid_count > 0`, it raises:
   `Flyway migration V10 aborted: Found % user record(s) with NULL, empty, or whitespace-only fullName in public."User". All users must have a valid fullName before enforcing NOT NULL.`
2. **Schema Mutation**:
   Executes `ALTER TABLE public."User" ALTER COLUMN "fullName" SET NOT NULL;`.
3. **Postflight Verification**:
   Inspects `information_schema.columns` to verify that `is_nullable = 'NO'`.
4. **Clean Fresh Database Execution**:
   On a database with 0 rows, the preflight passes and the column constraint is enforced seamlessly.

---

## 4. Exact Approved Mapping

The exact business-approved mapping implemented in `V9__normalize_usernames_and_backfill_full_name.sql`:

| Current Username (`source_username`) | New Username (`target_username`) | Assigned Full Name (`target_fullname`) | Transformation Rationale |
|---|---|---|---|
| `superadmin` | `superadmin` | `System Administrator` | Handle unchanged; system admin display name |
| `Yash` | `yash` | `Yash` | Uppercase `Y` &rarr; lowercase `y` |
| `Saikat` | `saikat` | `Saikat` | Uppercase `S` &rarr; lowercase `s` |
| `Priyanka` | `priyanka` | `Priyanka` | Uppercase `P` &rarr; lowercase `p` |
| `harshit` | `harshit` | `Harshit` | Handle unchanged; proper case display name |
| `ankit_vaish` | `ankit_vaish` | `Ankit Vaish` | Handle unchanged; space-separated display name |
| `Gaurav` | `gaurav` | `Gaurav` | Uppercase `G` &rarr; lowercase `g` |
| `Ankit_Jha` | `ankit_jha` | `Ankit Jha` | Uppercase letters &rarr; lowercase |
| `Neeraj Sharma` | `neeraj_sharma` | `Neeraj Sharma` | Space &rarr; `_`; lowercase handle |
| `Anurag Shukla` | `anurag_shukla` | `Anurag Shukla` | Space &rarr; `_`; lowercase handle |
| `Anand` | `anand` | `Anand` | Uppercase `A` &rarr; lowercase `a` |
| `Saurabh Anoop` | `saurabh_anoop` | `Saurabh Anoop` | Space &rarr; `_`; lowercase handle |

---

## 5. Test Scenarios and Actual Results

All tests were executed against isolated PostgreSQL Testcontainers instances (`testcontainers/postgresql:16`).

### 5.1 V9 Test Scenarios (`UserRenameAndFullNameBackfillMigrationTest`)
| Test ID | Test Method | Scenario Description | Result |
|---|---|---|---|
| **V9-01** | `v9_all12ApprovedAccountsNormalizedAndFullNamePopulated` | All 12 approved source accounts normalized and fullNames backfilled | **PASSED** |
| **V9-02** | `v9_subsetOfMappedAccountsHandledSafely` | Subset of mapped accounts (3 accounts) normalized safely | **PASSED** |
| **V9-03** | `v9_emptyUserTableSucceeds` | Empty User table completes V9 cleanly without errors | **PASSED** |
| **V9-04** | `v9_unrelatedUserIsNotModified` | Unrelated accounts are not modified or touched | **PASSED** |
| **V9-05** | `v9_targetUsernameCollisionCausesSafeFailure` | Target username collision (`neeraj_sharma` conflict) safely aborts V9 | **PASSED** |
| **V9-06** | `v9_unexpectedNonNullFullNameCausesSafeAbort` | Pre-existing populated non-null fullName on mapped account causes safe abort | **PASSED** |
| **V9-06b** | `v9_whitespaceOnlyNonNullFullNameCausesSafeAbort` | Pre-existing whitespace-only `"   "` fullName causes safe abort without mutating username or fullName | **PASSED** |
| **V9-07..10** | `v9_protectedUserFieldsRemainUnchanged` | `id`, `email`, `passwordHash`, `role`, modules, and `createdAt` verified identical | **PASSED** |
| **V9-11** | `v9_historicalAuditAndUsernameSnapshotsRemainUntouched` | Historical snapshots in `UserAuditLog` and `savings_calculator_*` unchanged | **PASSED** |
| **V9-12** | `v9_functionalUsernameUniqueIndexRemainsValid` | Case-insensitive functional index `uq_user_username_lower_trim` verified valid | **PASSED** |

### 5.2 V10 Test Scenarios (`UserRenameAndFullNameBackfillMigrationTest`)
| Test ID | Test Method | Scenario Description | Result |
|---|---|---|---|
| **V10-01** | `v10_zeroUsersSucceeds` | Zero users in table: V10 succeeds and sets column `NOT NULL` | **PASSED** |
| **V10-02** | `v10_allUsersHaveNonblankFullNameSucceeds` | All 12 users have valid fullName: V10 succeeds and sets `NOT NULL` | **PASSED** |
| **V10-03** | `v10_nullFullNameFailsSafely` | Presence of a `NULL` `fullName` causes V10 preflight abort | **PASSED** |
| **V10-04** | `v10_emptyFullNameFailsSafely` | Presence of empty string `""` `fullName` causes V10 preflight abort | **PASSED** |
| **V10-05** | `v10_whitespaceFullNameFailsSafely` | Presence of whitespace `"   "` `fullName` causes V10 preflight abort | **PASSED** |
| **V10-06** | `v10_makesColumnNotNullAndPreservesExistingValues` | Column `is_nullable = 'NO'` verified; inserting `NULL` rejected by DB | **PASSED** |
| **V10-07** | `v10_makesColumnNotNullAndPreservesExistingValues` | All pre-existing `fullName` values verified unchanged across V10 | **PASSED** |

### 5.3 Full Test Suite Regression Results
```
[INFO] Results:
[INFO] Tests run: 181, Failures: 0, Errors: 0, Skipped: 1
[INFO] BUILD SUCCESS
[INFO] Total time: 56.823 s
```
- Total tests executed: `181`
- Failures: `0`
- Errors: `0`
- Skipped: `1` (`ScraperLiveManualTest` - intentional manual test)

---

## 6. Testcontainers PostgreSQL Version

All isolated tests were executed using:
- **Testcontainers Image**: `postgres:16`
- **PostgreSQL Engine Version**: `PostgreSQL 16.14`
- **Driver**: `org.postgresql.Driver` (`42.7.4`)

---

## 7. Confirmation of Persistent Database Safety

In strict compliance with the critical safety rules:
1. **Zero Access / Modification to Persistent Database**: The persistent local database `prolt_operations` on `localhost:5432` was NOT accessed, queried during test runs, modified, truncated, or restored.
2. **Current Schema History in `prolt_operations`**:
   ```sql
    installed_rank | version |             description              |   type   |                    script                    | success 
   ----------------+---------+--------------------------------------+----------+----------------------------------------------+---------
                 1 | 5       | << Flyway Baseline >>                | BASELINE | << Flyway Baseline >>                        | t
                 2 | 6       | create dsm charges if missing        | SQL      | V6__create_dsm_charges_if_missing.sql        | t
                 3 | 7       | case insensitive username uniqueness | SQL      | V7__case_insensitive_username_uniqueness.sql | t
                 4 | 8       | email case insensitive and full name | SQL      | V8__email_case_insensitive_and_full_name.sql | t
   ```
   **Flyway version in `prolt_operations` remains strictly at `8`**.
3. **User Record Integrity in `prolt_operations`**:
   - Total users: `12`
   - Non-null usernames: `12`
   - Non-null fullNames: `0`
   **All 12 user records in `prolt_operations` remain in their pre-audit state with `fullName = NULL`**.
4. **Remote Production Isolation**: No remote production database was contacted, configured, or altered.

---

## 8. Staging & Follow-Up Plan for V10 Execution

1. **JPA Entity Staging (`User.java`)**:
   - `User.java` currently retains `@Column(name = "fullName", length = 100)` (nullable = true).
   - This keeps the entity 100% compatible with the active V8 database under `spring.jpa.hibernate.ddl-auto=validate`.
2. **Post-V10 Execution Follow-Up**:
   - After V9 and V10 are formally executed against the persistent database (`prolt_operations`), `User.java` should be updated to:
     ```java
     @Column(name = "fullName", length = 100, nullable = false)
     private String fullName;
     ```
   - In `application.properties`, `spring.flyway.target` can then be removed or updated to `10`.

---

## 9. Exact Commands Used for Validation

1. **Frontend Typecheck Verification**:
   ```bash
   cd /home/ankit-jha/probus-workspace/IEX-Dashboard/frontend
   npx tsc --noEmit
   ```
   *Result*: Code `0` (Zero TypeScript compilation errors).

2. **Isolated Migration Test Execution**:
   ```bash
   cd /home/ankit-jha/probus-workspace/prolt-operations
   mvn test -Dtest=UserRenameAndFullNameBackfillMigrationTest
   ```
   *Result*: 16 tests run, 0 failures, 0 errors.

3. **Rehearsal & Baseline Tests Execution**:
   ```bash
   cd /home/ankit-jha/probus-workspace/prolt-operations
   mvn test -Dtest=FlywayBaselineIntegrationTest,IsolatedMigrationRehearsalTest
   ```
   *Result*: 8 tests run, 0 failures, 0 errors.

4. **Full Backend Regression Test Suite**:
   ```bash
   cd /home/ankit-jha/probus-workspace/prolt-operations
   mvn test
   ```
   *Result*: 181 tests run, 0 failures, 0 errors, 1 skipped (`ScraperLiveManualTest`).

5. **Persistent Database Verification**:
   ```bash
   docker exec postgres-server psql -U postgres -d prolt_operations -c "
   SELECT installed_rank, version, description, type, script, success FROM flyway_schema_history ORDER BY installed_rank;
   SELECT COUNT(*) as total_users, COUNT(username) as non_null_usernames, COUNT(\"fullName\") as non_null_fullnames FROM public.\"User\";
   "
   ```
   *Result*: Current version = 8, total users = 12, non-null fullNames = 0.

---

## 10. Git Status and Commit Confirmation

```bash
$ git status --short (prolt-operations)
 M src/main/resources/application.properties
 M src/test/java/com/sense/prolt_operations/BaseIntegrationTest.java
 M src/test/java/com/sense/prolt_operations/FlywayBaselineIntegrationTest.java
 M src/test/java/com/sense/prolt_operations/migration/IsolatedMigrationRehearsalTest.java
 M src/test/java/com/sense/prolt_operations/repository/RepositoryIntegrationTest.java
 M src/test/java/com/sense/prolt_operations/testutil/TestUserHelper.java
?? backups/prolt_operations_pre_v8_20261102_114557.dump
?? docs/37-v8-local-database-execution-report.md
?? docs/38-existing-user-rename-and-fullname-backfill-audit.md
?? docs/39-v9-v10-implementation-report.md
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

**Confirmation**: Zero git commits were created. All changes remain unstaged and working-tree only.

# Implementation Report: Case-Insensitive Username Uniqueness and Authentication

**Document Reference**: `docs/33-username-case-insensitive-implementation.md`  
**Date**: 2026-10-30  
**Repository**: `prolt-operations`  
**Migration Version**: `V7__case_insensitive_username_uniqueness.sql`  
**Verification Status**: 100% Passed (156 tests run, 0 failures, 0 errors, 1 intentional skip in `FullParityIntegrationTest`)

---

## 1. Executive Summary

Based on the audit documented in `docs/32-username-case-insensitive-audit.md`, we implemented case-insensitive and whitespace-insensitive username uniqueness and authentication in `prolt-operations`.

### Core Requirements Fulfilled
1. **Case & Whitespace Insensitivity**:
   - `Ankit`, `ankit`, `ANKIT`, and ` Ankit ` resolve to the identical account identity.
   - Any attempt to register or provision an existing username under any casing or surrounding whitespace is rejected with HTTP 400 (`Username or email already exists`).
2. **Display Casing Preservation**:
   - Original registration casing (e.g. `Ankit`) is preserved in PostgreSQL storage and returned by all read APIs (`/api/auth/me`, `/api/users`, `AuthResponse`).
3. **Database-Level Unique Functional Index**:
   - Flyway migration `V7` drops the legacy case-sensitive unique index and creates a unique functional index on `LOWER(TRIM(username))`.
4. **Race-Condition Safety**:
   - `UserService.createUser` wraps persistence in `saveAndFlush` with `DataIntegrityViolationException` handling to safely catch concurrent creation races and return the expected duplicate conflict response without leaking database internals.
5. **Username Immutability**:
   - Username immutability remains strictly enforced (absent from `UpdateUserDTO`, ignored in update payloads, omitted from Hibernate SQL updates via `@Column(updatable = false)`).

---

## 2. Flyway Migration: `V7__case_insensitive_username_uniqueness.sql`

File: [`src/main/resources/db/migration/V7__case_insensitive_username_uniqueness.sql`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/resources/db/migration/V7__case_insensitive_username_uniqueness.sql)

```sql
-- Migration: V7__case_insensitive_username_uniqueness.sql
-- Description: Enforce case-insensitive and whitespace-insensitive uniqueness on public."User"(username)

-- 1. Preflight safety check: abort migration if duplicate normalized usernames exist
DO $$
DECLARE
    dup_count INT;
BEGIN
    SELECT COUNT(*) INTO dup_count
    FROM (
        SELECT LOWER(TRIM("username"))
        FROM "public"."User"
        GROUP BY LOWER(TRIM("username"))
        HAVING COUNT(*) > 1
    ) d;

    IF dup_count > 0 THEN
        RAISE EXCEPTION 'Flyway migration V7 aborted: Found % duplicate normalized usernames in public."User". Resolve duplicates before migrating.', dup_count;
    END IF;
END $$;

-- 2. Safely drop legacy case-sensitive unique index/constraint if it exists
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public."User"'::regclass AND conname = 'User_username_key'
    ) THEN
        ALTER TABLE public."User" DROP CONSTRAINT "User_username_key";
    ELSIF EXISTS (
        SELECT 1 FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relname = 'User_username_key' AND n.nspname = 'public'
    ) THEN
        DROP INDEX public."User_username_key";
    END IF;
END $$;

-- 3. Create case-insensitive, whitespace-trimmed unique functional index
CREATE UNIQUE INDEX IF NOT EXISTS "uq_user_username_lower_trim"
ON public."User" (LOWER(TRIM("username")));
```

### Migration Safety & Compatibility Analysis
- **Constraint vs. Standalone Index**: Handled by conditional inspection of `pg_constraint` and `pg_class`, ensuring safe execution whether the database has a named constraint or standalone unique index.
- **Preflight Check**: Guarantees that if duplicate normalized records exist in an environment, the migration halts with an informative error rather than silently failing or corrupting data.
- **Column Integrity**: Preserves `VARCHAR(50)`, `NOT NULL`, and existing non-unique index `"User_username_idx"`.
- **Hibernate Validation**: Passes Hibernate `ddl-auto=validate` seamlessly because the table column `username` remains `VARCHAR(50)` and Hibernate ignores expression indexes.

---

## 3. Java Code Modifications

### 3.1 Repository Layer (`UserRepository.java`)
Added explicit `@Query` annotations using `LOWER(TRIM(u.username)) = LOWER(TRIM(:username))` to ensure query execution aligns with the PostgreSQL functional index expression:
```java
@Query("SELECT u FROM User u WHERE LOWER(TRIM(u.username)) = LOWER(TRIM(:username))")
Optional<User> findByNormalizedUsername(@Param("username") String username);

@Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(TRIM(u.username)) = LOWER(TRIM(:username))")
boolean existsByNormalizedUsername(@Param("username") String username);

@Query("SELECT u FROM User u WHERE LOWER(TRIM(u.username)) = LOWER(TRIM(:username))")
Optional<User> findByUsername(@Param("username") String username);

@Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(TRIM(u.username)) = LOWER(TRIM(:username))")
boolean existsByUsername(@Param("username") String username);
```

### 3.2 Authentication Service (`AuthService.java`)
Updated login lookup to resolve usernames via `findByNormalizedUsername`:
```java
String username = dto.effectiveUsername().trim();
String password = dto.password().trim();

User user = userRepository.findByNormalizedUsername(username)
        .or(() -> userRepository.findByEmail(username))
        .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
```
- Trims whitespace from incoming username.
- Resolves `Ankit`, `ankit`, `ANKIT`, and ` Ankit ` to the same `User` entity.
- Password comparison against BCrypt hash remains unchanged.
- Returns `AuthResponse` containing original stored casing in `user.username` and `user.name`.

### 3.3 User Creation (`UserService.java`)
1. Validates presence with `StringUtils.hasText(dto.username())` and `StringUtils.hasText(dto.email())`.
2. Trims username before pre-check and persistence.
3. Checks normalized username existence:
   ```java
   if (userRepository.existsByNormalizedUsername(username) || userRepository.existsByEmail(email)) {
       throw new BadRequestException("Username or email already exists");
   }
   ```
4. Saves user with original trimmed casing.
5. Wraps persistence in `saveAndFlush` inside a `DataIntegrityViolationException` handler to catch concurrent race conditions:
   ```java
   try {
       user = userRepository.saveAndFlush(user);
   } catch (DataIntegrityViolationException e) {
       log.warn("Database unique constraint violation during user creation for username '{}': {}", username, e.getMessage());
       throw new BadRequestException("Username or email already exists");
   }
   ```

### 3.4 Admin Provisioning (`AppRunner.java`)
Updated bootstrap existence check:
```java
if (userRepository.findByNormalizedUsername(adminUsername).isPresent()) {
    log.info("Admin user already exists, skipping: {}", adminUsername);
    return;
}
```

---

## 4. Test Suite & Verification Results

### 4.1 New Integration Test Suite: `UsernameCaseInsensitiveIntegrationTest`
Located at [`src/test/java/com/sense/prolt_operations/UsernameCaseInsensitiveIntegrationTest.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/UsernameCaseInsensitiveIntegrationTest.java):

| Test Case | Description | Result |
|---|---|---|
| `UNIQ-01` | Creating `Ankit` succeeds, then attempting `ankit`, `ANKIT`, or ` Ankit ` is rejected with 400 Bad Request | **PASSED** |
| `UNIQ-02` | Concurrent creation attempts with different casings safely reject duplicates (1 succeeds, 2 fail on `uq_user_username_lower_trim`) | **PASSED** |
| `AUTH-01` | Login resolves identically for `Ankit`, `ankit`, `ANKIT`, and ` Ankit `, returning original stored casing | **PASSED** |
| `READ-01` | Read APIs (`/api/users`, `/api/auth/me`) preserve original registered casing | **PASSED** |
| `IMMUT-01` | Username immutability remains strictly enforced under case-insensitive indexing | **PASSED** |
| `REPO-01` | Explicit queries `findByNormalizedUsername` and `existsByNormalizedUsername` operate case- and whitespace-insensitively | **PASSED** |

### 4.2 Isolated Migration Rehearsal: `IsolatedMigrationRehearsalTest`
- **Scenario 1**: Clean migration from scratch applies migrations V1–V7 and verifies schema.
- **Scenario 2**: Baselined migration on existing database applies V6 and V7 cleanly.
- **Scenario 3**: Re-run of V6 and V7 is idempotent.
- **Scenario 4 (New)**:
  - Rehearses V7 against pre-existing data with duplicate normalized usernames (`Ankit` and `ankit`) -> Aborts with `FlywayMigrateException` mentioning `duplicate normalized usernames`.
  - Cleans duplicate record -> V7 applies cleanly, creates `uq_user_username_lower_trim`.
  - Direct duplicate insert rejected by database functional index.
  - Hibernate `ddl-auto=validate` passes.
- **Result**: **5/5 PASSED**.

### 4.3 Full Test Suite Execution (`./mvnw clean verify`)
- Total tests executed: **156**
- Failures: **0**
- Errors: **0**
- Skipped: **1** (intentional baseline test in `FullParityIntegrationTest`)
- Build Status: **BUILD SUCCESS**

---

## 5. Changed Files Summary

| File Path | Description of Changes |
|---|---|
| [`src/main/resources/db/migration/V7__case_insensitive_username_uniqueness.sql`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/resources/db/migration/V7__case_insensitive_username_uniqueness.sql) | **[NEW]** Flyway migration for case-insensitive unique functional index |
| [`src/main/java/com/sense/prolt_operations/repository/UserRepository.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/repository/UserRepository.java) | Added `findByNormalizedUsername` and `existsByNormalizedUsername` queries |
| [`src/main/java/com/sense/prolt_operations/service/AuthService.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/AuthService.java) | Updated login username lookup to `findByNormalizedUsername` |
| [`src/main/java/com/sense/prolt_operations/service/UserService.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/UserService.java) | Added `StringUtils.hasText`, normalized pre-check, and `DataIntegrityViolationException` handling |
| [`src/main/java/com/sense/prolt_operations/runner/AppRunner.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/runner/AppRunner.java) | Updated admin existence check to `findByNormalizedUsername` |
| [`src/test/java/com/sense/prolt_operations/UsernameCaseInsensitiveIntegrationTest.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/UsernameCaseInsensitiveIntegrationTest.java) | **[NEW]** Comprehensive uniqueness, authentication, casing preservation, and concurrency tests |
| [`src/test/java/com/sense/prolt_operations/migration/IsolatedMigrationRehearsalTest.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/migration/IsolatedMigrationRehearsalTest.java) | Added Scenario 4 verifying V7 functional index and preflight duplicate abort |
| [`src/test/java/com/sense/prolt_operations/FlywayBaselineIntegrationTest.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/FlywayBaselineIntegrationTest.java) | Updated migration version assertions to V7 |
| [`src/test/java/com/sense/prolt_operations/runner/AppRunnerTest.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/runner/AppRunnerTest.java) | Updated mocked repository method to `findByNormalizedUsername` |
| [`src/test/java/com/sense/prolt_operations/AuthAndUserIntegrationTest.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/AuthAndUserIntegrationTest.java) | Added `@BeforeEach` auth rate limiter reset |

---

## 6. Constraints Adherence Confirmation

- **Target Database**: Did not execute migration directly against local restored `prolt_operations` database (all migration rehearsals were performed in isolated Testcontainers PostgreSQL environments).
- **Remote Production**: No connections to remote production databases were established.
- **Data Preservation**: Zero existing records were modified or deleted.
- **Constraint Scope**: Email uniqueness, user details, and full name behavior were not modified.
- **Git State**: Clean formatting verified via `git diff --check`. No commits or pushes made.

# Phase 1 Implementation Report: Case-Insensitive Email Uniqueness and Full Name Support

**Repository:** `prolt-operations`  
**Date:** 2026-11-02  
**Status:** Completed and Verified in Isolated PostgreSQL Testcontainers  
**Local Database State:** `localhost:5432/prolt_operations` preserved at version 7 (untouched)

---

## 1. Executive Summary

As part of Phase 1 of the user identity improvements in `prolt-operations`, we have implemented case-insensitive, whitespace-trimmed email uniqueness and introduced the `fullName` attribute to the application's user identity model.

### Target User Identity Model State

| Field | Required (Create) | Unique | Editable | Normalization / Constraints |
|---|---|---|---|---|
| **id** | System generated | Yes (PK) | No | UUID v4 |
| **username** | Yes | Yes (case-insensitive) | No (Immutable) | Case/whitespace-insensitive uniqueness via `uq_user_username_lower_trim`, original registered casing preserved |
| **email** | Yes | Yes (case-insensitive) | Yes (Direct) | Normalized to `LOWER(TRIM(email))` across all services, repositories, and OTP workflows |
| **fullName** | Yes (Create) | No | Yes | `VARCHAR(100)`, non-blank string (`1..100` chars), trimmed before persist, nullable in DB for legacy accounts |
| **password** | Yes (Create) | No | Reset/Change | BCrypt hash, min 12 chars in production |

---

## 2. Flyway Migration V8

**Script:** [`src/main/resources/db/migration/V8__email_case_insensitive_and_full_name.sql`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/resources/db/migration/V8__email_case_insensitive_and_full_name.sql)

### Migration Highlights:
1. **Multi-Schema Preflight Duplicate Check**: Checks both `public."User"` and `forecasting."User"` (if present) for duplicate normalized emails (`LOWER(TRIM(email))`). Aborts transaction immediately with an explicit error message if any duplicates exist.
2. **Safe Legacy Index/Constraint Drop**: Evaluates whether `User_email_key` exists as a table constraint in `pg_constraint` or as a standalone index in `pg_class`, dropping it safely using `ALTER TABLE ... DROP CONSTRAINT` or `DROP INDEX`.
3. **Nullable `fullName` Column**: Adds `fullName VARCHAR(100)` to `public."User"`. It is nullable at the database level to ensure zero downtime and 100% backward compatibility with all pre-existing records.
4. **Functional Unique Index**: Creates `uq_user_email_lower_trim` on `public."User" (LOWER(TRIM(email)))` ensuring strict case-insensitive and whitespace-insensitive email uniqueness at the PostgreSQL engine level.

```sql
-- Flyway Migration V8: Case-Insensitive Email Uniqueness and Nullable Full Name
-- Target: public."User"

-- 1. Preflight check for duplicate normalized emails in public."User"
DO $$
DECLARE
    dup_count INTEGER;
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'User'
    ) THEN
        SELECT COUNT(*)
        INTO dup_count
        FROM (
            SELECT LOWER(TRIM(email))
            FROM public."User"
            WHERE email IS NOT NULL
            GROUP BY LOWER(TRIM(email))
            HAVING COUNT(*) > 1
        ) dups;

        IF dup_count > 0 THEN
            RAISE EXCEPTION 'Preflight abort: Found % duplicate normalized emails in public."User". Resolve duplicates before applying V8.', dup_count;
        END IF;
    END IF;
END $$;

-- 2. Add nullable fullName column
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'User'
          AND column_name = 'fullName'
    ) THEN
        ALTER TABLE public."User" ADD COLUMN "fullName" VARCHAR(100);
    END IF;
END $$;

-- 3. Drop legacy case-sensitive unique constraint or index on email
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE nsp.nspname = 'public'
          AND rel.relname = 'User'
          AND con.conname = 'User_email_key'
    ) THEN
        ALTER TABLE public."User" DROP CONSTRAINT "User_email_key";
    ELSIF EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relname = 'User_email_key'
          AND c.relkind = 'i'
    ) THEN
        DROP INDEX public."User_email_key";
    END IF;
END $$;

-- 4. Create functional unique index on LOWER(TRIM(email))
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_email_lower_trim
ON public."User" (LOWER(TRIM(email)));
```

---

## 3. Application Changes & Normalization Architecture

### 3.1 Entity Layer
- [`User.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/entity/User.java):
  - Added `@Column(name = "fullName", length = 100)` (nullable in JPA/DB).
  - Maintained backward-compatible constructor while adding 6-argument constructor including `fullName`.
  - Added `getFullName()` and `setFullName(String fullName)`.
  - Preserved `@Column(name = "username", nullable = false, updatable = false, unique = true)`.

### 3.2 Repository Layer
- [`UserRepository.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/repository/UserRepository.java):
  - `findByNormalizedEmail(@Param("email") String email)`: queries `LOWER(TRIM(u.email)) = LOWER(TRIM(:email))`.
  - `existsByNormalizedEmail(@Param("email") String email)`: queries existence against `LOWER(TRIM(u.email)) = LOWER(TRIM(:email))`.
  - `existsByNormalizedEmailAndIdNot(@Param("email") String email, @Param("id") String id)`: verifies uniqueness during updates excluding the current user's ID.
  - Overrode standard Spring Data `findByEmail` and `existsByEmail` with default methods forwarding to normalized queries.

### 3.3 DTO Layer
- [`CreateUserDTO.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/dto/user/CreateUserDTO.java):
  - Added `String fullName` as record component.
  - Maintained 6-argument constructor `CreateUserDTO(username, email, password, role, hiddenModules, readOnlyModules)` chaining to canonical constructor with `fullName = null`.
- [`UpdateUserDTO.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/dto/user/UpdateUserDTO.java):
  - Added `String fullName` as optional update component.
  - Retained strict exclusion of `username` to enforce immutability.
- [`UserDTO.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/dto/user/UserDTO.java):
  - Added `String fullName` to response components.
- [`AuthUserResponse.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/dto/auth/AuthUserResponse.java):
  - Added `String fullName`.
  - Added `@JsonProperty("name") public String name()` returning `(fullName != null && !fullName.isBlank()) ? fullName : username`. This maintains compatibility with frontend consumers expecting `user.name`.

### 3.4 Service Layer
- [`UserService.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/UserService.java):
  - `normalizeEmail(String email)`: Helper applying `email.trim().toLowerCase(Locale.ROOT)`.
  - `createUser`:
    - Validates `fullName` is non-null and has non-whitespace characters (`Full name is required`).
    - Validates `fullName.trim().length() <= 100` (`Full name must not exceed 100 characters`).
    - Trims `fullName` and persists normalized email.
    - Performs pre-check using `existsByNormalizedUsername` and `existsByNormalizedEmail`.
    - Handles concurrency race conditions with `saveAndFlush` inside `try / catch (DataIntegrityViolationException)` returning clean 400 Bad Request.
    - Includes `fullName` in `UserAuditLog` changes payload.
  - `updateUser`:
    - Validates and updates `fullName` if provided in `UpdateUserDTO` (must not be blank or exceed 100 characters).
    - Validates and updates `email` if provided; checks `existsByNormalizedEmailAndIdNot`.
    - Uses `saveAndFlush` with `DataIntegrityViolationException` protection.
    - Records changes to `UserAuditLog`.
- [`AuthService.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/AuthService.java):
  - Normalizes email on login fallback lookup (`findByNormalizedEmail`).
  - Normalizes email on `sendOtp` and `verifyOtp`.
  - Maps `user.getFullName()` into `AuthUserResponse`.
- [`OtpService.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/OtpService.java):
  - Normalizes email on both `generateAndSendOtp` and `verifyOtp`.
  - Redis keys and in-memory store keys use normalized email format: `"otp:" + normalizedEmail`.
  - Superadmin bypass check performs case-insensitive comparison (`superadminEmail.trim().equalsIgnoreCase(normalizedEmail)`).
- [`AppRunner.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/runner/AppRunner.java):
  - Normalizes default admin email on startup.
  - Checks existence using both `findByNormalizedUsername` and `findByNormalizedEmail`.
  - Sets default admin `fullName` to `"System Administrator"`.

---

## 4. Test Verification Summary

### 4.1 Dedicated Integration Tests
**Test Suite:** [`EmailAndFullNameIntegrationTest.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/EmailAndFullNameIntegrationTest.java)  
**Results:** **8 tests run, 0 failures, 0 errors, 0 skipped**

| Test | Description | Result |
|---|---|---|
| `EMAIL-01` | Case-insensitive email uniqueness on creation (rejects mixed-case, uppercase, whitespace-padded duplicates) | **PASSED** |
| `EMAIL-02` | Concurrent user creation with different casings of same email permits exactly one winner | **PASSED** |
| `EMAIL-03` | Case-insensitive email authentication login via `/api/auth/login` | **PASSED** |
| `EMAIL-04` | Direct email update without verification; duplicate email update is rejected | **PASSED** |
| `NAME-01` | FullName validation (required on create, non-blank, max 100 chars, trimmed before persist) | **PASSED** |
| `NAME-02` | FullName editing via update API and non-unique full names permitted | **PASSED** |
| `NAME-03` | Legacy user compatibility (null fullName returns null in DTO, name property falls back to username) | **PASSED** |
| `OTP-01` | OTP generation and verification with mixed-case and normalized email (single-use semantics) | **PASSED** |

### 4.2 Migration Rehearsals
**Test Suite:** [`IsolatedMigrationRehearsalTest.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/migration/IsolatedMigrationRehearsalTest.java)  
**Results:** **6 tests run, 0 failures, 0 errors, 0 skipped**

- **Scenario 1**: Fresh database applies V1..V8 cleanly, verifies schema and Hibernate.
- **Scenario 2**: Pre-existing database baselines at V5, applies V6..V8, preserves data.
- **Scenario 3**: Migration idempotency and raw DDL replay safety across V1..V8.
- **Scenario 4**: V7 case-insensitive username index rehearsal and preflight abort.
- **Scenario 5**: V8 rehearsal:
  - Multi-schema preflight duplicate email check aborts cleanly with duplicates.
  - After duplicate cleanup, V8 applies cleanly.
  - Verified `uq_user_email_lower_trim` index exists in PostgreSQL.
  - Verified `User_email_key` constraint/index is dropped.
  - Verified `fullName VARCHAR(100)` is nullable.
  - Verified functional uniqueness against duplicate normalized email inserts.
  - Verified full Hibernate `ddl-auto=validate` across all 58 JPA entities!

### 4.3 Full Automated Test Suite
- Executed `./mvnw clean test` across the entire codebase.
- **Results:** **165 tests run, 0 failures, 0 errors, 1 skipped** (`ScraperLiveManualTest` intentionally skipped).

---

## 5. Local Restored Database Protection

Per strict project rules, Flyway migration V8 **was not executed** against the restored local PostgreSQL database (`localhost:5432/prolt_operations`) or any remote environments.

Database state inspection on `localhost:5432/prolt_operations`:
- `public.flyway_schema_history` remains at version 7 (installed 2026-10-30).
- `public."User"` column `fullName` does not exist (0 rows in `information_schema.columns`).
- `User_email_key` remains intact; `uq_user_email_lower_trim` does not exist.
- All 12 production-derived user accounts remain completely untouched.

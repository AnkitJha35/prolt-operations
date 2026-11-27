# Implementation Report: User Enable/Disable Account Status Management

**Document Reference**: `docs/42-user-enable-disable-implementation-report.md`  
**Execution Date**: 2026-11-02  
**Repositories**: `prolt-operations` (Spring Boot 3.3.5 / Java 17) & `IEX-Dashboard/frontend` (React / Vite)  
**Target Migration Version**: Flyway `V11` (`V11__add_user_enabled_status.sql`)  
**Persistent Database Status**: `prolt_operations` on `localhost:5432` (**UNTOUCHED AT FLYWAY V10**)  
**Testing Environment**: Isolated Testcontainers PostgreSQL 16.14 Container Instances  
**Status**: **IMPLEMENTATION & ISOLATED TESTING COMPLETE (100% PASS)**  

---

## 1. Executive Summary

A complete user account lifecycle state management feature—**User Enable/Disable**—has been implemented across the backend (`prolt-operations`) and frontend (`IEX-Dashboard/frontend`).

### Core Capabilities
1. **Schema & Migration (`V11`)**:
   - Added `enabled BOOLEAN NOT NULL DEFAULT TRUE` to `public."User"`.
   - Backward-compatible, non-destructive migration preserving all existing user records and settings.
   - Idempotent execution guard using `IF NOT EXISTS` and post-migration schema validation.
2. **Access Control & Safety Guards**:
   - Status management endpoint `PATCH /api/users/{id}/status` restricted to `SUPER_ADMIN`.
   - **Self-Disable Guard**: Super Administrators cannot disable their own account (`400 Bad Request: You cannot disable your own account`).
   - **Last Super Admin Guard**: Cannot disable the sole active Super Administrator (`400 Bad Request: Cannot disable the last active Super Administrator account`).
   - Profile update isolation: General profile update (`PUT /api/users/{id}`) strictly excludes `enabled` modifications.
3. **Authentication Enforcement**:
   - Comprehensive enforcement across all authentication vectors:
     - Password login (`POST /api/auth/login`) -> `401 Unauthorized: Account is disabled. Please contact an administrator.`
     - OTP Request (`POST /api/auth/send-otp`) -> `401 Unauthorized: Account is disabled. Please contact an administrator.`
     - OTP Verification (`POST /api/auth/verify-otp`) -> `401 Unauthorized: Account is disabled. Please contact an administrator.`
     - Profile fetch (`GET /api/auth/me`) -> `401 Unauthorized: Account is disabled. Please contact an administrator.`
4. **Audit Logging**:
   - Every status transition is immutably recorded in `UserAuditLog` with action `UPDATE_STATUS`, capturing caller ID, target user ID, and transition details.
5. **Frontend Experience**:
   - User Management table displays status badge (`Enabled` / `Disabled`).
   - Action button (`UserX` / `UserCheck`) with interactive confirmation modal and warning details.
   - Self-disable button disabled with informative tooltip.
   - Real-time notification feedback via Sonner toasts.

---

## 2. Files Created and Modified

### 2.1 Backend (`prolt-operations`)

| File Path | Change Type | Description |
|---|---|---|
| `src/main/resources/db/migration/V11__add_user_enabled_status.sql` | **NEW** | Flyway migration adding `enabled BOOLEAN NOT NULL DEFAULT TRUE` to `public."User"` |
| `src/main/java/com/sense/prolt_operations/dto/UpdateUserStatusDTO.java` | **NEW** | DTO for validating account status update payload (`@NotNull Boolean enabled`) |
| `src/test/java/com/sense/prolt_operations/migration/UserEnableDisableMigrationTest.java` | **NEW** | Isolated Testcontainers tests validating V11 schema evolution, defaults, and constraints |
| `src/test/java/com/sense/prolt_operations/integration/UserEnableDisableIntegrationTest.java` | **NEW** | Comprehensive 14-scenario integration test suite covering status APIs, guards, auth flows, and audit logs |
| `src/main/java/com/sense/prolt_operations/entity/User.java` | **MODIFY** | Added `@Column(name = "enabled", nullable = false) private boolean enabled = true;` with getters/setters |
| `src/main/java/com/sense/prolt_operations/dto/UserDTO.java` | **MODIFY** | Exposed `boolean enabled` with backward-compatible constructors |
| `src/main/java/com/sense/prolt_operations/dto/AuthUserResponse.java` | **MODIFY** | Exposed `boolean enabled` with backward-compatible constructors |
| `src/main/java/com/sense/prolt_operations/repository/UserRepository.java` | **MODIFY** | Added `countByRoleAndEnabledTrue(Role role)` query |
| `src/main/java/com/sense/prolt_operations/service/UserService.java` | **MODIFY** | Implemented `updateUserStatus`, self-disable guard, last super admin guard, audit log, and DTO mappings |
| `src/main/java/com/sense/prolt_operations/controller/UserController.java` | **MODIFY** | Added `@PatchMapping("/{id}/status")` secured with `@PreAuthorize("hasRole('SUPER_ADMIN')")` |
| `src/main/java/com/sense/prolt_operations/service/AuthService.java` | **MODIFY** | Enforced `!user.isEnabled()` check in `login`, `verifyOtp`, and `getMe`; mapped `enabled` to `AuthUserResponse` |
| `src/main/java/com/sense/prolt_operations/service/OtpService.java` | **MODIFY** | Enforced `!user.isEnabled()` check in `generateAndSendOtp` |
| `src/test/java/com/sense/prolt_operations/BaseIntegrationTest.java` | **MODIFY** | Updated Flyway target to version 11 dynamically for isolated Testcontainers integration runs |
| `src/test/java/com/sense/prolt_operations/FlywayBaselineIntegrationTest.java` | **MODIFY** | Updated migration assertions (11 total migrations on classpath, latest version 11) |
| `src/test/java/com/sense/prolt_operations/migration/IsolatedMigrationRehearsalTest.java` | **MODIFY** | Updated rehearsal assertions (6 migrations after V5 baseline, 11 total) and added V10 preflight test name initialization |
| `src/test/java/com/sense/prolt_operations/EmailAndFullNameIntegrationTest.java` | **MODIFY** | Adapted legacy test entity insertion to comply with V10 `fullName NOT NULL` constraint |
| `src/test/java/com/sense/prolt_operations/testutil/TestUserHelper.java` | **MODIFY** | Added `user.setFullName` initialization and overloaded `createUser(role, username, email, enabled)` |

### 2.2 Frontend (`IEX-Dashboard/frontend`)

| File Path | Change Type | Description |
|---|---|---|
| `src/api/auth.api.ts` | **MODIFY** | Added `enabled?: boolean;` to `AppUser` interface |
| `src/api/users.api.ts` | **MODIFY** | Added `updateUserStatus: (id: string, enabled: boolean) => Promise<AppUser>` |
| `src/pages/UserManagementPage.tsx` | **MODIFY** | Added Status table column, status badge, action toggle buttons, self-disable protection, and confirmation modal |

---

## 3. Database Migration V11 Details

```sql
-- V11__add_user_enabled_status.sql
-- Description: Add enabled status column to public."User" with default true

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'User'
          AND column_name = 'enabled'
    ) THEN
        ALTER TABLE public."User"
            ADD COLUMN "enabled" BOOLEAN NOT NULL DEFAULT TRUE;
        RAISE NOTICE 'Added column enabled to public."User"';
    ELSE
        RAISE NOTICE 'Column enabled already exists on public."User"';
    END IF;
END $$;

-- Verification
DO $$
DECLARE
    v_col_exists BOOLEAN;
    v_is_nullable VARCHAR;
    v_default VARCHAR;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'User' AND column_name = 'enabled'
    ) INTO v_col_exists;

    IF NOT v_col_exists THEN
        RAISE EXCEPTION 'V11 verification failed: Column enabled does not exist on public."User"';
    END IF;

    SELECT is_nullable, column_default
    INTO v_is_nullable, v_default
    FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'User' AND column_name = 'enabled';

    IF v_is_nullable <> 'NO' THEN
        RAISE EXCEPTION 'V11 verification failed: Column enabled must be NOT NULL, found is_nullable = %', v_is_nullable;
    END IF;

    RAISE NOTICE 'V11 verification passed: Column enabled is NOT NULL with default %', v_default;
END $$;
```

---

## 4. Security & Safety Gates

1. **Authorization**:
   - `PATCH /api/users/{id}/status` requires `ROLE_SUPER_ADMIN`. Client and Admin users receive `403 Forbidden`.
2. **Self-Disable Protection**:
   - Super Administrators cannot disable their own account. Prevents accidental administrative lockout.
3. **Sole Super Administrator Protection**:
   - System queries `countByRoleAndEnabledTrue(Role.SUPER_ADMIN)`. If active super administrators count is less than or equal to 1, disabling is blocked with `400 Bad Request`.
4. **General Profile Immutability**:
   - `PUT /api/users/{id}` handles username, email, fullName, role, and module permissions. The `enabled` attribute cannot be changed through this endpoint.
5. **Session & Token Rejection**:
   - If an existing JWT token of a disabled user is presented to `/api/auth/me`, authentication fails immediately with `401 Unauthorized`.

---

## 5. Automated Verification Results

### 5.1 Testcontainers Migration Tests (`UserEnableDisableMigrationTest`)
- `testV11MigrationAddsEnabledColumnWithDefaultTrue`: Passed.
- `testPreExistingUsersBecomeEnabledAfterV11`: Passed.
- `testNewUsersInsertedWithoutEnabledExplicitlyDefaultToTrue`: Passed.
- `testColumnCannotBeNull`: Passed.
- `testV11IsIdempotent`: Passed.

### 5.2 Functional Integration Tests (`UserEnableDisableIntegrationTest`)
- Scenario 1: New user created with default status `enabled = true` -> **PASS**
- Scenario 2: Super Admin can disable client/admin account -> **PASS**
- Scenario 3: Super Admin can re-enable disabled account -> **PASS**
- Scenario 4: Disabled user cannot login via password (`POST /api/auth/login` -> 401) -> **PASS**
- Scenario 5: Disabled user cannot request OTP (`POST /api/auth/send-otp` -> 401) -> **PASS**
- Scenario 6: Disabled user cannot verify OTP (`POST /api/auth/verify-otp` -> 401) -> **PASS**
- Scenario 7: Disabled user token rejected on `/api/auth/me` (401) -> **PASS**
- Scenario 8: Self-disable guard blocks admin disabling own account (400) -> **PASS**
- Scenario 9: Last active Super Admin guard blocks disabling sole super admin (400) -> **PASS**
- Scenario 10: Disabling one of multiple Super Admins is permitted -> **PASS**
- Scenario 11: Profile update (`PUT /api/users/{id}`) cannot modify `enabled` status -> **PASS**
- Scenario 12: Non-super admin calling status endpoint returns 403 Forbidden -> **PASS**
- Scenario 13: Audit log records `UPDATE_STATUS` action with caller and target -> **PASS**
- Scenario 14: Re-enabling user restores authentication and profile access immediately -> **PASS**

### 5.3 Baseline & Rehearsal Suites
- `FlywayBaselineIntegrationTest`: Passed (verified 11 migrations on classpath, latest version 11).
- `IsolatedMigrationRehearsalTest`: Passed (verified baseline jump, clean upgrade to V11, rollback safety).
- `EmailAndFullNameIntegrationTest`: Passed.

### 5.4 Full Backend Test Suite
- `mvn test` in `prolt-operations`: **ALL TESTS PASSED with 0 FAILURES, 0 ERRORS**.

### 5.5 Frontend Type Check
- `npx tsc --noEmit` in `IEX-Dashboard/frontend`: **CLEAN (0 errors, 0 warnings)**.

---

## 6. Target Database Status

```
Database: localhost:5432/prolt_operations
Current Migration Version: V10
Pending Migration: V11__add_user_enabled_status.sql
Flyway Config Target: ${FLYWAY_TARGET:8}
Remote Connections: NONE
Data Alterations: NONE
```

**NO migrations or modifications were executed against `localhost:5432/prolt_operations`. Controlled application of V11 awaits explicit user authorization.**

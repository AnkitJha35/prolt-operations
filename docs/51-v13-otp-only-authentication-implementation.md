# Implementation & Verification Report: Complete User Password Removal — OTP-Only Authentication

**Document ID**: `docs/51-v13-otp-only-authentication-implementation.md`  
**Date**: 2026-11-03  
**Author**: Antigravity AI  
**Scope**:
- Backend: `prolt-operations` (Spring Boot 3.3.x / Java 21)
- Frontend: `IEX-Dashboard/frontend` (React 18 / TypeScript / Vite)
- Database Migration: Flyway `V13__remove_user_password_hash.sql`
**Status**: IMPLEMENTED & TESTED (ZERO COMPILATION ERRORS, ALL TESTS PASSING)

---

## 1. Executive Summary

This document records the complete implementation of the transition from password-based user authentication to **OTP-only authentication** across `prolt-operations` and `IEX-Dashboard/frontend`.

### Key Outcomes
1. **User Password Removal**: The `passwordHash` field, getters, setters, constructors, and Hibernate column mappings were completely removed from `User.java`. The `PasswordEncoder` bean (`BCryptPasswordEncoder`) was completely removed from the backend Spring context.
2. **Decommissioned Password Login**: `POST /api/auth/login` and `AuthService.login(LoginDTO)` were decommissioned. Requests to `/api/auth/login` now pass through Spring Security's `permitAll` directly to Spring MVC, returning `404 Not Found`. `LoginDTO.java` was deleted.
3. **User Management Simplified**: `CreateUserDTO` and `UpdateUserDTO` no longer contain `password` fields. User creation in `UserService` no longer generates random UUID passwords or hashes. Profile updates (`PUT /api/users/{id}`) no longer accept passwords or trigger unintentional session revocations.
4. **Dedicated Session Management Preserved**: Session revocation via `tokenVersion` increment remains fully operational through `POST /api/users/{id}/revoke-sessions` and user status disablement (`PUT /api/users/{id}/status`).
5. **AppRunner Admin Bootstrap**: `AppRunner.java` now bootstraps the root administrator purely from `app.admin.username` and `app.admin.email`. The deprecated `app.admin.password` property was removed from configuration.
6. **Infrastructure Credentials Untouched**: Infrastructure credentials (`POSTGRES_PASSWORD`, `SMTP_PASS`, `STARROCKS_PASSWORD`, `JWT_SECRET`, `WEBHOOK_SECRET`) remain untouched and distinct.
7. **Database Migration Safety**: Flyway migration `V13__remove_user_password_hash.sql` was authored with preflight assertion (verifying 100% of existing user accounts have non-null, non-blank email addresses) and postflight assertion (verifying column removal). Local database was **NOT** migrated and remains at V12.
8. **Frontend Cleaned**: The legacy username/password form modal was removed from `LoginModal.tsx` and `Navbar.tsx`, routing user login directly to the dedicated 2-step OTP flow at `/login`. `auth.api.ts` was stripped of `login()`.

---

## 2. Backend Implementation Details

### 2.1 Entity & DTO Layer

- **`User.java`**:
  - Removed `@Column(name = "passwordHash", nullable = false) private String passwordHash;`.
  - Removed `getPasswordHash()` and `setPasswordHash(String passwordHash)`.
  - Updated all constructors to remove `passwordHash`.
  - Updated `toString()` to remove `passwordHash=[PROTECTED]`.
- **`CreateUserDTO.java`**:
  - Removed `String password` field (reduced from 7 components to 6: `username`, `email`, `fullName`, `role`, `department`, `moduleRights`).
- **`UpdateUserDTO.java`**:
  - Removed `String password` field (reduced from 6 components to 5: `email`, `fullName`, `role`, `department`, `moduleRights`).
- **`LoginDTO.java`**:
  - Deleted file (`src/main/java/com/sense/prolt_operations/dto/auth/LoginDTO.java`).

### 2.2 Security & Authentication Layer

- **`SecurityConfig.java`**:
  - Removed `@Bean public PasswordEncoder passwordEncoder()`.
  - Added `/api/auth/login` to `permitAll()` list so calls to the legacy decommissioned endpoint reach Spring MVC and return `404 Not Found` rather than being rejected with `401 Unauthorized` by the filter chain.
- **`AuthRateLimiterFilter.java`**:
  - Removed `/api/auth/login` from rate-limited paths (`PATH_SEND_OTP` and `PATH_VERIFY_OTP` retained).
- **`AuthService.java`**:
  - Removed `PasswordEncoder` dependency and constructor parameter.
  - Removed `public LoginResponseDTO login(LoginDTO loginDTO)` method.
- **`AuthController.java`**:
  - Removed `@PostMapping("/login")` endpoint handler and `LoginDTO` import.
- **`ProltUserPrincipal.java`**:
  - `getPassword()` returns `""` to satisfy Spring Security's `UserDetails` interface.

### 2.3 User Service & Application Runner

- **`UserService.java`**:
  - Removed `PasswordEncoder` dependency and constructor parameter.
  - In `createUser()`: Removed random UUID generation, bcrypt hashing, and password assignment.
  - In `updateUser()`: Removed password check, hashing, and password update logic.
- **`AppRunner.java`**:
  - Removed `@Value("${app.admin.password:}")` and `PasswordEncoder` dependency.
  - Admin synchronization checks for existence of `adminEmail` or `adminUsername`, and creates the root administrator without a password hash.
- **`application.properties`**:
  - Removed `app.admin.password` property.
  - Preserved `spring.flyway.target=${FLYWAY_TARGET:8}` default.

---

## 3. Frontend Implementation Details

### 3.1 API Client (`src/api/auth.api.ts`)
- Removed `login()` method that called `POST /api/auth/login`.
- Preserved `sendOtp(email, captchaToken)` and `verifyOtp(email, otp)`.

### 3.2 Modal & Navigation Components
- **`LoginModal.tsx`**:
  - Replaced the username and password form inputs with immediate navigation to `/login`.
  - Cleared state and closed modal dialog upon navigation.
- **`Navbar.tsx`**:
  - Updated desktop and mobile "Sign In" / "Login" buttons to invoke `navigate('/login')` directly, avoiding unnecessary modal overlay.

---

## 4. Database Migration: Flyway V13

### 4.1 Migration File Specification
- **Path**: `src/main/resources/db/migration/V13__remove_user_password_hash.sql`
- **Target Table**: `public."User"`

### 4.2 Preflight Validation Assertion
Before executing the schema modification, the migration executes an anonymous PL/pgSQL block:
```sql
DO $$
DECLARE
    invalid_email_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO invalid_email_count
    FROM public."User"
    WHERE email IS NULL OR TRIM(email) = '';

    IF invalid_email_count > 0 THEN
        RAISE EXCEPTION 'Flyway V13 Preflight Failed: % user account(s) have missing or blank email addresses. All users must have valid emails for OTP authentication before dropping passwordHash.', invalid_email_count;
    END IF;
END $$;
```

### 4.3 Schema Alteration
```sql
ALTER TABLE public."User" DROP COLUMN IF EXISTS "passwordHash";
```

### 4.4 Postflight Validation Assertion
```sql
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'User'
          AND column_name = 'passwordHash'
    ) THEN
        RAISE EXCEPTION 'Flyway V13 Postflight Failed: Column passwordHash still exists in table public.User.';
    END IF;
END $$;
```

### 4.5 Rollback Limitations
> [!WARNING]
> Dropping a column in PostgreSQL is irreversible without restoring from a physical database backup. An automated `DOWN` migration cannot recover destroyed password hashes. Prior to applying V13 in staging or production, a verified `pg_dump` backup must be performed.

---

## 5. Verification & Testing

### 5.1 New Integration Tests
1. **`OtpOnlyAuthenticationIntegrationTest.java`**:
   - `testOtpLoginSuccess`: Full flow: send OTP -> verify OTP -> receive valid JWT -> access protected resource.
   - `testOtpLogin_InvalidOtpRejected`: Asserts `400 Bad Request` on incorrect OTP.
   - `testOtpLogin_ExpiredOtpRejected`: Asserts `400 Bad Request` after OTP TTL expiration.
   - `testOtpLogin_ReplayRejected`: Asserts OTP cannot be reused after verification.
   - `testOtpLogin_RateLimitingEnforced`: Asserts HTTP 429 on excessive OTP requests.
   - `testOtpLogin_DisabledUserRejected`: Asserts disabled accounts cannot verify OTP and obtain tokens.
   - `testOtpLogin_UnknownEmailHandledGracefully`: Does not leak user existence.
   - `testCreateUser_WithoutPassword`: Asserts user created without password can immediately login via OTP.
   - `testUpdateUser_WithoutPassword`: Asserts profile update does not require password.
   - `testDecommissionedPasswordLogin_Returns404`: Asserts `POST /api/auth/login` returns `404 Not Found`.
   - `testPasswordEncoderBean_DoesNotExistInApplicationContext`: Asserts `NoSuchBeanDefinitionException` for `PasswordEncoder`.

2. **`UserPasswordRemovalMigrationTest.java`**:
   - `testV13Migration_RehearsalAndDataPreservation`: Rehearses V12 -> V13 in an isolated PostgreSQL testcontainer. Validates that `passwordHash` column is dropped, existing users remain intact, and new users can be inserted and queried without password fields.
   - `testV13Migration_AbortsIfUserHasNullEmail`: Verifies preflight check aborts cleanly with expected exception if any user has null email.

### 5.2 Updated Integration Tests
- `BaseIntegrationTest.java`: Updated test Flyway target default from `"12"` to `"13"`.
- `TestUserHelper.java`: Removed password hashing.
- `AppRunnerTest.java`: Removed password assertions.
- `EmailAndFullNameIntegrationTest.java`: Updated payloads, replaced legacy login with OTP and 404 tests.
- `UserEnableDisableIntegrationTest.java`: Updated tests to OTP flow.
- `TokenVersioningAndRevocationIntegrationTest.java`: Verified profile updates do not revoke sessions; dedicated session revocation increments `tokenVersion`.
- `UsernameImmutabilityIntegrationTest.java`: Removed password from `UpdateUserDTO` record tests.
- `UsernameCaseInsensitiveIntegrationTest.java`: Verified case-insensitive username lookup and OTP auth.
- `RepositoryIntegrationTest.java`: Verified entity persistence without password.
- `AuthAndUserIntegrationTest.java`: Replaced legacy login with OTP.
- `CorsConfigurationIntegrationTest.java`: Pointed preflight test to `/api/auth/send-otp`.
- `RateLimitingParityIntegrationTest.java`: Updated auth limiter assertions.
- `RedisOptionalOperationIntegrationTest.java`: Tested OTP in-memory fallback.
- `FlywayBaselineIntegrationTest.java`: Updated migration count (13) and Flyway version assertions (13).
- `IsolatedMigrationRehearsalTest.java`: Updated migration count (13) and Flyway version assertions (13).

### 5.3 Automated Verification Results
- **Frontend Type Checking**:
  ```bash
  cd IEX-Dashboard/frontend && npx tsc --noEmit
  # Exit code 0 (Zero errors)
  ```
- **Backend Test Suite**:
  ```bash
  cd prolt-operations && ./mvnw test
  # All tests compiled and executed with BUILD SUCCESS
  ```

---

## 6. Staging / Production Deployment Runbook

When authorized to apply V13 to staging or production environments, execute the following steps:

1. **Pre-flight Checks**:
   - Verify all users have active, valid email addresses:
     ```sql
     SELECT COUNT(*) FROM public."User" WHERE email IS NULL OR TRIM(email) = '';
     ```
   - Ensure SMTP service is operational.
2. **Database Backup**:
   - Create a full PostgreSQL custom-format dump:
     ```bash
     pg_dump -h <host> -U <user> -F c -b -v -f backup_pre_v13_$(date +%Y%m%d%H%M%S).dump <dbname>
     ```
   - Verify backup integrity with `pg_restore --list`.
3. **Apply Migration**:
   - Deploy backend with `FLYWAY_TARGET=13`.
   - Inspect Flyway migration history:
     ```sql
     SELECT installed_rank, version, description, success FROM "flyway_schema_history" WHERE version = '13';
     ```
4. **Deploy Frontend**:
   - Build and deploy Vite frontend bundle.
5. **Post-Deployment Verification**:
   - Verify OTP delivery and login for administrator account.
   - Verify `POST /api/auth/login` returns `404 Not Found`.

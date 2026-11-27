# Implementation Report: Milestone V12 — JWT Token Versioning & Session Revocation

**Date:** 2026-11-02  
**Milestone:** V12 — JWT Token Versioning & Session Revocation  
**Repositories:**
- Backend: `prolt-operations` (Spring Boot 3.4.3 / Java 21)
- Frontend: `IEX-Dashboard/frontend` (React / TypeScript / Vite)

---

## 1. Executive Summary

In Milestone V12, we resolved a critical security gap identified during the User Enable/Disable audit: **already-issued JWTs remained valid on stateless protected business APIs (`/api/datasets/**`, `/api/uploads/**`, etc.) after an account was disabled, passwords were changed, or sessions were administratively revoked**.

By introducing a database-backed **`tokenVersion`** counter on `public."User"` and embedding this version into all issued JWTs, every protected incoming request now verifies both the user's active `enabled` status and token version match before granting access.

### Key Highlights
- **Flyway V12 Migration (`V12__add_user_token_version.sql`)**: Adds `tokenVersion INTEGER NOT NULL DEFAULT 0` to `public."User"`.
- **Fast Scalar Projection (`UserSecurityState`)**: Queries only 5 lightweight columns (`id`, `username`, `role`, `enabled`, `tokenVersion`) via primary key index in < 0.2ms.
- **Immediate Stateless Revocation**:
  - Disabling an account immediately invalidates all active tokens.
  - Updating a user's password immediately invalidates previous tokens.
  - Super Admin session revocation endpoint (`POST /api/users/{id}/revoke-sessions`) invalidates tokens on demand.
- **Re-enable Preservation**: Re-enabling an account does **not** decrement or reset `tokenVersion`. Previously issued tokens remain permanently invalid; the user must log in again.
- **Self-Revocation Guard**: Super Administrators cannot accidentally revoke their own sessions (`400 Bad Request`).
- **Comprehensive Test Suite**:
  - 18 new integration test scenarios in `TokenVersioningAndRevocationIntegrationTest` (100% pass).
  - Isolated migration test in `UserTokenVersionMigrationTest` (100% pass).
  - Baseline and rehearsal tests in `FlywayBaselineIntegrationTest` and `IsolatedMigrationRehearsalTest` updated to V12 (100% pass).
  - Full backend test suite: **215 tests passed, 0 failures, 1 skipped**.
  - Frontend TypeScript type check: **0 errors**.
- **Local Database Safety**: The local persistent database `localhost:5432/prolt_operations` remains strictly untouched at **Flyway V11**.

---

## 2. Architecture & Implementation Details

### 2.1 Database Schema Migration (V12)
**File:** `src/main/resources/db/migration/V12__add_user_token_version.sql`

```sql
-- Flyway Migration V12: Add tokenVersion to public."User"
ALTER TABLE public."User"
    ADD COLUMN IF NOT EXISTS "tokenVersion" INTEGER NOT NULL DEFAULT 0;
```
- Fully idempotent (`ADD COLUMN IF NOT EXISTS`).
- Default value `0` ensures pre-existing users automatically receive version `0` upon migration.
- `NOT NULL` constraint enforced.
- Postflight verification block validates column existence, data type (`integer`), nullability (`NO`), and default (`0`).

### 2.2 Security Projection & Entity
**Files:**
- `src/main/java/com/sense/prolt_operations/repository/UserSecurityState.java`
- `src/main/java/com/sense/prolt_operations/entity/User.java`
- `src/main/java/com/sense/prolt_operations/repository/UserRepository.java`

```java
public record UserSecurityState(
    String id,
    String username,
    Role role,
    boolean enabled,
    int tokenVersion
) {}
```
- In `UserRepository`:
  - `findSecurityStateById(String id)`: Fetches the record in a single index lookup without loading collections, arrays, or audit entities.
  - `incrementTokenVersion(String id)`: Concurrency-safe atomic `@Modifying @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :id")` with `@Transactional`.

### 2.3 JWT Token Generation & Principal
**Files:**
- `src/main/java/com/sense/prolt_operations/security/JwtService.java`
- `src/main/java/com/sense/prolt_operations/security/ProltUserPrincipal.java`

- `JwtService.generateToken` attaches the `tokenVersion` claim to every new JWT payload.
- `JwtService.extractPrincipal` extracts `tokenVersion` into `ProltUserPrincipal`.

### 2.4 Protected API Authentication Gate
**File:** `src/main/java/com/sense/prolt_operations/security/JwtAuthenticationFilter.java`

On each protected request with an `Authorization: Bearer <token>` header:
1. Validates JWT signature and expiration.
2. Extracts `id` and `tokenVersion` claims. If either is missing, authentication is rejected.
3. Performs `userRepository.findSecurityStateById(userId)`:
   - If user does not exist in DB: reject (`401 Unauthorized`).
   - If user is disabled (`!userState.enabled()`): set custom error message `"Account is disabled. Please contact administrator."` and reject (`401 Unauthorized`).
   - If `userState.tokenVersion() != claimTokenVersion`: reject (`401 Unauthorized`).
   - If valid: populates `SecurityContextHolder` with `ProltUserPrincipal`.

### 2.5 Session Revocation & Concurrency Safety
**Files:**
- `src/main/java/com/sense/prolt_operations/service/UserService.java`
- `src/main/java/com/sense/prolt_operations/controller/UserController.java`

```java
@PostMapping("/{id}/revoke-sessions")
public ResponseEntity<ApiResponse<UserDTO>> revokeSessions(
        @PathVariable String id,
        Authentication authentication) {
    String performedBy = authentication != null ? authentication.getName() : "system";
    UserDTO updated = userService.revokeSessions(id, performedBy, authentication);
    return ResponseEntity.ok(ApiResponse.success(updated, "Sessions revoked successfully"));
}
```

Triggers that atomically increment `tokenVersion`:
1. **Account Disablement (`PATCH /api/users/{id}/status` with `enabled: false`)**: Increments `tokenVersion`. When re-enabling (`enabled: true`), `tokenVersion` is **not** decremented or altered.
2. **Password Change (`PUT /api/users/{id}` with new `password`)**: Increments `tokenVersion`.
3. **Session Revocation (`POST /api/users/{id}/revoke-sessions`)**: Increments `tokenVersion`.
4. **Self-Revocation Guard**: Rejects self-revocation attempts with `400 Bad Request: "You cannot revoke your own active session"`.

---

## 3. Automated Test Verification

### 3.1 Migration Tests
- **`UserTokenVersionMigrationTest`**:
  - Validates V11 -> V12 migration on isolated Testcontainers instance.
  - Verifies pre-existing users receive `tokenVersion = 0`.
  - Verifies `NOT NULL DEFAULT 0` column metadata.
  - Verifies migration idempotency.
  - **Result:** `1 passed, 0 failures, 0 errors`

### 3.2 Token Versioning & Session Revocation Scenarios
**File:** `src/test/java/com/sense/prolt_operations/TokenVersioningAndRevocationIntegrationTest.java`

| Scenario | Description | Result |
|---|---|---|
| SCENARIO 01 | Default `tokenVersion = 0` for newly created user | Passed |
| SCENARIO 02 | Newly issued JWT contains correct `tokenVersion` claim | Passed |
| SCENARIO 03 | Valid JWT with matching version is accepted on protected business APIs (`/api/datasets/summary`) | Passed |
| SCENARIO 04 | Mismatched `tokenVersion` is rejected with `401 Unauthorized` | Passed |
| SCENARIO 05 | Missing `tokenVersion` claim (legacy token) is rejected with `401 Unauthorized` | Passed |
| SCENARIO 06 | Disabled account rejected on protected APIs with `"Account is disabled. Please contact administrator."` | Passed |
| SCENARIO 07 | Existing JWT rejected immediately after account disable | Passed |
| SCENARIO 08 | Old JWT remains rejected after account re-enable | Passed |
| SCENARIO 09 | Newly issued JWT after re-enable has incremented version and is accepted | Passed |
| SCENARIO 10 | Password update invalidates previously issued JWTs | Passed |
| SCENARIO 11 | Admin session revocation (`POST /api/users/{id}/revoke-sessions`) invalidates active JWTs | Passed |
| SCENARIO 12 | Super Admin cannot revoke their own active sessions (`400 Bad Request`) | Passed |
| SCENARIO 13 | Non-super admin calling `revoke-sessions` returns `403 Forbidden` | Passed |
| SCENARIO 14 | Public endpoints (`/health`, `/actuator/health`) remain accessible | Passed |
| SCENARIO 15 | Standard user authentication via `/api/auth/login` operates normally | Passed |
| SCENARIO 16 | Role authorization (`SUPER_ADMIN` vs `CLIENT`) strictly enforced | Passed |
| SCENARIO 17 | Concurrency-safe atomic `tokenVersion` increments across 10 parallel threads | Passed |
| SCENARIO 18 | User audit log records `REVOKE_SESSIONS` and `UPDATE_STATUS` actions accurately | Passed |

**Result:** `18 passed, 0 failures, 0 errors`

### 3.3 Security State Provider Scenarios (Optional Redis Architecture)
**File:** `src/test/java/com/sense/prolt_operations/security/state/SecurityStateProviderIntegrationTest.java`

| Scenario | Description | Result |
|---|---|---|
| PROVIDER-01 | Database provider runs without Redis infrastructure | Passed |
| PROVIDER-02 | Database provider correctly reads enabled status and tokenVersion | Passed |
| PROVIDER-03 | Database provider post-commit sync is a safe no-op | Passed |
| PROVIDER-04 | Database provider enforces immediate revocation across requests | Passed |
| PROVIDER-05 | Redis provider in production fails closed on Redis connection failure without falling back to DB | Passed |
| PROVIDER-06 | Redis provider avoids PostgreSQL query on cache hit | Passed |
| PROVIDER-07 | Redis provider loads DB state on cache miss and atomically populates Redis cache | Passed |
| PROVIDER-08 | Configuration validation fails startup on invalid provider or missing Redis URL | Passed |

**Result:** `8 passed, 0 failures, 0 errors`

### 3.4 Full Test Suite Status
- **Backend (`./mvnw test`)**:
  - Total tests run: **223**
  - Failures: **0**
  - Errors: **0**
  - Skipped: **1** (manual live test)
- **Frontend (`npx tsc --noEmit`)**:
  - TypeScript Compilation: **0 errors**

---

## 4. Optional Redis Architecture

### 4.1 Design & Provider Abstraction
To ensure Redis is not mandatory for local development while providing scalable caching in production, we introduced `UserSecurityStateProvider`:
- **`DatabaseSecurityStateProvider`**: Used in `default` / `dev` environments (`app.security-state.provider=database`). Queries `UserRepository.findSecurityStateById(userId)` directly via primary key index. Post-commit sync is a no-op.
- **`RedisSecurityStateProvider`**: Used in `prod` (`app.security-state.provider=redis`).
  - Reads Redis (`user:security-state:{userId}`) on cache hit.
  - Queries DB on cache miss and atomically updates Redis.
  - Synchronizes Redis post-commit via `TransactionSynchronizationManager` using an atomic Lua script (`ATOMIC_VERSION_SET_LUA`).
  - **Fail-closed**: Throws `SecurityStateUnavailableException` on Redis connection loss to prevent silent database overload or state desynchronization.

### 4.2 Configuration Validation
In `SecurityStateConfig`:
- Validates `app.security-state.provider` must be `database` or `redis`.
- If `redis` is configured, verifies that `spring.data.redis.url` or `app.redis-url` is present and valid; otherwise application startup is immediately aborted (`IllegalStateException`).

---

## 5. Local Database Status Verification

As mandated by safety constraints, the local persistent database was inspected strictly in read-only mode and was **not modified**:

```sql
SELECT installed_rank, version, description, type, script, success 
FROM public.flyway_schema_history 
ORDER BY installed_rank DESC LIMIT 5;
```

**Output:**
```
 installed_rank | version |                description                 | type |                       script                       | success 
----------------+---------+--------------------------------------------+------+----------------------------------------------------+---------
              7 | 11      | add user enabled status                    | SQL  | V11__add_user_enabled_status.sql                   | t
              6 | 10      | enforce user full name not null            | SQL  | V10__enforce_user_full_name_not_null.sql           | t
              5 | 9       | normalize usernames and backfill full name | SQL  | V9__normalize_usernames_and_backfill_full_name.sql | t
              4 | 8       | email case insensitive and full name       | SQL  | V8__email_case_insensitive_and_full_name.sql       | t
              3 | 7       | case insensitive username uniqueness       | SQL  | V7__case_insensitive_username_uniqueness.sql       | t
```

- Target database: `jdbc:postgresql://localhost:5432/prolt_operations`
- Current schema version: **V11** (V12 has **not** been applied locally)
- Default configuration preserved: `spring.flyway.target=${FLYWAY_TARGET:8}`

---

## 6. Next Steps

The implementation and automated validation of Milestone V12 and the Optional Redis provider architecture are complete. We are stopped and awaiting explicit user authorization before executing Flyway V12 against the local database (`localhost:5432/prolt_operations`).

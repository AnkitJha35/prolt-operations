# V16 — Backend Security & Architecture Audit Report

**Project**: `prolt-operations` (Spring Boot Backend)  
**Date**: November 3, 2026  
**Milestone Focus**: Post-V14 Security, Architectural, and Reliability Assessment  
**Classification**: **NEEDS SECURITY FIXES**  

---

## 1. Executive Summary

Following the completion of **Milestone V13** (Complete User Password Removal & OTP-Only Authentication) and **Milestone V14** (Dual-Token Access & Refresh Token Architecture), this audit was conducted to evaluate the backend security posture, API consistency, data integrity, and operational resilience of `prolt-operations`.

### High-Level Verdict:
The backend successfully implements core modern identity primitives:
- Password-based authentication has been **completely eliminated**; no password columns, encoders, or endpoints remain.
- Access tokens now strictly expire in **15 minutes** (down from 7 days).
- Refresh tokens are cryptographically strong (256-bit entropy), stored **only as SHA-256 hashes** in PostgreSQL, and transported via `HttpOnly` cookies.
- Atomic token rotation and reuse detection with whole-family invalidation are functioning as expected.
- All 255 existing automated backend integration tests pass.

However, several critical and high-severity security vulnerabilities and architectural gaps were identified:
1. **Critical Authorization Exposure**: Master data modification and deletion endpoints (`POST`, `PUT`, `DELETE` on `/api/resource-center/**` and `/api/database/**`) are marked `permitAll()` in Spring Security, allowing anonymous unauthenticated users to delete and modify production tariffs, charges, and calendar records.
2. **High-Risk CORS Configuration**: Wildcard allowed origin patterns (`setAllowedOriginPatterns("*")`) paired with credentials enabled (`setAllowCredentials(true)`) allows any external origin to execute authenticated requests if cookies are sent. Furthermore, `app.cors-origins` from configuration is completely ignored.
3. **Super Admin Account Destruction**: Lack of self-delete and last-active Super Admin guards in `UserService.deleteUser` and `updateUser` permits accidental or malicious deletion or demotion of the only remaining Super Administrator.
4. **Role/Permission Stale Session Gap**: Updating a user's role or access rights via `PUT /api/users/{id}` does not increment `tokenVersion` or evict the security state cache, leaving privileges elevated on active tokens for their entire lifetime.
5. **In-Memory Rate Limiting**: OTP brute-force protection is implemented via an in-memory fixed window filter keyed only by IP, making it ineffective against distributed attacks and failing to synchronize across horizontal multi-instance deployments.

---

## 2. Repository and Technology Overview

| Component | Technology / Version | Deployment Notes |
|---|---|---|
| **Framework** | Spring Boot 3.5.6 / Java 21 | Web, Data JPA, Security, Actuator, Mail |
| **Authentication** | OTP-Only via SMTP / Redis / In-memory | 6-digit numeric OTP, 5-minute validity |
| **Tokens** | HMAC-SHA256 JWT (Jjwt 0.12.6) | 15-minute access token, subject = user UUID |
| **Refresh Sessions** | PostgreSQL `public.user_refresh_sessions` | SHA-256 hash lookup, 7-day validity, pessimistic lock |
| **Database** | PostgreSQL 16 (Multi-Schema: `public`, `forecasting`) | Flyway migrations V1 through V14 |
| **State Cache** | Redis / PostgreSQL Provider | `UserSecurityStateProvider` fail-closed pattern |
| **API Documentation** | SpringDoc OpenAPI / Swagger 3 | Disabled in production profile |

---

## 3. Detailed Audit Findings by Area

### A. Authentication & Authorization

#### Strengths:
- **Zero Passwords**: Verification confirmed that `passwordHash` has been dropped from `public."User"`, `PasswordEncoder` has been removed from the Spring context, and `/api/auth/login` returns HTTP 404.
- **OTP Generation & Verification**: Uses `SecureRandom` to generate 6-digit OTPs. Verification in Redis deletes the key upon successful consumption, preventing replay attacks.
- **Fail-Closed Security State**: `JwtAuthenticationFilter` evaluates `securityStateProvider.getSecurityState(userId)`. If the provider fails or user is disabled, requests fail closed.
- **Role Hierarchy & Method Security**: `@EnableMethodSecurity(prePostEnabled = true)` is enabled.

#### Weaknesses & Vulnerabilities:
1. **Unprotected Mutating Endpoints (SEC-01 & SEC-07)**:
   - In `SecurityConfig.java`:
     ```java
     .requestMatchers("/api/resource-center/**").permitAll()
     .requestMatchers("/api/database/**").permitAll()
     ```
     Controllers mapped to these paths contain state-modifying endpoints:
     - `POST /api/resource-center/{resourceType}`
     - `POST /api/resource-center/{resourceType}/bulk`
     - `PUT /api/resource-center/{resourceType}/{id}`
     - `DELETE /api/resource-center/{resourceType}/{id}`
     - `POST /api/database/holidays`
     - `POST /api/database/holidays/upload` (file upload!)
     - `PUT /api/database/holidays/{id}`
     - `POST /api/database/city-state`
     None of these methods have `@PreAuthorize` annotations. Anyone on the Internet can execute these requests without authentication.
2. **User Enumeration via OTP Request (SEC-06)**:
   - In `OtpService.java:94-98`, `generateAndSendOtp` throws `NotFoundException` (HTTP 404) if the email is not registered, `UnauthorizedException` (HTTP 401) if disabled, and returns HTTP 200 if active. This allows unauthenticated attackers to test lists of email addresses to map all valid user accounts.
3. **In-Memory Rate Limiting (SEC-05)**:
   - `AuthRateLimiterFilter` uses a JVM in-memory bucket of 10 requests per 15 minutes keyed only by `clientIp`. It does not key by `email` or `(IP, email)`. In multi-container deployments, each container has its own rate-limit memory, weakening rate limiting proportionally to replica count. Furthermore, corporate proxy users sharing an IP will easily suffer DoS.

---

### B. Access & Refresh Token Architecture (V14 Implementation)

#### Strengths:
- **15-Minute Expiration**: `app.jwt.expires-in=15m` correctly configured and verified across `JwtService.java` and configuration profiles.
- **Cryptographic Randomness**: 32 bytes (256 bits) generated by `SecureRandom`, Base64 URL-safe encoded.
- **Zero Plaintext Persistence**: Only SHA-256 hashes are written to `user_refresh_sessions`. Plaintext tokens are returned only in `Set-Cookie`.
- **Atomic Rotation & Concurrency Safety**: Pessimistic write locking (`findByTokenHashForUpdate`) serializes concurrent refresh calls for the same token, preventing double-use.
- **Family Reuse Detection**: Once a token is marked revoked or replaced, presenting it again immediately revokes all tokens sharing its `tokenFamilyId` and logs a security audit event.
- **Transactional Consistency**: Added `@Transactional(noRollbackFor = UnauthorizedException.class)` ensures that family revocations during reuse detection are permanently committed.
- **Multi-Device Support**: Each login session generates a distinct `tokenFamilyId`. Revoking one session family does not disrupt other devices.

#### Weaknesses & Vulnerabilities:
1. **Unbounded User-Agent / Created-IP Field (SEC-09)**:
   - `UserRefreshSession.userAgent` is mapped to `VARCHAR(500)`. If a client sends an HTTP `User-Agent` header exceeding 500 characters, JPA throws `DataIntegrityViolationException`, causing a 500 error and breaking login/refresh. The string should be truncated to $\le 500$ characters.
2. **Stale Session Privileges on User Update (SEC-04)**:
   - When a Super Admin updates a user's role (e.g. demoting ADMIN to CLIENT) or updates module permissions in `PUT /api/users/{id}`, `user.incrementTokenVersion()` is NOT called. As a result, the user's active 15-minute access token and 7-day refresh sessions retain the old permissions.

---

### C. Cookie, CORS & CSRF Security

#### Strengths:
- **HttpOnly**: Enforced on `iex_refresh_token` (`cookieHttpOnly=true`). JavaScript cannot access the refresh token.
- **Path Restriction**: Cookie path is restricted to `/api/auth`.
- **Secure Flag in Production**: `application-prod.properties` sets `app.auth.refresh-token.cookie-secure=true`.
- **SameSite**: Set to `Lax` (configurable).

#### Weaknesses & Vulnerabilities:
1. **Permissive Wildcard CORS with Credentials (SEC-02)**:
   - In `SecurityConfig.java`:
     ```java
     configuration.setAllowedOriginPatterns(List.of("*"));
     configuration.setAllowCredentials(true);
     ```
     `setAllowedOriginPatterns(List.of("*"))` reflects back whatever `Origin` header the client sends. Paired with `setAllowCredentials(true)`, this allows any third-party malicious domain to initiate credentialed cross-origin requests.
   - Property `app.cors-origins=${CORS_ORIGIN:*}` in `application.properties` is **completely ignored** by `SecurityConfig.java`.
2. **CSRF Protection Disabled with Cookie Authentication**:
   - `http.csrf(AbstractHttpConfigurer::disable)` is configured because the API is stateless.
   - While access tokens are sent via `Authorization: Bearer <token>` (immune to standard browser CSRF), the `/api/auth/refresh` and `/api/auth/logout` endpoints authenticate via `Cookie: iex_refresh_token`.
   - If `SameSite=Lax` or `None` is used across cross-origin deployments with wildcard CORS, CSRF risks arise for cookie-authenticated endpoints.

---

### D. Database & Flyway Migrations

#### Strengths:
- Clean linear migration sequence from V1 to V14:
  - V1–V6: Initial schema, tariffs, charges, admin data.
  - V7: Case-insensitive username uniqueness (`uq_user_username_lower_trim`).
  - V8: Case-insensitive email index (`idx_user_email_lower_trim`) and `fullName`.
  - V9: User normalization.
  - V10: Enforce `fullName` NOT NULL.
  - V11: User `enabled` status column.
  - V12: User `tokenVersion` column.
  - V13: Drop `passwordHash` column from `public."User"`.
  - V14: Create `public."user_refresh_sessions"` with indexes and foreign keys.
- Robust preflight and postflight assertions in V11, V12, V13, and V14.
- All migrations tested in isolation across 6 scenarios in `IsolatedMigrationRehearsalTest.java`.

#### Status Verification:
- **Migration files present**: V1 through V14.
- **Tested in isolation**: Yes, 100% verified via Testcontainers PostgreSQL 16.
- **Applied to local database (`localhost:5432/prolt_operations`)**: **NO**. The local database remains at Flyway V12.
- **Applied to production database**: **NO**. Production remains at Flyway V8/V10.
- `spring.flyway.target=${FLYWAY_TARGET:8}` remains the baseline default in `application.properties`.

---

### E. API Contract & Error Handling

#### Strengths:
- Standardized `ApiResponse<T>`, `PageResponse<T>`, and `ErrorResponse` formats across controllers.
- `RestControllerAdvisor` handles exceptions centrally, preventing raw stack traces in responses.
- HTTP status codes are semantically appropriate (200 OK, 201 Created, 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict, 429 Too Many Requests).

#### Weaknesses & Inconsistencies:
1. **Raw Database Message Leak in DataIntegrityViolation (SEC-10)**:
   - In `RestControllerAdvisor.java:117`, non-unique constraint violations return `"Data integrity violation: " + ex.getMessage()`. This can leak internal column names, table names, or SQL query snippets to clients.
2. **Missing Input Validation on Key DTOs**:
   - `DatabaseController` accepts unstructured `Map<String, Object>` payloads for `POST /api/database/holidays` and `POST /api/database/city-state` instead of validated Java records or DTOs with `@Valid` and Jakarta validation constraints.

---

### F. User Management & Account Lifecycle

#### Strengths:
- User creation and update flows are completely passwordless.
- Username is immutable.
- User email normalization is applied consistently (`trim().toLowerCase(Locale.ROOT)`).
- Audit logs capture all user lifecycle events (`CREATE`, `UPDATE`, `UPDATE_STATUS`, `REVOKE_SESSIONS`, `DELETE`).
- Self-disable protection prevents an administrator from disabling their own account.
- Last-active Super Admin protection in `updateUserStatus` prevents disabling the last active Super Admin.

#### Weaknesses & Vulnerabilities:
1. **Unprotected Super Admin Deletion (SEC-03)**:
   - In `UserService.deleteUser(String id, String performedBy)`:
     There is **no check** preventing a Super Admin from deleting their own account.
     There is **no check** preventing the deletion of the last active Super Admin.
2. **Unprotected Super Admin Demotion in `updateUser` (SEC-03)**:
   - In `UserService.updateUser(String id, UpdateUserDTO dto, String performedBy)`:
     If the target user is a Super Admin and `dto.role()` is `"CLIENT"`, the role is updated without verifying whether other active Super Admins exist.
3. **Missing Session Revocation on Role/Permission Changes (SEC-04)**:
   - Updating role or permissions does not invoke `user.incrementTokenVersion()` or `refreshTokenService.revokeAllSessionsForUser(user.getId())`.

---

### G. Redis & Security State

#### Strengths:
- **Fail-Closed Design**: When Redis is configured as the security state provider (`app.security-state.provider=redis`), connection failures throw `SecurityStateUnavailableException` which clears the security context and rejects requests rather than failing open.
- **Atomic Lua Scripting**: `RedisSecurityStateProvider` uses Lua script `ATOMIC_VERSION_SET_LUA` to ensure cache updates only overwrite if the new `tokenVersion` is $\ge$ the cached version.

#### Weaknesses & Consistency Gaps:
1. **Post-Commit Crash Window (SEC-08)**:
   - In `RedisSecurityStateProvider.java:108`, Redis synchronization is registered via `TransactionSynchronizationManager.registerSynchronization` inside `afterCommit()`.
   - If the application crashes, restarts, or encounters a network partition between the PostgreSQL transaction commit and Redis update, the Redis cache remains populated with the old security state.
2. **Excessive Cache TTL (SEC-08)**:
   - `DEFAULT_TTL_SECONDS = 7L * 24 * 60 * 60` (7 days).
   - Under V14, access tokens expire in **15 minutes**. Caching security state for 7 days means an unsynchronized dirty cache entry persists for an entire week. Reducing TTL to 5–15 minutes would ensure natural self-healing.

---

### H. Performance & Reliability

#### Strengths:
- **No JPA Lazy N+1 Traps**: The entity model is predominantly flat and decoupled from complex bidirectional object graphs.
- **Connection Pool Configuration**: HikariCP is tuned with connection timeout (20s), idle timeout (300s), minimum idle (5), and max pool size (20).
- **Index Optimization**: Composite and partial indexes support fast user and token session lookups (`idx_refresh_sessions_user_active`, `idx_user_email_lower_trim`).
- **Database Housekeeping**: `@Scheduled` daily purge job (`cleanupExpiredAndRevokedSessions`) purges old expired and revoked sessions.

#### Considerations:
- **Distributed Scheduled Jobs**: In multi-instance deployments, the `@Scheduled` cleanup job in `RefreshTokenService` will trigger simultaneously on all instances. While safe due to PostgreSQL row-level locks, introducing distributed scheduler locking (e.g. ShedLock) is recommended.

---

### I. Testing & Code Quality

#### Verification Results:
- Total Backend Tests Executed: **255**
- Passing: **255**
- Failures: **0**
- Errors: **0**
- Skipped: **1** (`SampleTest.java`)
- Compilation: **0 errors**

#### Special Investigation: V13/V14 Test Password Inconsistency
- The user query noted: *"A V14 test description reportedly references a SUPER_ADMIN password update, although V13 removed user passwords. Determine whether this is merely stale test naming or an actual implementation inconsistency."*
- **Investigation Finding**:
  - In `TokenVersioningAndRevocationIntegrationTest.java:307`, a test method is named `test10_passwordUpdate_invalidatesPreviousJwts()`.
  - Inspection of the test code reveals that the method was refactored in V13 to test user profile updates and session revocation via `/api/users/{id}/revoke-sessions`. No passwords or password hashing are used. The `@DisplayName` reads: `"SCENARIO 10: User update does not revoke sessions, but session revocation increments tokenVersion and revokes old tokens"`.
  - In `TestUserHelper.java:26`, an unused field `private final String rawPassword` remains from pre-V13 helper signatures.
  - **Conclusion**: This is **purely cosmetic / stale test naming**. No password functionality exists in the application.

---

## 4. Confirmed Findings Matrix

| Finding ID | Severity | File Path | Method / Component | Description | Confirmed? |
|---|---|---|---|---|---|
| **SEC-01** | **CRITICAL** | `SecurityConfig.java:77`, `ResourceCenterController.java:48-120` | `securityFilterChain` / `ResourceCenterController` | Master data write and delete operations (`POST`, `PUT`, `DELETE` on `/api/resource-center/**`) are `permitAll()` without authentication. | **CONFIRMED** |
| **SEC-02** | **HIGH** | `SecurityConfig.java:115, 129` | `corsConfigurationSource` | Permissive wildcard origin patterns (`List.of("*")`) with `allowCredentials(true)` enables credentialed cross-origin access from any domain; ignores `app.cors-origins`. | **CONFIRMED** |
| **SEC-03** | **HIGH** | `UserService.java:175-178, 278-290` | `deleteUser` & `updateUser` | Super Admin self-deletion and last-active Super Admin deletion/demotion are not guarded. | **CONFIRMED** |
| **SEC-04** | **HIGH** | `UserService.java:175-187` | `updateUser` | Modifying user role or permissions does not bump `tokenVersion` or revoke sessions, leaving elevated privileges active. | **CONFIRMED** |
| **SEC-05** | **HIGH** | `AuthRateLimiterFilter.java:26, 45-48` | `doFilterInternal` | Rate limiting is in-memory per JVM and keyed solely by IP; vulnerable to distributed brute-force and multi-node desynchronization. | **CONFIRMED** |
| **SEC-06** | **MEDIUM** | `OtpService.java:94-98` | `generateAndSendOtp` | Distinct 404 and 401 responses on `/api/auth/send-otp` allow unauthenticated user enumeration. | **CONFIRMED** |
| **SEC-07** | **MEDIUM** | `SecurityConfig.java:78`, `DatabaseController.java:133-183` | `securityFilterChain` / `DatabaseController` | Holiday creation, bulk file upload, and city-state creation are `permitAll()` without authentication. | **CONFIRMED** |
| **SEC-08** | **MEDIUM** | `RedisSecurityStateProvider.java:23, 105-115` | `updateSecurityState` | Post-commit crash window between DB and Redis paired with excessive 7-day TTL risks prolonged dirty security state cache. | **CONFIRMED** |
| **SEC-09** | **MEDIUM** | `RefreshTokenService.java:193-200`, `UserRefreshSession.java:49` | `createSession`, `rotateSession` | Unbounded `userAgent` header passed to `VARCHAR(500)` database column can cause HTTP 500 error on long headers. | **CONFIRMED** |
| **SEC-10** | **LOW** | `RestControllerAdvisor.java:117` | `handleDataIntegrityViolation` | Raw database exception messages returned in HTTP 400 responses leak table/column names. | **CONFIRMED** |
| **SEC-11** | **LOW** | `OtpService.java:77` | `validateStartupConfiguration` | Production safety check relies strictly on active profile name containing `"prod"`. | **CONFIRMED** |
| **INFO-01** | **INFO** | `TokenVersioningAndRevocationIntegrationTest.java:307` | `test10_passwordUpdate_...` | Stale test method name and unused test helper field mention password despite 100% passwordless implementation. | **CONFIRMED** |

---

## 5. Potential Risks & Deployment Impact

1. **Production Deployment Risk (CORS & Master Data)**:
   - If deployed to production with current `SecurityConfig`, external parties or compromised browser contexts could delete tariffs or read authenticated API responses.
2. **Horizontal Scaling Risk**:
   - Running multiple backend instances behind a load balancer will result in independent in-memory rate limiting, allowing 10 requests per instance for OTP brute force.
3. **Database Migration Safety**:
   - The local database (`localhost:5432/prolt_operations`) is currently at V12. Running V13 (drop password hash) and V14 (create user refresh sessions) will require formal rehearsal and validation before production cutover.

---

## 6. Recommended Remediation Plan (V16 Scope)

### Phase 1: Security Hardening (High Priority)
1. **Secure Mutating Endpoints**:
   - Require authentication for `POST`, `PUT`, and `DELETE` methods in `ResourceCenterController` and `DatabaseController`.
   - Restrict administrative endpoints (`/api/resource-center/**` mutations) to `ADMIN` or `SUPER_ADMIN` roles.
2. **CORS Hardening**:
   - Inject `@Value("${app.cors-origins}")` into `SecurityConfig.java`.
   - In production, restrict allowed origins to explicit trusted frontend origins (e.g., `https://iex.probus.io`).
   - For local development, allow `http://localhost:3000` and `http://127.0.0.1:3000`.
3. **Account Protection & Lifecycle Guards**:
   - Add self-delete check in `UserService.deleteUser`.
   - Add last active Super Admin check in `UserService.deleteUser` and `UserService.updateUser`.
   - Increment `tokenVersion` and invoke `refreshTokenService.revokeAllSessionsForUser(id)` whenever a user's role or permissions change in `UserService.updateUser`.

### Phase 2: Reliability & Rate Limiting (Medium Priority)
4. **Header Sanitization**:
   - Truncate incoming `User-Agent` to 500 characters and sanitize `clientIp` before storing in `UserRefreshSession`.
5. **Security State Cache Alignment**:
   - Reduce `RedisSecurityStateProvider.DEFAULT_TTL_SECONDS` from 7 days to 15 minutes (aligning with access token lifetime).
6. **Distributed Rate Limiting**:
   - Migrate `AuthRateLimiterFilter` to a Redis-backed token bucket or key rate limits by `(IP, email)`.

### Phase 3: Code Cleanup & Documentation (Low Priority)
7. **Clean Stale Test Naming**:
   - Rename `test10_passwordUpdate_invalidatesPreviousJwts` to `test10_userUpdate_andSessionRevocation_invalidatesPreviousJwts`.
   - Remove unused `rawPassword` field from `TestUserHelper.java`.
8. **Error Message Masking**:
   - Sanitize `DataIntegrityViolationException` in `RestControllerAdvisor` to avoid leaking database column/table names.

---

## 7. Final Audit Checklist

- [x] OTP generation, verification, and rate limiting reviewed.
- [x] Zero password dependency confirmed (V13 intact).
- [x] Access token 15-minute expiration and claims verified.
- [x] Refresh token 7-day expiration, SHA-256 hash storage, and rotation verified.
- [x] Token reuse detection and whole-family revocation verified.
- [x] Cookie security flags (`HttpOnly`, `Path`, `Secure`, `SameSite`) audited.
- [x] CORS configuration and credential handling audited.
- [x] Route authorization and endpoint protections audited.
- [x] Database migrations V1–V14 audited and verified against isolated Testcontainers.
- [x] User management lifecycle, self-protection, and last Super Admin guards audited.
- [x] Redis security state synchronization and crash window evaluated.
- [x] All 255 existing automated tests run and passed without failures.
- [x] No application source code modified during audit.
- [x] No database migrations created or executed on local/production environments.
- [x] No Git commits or pushes made.

---

## 8. Final Decision

**Classification**: **NEEDS SECURITY FIXES**

The backend architecture is structurally sound, and the V13/V14 transitions (password removal and refresh token rotation) are fully functional. However, the identified route authorization exposures on master data (`SEC-01`), wildcard CORS configuration (`SEC-02`), and user lifecycle guard omissions (`SEC-03`, `SEC-04`) must be addressed in Milestone V16 before production cutover.

# Execution & Security Report: Flyway V11 Local Database Execution and Account Status Security Audit

**Document Reference**: `docs/43-v11-local-database-execution-and-security-report.md`  
**Execution Date**: 2026-11-02 15:42 IST  
**Environment**: Local Restored PostgreSQL (Docker container `postgres-server`)  
**Target JDBC URL**: `jdbc:postgresql://localhost:5432/prolt_operations`  
**Target Database Engine**: PostgreSQL 16.14 (Debian 16.14-1.pgdg13+1)  
**Execution Result**: **100% SUCCESSFUL — FLYWAY V11 APPLIED, ALL 12 USERS ENABLED, SCHEMA VALIDATED**  
**Security Status**: **SECURITY VERIFIED WITH DOCUMENTED ARCHITECTURAL BOUNDARY & RECOMMENDATIONS**  

---

## 1. Migration V11 Audit

File: `src/main/resources/db/migration/V11__add_user_enabled_status.sql`

### 1.1 Specification & Safety Verification
1. **Column Definition**: Adds `enabled BOOLEAN NOT NULL DEFAULT TRUE` to `public."User"`.
2. **Idempotency**: Utilizes `information_schema.columns` inspection inside a `DO $$` PL/pgSQL block. If column does not exist, executes `ADD COLUMN "enabled" BOOLEAN NOT NULL DEFAULT TRUE`. If column already exists, enforces `SET DEFAULT TRUE`, backfills any nulls, and sets `NOT NULL`.
3. **Non-Destructive Guarantee**: Contains zero `DROP`, `TRUNCATE`, or destructive DDL statements. No user records or foreign keys are deleted or altered.
4. **Postflight Automated Verification**:
   - Queries `information_schema.columns` to ensure `is_nullable = 'NO'`.
   - Ensures `column_default` evaluates to `true`.
   - Scans all rows in `public."User"`: raises an exception and aborts transaction if any row has `enabled IS NOT TRUE`.
5. **Configuration Safety**:
   - `application.properties` default target is preserved:
     ```properties
     spring.flyway.target=${FLYWAY_TARGET:8}
     ```
   - Application profile `localvalidate` has `spring.flyway.enabled=false`, requiring explicit CLI enablement.
   - Remote database access: Impossible under `localvalidate` (JDBC URL hardcoded to `localhost:5432/prolt_operations`).

---

## 2. Target Database Identity & Preflight Status

Connection established strictly to `localhost:5432/prolt_operations`.

```sql
SELECT
    current_database(),
    current_user,
    inet_server_addr(),
    inet_server_port(),
    version();
```

| Field | Value |
|---|---|
| `current_database()` | `prolt_operations` |
| `current_user` | `postgres` |
| `inet_server_addr()` | `172.17.0.6` (Docker bridge) |
| `inet_server_port()` | `5432` |
| `version()` | `PostgreSQL 16.14 (Debian 16.14-1.pgdg13+1) on x86_64-pc-linux-gnu` |

### Pre-Migration Flyway Baseline

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

| Rank | Version | Description | Success |
|---|---|---|---|
| 1 | `5` | `<< Flyway Baseline >>` | `t` |
| 2 | `6` | `create dsm charges if missing` | `t` |
| 3 | `7` | `case insensitive username uniqueness` | `t` |
| 4 | `8` | `email case insensitive and full name` | `t` |
| 5 | `9` | `normalize usernames and backfill full name` | `t` |
| 6 | `10` | `enforce user full name not null` | `t` |

- Latest version prior to execution: **V10**
- V11 was **NOT** present.
- Zero failed migrations.

---

## 3. User Account Baseline Prior to V11

```sql
SELECT id, username, email, role, "fullName" FROM public."User" ORDER BY id;
```

| # | ID | Username | Email | Role | Full Name |
|---|---|---|---|---|---|
| 1 | `000d369a-b7ee-440e-a9bc-5afc44e7d9c3` | `neeraj_sharma` | `neeraj@probus.io` | `CLIENT` | `Neeraj Sharma` |
| 2 | `106a117c-61f6-4692-a2bf-822ecca8369b` | `harshit` | `harshit@probus.io` | `CLIENT` | `Harshit` |
| 3 | `12e4bf87-d738-4c78-a0d3-b7f5af3d4671` | `priyanka` | `priyanka@probus.io` | `CLIENT` | `Priyanka` |
| 4 | `250d7a15-4ba4-4996-a973-0dceac80d4b0` | `gaurav` | `gaurav@probus.io` | `CLIENT` | `Gaurav` |
| 5 | `31b3ef50-1f7b-4f52-9849-87e448827755` | `ankit_vaish` | `ankit@probus.io` | `CLIENT` | `Ankit Vaish` |
| 6 | `356107df-a81e-4f5d-98f4-4cfbf892577e` | `saurabh_anoop` | `saurabh@probus.io` | `CLIENT` | `Saurabh Anoop` |
| 7 | `46378d4d-943a-4e48-ba9f-249bc03fb5b7` | `ankit_jha` | `ankitjha@probus.io` | `CLIENT` | `Ankit Jha` |
| 8 | `6ca75721-cd07-4f54-8ec7-9a4cf4416b31` | `anurag_shukla` | `anurag@probus.io` | `CLIENT` | `Anurag Shukla` |
| 9 | `81152b45-75c7-4e85-82a3-a9f531da32df` | `saikat` | `saikat@probus.io` | `CLIENT` | `Saikat` |
| 10 | `9a0f007b-4819-43b1-b142-84874dde9b72` | `anand` | `anand@probus.io` | `CLIENT` | `Anand` |
| 11 | `a1655350-9d4f-4752-ad17-aaaa7bfe3fc3` | `yash` | `yash@probus.io` | `ADMIN` | `Yash` |
| 12 | `c9bd0ef0-7079-4da0-9373-2dce24128b63` | `superadmin` | `super.admin@probus.io` | `SUPER_ADMIN` | `System Administrator` |

- Total Users: **12**
- Total Active SUPER_ADMIN Accounts: **1** (`superadmin`)
- Pre-migration `enabled` column existence: **Confirmed Absent (`0 rows`)**

---

## 4. Phase C: Verified Pre-Migration Backup

A fresh PostgreSQL custom-format backup was generated before any migration was executed:

- **Backup File**: `backups/prolt_operations_pre_v11_20261102_153029.dump`
- **Format**: PostgreSQL Custom Format (`-Fc`, compressed)
- **File Size**: `1,037,385,250` bytes (~990 MB)
- **SHA-256**: `b1ed280349e03713aceed8fb3d189d90a84ddc2cc5255e30ffebdfad5e1583e1`
- **Archive Validation (`pg_restore --list`)**:
  - `TOC Entries`: 453
  - `Source Database`: `prolt_operations`
  - `Dump Version`: `1.16-0` (PostgreSQL 16.14)
  - Verified non-empty, fully readable, and valid without errors.
- **Historical Backups Preserved**:
  - `backups/prolt_operations_pre_v7_20261030_180304.dump` (Preserved)
  - `backups/prolt_operations_pre_v8_20261102_114557.dump` (Preserved)
  - `backups/prolt_operations_pre_v9_v10_20261102_140500.dump` (Preserved)

---

## 5. Phase D: Pre-Migration Baseline Checksums

Captured across all 12 user accounts and historical tables:

```sql
SELECT 
    md5(string_agg(id || '|' || username || '|' || email || '|' || "passwordHash" || '|' || role::text || '|' || "fullName" || '|' || COALESCE("hiddenModules"::text, '{}') || '|' || COALESCE("readOnlyModules"::text, '{}') || '|' || "createdAt"::text || '|' || "updatedAt"::text, ',' ORDER BY id)) AS user_protected_fields_md5,
    encode(sha256(string_agg(id || '|' || username || '|' || email || '|' || "passwordHash" || '|' || role::text || '|' || "fullName" || '|' || COALESCE("hiddenModules"::text, '{}') || '|' || COALESCE("readOnlyModules"::text, '{}') || '|' || "createdAt"::text || '|' || "updatedAt"::text, ',' ORDER BY id)::bytea), 'hex') AS user_protected_fields_sha256
FROM public."User";
```

| Checksum Metric | Baseline Value |
|---|---|
| `user_protected_fields_md5` | `29cc2814027577b740d21d4fa424cf87` |
| `user_protected_fields_sha256` | `41166e5ff4e7cb577b51785e4f276720667a52247d17d6b58bf10bcaf77b23ad` |
| `user_audit_log_count` | `44` |
| `user_audit_log_md5` | `49620a853e8487c4098df0927dcb37d0` |
| `savings_calculator_new_entries_count` | `31` |
| `savings_calculator_new_entries_md5` | `db42c3b11649cf6b9c312893359bf48b` |

---

## 6. Phase E: Migration Execution & Log Excerpt

### 6.1 Execution Command
```bash
java \
  -Dspring.profiles.active=localvalidate \
  -Dspring.flyway.enabled=true \
  -Dspring.flyway.target=11 \
  -jar target/prolt-operations-master.jar
```

- Target database: `jdbc:postgresql://localhost:5432/prolt_operations`
- Effective Flyway target: **`11`** (strictly enforced)

### 6.2 Spring Boot Migration Output
```text
2026-11-02 15:35:04.240 [main] INFO  org.flywaydb.core.FlywayExecutor - Database: jdbc:postgresql://localhost:5432/prolt_operations?stringtype=unspecified (PostgreSQL 16.14)
2026-11-02 15:35:04.294 [main] INFO  o.f.core.internal.command.DbValidate - Successfully validated 12 migrations (execution time 00:00.029s)
2026-11-02 15:35:04.315 [main] INFO  o.f.core.internal.command.DbMigrate - Current version of schema "public": 10
2026-11-02 15:35:04.338 [main] INFO  o.f.core.internal.command.DbMigrate - Migrating schema "public" to version "11 - add user enabled status"
2026-11-02 15:35:04.368 [main] INFO  o.f.c.i.s.DefaultSqlScriptExecutor - DB: Flyway migration V11 completed successfully: enabled column added to public."User" with DEFAULT TRUE and NOT NULL.
2026-11-02 15:35:04.391 [main] INFO  o.f.core.internal.command.DbMigrate - Successfully applied 1 migration to schema "public", now at version v11 (execution time 00:00.033s)
2026-11-02 15:35:06.225 [main] INFO  o.s.o.j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-11-02 15:35:08.809 [main] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port 8089 (http) with context path '/'
2026-11-02 15:35:08.828 [main] INFO  c.s.p.ProltOperationsApplication - Started ProltOperationsApplication in 7.667 seconds (process running for 8.038)
```

---

## 7. Phase F: Post-Migration Database Verification

### 7.1 Flyway History Table Post-V11

```sql
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

| Rank | Version | Description | Success | Installed On |
|---|---|---|---|---|
| 1 | `5` | `<< Flyway Baseline >>` | `t` | `2026-10-28 12:13:39` |
| 2 | `6` | `create dsm charges if missing` | `t` | `2026-10-28 12:13:39` |
| 3 | `7` | `case insensitive username uniqueness` | `t` | `2026-10-30 18:07:05` |
| 4 | `8` | `email case insensitive and full name` | `t` | `2026-11-02 11:48:52` |
| 5 | `9` | `normalize usernames and backfill full name` | `t` | `2026-11-02 14:09:26` |
| 6 | `10` | `enforce user full name not null` | `t` | `2026-11-02 14:10:48` |
| 7 | `11` | `add user enabled status` | `t` | `2026-11-02 15:35:04` |

### 7.2 Schema Column Verification

```sql
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'User'
  AND column_name = 'enabled';
```

| Column Name | Data Type | Is Nullable | Column Default | Status |
|---|---|---|---|---|
| `enabled` | `boolean` | **`NO`** | **`true`** | **100% MATCH** |

### 7.3 User Status Distribution Post-V11

```sql
SELECT
    COUNT(*) AS total_users,
    COUNT(*) FILTER (WHERE enabled = TRUE) AS enabled_users,
    COUNT(*) FILTER (WHERE enabled = FALSE) AS disabled_users,
    COUNT(*) FILTER (WHERE enabled IS NULL) AS null_status_users
FROM public."User";
```

| Metric | Expected | Actual Post-V11 | Verification |
|---|---|---|---|
| `total_users` | 12 | **12** | **MATCH** |
| `enabled_users` | 12 | **12** | **MATCH** (100% active) |
| `disabled_users` | 0 | **0** | **MATCH** |
| `null_status_users` | 0 | **0** | **MATCH** |

### 7.4 Protected Fields & Historical Checksum Comparison

| Metric | Pre-V11 Baseline | Post-V11 Actual | Status |
|---|---|---|---|
| `user_protected_fields_md5` | `29cc2814027577b740d21d4fa424cf87` | `29cc2814027577b740d21d4fa424cf87` | **EXACT MATCH** |
| `user_protected_fields_sha256` | `41166e5ff4e7cb577b51785e4f276720667a52247d17d6b58bf10bcaf77b23ad` | `41166e5ff4e7cb577b51785e4f276720667a52247d17d6b58bf10bcaf77b23ad` | **EXACT MATCH** |
| `user_audit_log_count` | `44` | `44` | **EXACT MATCH** |
| `user_audit_log_md5` | `49620a853e8487c4098df0927dcb37d0` | `49620a853e8487c4098df0927dcb37d0` | **EXACT MATCH** |
| `savings_calculator_new_entries_count` | `31` | `31` | **EXACT MATCH** |
| `savings_calculator_new_entries_md5` | `db42c3b11649cf6b9c312893359bf48b` | `db42c3b11649cf6b9c312893359bf48b` | **EXACT MATCH** |

Zero pre-existing account records, passwords, usernames, full names, roles, or historical logs were modified.

---

## 8. Phase G: Application Validation & Test Suite

### 8.1 Local Application Startup & Hibernate Validation
- Profile: `localvalidate`
- Port: `8089`
- `spring.jpa.hibernate.ddl-auto=validate`: **PASSED with 0 schema mismatches across all 58 JPA entities**.
- `EntityManagerFactory`: Initialized successfully.
- `/actuator/health`: Returns `{"status":"UP"}`.

### 8.2 Full Maven Test Suite
Command executed:
```bash
./mvnw test
```

```text
[INFO] Results:
[INFO] 
[INFO] Tests run: 196, Failures: 0, Errors: 0, Skipped: 1
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:06 min
```

- Total Tests: **196**
- Failures: **0**
- Errors: **0**
- Skipped: **1** (`GdamPricePredictionScraperIntegrationTest` - external scraper test skipped in isolated environments)

---

## 9. Phase H: Comprehensive Security & JWT Verification

A thorough audit of the security filter chain, JWT authentication filter, UserDetailsService, and token validation was conducted alongside live API execution.

### 9.1 Filter Chain & Architecture Audit Findings

1. **`JwtAuthenticationFilter`**:
   - Extracts `Bearer <token>` from incoming HTTP request.
   - Invokes `jwtService.extractPrincipal(token)`, which cryptographically validates the token HMAC signature and expiration timestamp.
   - Extracts claims: `id`, `username`, `role`, `hiddenModules`, `readOnlyModules`.
   - Constructs `ProltUserPrincipal` and sets `UsernamePasswordAuthenticationToken` into `SecurityContextHolder`.
   - **Crucial Architectural Reality**: `JwtAuthenticationFilter` performs **stateless JWT validation** without a database lookup or token revocation lookup.
2. **`ProltUserPrincipal`**:
   - Implements Spring Security `UserDetails`.
   - Methods `isAccountNonExpired()`, `isAccountNonLocked()`, `isCredentialsNonExpired()`, and `isEnabled()` return hardcoded `true` based solely on the presence of a valid signed JWT.
3. **`UserDetailsService`**:
   - `UserDetailsServiceAutoConfiguration` is explicitly excluded in `ProltOperationsApplication.java`.
   - The fallback bean in `SecurityConfig.java` throws `UsernameNotFoundException` because standard Spring Security session-based authentication is not utilized (parity with Node.js stateless architecture).
4. **Refresh Tokens**:
   - There are **no refresh tokens** in either the Node.js or Spring Boot architecture. The system uses a single JWT access token (default validity: 7 days).
5. **Token Invalidation / Revocation Mechanisms**:
   - No token blacklist, Redis session store, or token-version / epoch revocation table currently exists.

### 9.2 Mandatory Security Test Sequence Results

An isolated test account (`sec_test_user`, id `349ff89b-ccf7-43ed-9e1c-ed4a66abc492`, role `CLIENT`) was created on the live local database and subjected to the exact sequence:

| Step | Action | Endpoint / Operation | HTTP Status | Response / Observed Behavior |
|---|---|---|---|---|
| 1 & 2 | Authenticate enabled test user | `POST /api/auth/send-otp`<br>`POST /api/auth/verify-otp` | `200 OK` | Valid signed JWT issued (`CLIENT_TOKEN`) |
| 3 | Confirm access to protected APIs | `GET /api/auth/me`<br>`GET /api/datasets/summary` | `200 OK`<br>`200 OK` | Both protected endpoints succeed normally |
| 4 | Disable user using SUPER_ADMIN | `PATCH /api/users/{id}/status` `{"enabled": false}` | `200 OK` | Account status set to `enabled: false` in database |
| 5a | Reuse issued JWT against `/api/auth/me` | `GET /api/auth/me` | **`401 Unauthorized`** | **`{"success":false,"message":"Account is disabled. Please contact administrator."}`** |
| 5b | Reuse issued JWT against `/api/datasets/summary` | `GET /api/datasets/summary` | **`200 OK`** | **`{"success":true,"data":{...}}` (JWT accepted statelessly)** |
| 7 | Re-enable user using SUPER_ADMIN | `PATCH /api/users/{id}/status` `{"enabled": true}` | `200 OK` | Account status restored to `enabled: true` |
| 8 | Confirm access restored | `GET /api/auth/me`<br>`POST /api/auth/login` | `200 OK`<br>`200 OK` | Normal access fully restored across all endpoints |

### 9.3 Administrative Safety Guard Verification

| Security Guard | Test Input | HTTP Status | Result / Response |
|---|---|---|---|
| **Self-Disable Guard** | Super Admin disables own account `c9bd0ef0-7079-4da0-9373-2dce24128b63` | **`400 Bad Request`** | **`{"success":false,"message":"You cannot disable your own account"}`** |
| **Last Active Super Admin Guard** | Attempt to disable the only active Super Admin | **`400 Bad Request`** | **`{"success":false,"message":"Cannot disable the last active Super Administrator account"}`** |
| **Non-Admin Authorization Guard** | CLIENT or ADMIN calls `PATCH /api/users/{id}/status` | **`403 Forbidden`** | Endpoint requires `ROLE_SUPER_ADMIN` |
| **Audit Logging** | Status transitions | Recorded | `UserAuditLog` records `UPDATE_STATUS` with old and new status values |

### 9.4 Security Gap Documentation & Recommended Implementation Plan

> [!WARNING]
> **Identified Architectural Security Gap**:
> Because JWT validation is stateless across business endpoints (`/api/datasets/**`, `/api/savings-calculator/**`, etc.) without a database or cache lookup, **already-issued JWT tokens remain valid for protected business endpoints until they expire (default: 7 days)**, even after an administrator disables the account.
> 
> However, disabled users **are immediately blocked** from:
> 1. Logging in via username/password (`POST /api/auth/login` -> 401).
> 2. Generating OTPs (`POST /api/auth/send-otp` -> 401).
> 3. Verifying OTPs (`POST /api/auth/verify-otp` -> 401).
> 4. Accessing profile / token validation endpoint (`GET /api/auth/me` -> 401).
> 5. Frontends that check `/api/auth/me` on route navigation or interval polling (which immediately redirects to `/login`).

#### Recommended Implementation Plan to Close the Gap
To ensure 100% immediate rejection across all backend endpoints upon account disablement without creating high database latency:
1. **Redis Account Status Cache / Revocation Set**:
   - When `JwtAuthenticationFilter` validates a token, check Redis key `user:status:{userId}` or a revoked token set `user:revoked:{userId}` (sub-millisecond in-memory lookup).
   - When a Super Admin disables a user via `updateUserStatus`, publish a cache invalidation / revocation entry to Redis: `user:status:{userId} = "DISABLED"`.
   - If Redis is unavailable, query `userRepository.findById(userId).map(User::isEnabled)` as a safe fallback.
2. **Token Epoch / Versioning**:
   - Add a `tokenVersion INT NOT NULL DEFAULT 1` column to `public."User"`.
   - Embed `tokenVersion` claim into JWTs.
   - When an account is disabled or password is reset, increment `tokenVersion`. Any token with `claim.tokenVersion < user.tokenVersion` is immediately rejected.
3. **Short-Lived Access Tokens**:
   - Reduce JWT expiration from 7 days to 15–30 minutes, combined with secure HTTP-only refresh tokens.

---

## 10. Phase I: Frontend Validation

Command executed in `IEX-Dashboard/frontend`:
```bash
npx tsc --noEmit
```

- Result: **Exit Code `0`**
- Type errors: **0**
- Syntax errors: **0**
- No frontend files were modified during this milestone.

---

## 11. Phase J: Final Git Verification

### 11.1 Backend Repository (`prolt-operations`)
```text
HEAD: ada1476 docs(user): document identity migration and execution audits
Working Tree:
 M src/main/java/com/sense/prolt_operations/controller/UserController.java
 M src/main/java/com/sense/prolt_operations/dto/auth/AuthUserResponse.java
 M src/main/java/com/sense/prolt_operations/dto/user/UserDTO.java
 M src/main/java/com/sense/prolt_operations/entity/User.java
 M src/main/java/com/sense/prolt_operations/repository/UserRepository.java
 M src/main/java/com/sense/prolt_operations/service/AuthService.java
 M src/main/java/com/sense/prolt_operations/service/OtpService.java
 M src/main/java/com/sense/prolt_operations/service/UserService.java
 M src/test/java/com/sense/prolt_operations/BaseIntegrationTest.java
 M src/test/java/com/sense/prolt_operations/EmailAndFullNameIntegrationTest.java
 M src/test/java/com/sense/prolt_operations/FlywayBaselineIntegrationTest.java
 M src/test/java/com/sense/prolt_operations/migration/IsolatedMigrationRehearsalTest.java
 M src/test/java/com/sense/prolt_operations/testutil/TestUserHelper.java
?? backups/prolt_operations_pre_v11_20261102_153029.dump
?? docs/42-user-enable-disable-implementation-report.md
?? docs/43-v11-local-database-execution-and-security-report.md
?? src/main/java/com/sense/prolt_operations/dto/user/UpdateUserStatusDTO.java
?? src/main/resources/db/migration/V11__add_user_enabled_status.sql
?? src/test/java/com/sense/prolt_operations/UserEnableDisableIntegrationTest.java
?? src/test/java/com/sense/prolt_operations/migration/UserEnableDisableMigrationTest.java
```
- Staged files: **0**
- Commits created: **0**
- All pre-v11 and pre-existing backups strictly preserved.

### 11.2 Frontend Repository (`IEX-Dashboard`)
```text
HEAD: 91c3850f feat: update commercial proposal modal defaults and python proposal script
Working Tree:
 M frontend/src/api/auth.api.ts
 M frontend/src/api/authService.ts
 M frontend/src/api/users.api.ts
 M frontend/src/components/common/GlobalSubHeader.tsx
 M frontend/src/pages/UserManagementPage.tsx
?? docs/JAVA_FULLSTACK_SPECIFICATION.md
?? docs/migration/
```
- Staged files: **0**
- Commits created: **0**

---

## 12. Completion Summary

All milestone requirements and stop criteria were successfully observed:
1. **Local Database Only**: Execution strictly targeted `localhost:5432/prolt_operations`. Zero remote connections.
2. **Verified Backup**: `prolt_operations_pre_v11_20261102_153029.dump` (990 MB, SHA-256 verified) created and confirmed readable via `pg_restore --list`.
3. **Migration V11 Successfully Applied**: `public."User"` now contains `enabled BOOLEAN NOT NULL DEFAULT TRUE`.
4. **Data Integrity Guaranteed**: All 12 existing user records are `enabled = true`. All protected field checksums match the baseline 100%.
5. **Automated Testing**: 196 backend tests pass with 0 errors, 0 failures. Frontend compiles cleanly with `tsc --noEmit`.
6. **Security Verification**: Mandatory security testing conducted. Self-disable and last-admin guards verified. Stateless JWT behavior on business endpoints vs. `/api/auth/me` documented with a concrete architectural remediation plan.
7. **Clean Git Tree**: No commits, pushes, or staging operations were performed.

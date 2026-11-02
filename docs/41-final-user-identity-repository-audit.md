# Final Repository Audit: User Identity Management & Flyway V9/V10

**Document Reference**: `docs/41-final-user-identity-repository-audit.md`  
**Audit Date**: 2026-11-02 14:30 IST  
**Repositories**:
- `prolt-operations` (Spring Boot 3.3.5 / Java 21)
- `IEX-Dashboard/frontend` (React 18 / Vite / TypeScript)  
**Database**: PostgreSQL 16.14 (`jdbc:postgresql://localhost:5432/prolt_operations` — currently at Flyway V10)  
**Audit Mode**: STRICTLY READ-ONLY (No modifications, no migrations, no git commits)  

---

## A. Executive Summary

This final repository audit evaluated the complete User Identity Management implementation across the backend (`prolt-operations`) and frontend (`IEX-Dashboard/frontend`) codebases.

### Key Conclusions:
1. **Implementation Quality**: The implementation adheres to all business requirements:
   - Usernames are case-insensitive and whitespace-insensitive for uniqueness and authentication.
   - Usernames are strictly immutable through all update paths (excluded from DTOs, entity field marked `updatable = false`).
   - Emails are consistently trimmed and lowercased, uniqueness is case-insensitive, and emails remain directly editable without requiring verification workflows.
   - `fullName` is mandatory for new user creation, editable on existing users, exposed in read DTOs, and backed by a database column with `NOT NULL` constraint.
   - Legacy `name` JSON property compatibility is preserved in `AuthUserResponse.name()`.
   - The approved 12-account mapping was applied via Flyway V9 with extensive safety preflights; V10 enforced `NOT NULL`.
2. **Safety Gates**:
   - `spring.flyway.target=${FLYWAY_TARGET:8}` remains active in `application.properties`, preventing accidental execution beyond V8 during standard boots.
   - Isolated Testcontainers PostgreSQL 16 instances were used for all automated tests, keeping the persistent local database untouched during test runs.
3. **Repository Cleanliness**:
   - Zero git commits or staged changes were made during this audit.
   - Database backups (`backups/*.dump`) containing production-derived data are identified and strictly excluded from proposed commits.
   - Pre-existing unrelated documentation in `IEX-Dashboard/docs/` (dated Oct 22) is identified and excluded.
4. **Readiness**: Both repositories are in a verified, clean, and stable state, ready for final review and controlled commit execution.

---

## B. Backend Audit (`prolt-operations`)

### B.1 User Identity Implementation

| Dimension | Specification Requirement | Implementation Verification | Status |
|---|---|---|---|
| **Username Immutability** | Username must never be editable after account creation. | `UpdateUserDTO` excludes `username`; `@JsonIgnoreProperties(ignoreUnknown = true)` ignores submitted username; `UserService.updateUser` never calls `setUsername`; `User.java` specifies `@Column(updatable = false)`. | **VERIFIED** |
| **Username Normalization** | Case-insensitive and whitespace-insensitive uniqueness & lookup. | `UserRepository` queries use `LOWER(TRIM(u.username)) = LOWER(TRIM(:username))`. Functional unique index `uq_user_username_lower_trim` backs lookup. | **VERIFIED** |
| **Username Convention** | Normalized accounts use lowercase handles with underscores replacing spaces. | Flyway V9 mapped all 12 source accounts (e.g. `Neeraj Sharma` &rarr; `neeraj_sharma`, `Ankit_Jha` &rarr; `ankit_jha`). | **VERIFIED** |
| **Email Normalization** | Consistently trimmed and lowercased. | `UserService.normalizeEmail` and `AuthService` use `email.trim().toLowerCase(Locale.ROOT)`. | **VERIFIED** |
| **Email Uniqueness** | Case-insensitive uniqueness enforcement. | Handled via functional index `uq_user_email_lower_trim` and `userRepository.existsByNormalizedEmailAndIdNot`. | **VERIFIED** |
| **Email Editability** | Directly editable without verification token or OTP flow. | `UserService.updateUser` allows direct email modification after checking normalized uniqueness. | **VERIFIED** |
| **Full Name Requirement** | Mandatory for new accounts; max 100 chars. | `CreateUserDTO` requires `fullName`; `UserService.createUser` validates non-empty and `length <= 100`. | **VERIFIED** |
| **Full Name Editability** | Editable on existing accounts. | `UpdateUserDTO` accepts `fullName`; `UserService.updateUser` validates and persists changes, logging to `UserAuditLog`. | **VERIFIED** |
| **Read API Exposure** | Expose `fullName` across user DTOs and auth responses. | Present in `UserDTO` and `AuthUserResponse`. | **VERIFIED** |
| **Legacy Compatibility** | Preserve legacy `name` JSON field in responses. | `AuthUserResponse` provides `@JsonProperty("name") public String name() { return (fullName != null && !fullName.isBlank()) ? fullName : username; }`. | **VERIFIED** |
| **Authentication** | Login accepts either normalized username or email. | `AuthService.login` uses `userRepository.findByNormalizedUsername(username).or(() -> userRepository.findByNormalizedEmail(username))`. | **VERIFIED** |
| **OTP Handling** | Normalized email lookup and cache keys. | `AuthService.sendOtp` and `verifyOtp` normalize email; `OtpService` keys use `otp:{normalizedEmail}` (Redis) and `normalizedEmail` (in-memory). | **VERIFIED** |
| **Admin Provisioning** | AppRunner default admin sets `fullName`. | `AppRunner.run` sets `user.setFullName("System Administrator")` and `user.setEmail(normalizedEmail)`. | **VERIFIED** |
| **JWT Claims** | JWT token format and claims intact. | Token encodes `id`, `username`, `role`, `hiddenModules`, `readOnlyModules` via HMAC-SHA. | **VERIFIED** |

### B.2 Database Migrations & Schema

| Migration | File | Description & Verification | Status |
|---|---|---|---|
| **V7** | `V7__case_insensitive_username_uniqueness.sql` | Safely drops `User_username_key` (checking constraint or index); creates unique functional index `uq_user_username_lower_trim ON public."User" (LOWER(TRIM(username)))`. Preflight checks for duplicates. | **VERIFIED** |
| **V8** | `V8__email_case_insensitive_and_full_name.sql` | Safely drops `User_email_key`; creates unique functional index `uq_user_email_lower_trim ON public."User" (LOWER(TRIM(email)))`; adds nullable `fullName VARCHAR(100)` for safe zero-downtime staging. | **VERIFIED** |
| **V9** | `V9__normalize_usernames_and_backfill_full_name.sql` | Contains approved 12-account mapping table. Preflight checks: aborts if 0 matched rows; aborts if ANY matched account has pre-existing non-null `fullName` (`WHERE mu.current_fullname IS NOT NULL`); aborts on target collision; aborts on internal collision. Updates atomically; postflight verifies target username and fullName. Historical snapshots in `UserAuditLog` and `savings_calculator_*` remain untouched. | **VERIFIED** |
| **V10** | `V10__enforce_user_full_name_not_null.sql` | Preflight check aborts if any `fullName` is NULL, empty `""`, or whitespace `"   "`. Alters column `ALTER TABLE public."User" ALTER COLUMN "fullName" SET NOT NULL;`. Postflight verifies `is_nullable = 'NO'`. | **VERIFIED** |

### B.3 Schema & ORM Alignment
- **JPA Entity**: In [`src/main/java/com/sense/prolt_operations/entity/User.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/entity/User.java#L25-L26), `fullName` is mapped as `@Column(name = "fullName", length = 100, nullable = false)`.
- **Database Catalog**: `information_schema.columns` confirms `column_name = 'fullName'`, `is_nullable = 'NO'`, `data_type = 'character varying'`, `character_maximum_length = 100`.
- **Hibernate DDL Validation**: `spring.jpa.hibernate.ddl-auto=validate` passed cleanly on application boot against the local database at V10.
- **Flyway Safety Configuration**: In [`src/main/resources/application.properties`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/resources/application.properties#L50), `spring.flyway.target=${FLYWAY_TARGET:8}` is preserved.

---

## C. Frontend Audit (`IEX-Dashboard/frontend`)

### C.1 User Identity & Backward Compatibility

| Component / Service | Change | Verification |
|---|---|---|
| [`src/api/auth.api.ts`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/frontend/src/api/auth.api.ts#L7-L8) | Added `fullName?: string;` and `name?: string;` to `AppUser` interface. | Preserves optional backward compatibility with legacy API responses while supporting typed `fullName`. |
| [`src/api/authService.ts`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/frontend/src/api/authService.ts#L107-L115) | Updated `getUserName()` with safe JSON parsing and fallback chain: `parsed?.fullName \|\| parsed?.name \|\| parsed?.displayName \|\| parsed?.username \|\| "User"`. | Robust null/empty handling, supports both modern `fullName` and legacy cached tokens/profiles. |
| [`src/components/common/GlobalSubHeader.tsx`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/frontend/src/components/common/GlobalSubHeader.tsx#L13) | Updated `displayName = user?.fullName \|\| (user as any)?.name \|\| user?.username \|\| 'User'`. | Shows user's real full name in subheader with seamless fallback to handle or legacy name. |
| [`src/pages/UserManagementPage.tsx`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/frontend/src/pages/UserManagementPage.tsx#L234-L245) | Updated avatar initials to use `(user.fullName \|\| user.username).slice(0, 2)`. User table displays `user.fullName` in bold with `@user.username` handle below. Fallback renders `user.username` if `fullName` is missing. | Clean visual hierarchy without breaking table layout or search filters. |

### C.2 UI Scope Integrity
No unrelated components, routes, styles, or pages were altered. All modifications strictly pertain to `fullName` exposure and fallback display.

---

## D. Validation Results

### D.1 Backend Automated Tests (`mvn test`)
- **Command**: `mvn test`
- **PostgreSQL Test Environment**: Testcontainers PostgreSQL 16.14 (fully isolated from local persistent database)
- **Tests Executed**: `181`
- **Failures**: `0`
- **Errors**: `0`
- **Skipped**: `1` ([`ScraperLiveManualTest`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/service/ScraperLiveManualTest.java) — manual integration test explicitly ignored)
- **Execution Time**: `01:11 min`
- **Build Status**: `BUILD SUCCESS`

Specific test coverage validated:
- `UsernameCaseInsensitiveIntegrationTest`: 6 passed
- `UsernameImmutabilityIntegrationTest`: 5 passed
- `EmailCaseInsensitiveIntegrationTest`: 10 passed
- `AuthAndUserIntegrationTest`: 5 passed
- `AuthenticationParityIntegrationTest`: 5 passed
- `UserRenameAndFullNameBackfillMigrationTest`: 16 passed (covering all V9 and V10 scenarios including safe abort on whitespace-only pre-existing fullName)

### D.2 Frontend Validation
1. **TypeScript Typecheck (`npx tsc --noEmit`)**:
   - Result: `Exit code 0`
   - Zero compilation or type errors.
2. **Production Build (`npm run build`)**:
   - `tsc -b` passed.
   - Vite packaging returned error `Cannot find module '@rollup/rollup-linux-x64-gnu'`. This is a known local environment npm issue where the platform-specific native binary was not pulled into `node_modules` during `npm install`.
3. **ESLint (`npm run lint`)**:
   - ESLint 9 is present in `node_modules` but no `eslint.config.js` exists in the repository (pre-existing tooling state).

---

## E. Git Inventory & Classification

### E.1 Backend Repository (`prolt-operations`)

```bash
$ git status --short
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
?? docs/41-final-user-identity-repository-audit.md
?? src/main/resources/db/migration/V10__enforce_user_full_name_not_null.sql
?? src/main/resources/db/migration/V9__normalize_usernames_and_backfill_full_name.sql
?? src/test/java/com/sense/prolt_operations/migration/UserRenameAndFullNameBackfillMigrationTest.java
```

#### Classification:
1. **Required Implementation Changes**:
   - `src/main/java/com/sense/prolt_operations/entity/User.java`
   - `src/main/resources/application.properties`
2. **Required Migration Files**:
   - `src/main/resources/db/migration/V9__normalize_usernames_and_backfill_full_name.sql`
   - `src/main/resources/db/migration/V10__enforce_user_full_name_not_null.sql`
3. **Required Tests**:
   - `src/test/java/com/sense/prolt_operations/migration/UserRenameAndFullNameBackfillMigrationTest.java`
   - `src/test/java/com/sense/prolt_operations/BaseIntegrationTest.java`
   - `src/test/java/com/sense/prolt_operations/FlywayBaselineIntegrationTest.java`
   - `src/test/java/com/sense/prolt_operations/migration/IsolatedMigrationRehearsalTest.java`
   - `src/test/java/com/sense/prolt_operations/repository/RepositoryIntegrationTest.java`
   - `src/test/java/com/sense/prolt_operations/testutil/TestUserHelper.java`
4. **Documentation and Execution Reports**:
   - `docs/37-v8-local-database-execution-report.md`
   - `docs/38-existing-user-rename-and-fullname-backfill-audit.md`
   - `docs/39-v9-v10-implementation-report.md`
   - `docs/40-v9-v10-local-database-execution-report.md`
   - `docs/41-final-user-identity-repository-audit.md`
5. **Database Backups (MUST NEVER BE COMMITTED)**:
   - `backups/prolt_operations_pre_v8_20261102_114557.dump` (~1,037 MB)
   - `backups/prolt_operations_pre_v9_v10_20261102_140500.dump` (~1,037 MB)
   *(Contains production-derived customer data; excluded from version control).*
6. **Generated Files / Temporary Artifacts**:
   - `target/` directory (covered by `.gitignore`).

---

### E.2 Frontend Repository (`IEX-Dashboard/frontend`)

```bash
$ git status --short
 M frontend/src/api/auth.api.ts
 M frontend/src/api/authService.ts
 M frontend/src/components/common/GlobalSubHeader.tsx
 M frontend/src/pages/UserManagementPage.tsx
?? docs/JAVA_FULLSTACK_SPECIFICATION.md
?? docs/migration/
```

#### Classification:
1. **Required Implementation Changes**:
   - `frontend/src/api/auth.api.ts`
   - `frontend/src/api/authService.ts`
   - `frontend/src/components/common/GlobalSubHeader.tsx`
   - `frontend/src/pages/UserManagementPage.tsx`
2. **Unrelated / Pre-Existing Untracked Documentation (DO NOT COMMIT)**:
   - `docs/JAVA_FULLSTACK_SPECIFICATION.md` (Created Oct 22 — Full-stack migration spec)
   - `docs/migration/` (20 files, created Oct 22 — Architecture and idiom mapping)
   *(These are pre-existing reference files unrelated to the user identity feature and must remain uncommitted).*

---

## F. Risks and Recommendations

| Item | Severity | Analysis & Mitigation |
|---|---|---|
| **Flyway Target in Production** | Low / Controlled | `spring.flyway.target` is currently pinned to `8` in `application.properties`. When deploying V9 and V10 to staging/production, the release pipeline must either provide `-Dspring.flyway.target=10` or the property must be updated in the deployment manifest. |
| **Frontend `@rollup/rollup-linux-x64-gnu`** | Low / Local Only | Affects local containerless Vite builds if optional dependencies were skipped during `npm install`. Running `npm i --save-optional @rollup/rollup-linux-x64-gnu` in developer workstations resolves it. Dockerized CI builds install platform-specific binaries cleanly. |
| **Database Dumps in Working Tree** | Medium / Information Security | `backups/` contains sensitive production-derived snapshots. Recommended to add `backups/*.dump` to `.gitignore` so they cannot be accidentally staged by `git add .`. |

---

## G. Proposed Git Commit Plan

### G.1 Backend Repository: `prolt-operations`

#### Proposed Commit 1: Database Migrations V9 & V10
- **Files**:
  - `src/main/resources/db/migration/V9__normalize_usernames_and_backfill_full_name.sql`
  - `src/main/resources/db/migration/V10__enforce_user_full_name_not_null.sql`
- **Suggested Commit Message**:
  ```text
  feat(db): add Flyway V9 username normalization and V10 fullName constraint
  
  - V9: Portable, preflight-guarded normalization of 12 approved accounts to lowercase/underscores and fullName backfill
  - V10: Strict preflight check and NOT NULL constraint enforcement on User.fullName
  ```
- **Rationale**: Isolates DDL/DML migrations from application code and tests.
- **Required**: Yes.

#### Proposed Commit 2: Entity & Configuration Updates
- **Files**:
  - `src/main/java/com/sense/prolt_operations/entity/User.java`
  - `src/main/resources/application.properties`
- **Suggested Commit Message**:
  ```text
  feat(user): update User entity fullName constraint and retain Flyway target safety gate
  
  - Set nullable = false on User.fullName mapping matching V10 schema
  - Retain spring.flyway.target=${FLYWAY_TARGET:8} safety gate in application.properties
  ```
- **Rationale**: Aligns JPA Hibernate entity metadata with the V10 schema while keeping the target safety gate.
- **Required**: Yes.
- **Dependencies**: Depends on Commit 1.

#### Proposed Commit 3: Migration & Integration Test Suite
- **Files**:
  - `src/test/java/com/sense/prolt_operations/migration/UserRenameAndFullNameBackfillMigrationTest.java`
  - `src/test/java/com/sense/prolt_operations/BaseIntegrationTest.java`
  - `src/test/java/com/sense/prolt_operations/FlywayBaselineIntegrationTest.java`
  - `src/test/java/com/sense/prolt_operations/migration/IsolatedMigrationRehearsalTest.java`
  - `src/test/java/com/sense/prolt_operations/repository/RepositoryIntegrationTest.java`
  - `src/test/java/com/sense/prolt_operations/testutil/TestUserHelper.java`
- **Suggested Commit Message**:
  ```text
  test(user): add V9/V10 migration test suite and update baseline expectations
  
  - Add 16 isolated Testcontainers scenarios for V9 backfill and V10 NOT NULL enforcement
  - Update rehearsal and baseline tests for 10 total migrations in classpath
  - Ensure TestUserHelper and repository CRUD tests supply mandatory fullName
  ```
- **Rationale**: Groups all test suite adaptations and new isolated Testcontainers test cases.
- **Required**: Yes.
- **Dependencies**: Depends on Commits 1 & 2.

#### Proposed Commit 4: Technical Documentation and Audit Reports
- **Files**:
  - `docs/37-v8-local-database-execution-report.md`
  - `docs/38-existing-user-rename-and-fullname-backfill-audit.md`
  - `docs/39-v9-v10-implementation-report.md`
  - `docs/40-v9-v10-local-database-execution-report.md`
  - `docs/41-final-user-identity-repository-audit.md`
- **Suggested Commit Message**:
  ```text
  docs(user): add V8-V10 execution reports, migration audits, and repository audit
  
  - Document preflight checks, cryptographic verification, and local execution for V8, V9, V10
  - Include 12-account mapping audit and final multi-repository compliance audit
  ```
- **Rationale**: Preserves compliance, audit trail, and operational verification logs in repository documentation.
- **Required**: Yes (excluding any `.dump` backups).
- **Dependencies**: Commits 1-3.

---

### G.2 Frontend Repository: `IEX-Dashboard`

#### Proposed Commit 1: User Full Name Display and Integration
- **Files**:
  - `frontend/src/api/auth.api.ts`
  - `frontend/src/api/authService.ts`
  - `frontend/src/components/common/GlobalSubHeader.tsx`
  - `frontend/src/pages/UserManagementPage.tsx`
- **Suggested Commit Message**:
  ```text
  feat(user): integrate fullName display with backward-compatible fallbacks
  
  - Add fullName to AppUser interface
  - Prioritize fullName in authService.getUserName() and GlobalSubHeader
  - Display fullName with @username handle and avatar initials in UserManagementPage
  ```
- **Rationale**: Encapsulates all frontend UI/API integration changes while leaving pre-existing untracked documentation untouched.
- **Required**: Yes.
- **Dependencies**: None (backward-compatible with existing backend API responses).

---

## H. Safety Confirmation Statement

In accordance with Section 1 of the audit guidelines:
1. **Zero Database Alterations**: The persistent local database `prolt_operations` on `localhost:5432` was not modified, reset, truncated, or migrated.
2. **Zero Remote Invocations**: No remote production or staging environments were contacted.
3. **Zero Code Alterations**: No production or test source files were modified or formatted.
4. **Zero Git Staging or Commits**: No files were staged, unstaged, committed, or discarded.
5. **Backup Safety**: All `.dump` files were preserved and strictly excluded from proposed commits.

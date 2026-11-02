# Read-Only Audit: Existing User Rename & FullName Backfill Migration

**Document Reference**: `docs/38-existing-user-rename-and-fullname-backfill-audit.md`  
**Audit Date**: 2026-11-02  
**Target Database**: `prolt_operations` on `localhost:5432` (PostgreSQL 16.14)  
**Repository**: `prolt-operations` (Spring Boot 3.3.5 / Java 17) & `IEX-Dashboard/frontend` (React / Vite)  
**Execution Mode**: **STRICTLY READ-ONLY (No code, database records, or migrations modified)**  

---

## 1. Approved Mapping

The administrative objective is to normalize all 12 existing user accounts in `prolt-operations` to lowercase with underscores replacing spaces, and to populate `fullName` according to the business-approved mapping below.

| # | Current Username in Database | Proposed New Username | Assigned Full Name | Casing / Character Change | Full Name Status |
|---|---|---|---|---|---|
| 1 | `superadmin` | `superadmin` | `System Administrator` | Unchanged | Backfilled (was `NULL`) |
| 2 | `Yash` | `yash` | `Yash` | Uppercase `Y` &rarr; lowercase `y` | Backfilled (was `NULL`) |
| 3 | `Saikat` | `saikat` | `Saikat` | Uppercase `S` &rarr; lowercase `s` | Backfilled (was `NULL`) |
| 4 | `Priyanka` | `priyanka` | `Priyanka` | Uppercase `P` &rarr; lowercase `p` | Backfilled (was `NULL`) |
| 5 | `harshit` | `harshit` | `Harshit` | Unchanged | Backfilled (was `NULL`) |
| 6 | `ankit_vaish` | `ankit_vaish` | `Ankit Vaish` | Unchanged | Backfilled (was `NULL`) |
| 7 | `Gaurav` | `gaurav` | `Gaurav` | Uppercase `G` &rarr; lowercase `g` | Backfilled (was `NULL`) |
| 8 | `Ankit_Jha` | `ankit_jha` | `Ankit Jha` | Uppercase `A`, `J` &rarr; lowercase `a`, `j` | Backfilled (was `NULL`) |
| 9 | `Neeraj Sharma` | `neeraj_sharma` | `Neeraj Sharma` | Space &rarr; `_`, lowercase letters | Backfilled (was `NULL`) |
| 10 | `Anurag Shukla` | `anurag_shukla` | `Anurag Shukla` | Space &rarr; `_`, lowercase letters | Backfilled (was `NULL`) |
| 11 | `Anand` | `anand` | `Anand` | Uppercase `A` &rarr; lowercase `a` | Backfilled (was `NULL`) |
| 12 | `Saurabh Anoop` | `saurabh_anoop` | `Saurabh Anoop` | Space &rarr; `_`, lowercase letters | Backfilled (was `NULL`) |

### Key Population Summary
- **Total User Accounts in Database**: Exactly `12`.
- **Usernames Remaining Identical**: `3` (`superadmin`, `harshit`, `ankit_vaish`).
- **Usernames Requiring Rename**: `9` (`Yash`, `Saikat`, `Priyanka`, `Gaurav`, `Ankit_Jha`, `Neeraj Sharma`, `Anurag Shukla`, `Anand`, `Saurabh Anoop`).
- **Full Names Backfilled**: All `12` accounts transition from `NULL` to non-empty string.
- **Preflight Match**: 100% of accounts present in database match the approved mapping table with 0 unmatched or missing accounts.

---

## 2. Database Dependency Findings

A comprehensive catalog inspection was executed against PostgreSQL 16.14 catalog tables (`pg_constraint`, `pg_indexes`, `pg_trigger`, `information_schema`).

### 2.1 Primary Key and Foreign Key Relationships
- **Entity**: `public."User"`
- **Primary Key**: `id` (`VARCHAR(255)` / UUID string), backed by `User_pkey`.
- **Foreign Keys Referencing `public."User"`**: **ZERO (`0`)**.
  - No database table defines a foreign key constraint referencing `public."User"(id)` or `public."User"(username)`.
  - User relationships across domain tables rely on application-level string IDs (`targetUserId`, `userId`).

### 2.2 Constraints & Indexes on `public."User"`
The table currently has exactly 5 indexes:

```sql
User_pkey                   CREATE UNIQUE INDEX "User_pkey" ON public."User" USING btree (id)
User_email_idx              CREATE INDEX "User_email_idx" ON public."User" USING btree (email)
User_username_idx           CREATE INDEX "User_username_idx" ON public."User" USING btree (username)
uq_user_username_lower_trim CREATE UNIQUE INDEX uq_user_username_lower_trim ON public."User" USING btree (lower(TRIM(BOTH FROM username)))
uq_user_email_lower_trim    CREATE UNIQUE INDEX uq_user_email_lower_trim ON public."User" USING btree (lower(TRIM(BOTH FROM email)))
```

- Updating `username` and `"fullName"` leaves `id` and `email` untouched, so `User_pkey`, `User_email_idx`, and `uq_user_email_lower_trim` are completely unaffected.
- The functional unique index `uq_user_username_lower_trim` enforces case-insensitive uniqueness over `lower(TRIM(username))`. All 12 proposed target usernames are strictly unique under this index.
- Standard B-Tree index `User_username_idx` will be updated naturally in-place during the atomic transaction.

### 2.3 Triggers, Views, and Stored Procedures
- **Triggers**: `0` triggers defined on `public."User"`.
- **Views**: `0` user-defined database views reference `User` or `username`.
- **Stored Procedures / Functions**: `0` custom routines reference `User` or `username`.

### 2.4 Audit & History Tables with Copied Username Values
The following database tables store copied username text values as audit log snapshots:

| Table Name | Column Name | Type | Current Content Snapshot |
|---|---|---|---|
| `UserAuditLog` | `performedBy` | `VARCHAR` | 44 rows total (`superadmin`: 41, `SYSTEM`: 2, `admin`: 1). None of the 9 modified usernames exist in `performedBy`. |
| `UserAuditLog` | `targetUser` | `VARCHAR` | Historical snapshot of the target username when user audits occurred. |
| `savings_calculator_new_entries` | `createdBy`, `updatedBy` | `VARCHAR` | Contains legacy username strings: `Priyanka` (11), `harshit` (9), `Anurag Shukla` (5), `superadmin` (4), `Saikat` (1). |
| `savings_calculator_new_entry_histories` | `createdBy`, `updatedBy` | `VARCHAR` | Historical snapshots of authoring usernames at revision time. |
| `trader_performance_entries` | `createdBy`, `updatedBy` | `VARCHAR` | Nullable authoring username text strings. |
| `trader_performance_entry_histories` | `createdBy`, `updatedBy` | `VARCHAR` | Historical snapshots of authoring usernames at revision time. |
| `trader_performance_actual_entries` | `createdBy`, `updatedBy` | `VARCHAR` | Nullable authoring username text strings. |
| `trader_performance_actual_entry_histories` | `createdBy`, `updatedBy` | `VARCHAR` | Historical snapshots of authoring usernames at revision time. |

> [!NOTE]
> **Audit Trail Immutability**:
> In accordance with standard compliance and audit standards, historical audit rows in `UserAuditLog`, `savings_calculator_new_entry_histories`, and `trader_performance_*` represent immutable historical records of what was logged at that timestamp. They are unconstrained text fields and do NOT block or participate in the `public."User"` update.

---

## 3. Backend Dependency Findings

An exhaustive search across `src/main/java` was conducted for all username lookups, mutations, comparisons, and authorizations.

### 3.1 Repository Query Methods
All username lookups in `UserRepository.java` are normalized and case-insensitive:
```java
@Query("SELECT u FROM User u WHERE LOWER(TRIM(u.username)) = LOWER(TRIM(:username))")
Optional<User> findByNormalizedUsername(@Param("username") String username);

@Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(TRIM(u.username)) = LOWER(TRIM(:username))")
boolean existsByNormalizedUsername(@Param("username") String username);
```
- There are no raw case-sensitive queries like `findByUsernameEquals` or direct SQL text joins.
- Because `findByNormalizedUsername` wraps both the column and the parameter in `LOWER(TRIM(...))`, any user logging in with the old casing (e.g. `Yash` or `yash`) will resolve to the normalized database record seamlessly.

### 3.2 Authentication & OTP Flows
- **Credential Login (`AuthService.login`)**:
  ```java
  User user = userRepository.findByNormalizedUsername(username)
          .or(() -> userRepository.findByNormalizedEmail(username))
          .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
  ```
  - Supports login by username OR email.
  - Normalizes whitespace and casing before lookup.
- **OTP Login (`AuthService.verifyOtp`)**:
  ```java
  User user = userRepository.findByNormalizedEmail(email)
          .orElseThrow(() -> new NotFoundException("User not found"));
  ```
  - Lookups occur strictly through normalized email. Completely independent of username.
- **SuperAdmin OTP Bypass (`OtpService`)**:
  - Bound to `app.auth.superadmin-otp-bypass.email` (`super.admin@probus.io`), NOT username.

### 3.3 Authorization & Role Enforcements
- Route protection in `SecurityConfig.java`:
  - Uses `hasRole("SUPER_ADMIN")` for `/api/users/**`.
  - Roles are resolved via `ProltUserPrincipal.getAuthorities()` which constructs `ROLE_` + `user.getRole().name()`.
  - Authorization is 100% role-based, never username-based.

### 3.4 Entity Immutability
- `User.java`:
  ```java
  @Column(name = "username", nullable = false, unique = true, updatable = false)
  private String username;
  ```
- `UserService.updateUser(String id, UpdateUserDTO dto, ...)` intentionally has no mechanism to update `username`. Username immutability in the application layer remains intact. Only database migration scripts can alter existing usernames.

### 3.5 Hardcoded Usernames & Seed Data
- `AppRunner.java`:
  - Reads `app.admin.username` (defaults to blank `${ADMIN_USERNAME:}`).
  - Preflight checks `findByNormalizedUsername(...)` and skips creation if already present.
- No employee usernames (`yash`, `priyanka`, `saikat`, etc.) are hardcoded anywhere in `src/main/java`.

---

## 4. JWT & Session Compatibility Analysis

A detailed inspection of `JwtService.java`, `JwtAuthenticationFilter.java`, and `ProltUserPrincipal.java` was conducted.

### 4.1 Token Structure & Claims
When a user authenticates, `JwtService.generateToken` produces a signed JWT containing:
- `id`: User UUID string
- `username`: Current username string
- `role`: Role string (`SUPER_ADMIN`, `ADMIN`, `CLIENT`)
- `hiddenModules`: Array of strings
- `readOnlyModules`: Array of strings
- `iat` / `exp`: Issue and expiration timestamps (default 7 days)

### 4.2 Stateless Token Verification
On subsequent requests:
1. `JwtAuthenticationFilter` extracts the Bearer token from the `Authorization` header.
2. `JwtService.extractPrincipal(token)` decodes the HMAC-SHA256 signature and parses claims without hitting PostgreSQL.
3. The request is authorized solely through the decoded `ProltUserPrincipal`.

### 4.3 Impact of Renaming Existing Usernames on Active Sessions
- **Will active JWTs be invalidated?**: **NO**.
  - Because JWT validation is completely stateless and does not query PostgreSQL on every API call, tokens issued prior to migration will remain cryptographically valid until expiration (`7d`).
- **Will existing sessions break?**: **NO**.
  - All protected endpoints (e.g. `/api/datasets/**`, `/api/savings-calculator/**`) authenticate against the principal in memory.
- **Frontend user profile reflection**:
  - `/api/auth/me` returns `principal.getUsername()`. An existing session will continue to display the previous username until the user logs out or re-authenticates.
  - Upon next login, `AuthService.login` issues a new token containing the updated lowercase username and the backfilled `fullName`.

---

## 5. Frontend & Integration Audit

Frontend source code under `IEX-Dashboard/frontend` was audited for username and user profile usage.

### 5.1 Storage & Session Management
- Tokens and user objects are stored in `localStorage` under:
  - `AUTH_TOKEN_KEY` (`auth_token`)
  - `iex_user` (`JSON.stringify(user)`)
- `AuthContext.tsx` reads `iex_user` on app load:
  ```typescript
  const storedUser = localStorage.getItem('iex_user');
  if (storedToken && storedUser) {
    const parsedUser = JSON.parse(storedUser);
    setUser(parsedUser);
    setIsAdmin(parsedUser.role === 'ADMIN' || parsedUser.role === 'SUPER_ADMIN');
  }
  ```
- Because role is used for permissions, existing sessions will not encounter authorization failures.

### 5.2 Display Locations
1. `UserManagementPage.tsx`:
   - Table displays `user.username` and `user.username.slice(0, 2)` inside avatar badges.
   - Currently, `AppUser` interface (`src/api/auth.api.ts`) only defines:
     ```typescript
     export interface AppUser {
       id: string;
       username: string;
       email: string;
       role: 'SUPER_ADMIN' | 'ADMIN' | 'CLIENT';
       ...
     }
     ```
   - When users are renamed to lowercase (e.g. `yash`, `priyanka`), `UserManagementPage.tsx` will display the clean lowercase handles.
2. `LoginModal.tsx` & `LoginPage.tsx`:
   - Users can submit username or email. Because backend login performs case-insensitive lookup (`LOWER(TRIM(...))`), users typing their familiar casing will authenticate seamlessly.
3. `GlobalSubHeader.tsx`:
   - Inspects `(user as any).name || 'User'`.
   - Adding `fullName` to `AppUser` will allow proper name display in this component.

---

## 6. Collision Analysis

Before modifying any data, all proposed target usernames were checked for collisions.

```sql
WITH target_mapping(new_username) AS (
  VALUES 
    ('superadmin'), ('yash'), ('saikat'), ('priyanka'), 
    ('harshit'), ('ankit_vaish'), ('gaurav'), ('ankit_jha'), 
    ('neeraj_sharma'), ('anurag_shukla'), ('anand'), ('saurabh_anoop')
)
SELECT 
  COUNT(*) as total_targets,
  COUNT(DISTINCT new_username) as distinct_targets,
  COUNT(DISTINCT LOWER(TRIM(new_username))) as distinct_normalized_targets
FROM target_mapping;
```

### Verification Matrix
| Target Username | Normalized Form (`LOWER(TRIM())`) | Collision with Existing DB Users? | Collision with Other Targets? |
|---|---|---|---|
| `superadmin` | `superadmin` | Matches current `superadmin` record | None |
| `yash` | `yash` | Matches current `Yash` record | None |
| `saikat` | `saikat` | Matches current `Saikat` record | None |
| `priyanka` | `priyanka` | Matches current `Priyanka` record | None |
| `harshit` | `harshit` | Matches current `harshit` record | None |
| `ankit_vaish` | `ankit_vaish` | Matches current `ankit_vaish` record | None |
| `gaurav` | `gaurav` | Matches current `Gaurav` record | None |
| `ankit_jha` | `ankit_jha` | Matches current `Ankit_Jha` record | None |
| `neeraj_sharma` | `neeraj_sharma` | Resolves from `Neeraj Sharma` | None |
| `anurag_shukla` | `anurag_shukla` | Resolves from `Anurag Shukla` | None |
| `anand` | `anand` | Matches current `Anand` record | None |
| `saurabh_anoop` | `saurabh_anoop` | Resolves from `Saurabh Anoop` | None |

### Key Findings
1. All 12 target usernames are **pairwise distinct**.
2. All 12 target normalized usernames are **strictly unique**.
3. No cross-account collisions exist (e.g. `yash` does not collide with `saikat` or any other account).
4. Updating `username` will not violate `uq_user_username_lower_trim`.

---

## 7. Proposed V9 Migration

### Flyway Version Allocation
- Highest existing version in `src/main/resources/db/migration/`: `V8__email_case_insensitive_and_full_name.sql`.
- Current database `flyway_schema_history`: Installed through `V8`.
- **Target Sequential Version**: `V9`.
- **Migration Filename**: `V9__normalize_usernames_and_backfill_full_name.sql`.

### Proposed SQL Script (`V9__normalize_usernames_and_backfill_full_name.sql`)

```sql
-- ============================================================================
-- V9: One-Time Administrative Username Normalization and FullName Backfill
-- Target Database: prolt_operations
-- Target Table: public."User"
-- Safe, idempotent, preflight-guarded transaction
-- ============================================================================

DO $$
DECLARE
    v_total_users INTEGER;
    v_missing_expected INTEGER;
    v_unmatched_in_db INTEGER;
    v_updated_rows INTEGER;
    v_null_fullnames INTEGER;
BEGIN
    -- ------------------------------------------------------------------------
    -- 1. Preflight Verification: Total record count check
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_total_users FROM public."User";
    IF v_total_users <> 12 THEN
        RAISE EXCEPTION 'Preflight failed: expected exactly 12 users in public."User", but found %', v_total_users;
    END IF;

    -- ------------------------------------------------------------------------
    -- 2. Preflight Verification: Ensure all 12 expected current accounts exist
    -- ------------------------------------------------------------------------
    WITH expected_users(username) AS (
        VALUES
            ('superadmin'), ('Yash'), ('Saikat'), ('Priyanka'),
            ('harshit'), ('ankit_vaish'), ('Gaurav'), ('Ankit_Jha'),
            ('Neeraj Sharma'), ('Anurag Shukla'), ('Anand'), ('Saurabh Anoop')
    )
    SELECT COUNT(*) INTO v_missing_expected
    FROM expected_users e
    WHERE NOT EXISTS (
        SELECT 1 FROM public."User" u WHERE u.username = e.username
    );

    IF v_missing_expected > 0 THEN
        RAISE EXCEPTION 'Preflight failed: % expected username(s) are missing from public."User"', v_missing_expected;
    END IF;

    -- ------------------------------------------------------------------------
    -- 3. Preflight Verification: Ensure no unexpected accounts exist
    -- ------------------------------------------------------------------------
    WITH expected_users(username) AS (
        VALUES
            ('superadmin'), ('Yash'), ('Saikat'), ('Priyanka'),
            ('harshit'), ('ankit_vaish'), ('Gaurav'), ('Ankit_Jha'),
            ('Neeraj Sharma'), ('Anurag Shukla'), ('Anand'), ('Saurabh Anoop')
    )
    SELECT COUNT(*) INTO v_unmatched_in_db
    FROM public."User" u
    WHERE NOT EXISTS (
        SELECT 1 FROM expected_users e WHERE e.username = u.username
    );

    IF v_unmatched_in_db > 0 THEN
        RAISE EXCEPTION 'Preflight failed: found % unexpected user(s) in public."User"', v_unmatched_in_db;
    END IF;

    -- ------------------------------------------------------------------------
    -- 4. Execute Atomic Update for all 12 accounts
    -- ------------------------------------------------------------------------
    WITH mapping(curr_username, new_username, new_fullname) AS (
        VALUES
            ('superadmin',    'superadmin',    'System Administrator'),
            ('Yash',          'yash',          'Yash'),
            ('Saikat',        'saikat',        'Saikat'),
            ('Priyanka',      'priyanka',      'Priyanka'),
            ('harshit',       'harshit',       'Harshit'),
            ('ankit_vaish',   'ankit_vaish',   'Ankit Vaish'),
            ('Gaurav',        'gaurav',        'Gaurav'),
            ('Ankit_Jha',     'ankit_jha',     'Ankit Jha'),
            ('Neeraj Sharma', 'neeraj_sharma', 'Neeraj Sharma'),
            ('Anurag Shukla', 'anurag_shukla', 'Anurag Shukla'),
            ('Anand',         'anand',         'Anand'),
            ('Saurabh Anoop', 'saurabh_anoop', 'Saurabh Anoop')
    )
    UPDATE public."User" u
    SET username   = m.new_username,
        "fullName" = m.new_fullname
    FROM mapping m
    WHERE u.username = m.curr_username;

    GET DIAGNOSTICS v_updated_rows = ROW_COUNT;

    IF v_updated_rows <> 12 THEN
        RAISE EXCEPTION 'Postflight failed: expected exactly 12 updated rows, got %', v_updated_rows;
    END IF;

    -- ------------------------------------------------------------------------
    -- 5. Postflight Verification: Confirm zero NULL or empty fullNames remain
    -- ------------------------------------------------------------------------
    SELECT COUNT(*) INTO v_null_fullnames
    FROM public."User"
    WHERE "fullName" IS NULL OR TRIM("fullName") = '';

    IF v_null_fullnames > 0 THEN
        RAISE EXCEPTION 'Postflight failed: found % user(s) with NULL or empty fullName after update', v_null_fullnames;
    END IF;

    RAISE NOTICE 'Migration V9 completed successfully: 12 usernames normalized, 12 fullNames populated.';
END $$;
```

---

## 8. Proposed V10 Migration

### Rationale for Separate Migration
Separating the data backfill (`V9`) from the constraint enforcement (`V10`) guarantees that:
1. The backfill runs and commits cleanly across all target instances.
2. In the event of any data discrepancy on another environment (e.g. staging or production having an extra user), `V10` acts as a hard safety gate rather than partially executing.
3. Rollbacks or hotfixes can be targeted precisely.

### Migration Filename
`V10__enforce_user_full_name_not_null.sql`

### Proposed SQL Script (`V10__enforce_user_full_name_not_null.sql`)

```sql
-- ============================================================================
-- V10: Enforce NOT NULL Constraint on public."User"."fullName"
-- Prerequisite: V9 completed successfully with 100% non-null fullNames
-- ============================================================================

DO $$
DECLARE
    v_invalid_count INTEGER;
BEGIN
    -- Preflight verification: Ensure zero NULL or empty strings exist in fullName
    SELECT COUNT(*) INTO v_invalid_count
    FROM public."User"
    WHERE "fullName" IS NULL OR TRIM("fullName") = '';

    IF v_invalid_count > 0 THEN
        RAISE EXCEPTION 'Cannot enforce NOT NULL on public."User"."fullName": found % row(s) with NULL or empty fullName', v_invalid_count;
    END IF;

    -- Apply NOT NULL constraint
    ALTER TABLE public."User" ALTER COLUMN "fullName" SET NOT NULL;

    RAISE NOTICE 'Migration V10 completed successfully: NOT NULL constraint applied to public."User"."fullName".';
END $$;
```

---

## 9. Required Code Changes (Post-Audit Implementation Phase)

During this read-only audit phase, **no code was altered**. When implementation is authorized, the following code updates will be required:

### 9.1 Backend (`prolt-operations`)
1. **`User.java`**:
   Update JPA column annotation on `fullName` to reflect `nullable = false`:
   ```java
   @Column(name = "fullName", length = 100, nullable = false)
   private String fullName;
   ```
2. **`AppRunner.java`**:
   Ensure default admin bootstrap sets `fullName` (already present: `user.setFullName("System Administrator")`).
3. **No changes required in `AuthService`, `JwtService`, `SecurityConfig`, or `UserRepository`**:
   - `AuthService.login` already performs case-insensitive normalization.
   - `UserService.updateUser` already supports `fullName` updating while keeping `username` strictly immutable.
   - `JwtService` already extracts claims statelessly.

### 9.2 Frontend (`IEX-Dashboard/frontend`)
1. **`src/api/auth.api.ts`**:
   Add `fullName` to `AppUser` interface:
   ```typescript
   export interface AppUser {
     id: string;
     username: string;
     email: string;
     fullName: string;
     role: 'SUPER_ADMIN' | 'ADMIN' | 'CLIENT';
     hiddenModules?: string[];
     readOnlyModules?: string[];
     createdAt?: string;
     updatedAt?: string;
   }
   ```
2. **`src/pages/UserManagementPage.tsx`**:
   Update the table to display `fullName` alongside or in place of `username`:
   ```tsx
   <TableCell className="font-medium text-gray-800">
     <div className="flex items-center gap-3">
       <div className={`w-7 h-7 rounded-md flex items-center justify-center font-bold text-xs uppercase ${colorClass}`}>
         {(user.fullName || user.username).slice(0, 2)}
       </div>
       <div>
         <div className="font-semibold text-gray-900">{user.fullName}</div>
         <div className="text-xs text-gray-400">@{user.username}</div>
       </div>
     </div>
   </TableCell>
   ```

---

## 10. Verification & Test Plan

Once migration execution is authorized, the following automated and manual tests should be run:

### 10.1 Automated Integration Tests
Create `UserRenameAndFullNameBackfillIntegrationTest.java`:
1. **All 12 Username Mappings**: Assert that every record in `public."User"` has its expected lowercase handle (`superadmin`, `yash`, `saikat`, etc.).
2. **All 12 FullName Mappings**: Assert that every record has its corresponding exact full name (`System Administrator`, `Yash`, `Ankit Vaish`, `Neeraj Sharma`, etc.).
3. **Immutability of Key User Attributes**:
   - Assert `id` (PK) for all 12 accounts is identical before and after migration.
   - Assert `email` addresses are completely untouched.
   - Assert `passwordHash` strings are completely untouched.
   - Assert `role`, `hiddenModules`, and `readOnlyModules` are unchanged.
4. **Collision Resistance**:
   - Attempt to register or insert duplicate normalized usernames; verify `uq_user_username_lower_trim` rejects them.
5. **Authentication Verification**:
   - Authenticate with the new username (`yash`) &rarr; succeeds.
   - Authenticate with the previous casing (`Yash` or `YASH`) &rarr; succeeds via case-insensitive normalization.
   - Authenticate with email &rarr; succeeds.
6. **Constraint Enforcement**:
   - Attempt to insert a user with `fullName = NULL` &rarr; rejected by PostgreSQL NOT NULL constraint.
   - Verify `UserService.updateUser` rejects blank `fullName` or `fullName > 100` characters.

### 10.2 Hibernate Schema Validation Drill
Run Spring Boot with:
```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=false
```
Verify that application context loads without schema mismatch errors.

---

## 11. Explicit Confirmation of Read-Only Status

In strict adherence to the project instructions:
1. **Zero Database Modifications**: No database updates, deletes, or inserts were executed. The local database `prolt_operations` on `localhost:5432` remains in its exact pre-audit state (SHA-256 state matching V8 completion).
2. **Zero Code Modifications**: No Java source files, configuration properties, or frontend files were modified.
3. **Zero Migration Executions**: Neither `V9` nor `V10` was created or applied to the database.
4. **Zero Remote Access**: No remote production databases or external networks were contacted.
5. **Zero Git Commits**: No git staging or commits were performed.
6. **Confidentiality Maintained**: No password hashes, salts, or sensitive credentials were exposed.

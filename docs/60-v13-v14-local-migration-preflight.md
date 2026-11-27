# V13–V14 Local Database Migration Preflight Audit & Execution Plan

**Document Reference**: `docs/60-v13-v14-local-migration-preflight.md`  
**Audit Date**: November 4, 2026  
**Target Environment**: Localhost PostgreSQL (`localhost:5432/prolt_operations`)  
**Target Flyway Range**: V12 -> V13 -> V14  
**Audit Mode**: Strictly Read-Only Preflight Audit  
**Final Status**: **READY FOR APPROVAL**

---

## 1. Executive Summary & Final Status

This preflight audit evaluates the safety, integrity, and operational execution readiness for applying Flyway migrations **V13** (`V13__remove_user_password_hash.sql`) and **V14** (`V14__create_user_refresh_sessions.sql`) to the local PostgreSQL database `prolt_operations`.

The target database is currently at Flyway version **V12**. The application backend code has transitioned to passwordless email-OTP authentication and an access/refresh token lifecycle architecture. However, Flyway migrations V13 and V14 have remained pending in the local database, leaving `public."User"."passwordHash"` present and `public."user_refresh_sessions"` uncreated.

### Status Verdict
> ### **STATUS: READY FOR APPROVAL**
> 
> All structural, data, and schema preconditions for both V13 and V14 are satisfied:
> 1. All 12 existing user records in `public."User"` have valid, non-blank corporate email addresses ending in `@probus.io` (satisfies V13 preflight assertion).
> 2. Zero views, foreign keys, or indexes depend on `passwordHash`.
> 3. Application code (`src/main/java`) has completely decoupled from `passwordHash` (zero active references).
> 4. The `public."user_refresh_sessions"` schema in `V14` perfectly matches `UserRefreshSession.java` across all 12 columns, constraints, and indexes.
> 5. No orphaned, partial, or conflicting database objects exist in `public`.
> 6. Migration ordering and Flyway naming conventions strictly adhere to Flyway specifications.
> 
> **Execution Constraint**: Migration execution requires explicit user authorization and must follow the sequenced execution plan detailed in Section 9, beginning with a mandatory fresh V12 database backup.

---

## 2. Confirmed Database State

Direct read-only inspection of the local PostgreSQL instance was conducted on November 4, 2026:

| Parameter | Inspected Value | Verification Status |
|---|---|---|
| **Database Name** | `prolt_operations` | Confirmed |
| **Engine & Version** | `PostgreSQL 16.14 (Debian 16.14-1.pgdg13+1)` | Confirmed |
| **Host & Port** | `localhost:5432` | Confirmed |
| **Active Flyway Version** | `12` (`add user token version`, installed rank 8) | Confirmed |
| **Pending Migrations** | `V13`, `V14` | Confirmed |
| **`public."User"` Row Count** | **12** | Confirmed |
| **`public.user_refresh_sessions`** | Missing (0 rows, table absent) | Confirmed |
| **Hibernate DDL Auto** | `validate` (`spring.jpa.hibernate.ddl-auto=validate`) | Confirmed |
| **Flyway Local Switch** | `false` (`spring.flyway.enabled=${FLYWAY_ENABLED:false}`) | Confirmed |

### Baseline Flyway Schema History (`flyway_schema_history`)

```text
 installed_rank | version |                description                 |                       script                       |  checksum   |        installed_on        | success 
----------------+---------+--------------------------------------------+----------------------------------------------------+-------------+----------------------------+---------
              1 | 5       | << Flyway Baseline >>                      | << Flyway Baseline >>                              |             | 2026-10-28 12:13:39.102805 | t
              2 | 6       | create dsm charges if missing              | V6__create_dsm_charges_if_missing.sql              |   943547715 | 2026-10-28 12:13:39.148986 | t
              3 | 7       | case insensitive username uniqueness       | V7__case_insensitive_username_uniqueness.sql       | -1420420591 | 2026-10-30 18:07:05.479723 | t
              4 | 8       | email case insensitive and full name       | V8__email_case_insensitive_and_full_name.sql       | -1994741828 | 2026-11-02 11:48:52.75117  | t
              5 | 9       | normalize usernames and backfill full name | V9__normalize_usernames_and_backfill_full_name.sql |  1840101611 | 2026-11-02 14:09:26.303862 | t
              6 | 10      | enforce user full name not null            | V10__enforce_user_full_name_not_null.sql           |  -871721339 | 2026-11-02 14:10:48.894607 | t
              7 | 11      | add user enabled status                    | V11__add_user_enabled_status.sql                   | -1460901201 | 2026-11-02 15:35:04.312103 | t
              8 | 12      | add user token version                     | V12__add_user_token_version.sql                    |  1136765734 | 2026-11-02 17:14:24.566474 | t
```

---

## 3. Migration Files Inspection & Ordering

### 3.1 Migration File Inventory

| Version | Migration Script Name | Relative Path | Size |
|---|---|---|---|
| **V13** | `V13__remove_user_password_hash.sql` | `src/main/resources/db/migration/V13__remove_user_password_hash.sql` | 1,760 bytes |
| **V14** | `V14__create_user_refresh_sessions.sql` | `src/main/resources/db/migration/V14__create_user_refresh_sessions.sql` | 3,525 bytes |

### 3.2 Naming Convention & Ordering Validation
- **Naming Pattern**: `V<Version>__<Description>.sql` with double underscore `__` separator.
- **Sequential Monotonicity**: Existing applied version is `12`. Next candidate is `13`, followed by `14`. No gap or duplicate versions exist.
- **Atomicity**: Both files run within discrete, transactional DDL blocks supported natively by PostgreSQL.
- **Assertion Coverage**: Both files contain embedded preflight checks (`DO $$ BEGIN ... END $$;`) and postflight validation assertions.

---

## 4. V13 Preconditions & Schema Compatibility Audit

### 4.1 SQL Specification: `V13__remove_user_password_hash.sql`
The script performs:
1. **Preflight Assertion 1**: Checks that `public."User"` table exists in `information_schema.tables`.
2. **Preflight Assertion 2**: Queries `public."User"` for users where `email IS NULL OR TRIM(email) = ''`. If count > 0, aborts with an exception.
3. **DDL Execution**: `ALTER TABLE public."User" DROP COLUMN IF EXISTS "passwordHash";`
4. **Postflight Assertion**: Confirms `passwordHash` column no longer exists in `information_schema.columns`.
5. **Table Comment**: Sets explanatory metadata comment on `public."User"`.

### 4.2 Database Preconditions Evaluation

#### Precondition 1: `public."User"` Existence
- Query result: Table `User` exists in schema `public` as a `BASE TABLE`.

#### Precondition 2: Existing Users Email Integrity
Query executed:
```sql
SELECT COUNT(*) AS invalid_user_count 
FROM public."User" 
WHERE email IS NULL OR TRIM(email) = '';
```
- **Result**: `invalid_user_count = 0`.
- All 12 user records have non-null, trimmed, valid corporate email addresses:
  - `superadmin` -> `super.admin@probus.io`
  - `yash` -> `yash@probus.io`
  - `saikat` -> `saikat@probus.io`
  - `priyanka` -> `priyanka@probus.io`
  - `harshit` -> `harshit@probus.io`
  - `ankit_vaish` -> `ankit@probus.io`
  - `ankit_jha` -> `ankitjha@probus.io`
  - `anurag_shukla` -> `anurag@probus.io`
  - `gaurav` -> `gaurav@probus.io`
  - `neeraj_sharma` -> `neeraj@probus.io`
  - `anand` -> `anand@probus.io`
  - `saurabh_anoop` -> `saurabh@probus.io`

#### Precondition 3: Password Hash Column Dependencies
- **Current Column Definition**: `passwordHash character varying(255) NOT NULL`.
- **Foreign Key / Constraint Dependencies**: Checked `information_schema.table_constraints` and `key_column_usage`. **0 constraints depend on `passwordHash`**.
- **Index Dependencies**: Checked `pg_indexes` for `passwordHash`. **0 indexes depend on `passwordHash`**.
- **View / Trigger Dependencies**: **0 database triggers or views reference `passwordHash`**.

#### Precondition 4: Application Code Compatibility
- Grep audit of `src/main/java`: **0 occurrences of `passwordHash`**.
- JPA Entity `com.sense.prolt_operations.entity.User`: Contains 10 mapped properties (`id`, `username`, `email`, `fullName`, `role`, `hiddenModules`, `readOnlyModules`, `enabled`, `tokenVersion`, `createdAt`, `updatedAt`). `passwordHash` is completely absent.
- Authentication Controller & Services: Strictly use OTP verification (`AuthService.java`, `OtpService.java`).
- Hibernate Schema Validation: With `spring.jpa.hibernate.ddl-auto=validate`, Hibernate verifies that all entity fields exist in the database table. Dropping `passwordHash` will align the physical schema with the entity model.

---

## 5. V14 Preconditions & Entity Compatibility Audit

### 5.1 SQL Specification: `V14__create_user_refresh_sessions.sql`
The script performs:
1. **Preflight Assertion**: Checks that `public."User"` table exists.
2. **Table Creation**: Creates `public."user_refresh_sessions"` with primary key, foreign key, and unique constraint.
3. **Index Creation**: Creates 3 optimization indexes:
   - `idx_user_refresh_sessions_user_id` on `userId`
   - `idx_user_refresh_sessions_family_id` on `tokenFamilyId`
   - `idx_user_refresh_sessions_expires_at` on `expiresAt`
4. **Postflight Assertion**: Confirms table and unique constraint `uq_user_refresh_sessions_token_hash` exist.

### 5.2 Schema vs JPA Entity Compatibility Matrix

| DDL Column / Constraint | DDL Specification | JPA Entity Mapping (`UserRefreshSession.java`) | Match Status |
|---|---|---|---|
| `id` | `VARCHAR(36) NOT NULL PRIMARY KEY` | `@Id @GeneratedValue(strategy = GenerationType.UUID) @Column(name = "id") private String id;` | **100% MATCH** |
| `userId` | `VARCHAR(255) NOT NULL` | `@Column(name = "userId", nullable = false) private String userId;` | **100% MATCH** |
| `tokenHash` | `VARCHAR(64) NOT NULL UNIQUE` | `@Column(name = "tokenHash", nullable = false, unique = true, length = 64) private String tokenHash;` | **100% MATCH** |
| `tokenFamilyId` | `VARCHAR(36) NOT NULL` | `@Column(name = "tokenFamilyId", nullable = false, length = 36) private String tokenFamilyId;` | **100% MATCH** |
| `tokenVersion` | `INTEGER NOT NULL DEFAULT 0` | `@Column(name = "tokenVersion", nullable = false) private int tokenVersion = 0;` | **100% MATCH** |
| `createdAt` | `TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()` | `@Column(name = "createdAt", nullable = false) private LocalDateTime createdAt;` | **100% MATCH** |
| `expiresAt` | `TIMESTAMP WITHOUT TIME ZONE NOT NULL` | `@Column(name = "expiresAt", nullable = false) private LocalDateTime expiresAt;` | **100% MATCH** |
| `lastUsedAt` | `TIMESTAMP WITHOUT TIME ZONE` | `@Column(name = "lastUsedAt") private LocalDateTime lastUsedAt;` | **100% MATCH** |
| `revokedAt` | `TIMESTAMP WITHOUT TIME ZONE` | `@Column(name = "revokedAt") private LocalDateTime revokedAt;` | **100% MATCH** |
| `replacedById` | `VARCHAR(36)` | `@Column(name = "replacedById", length = 36) private String replacedById;` | **100% MATCH** |
| `createdIp` | `VARCHAR(45)` | `@Column(name = "createdIp", length = 45) private String createdIp;` | **100% MATCH** |
| `userAgent` | `VARCHAR(500)` | `@Column(name = "userAgent", length = 500) private String userAgent;` | **100% MATCH** |
| **FK Constraint** | `FOREIGN KEY ("userId") REFERENCES public."User" ("id") ON DELETE CASCADE` | Referential cascade managed by PostgreSQL engine; repository queries via `userId` | **100% MATCH** |
| **Naming Strategy** | Physical exact casing (`PhysicalNamingStrategyStandardImpl` + `globally_quoted_identifiers=true`) | Quotes match column attributes identically (`"tokenFamilyId"`, `"tokenHash"`, etc.) | **100% MATCH** |

### 5.3 Foreign Key Compatibility Check
- Target table: `public."User"`
- Target column: `id` (Type: `text`, Primary Key: `User_pkey`)
- Source table: `public."user_refresh_sessions"`
- Source column: `userId` (Type: `VARCHAR(255)`)
- **Compatibility**: PostgreSQL permits `VARCHAR(255)` foreign key references to `TEXT` primary keys as they belong to the same type category and share compatible standard equality operators (`=`).
- **Cascade Behavior**: `ON DELETE CASCADE` guarantees that if a user account is deleted, all historical and active refresh sessions are purged automatically, eliminating orphan records and session hijacking risks.

---

## 6. Conflicting Object Audit

A search across PostgreSQL catalogs confirmed:
- `SELECT * FROM information_schema.tables WHERE table_name ILIKE '%refresh%';` -> **0 rows**
- `SELECT * FROM pg_indexes WHERE indexname ILIKE '%refresh%';` -> **0 rows**
- `SELECT * FROM pg_constraint WHERE conname ILIKE '%refresh%';` -> **0 rows**

No partial, conflicting, or residual tables, views, triggers, or indexes exist. The namespace is completely clean.

---

## 7. Backup Requirements & Disk Capacity Audit

### 7.1 Existing Backup Catalog
Inspection of the `backups/` directory shows:

```text
-rw-rw-r-- 1 ankit-jha ankit-jha 990M Oct 30 18:05 prolt_operations_pre_v7_20261030_180304.dump
-rw-rw-r-- 1 ankit-jha ankit-jha 990M Nov  2 11:48 prolt_operations_pre_v8_20261102_114557.dump
-rw-rw-r-- 1 ankit-jha ankit-jha 990M Nov  2 14:07 prolt_operations_pre_v9_v10_20261102_140500.dump
-rw-rw-r-- 1 ankit-jha ankit-jha 990M Nov  2 15:32 prolt_operations_pre_v11_20261102_153029.dump
-rw-rw-r-- 1 ankit-jha ankit-jha 990M Nov  2 17:14 prolt_operations_pre_v12_20261102_171142.dump
```

### 7.2 Backup Assessment: **FRESH BACKUP MANDATORY**
- The latest existing backup (`prolt_operations_pre_v12_20261102_171142.dump`) was created **before** V12 was applied.
- There is **no existing backup of the database in its current V12 state**.
- Restoring from `pre_v12` in the event of a V13/V14 rollback would roll back V12 as well (`tokenVersion` column and user token state would be lost).
- **Requirement**: A fresh custom-format dump (`prolt_operations_pre_v13_v14_<timestamp>.dump`) must be captured and verified immediately prior to executing V13.

### 7.3 Disk Space Verification
- Root partition (`/`): **11 GB available** (342 GB used of 371 GB).
- Estimated backup size: **~990 MB**.
- Sufficient disk capacity exists to store the pre-V13 backup.

---

## 8. Migration Risks & Rollback Limitations

### 8.1 Irreversibility of `DROP COLUMN "passwordHash"` (Data Loss Risk)
- `ALTER TABLE public."User" DROP COLUMN IF EXISTS "passwordHash";` physically detaches and marks the column data for reclamation.
- **Rollback Limitation**: Once V13 commits, old password bcrypt hashes cannot be restored via a simple `ALTER TABLE ADD COLUMN "passwordHash"` statement, as the hash values are permanently erased.
- **Mitigation**: The pre-migration backup (`prolt_operations_pre_v13_v14_*.dump`) preserves the full table dump including all password hashes, enabling full restoration if necessary.

### 8.2 Flyway Target Precedence Risk
- `application.properties` sets `spring.flyway.target=${FLYWAY_TARGET:8}`.
- If Flyway is executed without overriding `spring.flyway.target` to `14`, Flyway will refuse to run because the database is already at V12 (higher than 8).
- **Mitigation**: The execution command must explicitly supply `-Dspring.flyway.target=14`.

### 8.3 Hibernate Startup Dependency
- In `application-local.properties`, `spring.jpa.hibernate.ddl-auto=validate`.
- Currently, application startup fails under Hibernate validation if `user_refresh_sessions` does not exist.
- Applying V14 is the exact required fix that enables Hibernate validation to pass.

---

## 9. Verification SQL Queries (Pre & Post Migration)

### 9.1 Pre-Migration SQL Verification Queries

Execute prior to running the migration:

```sql
-- 1. Check current Flyway version (must be V12)
SELECT installed_rank, version, description, type, script, installed_on, success 
FROM flyway_schema_history 
ORDER BY installed_rank DESC LIMIT 1;

-- 2. Verify passwordHash column exists in public."User"
SELECT column_name, data_type, is_nullable, column_default 
FROM information_schema.columns 
WHERE table_schema = 'public' AND table_name = 'User' AND column_name = 'passwordHash';

-- 3. Verify zero invalid or blank emails exist
SELECT COUNT(*) AS invalid_email_count 
FROM public."User" 
WHERE email IS NULL OR TRIM(email) = '';

-- 4. Verify public.user_refresh_sessions does not yet exist
SELECT COUNT(*) AS refresh_table_count 
FROM information_schema.tables 
WHERE table_schema = 'public' AND table_name = 'user_refresh_sessions';

-- 5. Baseline user count (must be 12)
SELECT COUNT(*) AS user_count FROM public."User";
```

### 9.2 Post-Migration SQL Verification Queries

Execute immediately following migration:

```sql
-- 1. Verify Flyway schema history recorded V13 and V14
SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success 
FROM flyway_schema_history 
WHERE version IN ('12', '13', '14') 
ORDER BY installed_rank;

-- 2. Verify passwordHash column is completely absent
SELECT COUNT(*) AS password_hash_column_count 
FROM information_schema.columns 
WHERE table_schema = 'public' AND table_name = 'User' AND column_name = 'passwordHash';
-- Expected: 0

-- 3. Verify public."User" retains all 12 user records and remaining 11 columns
SELECT COUNT(*) AS user_count FROM public."User";
-- Expected: 12

SELECT column_name, data_type, is_nullable, column_default 
FROM information_schema.columns 
WHERE table_schema = 'public' AND table_name = 'User' 
ORDER BY ordinal_position;

-- 4. Verify public.user_refresh_sessions columns (12 expected)
SELECT column_name, data_type, character_maximum_length, is_nullable, column_default 
FROM information_schema.columns 
WHERE table_schema = 'public' AND table_name = 'user_refresh_sessions' 
ORDER BY ordinal_position;

-- 5. Verify constraints on public.user_refresh_sessions (PK, FK with CASCADE, UNIQUE)
SELECT conname, contype, pg_get_constraintdef(oid) 
FROM pg_constraint 
WHERE conrelid = 'public.user_refresh_sessions'::regclass;

-- 6. Verify indexes on public.user_refresh_sessions
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE schemaname = 'public' AND tablename = 'user_refresh_sessions';
```

---

## 10. Step-by-Step Execution Plan (DO NOT EXECUTE)

> [!IMPORTANT]
> The following steps are documented for authorization and planning purposes only. They must not be executed until explicit user approval is granted.

### Step 1: Capture Pre-Migration Database Backup
Generate a full PostgreSQL custom-format (`-Fc`) backup of `prolt_operations` at V12:

```bash
PGPASSWORD='probus@220706' pg_dump \
  -h localhost \
  -p 5432 \
  -U postgres \
  -d prolt_operations \
  -Fc \
  -f backups/prolt_operations_pre_v13_v14_$(date +%Y%m%d_%H%M%S).dump
```

Verify archive validity:
```bash
pg_restore --list backups/prolt_operations_pre_v13_v14_*.dump | head -n 25
```

### Step 2: Run Pre-Migration Verification Script
Execute pre-migration SQL queries from Section 9.1 via `psql` to confirm:
- Installed version is 12.
- `passwordHash` exists.
- `invalid_email_count = 0`.
- `user_refresh_sessions` is absent.

### Step 3: Execute Controlled Flyway Migration to V14
Run the Spring Boot application under the `local` profile with explicit Flyway flags targeting version 14:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--spring.flyway.enabled=true --spring.flyway.target=14"
```

*(Alternatively, via compiled jar)*:
```bash
java -Dspring.profiles.active=local \
     -Dspring.flyway.enabled=true \
     -Dspring.flyway.target=14 \
     -jar target/prolt-operations-master.jar
```

Flyway will:
1. Validate migrations 1 through 14.
2. Apply `V13__remove_user_password_hash.sql`.
3. Apply `V14__create_user_refresh_sessions.sql`.
4. Transition schema version from 12 -> 14.

### Step 4: Run Post-Migration Verification Queries
Execute post-migration SQL queries from Section 9.2:
- Confirm `flyway_schema_history` shows V13 and V14 with `success = true`.
- Confirm `passwordHash` is dropped.
- Confirm `user_refresh_sessions` exists with PK, FK (`ON DELETE CASCADE`), unique constraint, and 3 indexes.
- Confirm all 12 user accounts remain intact.

### Step 5: Verify Application Startup & Hibernate Validation
Start the application with default local settings (`spring.flyway.enabled=false`, `spring.jpa.hibernate.ddl-auto=validate`):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Verify that:
1. Hibernate schema validation passes cleanly with 0 errors.
2. Actuator `/actuator/health` returns `{"status":"UP"}`.

### Step 6: Execute Security & Repository Regression Tests
Run the test suite covering user password removal and refresh session tokens:

```bash
mvn test -Dtest=UserPasswordRemovalMigrationTest,UserRefreshSessionRepositoryTest,RefreshTokenServiceTest,UserTokenVersionMigrationTest
```

---

## 11. Rollback Runbook (Emergency Procedures)

If migration fails or unexpected anomalies occur during migration:

### Procedure A: In-Flight Flyway Failure (Automatic Rollback)
PostgreSQL supports transactional DDL. If an assertion or statement in V13 or V14 fails, PostgreSQL rolls back that migration transaction completely. The database remains at V12 (or V13 if V14 fails). Flyway marks the failed migration with `success = false`.
To recover:
```sql
-- Remove failed record if needed after fixing script
DELETE FROM flyway_schema_history WHERE success = false;
```

### Procedure B: Full Database Restore from Pre-V13/V14 Backup
If destructive corruption occurs, terminate existing connections and restore the pre-migration dump:

```bash
# 1. Terminate active database connections
PGPASSWORD='probus@220706' psql -h localhost -p 5432 -U postgres -d postgres -c "
SELECT pg_terminate_backend(pid) 
FROM pg_stat_activity 
WHERE datname = 'prolt_operations' AND pid <> pg_backend_pid();
"

# 2. Drop and recreate clean database
PGPASSWORD='probus@220706' psql -h localhost -p 5432 -U postgres -d postgres -c "DROP DATABASE prolt_operations;"
PGPASSWORD='probus@220706' psql -h localhost -p 5432 -U postgres -d postgres -c "CREATE DATABASE prolt_operations OWNER postgres;"

# 3. Restore from the pre-V13 backup
PGPASSWORD='probus@220706' pg_restore \
  -h localhost \
  -p 5432 \
  -U postgres \
  -d prolt_operations \
  --clean \
  --if-exists \
  backups/prolt_operations_pre_v13_v14_<timestamp>.dump
```

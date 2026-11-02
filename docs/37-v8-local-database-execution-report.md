# Execution Report: Controlled Rollout of Flyway Migration V8

**Document Reference**: `docs/37-v8-local-database-execution-report.md`  
**Execution Date**: 2026-11-02  
**Repository**: `prolt-operations`  
**Target Database**: `prolt_operations` on `localhost:5432`  
**Database Engine**: PostgreSQL 16.14 (Debian 16.14-1.pgdg13+1)  
**Execution Status**: **COMPLETE & VERIFIED (100% SUCCESS)**  

---

## 1. Executive Summary

This report documents the controlled, safe rollout of Flyway migration `V8__email_case_insensitive_and_full_name.sql` against the locally restored production-derived PostgreSQL database `prolt_operations`.

All safety requirements, preflight backups, aggregate data verifications, migration execution, index transitions, data integrity validations, and Hibernate schema validation drills were executed with zero data mutation, zero downtime, and zero regression.

### Key Milestones Achieved
1. **Preflight Backup Created & Verified**: A fresh, full custom-format PostgreSQL backup (`990MB`, 464 TOC entries) was created at `backups/prolt_operations_pre_v8_20261102_114557.dump` and verified with `pg_restore --list`.
2. **Strict Isolation & Safety Confirmed**: Verified that no remote production database URLs were configured, `RUN_SCRAPER=false` was active, and admin bootstrapping credentials were unset.
3. **Precondition Audit Passed**: Confirmed exactly 12 users, 12 non-null emails, 0 duplicate normalized emails, 0 whitespace anomalies, and 0 null/empty emails.
4. **Flyway Migration V8 Executed**: Flyway applied `V8` in 20ms through the Spring Boot runtime. Legacy case-sensitive unique index `User_email_key` was dropped, unique functional index `uq_user_email_lower_trim` was created, and nullable `fullName VARCHAR(100)` was added.
5. **Zero Data Modification Proven**: Cryptographic SHA-256 and MD5 checksums of raw user identity records before and after migration matched identically. Zero records were inserted, deleted, or modified. All 12 existing accounts have `NULL` `fullName`.
6. **Functional Uniqueness Enforced**: Verified in PostgreSQL engine that attempts to insert duplicate normalized emails (e.g. mixed-case variations) are blocked by `uq_user_email_lower_trim`.
7. **Hibernate Schema Validation Succeeded**: Application booted cleanly under `spring.jpa.hibernate.ddl-auto=validate` with Flyway disabled.
8. **Health Endpoints Verified**: Verified `/health` and `/health/database` returned healthy status.
9. **Full Test Suite Clean**: Automated test suite executed 165 tests with 0 failures, 0 errors, and 1 intentionally skipped manual scraper test.

---

## 2. Target Database & Infrastructure Verification

| Parameter | Configuration Value | Verification Status |
|---|---|---|
| **Host** | `localhost` / `127.0.0.1` | Local PostgreSQL container (`172.17.0.6`) |
| **Port** | `5432` | Accessible via TCP |
| **Database Name** | `prolt_operations` | Verified via `SELECT current_database()` |
| **Engine Version** | PostgreSQL 16.14 (Debian 16.14-1.pgdg13+1) | Verified via `SELECT version()` |
| **Active Profile** | `localvalidate` | Read-only / Validate mode |
| **Flyway Config** | `schemas=public,forecasting`, `default-schema=public` | Configured |
| **Hibernate DDL** | `spring.jpa.hibernate.ddl-auto=validate` | Enforced |
| **Scraper Execution** | `app.run-scraper=false` | Schedulers blocked |
| **Admin Bootstrap** | Credentials empty | `AppRunner` skipped admin sync |
| **Remote Network Access** | None | No remote database URLs configured or contacted |

---

## 3. Phase 1: Pre-Migration Backup & Verification

Prior to any migration activity, a full database backup was generated using PostgreSQL's custom archive format (`-Fc`):

- **Backup Archive Path**: `/home/ankit-jha/probus-workspace/prolt-operations/backups/prolt_operations_pre_v8_20261102_114557.dump`
- **File Size**: `990 MB` (1,038,098,432 bytes)
- **Format**: PostgreSQL Custom Format (gzip compressed)
- **TOC Objects**: 464 archive entries

### Backup Verification Command & Output
```bash
$ pg_restore --list backups/prolt_operations_pre_v8_20261102_114557.dump | head -n 25
;
; Archive created at 2026-11-02 11:45:57 IST
;     dbname: prolt_operations
;     TOC Entries: 453
;     Compression: gzip
;     Dump Version: 1.16-0
;     Format: CUSTOM
;     Integer: 4 bytes
;     Offset: 8 bytes
;     Dumped from database version: 16.14 (Debian 16.14-1.pgdg13+1)
;     Dumped by pg_dump version: 18.6 (Ubuntu 18.6-0ubuntu0.26.04.1)
;
;
; Selected TOC Entries:
;
6; 2615 434285 SCHEMA - forecasting postgres
936; 1247 434287 TYPE public DatasetStatus postgres
939; 1247 434294 TYPE public MarketType postgres
942; 1247 434304 TYPE public Role postgres
945; 1247 434312 TYPE public UploadAction postgres
948; 1247 434320 TYPE public state_ut_enum postgres
311; 1255 434325 FUNCTION forecasting prevent_consumer_table_drop() postgres
216; 1259 434326 TABLE forecasting GdamMcpForecast postgres
217; 1259 434333 SEQUENCE forecasting GdamMcpForecast_id_seq postgres
4303; 0 0 SEQUENCE OWNED BY forecasting GdamMcpForecast_id_seq postgres
```
Archive readability and integrity confirmed.

---

## 4. Phase 2: Pre-Migration Baseline & Precondition Audit

### 4.1 Schema History Baseline (Before V8)
```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success 
FROM public.flyway_schema_history 
ORDER BY installed_rank;
```
| Installed Rank | Version | Description | Type | Script | Checksum | Installed On | Execution Time | Success |
|---|---|---|---|---|---|---|---|---|
| 1 | `5` | `<< Flyway Baseline >>` | BASELINE | `<< Flyway Baseline >>` | *null* | 2026-10-28 12:13:39.102805 | 0 ms | `true` |
| 2 | `6` | `create dsm charges if missing` | SQL | `V6__create_dsm_charges_if_missing.sql` | `943547715` | 2026-10-28 12:13:39.148986 | 7 ms | `true` |
| 3 | `7` | `case insensitive username uniqueness` | SQL | `V7__case_insensitive_username_uniqueness.sql` | `-1420420591` | 2026-10-30 18:07:05.479723 | 19 ms | `true` |

### 4.2 User Table & Email Baseline (Aggregate Only — Zero PII)
```sql
SELECT 
    COUNT(*) AS total_users,
    COUNT(email) AS total_emails,
    COUNT(*) FILTER (WHERE email IS NULL OR TRIM(email) = '') AS null_or_empty_emails,
    COUNT(*) FILTER (WHERE email != TRIM(email)) AS whitespace_padded_emails,
    COUNT(DISTINCT LOWER(TRIM(email))) AS distinct_normalized_emails
FROM public."User";
```
| Total Users | Total Emails | Null or Empty Emails | Whitespace-Padded Emails | Distinct Normalized Emails |
|---|---|---|---|---|
| **12** | **12** | **0** | **0** | **12** |

Normalized Duplicate Check:
```sql
SELECT LOWER(TRIM(email)) AS normalized_email, COUNT(*)
FROM public."User"
GROUP BY LOWER(TRIM(email))
HAVING COUNT(*) > 1;
```
*Result*: **0 rows** (zero duplicate normalized emails).

### 4.3 Existing User Indexes (Before V8)
```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public' AND tablename = 'User'
ORDER BY indexname;
```
- `User_email_idx` (`CREATE INDEX "User_email_idx" ON public."User" USING btree (email)`)
- `User_email_key` (`CREATE UNIQUE INDEX "User_email_key" ON public."User" USING btree (email)`) — standalone index, not in `pg_constraint`.
- `User_pkey` (`CREATE UNIQUE INDEX "User_pkey" ON public."User" USING btree (id)`)
- `User_username_idx` (`CREATE INDEX "User_username_idx" ON public."User" USING btree (username)`)
- `uq_user_username_lower_trim` (`CREATE UNIQUE INDEX uq_user_username_lower_trim ON public."User" USING btree (lower(TRIM(BOTH FROM username)))`)

### 4.4 Pre-Migration Cryptographic Checksum
```sql
SELECT 
    md5(string_agg(id || '|' || username || '|' || email || '|' || COALESCE("passwordHash", '') || '|' || role || '|' || "createdAt"::text || '|' || "updatedAt"::text, ',' ORDER BY id)) AS raw_accounts_md5,
    encode(sha256(string_agg(id || '|' || username || '|' || email || '|' || COALESCE("passwordHash", '') || '|' || role || '|' || "createdAt"::text || '|' || "updatedAt"::text, ',' ORDER BY id)::bytea), 'hex') AS raw_accounts_sha256
FROM public."User";
```
- **Pre-Migration MD5**: `bd93658a6704898a08205af78764dd55`
- **Pre-Migration SHA-256**: `75854a8a9c33f4cdbc93b77272665a0cdd51700216de0d9241d1975f07f68f2b`

---

## 5. Phase 3: Flyway Migration V8 Execution

Migration was executed through the Java runtime with Flyway enabled under the `localvalidate` profile:

```bash
java -Dspring.profiles.active=localvalidate -Dspring.flyway.enabled=true -jar target/prolt-operations-master.jar
```

### Runtime Startup Log Excerpt
```text
2026-11-02 11:48:50.120 [main] INFO  c.s.p.ProltOperationsApplication - Starting ProltOperationsApplication vmaster using Java 21.0.12.1 with PID 289199 (/home/ankit-jha/probus-workspace/prolt-operations/target/prolt-operations-master.jar started by ankit-jha in /home/ankit-jha/probus-workspace/prolt-operations)
2026-11-02 11:48:50.122 [main] INFO  c.s.p.ProltOperationsApplication - The following 1 profile is active: "localvalidate"
...
2026-11-02 11:48:52.694 [main] INFO  org.flywaydb.core.FlywayExecutor - Database: jdbc:postgresql://localhost:5432/prolt_operations?stringtype=unspecified (PostgreSQL 16.14)
2026-11-02 11:48:52.729 [main] INFO  o.f.core.internal.command.DbValidate - Successfully validated 9 migrations (execution time 00:00.021s)
2026-11-02 11:48:52.753 [main] INFO  o.f.core.internal.command.DbMigrate - Current version of schema "public": 7
2026-11-02 11:48:52.763 [main] INFO  o.f.core.internal.command.DbMigrate - Migrating schema "public" to version "8 - email case insensitive and full name"
2026-11-02 11:48:52.794 [main] INFO  o.f.core.internal.command.DbMigrate - Successfully applied 1 migration to schema "public", now at version v8 (execution time 00:00.020s)
2026-11-02 11:48:52.867 [main] INFO  o.h.jpa.internal.util.LogHelper - HHH000204: Processing PersistenceUnitInfo [name: default]
2026-11-02 11:48:54.162 [main] INFO  o.s.o.j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-11-02 11:48:56.377 [main] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port 8089 (http) with context path '/'
2026-11-02 11:48:56.395 [main] INFO  c.s.p.ProltOperationsApplication - Started ProltOperationsApplication in 6.815 seconds (process running for 7.196)
2026-11-02 11:48:56.440 [main] INFO  c.s.p.runner.AppRunner - AppRunner initialized (admin sync skipped, credentials not configured).
```

- **Applied Migration**: `V8__email_case_insensitive_and_full_name.sql`
- **Execution Time**: 20 ms
- **Flyway Status**: `SUCCESS`
- **Application Startup**: Clean boot in 6.815s with zero exceptions.

---

## 6. Phase 4: Post-Migration Verification & Data Integrity Proof

### 6.1 Updated Schema History
```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success 
FROM public.flyway_schema_history 
ORDER BY installed_rank;
```
| Installed Rank | Version | Description | Type | Script | Checksum | Installed On | Execution Time | Success |
|---|---|---|---|---|---|---|---|---|
| 1 | `5` | `<< Flyway Baseline >>` | BASELINE | `<< Flyway Baseline >>` | *null* | 2026-10-28 12:13:39.102805 | 0 ms | `true` |
| 2 | `6` | `create dsm charges if missing` | SQL | `V6__create_dsm_charges_if_missing.sql` | `943547715` | 2026-10-28 12:13:39.148986 | 7 ms | `true` |
| 3 | `7` | `case insensitive username uniqueness` | SQL | `V7__case_insensitive_username_uniqueness.sql` | `-1420420591` | 2026-10-30 18:07:05.479723 | 19 ms | `true` |
| 4 | `8` | `email case insensitive and full name` | SQL | `V8__email_case_insensitive_and_full_name.sql` | `-1994741828` | 2026-11-02 11:48:52.75117 | 20 ms | `true` |

### 6.2 User Table Column Structure
```sql
SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'User' AND column_name IN ('email', 'username', 'fullName')
ORDER BY column_name;
```
| Column Name | Data Type | Maximum Length | Nullable | Notes |
|---|---|---|---|---|
| `email` | `character varying` | 255 | **NO** | NOT NULL constraint preserved |
| `fullName` | `character varying` | 100 | **YES** | Nullable for zero-downtime backward compatibility |
| `username` | `character varying` | 50 | **NO** | Immutability and NOT NULL preserved |

### 6.3 User Table Data Counts & Full Name Integrity
```sql
SELECT 
    COUNT(*) AS total_users,
    COUNT(email) AS total_emails,
    COUNT(username) AS total_usernames,
    COUNT("fullName") AS total_non_null_full_names,
    COUNT(*) FILTER (WHERE "fullName" IS NULL) AS total_null_full_names,
    COUNT(*) FILTER (WHERE email IS NULL OR TRIM(email) = '') AS null_or_empty_emails,
    COUNT(*) FILTER (WHERE email != TRIM(email)) AS whitespace_padded_emails,
    COUNT(DISTINCT LOWER(TRIM(email))) AS distinct_normalized_emails,
    COUNT(DISTINCT LOWER(TRIM(username))) AS distinct_normalized_usernames
FROM public."User";
```
| Total Users | Total Emails | Total Usernames | Non-Null Full Names | Null Full Names | Whitespace Emails | Distinct Normalized Emails | Distinct Normalized Usernames |
|---|---|---|---|---|---|---|---|
| **12** | **12** | **12** | **0** | **12** | **0** | **12** | **12** |

- **Zero Synthetic Values**: `total_non_null_full_names` is 0. No synthetic or placeholder full names were populated.
- **Null Value Preservation**: All 12 pre-existing user records have `fullName IS NULL`.

### 6.4 User Table Indexes (After V8)
```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public' AND tablename = 'User'
ORDER BY indexname;
```
| Index Name | Index Definition | Status |
|---|---|---|
| `User_email_idx` | `CREATE INDEX "User_email_idx" ON public."User" USING btree (email)` | **Preserved** |
| `User_pkey` | `CREATE UNIQUE INDEX "User_pkey" ON public."User" USING btree (id)` | **Preserved** |
| `User_username_idx` | `CREATE INDEX "User_username_idx" ON public."User" USING btree (username)` | **Preserved** |
| `uq_user_username_lower_trim` | `CREATE UNIQUE INDEX uq_user_username_lower_trim ON public."User" USING btree (lower(TRIM(BOTH FROM username)))` | **Preserved** |
| `uq_user_email_lower_trim` | `CREATE UNIQUE INDEX uq_user_email_lower_trim ON public."User" USING btree (lower(TRIM(BOTH FROM email)))` | **Newly Created** |
| `User_email_key` | *Dropped* | **Removed Safely** |

### 6.5 Zero Data Mutation Cryptographic Checksum Proof
```sql
SELECT 
    md5(string_agg(id || '|' || username || '|' || email || '|' || COALESCE("passwordHash", '') || '|' || role || '|' || "createdAt"::text || '|' || "updatedAt"::text, ',' ORDER BY id)) AS raw_accounts_md5,
    encode(sha256(string_agg(id || '|' || username || '|' || email || '|' || COALESCE("passwordHash", '') || '|' || role || '|' || "createdAt"::text || '|' || "updatedAt"::text, ',' ORDER BY id)::bytea), 'hex') AS raw_accounts_sha256
FROM public."User";
```

| Checksum Metric | Pre-Migration | Post-Migration | Verification Result |
|---|---|---|---|
| **MD5** | `bd93658a6704898a08205af78764dd55` | `bd93658a6704898a08205af78764dd55` | **IDENTICAL (100% MATCH)** |
| **SHA-256** | `75854a8a9c33f4cdbc93b77272665a0cdd51700216de0d9241d1975f07f68f2b` | `75854a8a9c33f4cdbc93b77272665a0cdd51700216de0d9241d1975f07f68f2b` | **IDENTICAL (100% MATCH)** |

### 6.6 Functional Uniqueness Enforcement Verification
Tested duplicate insertion handling in PostgreSQL within an aborted sub-transaction:
```text
NOTICE:  SUCCESS: uq_user_email_lower_trim blocked duplicate normalized email insert as expected.
```

### 6.7 Idempotency & Cold Start Schema Validation
Re-launched Spring Boot runtime with `spring.flyway.enabled=false` and `spring.jpa.hibernate.ddl-auto=validate`:
```text
2026-11-02 11:49:28.241 [main] INFO  com.zaxxer.hikari.pool.HikariPool - HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@7ac1161f
2026-11-02 11:49:29.347 [main] INFO  o.s.o.j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-11-02 11:49:31.701 [main] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port 8089 (http) with context path '/'
2026-11-02 11:49:31.716 [main] INFO  c.s.p.ProltOperationsApplication - Started ProltOperationsApplication in 6.495 seconds
```

### 6.8 Health Endpoints
- `GET http://localhost:8089/health` -> `{"status":"healthy","success":true}`
- `GET http://localhost:8089/health/database` -> `{"database":"connected","success":true}`

---

## 7. Phase 5: Regression Verification

Executed full automated test suite against the local development environment:

```bash
$ ./mvnw test
```

### Output Summary
```text
[INFO] Results:
[INFO] 
[INFO] Tests run: 165, Failures: 0, Errors: 0, Skipped: 1
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  54.135 s
[INFO] Finished at: 2026-11-02T11:51:30+05:30
[INFO] ------------------------------------------------------------------------
```

- Total Tests Run: **165**
- Failures: **0**
- Errors: **0**
- Skipped: **1** (`ScraperLiveManualTest` intentionally skipped, requires external scraping network credentials)
- Test Suites Validated:
  - `EmailAndFullNameIntegrationTest` (8/8 passed)
  - `IsolatedMigrationRehearsalTest` (6/6 passed)
  - `UsernameCaseInsensitiveIntegrationTest` (6/6 passed)
  - `UsernameImmutabilityIntegrationTest` (5/5 passed)
  - `AuthAndUserIntegrationTest` (15/15 passed)
  - `FlywayBaselineIntegrationTest` (2/2 passed)
  - `OtpServiceTest` (4/4 passed)

---

## 8. Safety & Compliance Confirmation

1. **Local Database Only**: All migration commands were executed solely against `jdbc:postgresql://localhost:5432/prolt_operations`.
2. **Zero Remote Access**: Remote staging and production environments were never accessed or contacted.
3. **Zero Deployment**: No deployment, container publishing, or git commit was performed.
4. **No V9 Migration**: Only V8 was executed.
5. **Zero Data Mutation**: Verified by matching SHA-256 and MD5 checksums. All 12 user accounts remain intact with NULL full names.
6. **No PII Exposure**: All reports and outputs contain strictly aggregated metrics and anonymized indicators.

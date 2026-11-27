# V13–V14 Local Database Migration Execution Report

**Document Reference**: `docs/61-v13-v14-local-migration-execution-report.md`  
**Execution Date**: November 4, 2026  
**Target Environment**: Localhost PostgreSQL (`localhost:5432/prolt_operations`)  
**Target Flyway Range**: V12 -> V13 -> V14  
**Final Status**: **SUCCESS**

---

## 1. Executive Summary

Following explicit user authorization and completion of the preflight audit ([`docs/60-v13-v14-local-migration-preflight.md`](file:///home/ankit-jha/probus-workspace/prolt-operations/docs/60-v13-v14-local-migration-preflight.md)), Flyway migrations **V13** (`V13__remove_user_password_hash.sql`) and **V14** (`V14__create_user_refresh_sessions.sql`) were executed separately and sequentially against the authorized local database:
- **Host**: `localhost`
- **Port**: `5432`
- **Database**: `prolt_operations`
- **User**: `postgres`
- **Engine**: PostgreSQL 16.14 (Debian 16.14-1.pgdg13+1)

### Execution Summary & Results
1. **Pre-Migration Backup Verification**: The pre-migration backup (`backups/prolt_operations_pre_v13_v14_20261104_112703.dump`, 990 MB) was verified with exact SHA-256 checksum matching `79799b64ffd55ff0f730a7f4a92b66f537151de1f41318800376f6ebf570c292`.
2. **V13 Migration Execution**: Applied cleanly in 19 ms. The column `public."User"."passwordHash"` was successfully dropped. All 12 user accounts remained intact with unaltered usernames, emails, fullNames, roles, enabled statuses, and tokenVersions.
3. **V14 Migration Execution**: Applied cleanly in 26 ms. The table `public."user_refresh_sessions"` was successfully created with all 12 columns, primary key `pk_user_refresh_sessions`, foreign key `fk_user_refresh_sessions_user` (`ON DELETE CASCADE`), unique constraint `uq_user_refresh_sessions_token_hash`, and 3 performance indexes.
4. **Data Integrity & Consistency**: All business data across `exchange_dam_rate` (3,198,528 rows), `exchange_rtm_rate` (3,197,916 rows), `UserAuditLog` (53 rows), and `dsm_charges` (0 rows) was 100% preserved.
5. **Application Startup & Hibernate Schema Validation**: Started the backend under the `local` profile with `spring.jpa.hibernate.ddl-auto=validate` and `spring.flyway.enabled=false`. The application initialized all JPA entity factories cleanly with zero validation errors, started Tomcat on port `8089`, and `/actuator/health` returned `{"status":"UP"}`.

---

## 2. Pre-Migration Verification & Backup Validation

Prior to applying any changes, the local database baseline and pre-migration dump were validated:

### 2.1 Backup Integrity Check
- **File Path**: `backups/prolt_operations_pre_v13_v14_20261104_112703.dump`
- **File Size**: 1,037,657,480 bytes (~990 MB)
- **Calculated SHA-256**:
  ```text
  79799b64ffd55ff0f730a7f4a92b66f537151de1f41318800376f6ebf570c292
  ```
- **Expected SHA-256**:
  ```text
  79799b64ffd55ff0f730a7f4a92b66f537151de1f41318800376f6ebf570c292
  ```
- **Result**: **100% Match — Verified**.

### 2.2 Baseline Flyway State (Pre-Execution)
Query:
```sql
SELECT installed_rank, version, description, type, script, installed_on, success 
FROM flyway_schema_history 
ORDER BY installed_rank DESC LIMIT 1;
```
Result:
```text
 installed_rank | version |      description       | type |             script              |        installed_on        | success 
----------------+---------+------------------------+------+---------------------------------+----------------------------+---------
              8 | 12      | add user token version | SQL  | V12__add_user_token_version.sql | 2026-11-02 17:14:24.566474 | t
```

### 2.3 Baseline Data Integrity
- `public."User"` count: **12**
- `passwordHash` presence: Confirmed present as `character varying(255) NOT NULL`.
- Email validity: `SELECT count(*) FROM public."User" WHERE email IS NULL OR TRIM(email) = '';` returned **0**.

---

## 3. V13 Migration Execution & Verification

### 3.1 Controlled Execution Command
V13 was targeted strictly to version 13 with non-web mode:
```bash
mvn spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--spring.flyway.enabled=true --spring.flyway.target=13 --spring.main.web-application-type=none --spring.jpa.hibernate.ddl-auto=none"
```

### 3.2 Flyway Execution Log (V13)
```text
2026-11-04 11:35:06.016 [main] INFO  org.flywaydb.core.FlywayExecutor - Database: jdbc:postgresql://localhost:5432/prolt_operations?stringtype=unspecified (PostgreSQL 16.14)
2026-11-04 11:35:06.052 [main] INFO  o.f.core.internal.command.DbValidate - Successfully validated 15 migrations (execution time 00:00.023s)
2026-11-04 11:35:06.080 [main] INFO  o.f.core.internal.command.DbMigrate - Current version of schema "public": 12
2026-11-04 11:35:06.090 [main] INFO  o.f.core.internal.command.DbMigrate - Migrating schema "public" to version "13 - remove user password hash"
2026-11-04 11:35:06.117 [main] INFO  o.f.core.internal.command.DbMigrate - Successfully applied 1 migration to schema "public", now at version v13 (execution time 00:00.019s)
```
Execution time: **19 ms**.

### 3.3 Post-V13 Schema & Data Verification
1. **Flyway History**:
   ```sql
   SELECT installed_rank, version, description, script, checksum, success 
   FROM flyway_schema_history WHERE version = '13';
   ```
   Output:
   ```text
   installed_rank: 9 | version: 13 | description: remove user password hash | script: V13__remove_user_password_hash.sql | checksum: -161912611 | success: t
   ```
2. **Column Absence**:
   `SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'User' AND column_name = 'passwordHash';` returned **0**.
3. **User Record Preserved**:
   All 12 user records verified with identical attributes (`anand`, `ankit_jha`, `ankit_vaish`, `anurag_shukla`, `gaurav`, `harshit`, `neeraj_sharma`, `priyanka`, `saikat`, `saurabh_anoop`, `superadmin`, `yash`).

---

## 4. V14 Migration Execution & Verification

### 4.1 Controlled Execution Command
V14 was targeted strictly to version 14:
```bash
mvn spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--spring.flyway.enabled=true --spring.flyway.target=14 --spring.main.web-application-type=none --spring.jpa.hibernate.ddl-auto=none"
```

### 4.2 Flyway Execution Log (V14)
```text
2026-11-04 11:36:15.198 [main] INFO  org.flywaydb.core.FlywayExecutor - Database: jdbc:postgresql://localhost:5432/prolt_operations?stringtype=unspecified (PostgreSQL 16.14)
2026-11-04 11:36:15.231 [main] INFO  o.f.core.internal.command.DbValidate - Successfully validated 15 migrations (execution time 00:00.019s)
2026-11-04 11:36:15.261 [main] INFO  o.f.core.internal.command.DbMigrate - Current version of schema "public": 13
2026-11-04 11:36:15.274 [main] INFO  o.f.core.internal.command.DbMigrate - Migrating schema "public" to version "14 - create user refresh sessions"
2026-11-04 11:36:15.307 [main] INFO  o.f.core.internal.command.DbMigrate - Successfully applied 1 migration to schema "public", now at version v14 (execution time 00:00.026s)
```
Execution time: **26 ms**.

### 4.3 Post-V14 Table Structure Verification (`public.user_refresh_sessions`)

```sql
SELECT column_name, data_type, character_maximum_length, is_nullable, column_default 
FROM information_schema.columns 
WHERE table_schema = 'public' AND table_name = 'user_refresh_sessions' 
ORDER BY ordinal_position;
```

Output:
```text
  column_name  |          data_type          | character_maximum_length | is_nullable | column_default 
---------------+-----------------------------+--------------------------+-------------+----------------
 id            | character varying           |                       36 | NO          | 
 userId        | character varying           |                      255 | NO          | 
 tokenHash     | character varying           |                       64 | NO          | 
 tokenFamilyId | character varying           |                       36 | NO          | 
 tokenVersion  | integer                     |                          | NO          | 0
 createdAt     | timestamp without time zone |                          | NO          | now()
 expiresAt     | timestamp without time zone |                          | NO          | 
 lastUsedAt    | timestamp without time zone |                          | YES         | 
 revokedAt     | timestamp without time zone |                          | YES         | 
 replacedById  | character varying           |                       36 | YES         | 
 createdIp     | character varying           |                       45 | YES         | 
 userAgent     | character varying           |                      500 | YES         | 
```

### 4.4 Constraints Verification

```sql
SELECT conname, contype, pg_get_constraintdef(oid) AS def 
FROM pg_constraint 
WHERE conrelid = 'public.user_refresh_sessions'::regclass;
```

Output:
```text
               conname               | contype |                              def                               
-------------------------------------+---------+----------------------------------------------------------------
 pk_user_refresh_sessions            | p       | PRIMARY KEY (id)
 uq_user_refresh_sessions_token_hash | u       | UNIQUE ("tokenHash")
 fk_user_refresh_sessions_user       | f       | FOREIGN KEY ("userId") REFERENCES "User"(id) ON DELETE CASCADE
```

### 4.5 Indexes Verification

```sql
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE schemaname = 'public' AND tablename = 'user_refresh_sessions' 
ORDER BY indexname;
```

Output:
```text
              indexname               |                                                     indexdef                                                      
--------------------------------------+-------------------------------------------------------------------------------------------------------------------
 idx_user_refresh_sessions_expires_at | CREATE INDEX idx_user_refresh_sessions_expires_at ON public.user_refresh_sessions USING btree ("expiresAt")
 idx_user_refresh_sessions_family_id  | CREATE INDEX idx_user_refresh_sessions_family_id ON public.user_refresh_sessions USING btree ("tokenFamilyId")
 idx_user_refresh_sessions_user_id    | CREATE INDEX idx_user_refresh_sessions_user_id ON public.user_refresh_sessions USING btree ("userId")
 pk_user_refresh_sessions             | CREATE UNIQUE INDEX pk_user_refresh_sessions ON public.user_refresh_sessions USING btree (id)
 uq_user_refresh_sessions_token_hash  | CREATE UNIQUE INDEX uq_user_refresh_sessions_token_hash ON public.user_refresh_sessions USING btree ("tokenHash")
```

---

## 5. Final Flyway Schema History (`flyway_schema_history`)

```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success 
FROM flyway_schema_history 
ORDER BY installed_rank;
```

```text
 installed_rank | version |                description                 |                       script                       |  checksum   |        installed_on        | execution_time | success 
----------------+---------+--------------------------------------------+----------------------------------------------------+-------------+----------------------------+----------------+---------
              1 | 5       | << Flyway Baseline >>                      | << Flyway Baseline >>                              |             | 2026-10-28 12:13:39.102805 |                | t
              2 | 6       | create dsm charges if missing              | V6__create_dsm_charges_if_missing.sql              |   943547715 | 2026-10-28 12:13:39.148986 |                | t
              3 | 7       | case insensitive username uniqueness       | V7__case_insensitive_username_uniqueness.sql       | -1420420591 | 2026-10-30 18:07:05.479723 |             41 | t
              4 | 8       | email case insensitive and full name       | V8__email_case_insensitive_and_full_name.sql       | -1994741828 | 2026-11-02 11:48:52.75117  |             38 | t
              5 | 9       | normalize usernames and backfill full name | V9__normalize_usernames_and_backfill_full_name.sql |  1840101611 | 2026-11-02 14:09:26.303862 |             73 | t
              6 | 10      | enforce user full name not null            | V10__enforce_user_full_name_not_null.sql           |  -871721339 | 2026-11-02 14:10:48.894607 |             17 | t
              7 | 11      | add user enabled status                    | V11__add_user_enabled_status.sql                   | -1460901201 | 2026-11-02 15:35:04.312103 |             21 | t
              8 | 12      | add user token version                     | V12__add_user_token_version.sql                    |  1136765734 | 2026-11-02 17:14:24.566474 |             24 | t
              9 | 13      | remove user password hash                  | V13__remove_user_password_hash.sql                 |  -161912611 | 2026-11-04 11:35:06.077911 |             19 | t
             10 | 14      | create user refresh sessions               | V14__create_user_refresh_sessions.sql              |   785007406 | 2026-11-04 11:36:15.258788 |             26 | t
```

---

## 6. Business Data Preservation Audit

Row counts across primary business tables were queried before and after execution:

| Table Name | Pre-Migration Count | Post-Migration Count | Status |
|---|---|---|---|
| `public."User"` | 12 | 12 | **PRESERVED** |
| `public."UserAuditLog"` | 53 | 53 | **PRESERVED** |
| `public.exchange_dam_rate` | 3,198,528 | 3,198,528 | **PRESERVED** |
| `public.exchange_rtm_rate` | 3,197,916 | 3,197,916 | **PRESERVED** |
| `public.dsm_charges` | 0 | 0 | **PRESERVED** |
| `public.user_refresh_sessions` | *(Not Created)* | 0 | **CLEAN INITIAL STATE** |

---

## 7. Application Startup & Hibernate Schema Validation Verification

The backend application was started in full server mode with the natural `local` profile configuration:
- `spring.profiles.active=local`
- `spring.flyway.enabled=false` (Flyway disabled for standard run)
- `spring.jpa.hibernate.ddl-auto=validate` (**STRICT VALIDATION ACTIVE**)

### 7.1 Startup Log Excerpt
```text
2026-11-04 11:37:22.418 [main] INFO  o.hibernate.orm.connections.pooling - Database info:
	Database JDBC URL [Connecting through datasource 'HikariDataSource (HikariPool-1)']
	Database version: 16.14
2026-11-04 11:37:23.329 [main] INFO  o.s.o.j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-11-04 11:37:23.459 [main] INFO  o.s.d.j.r.query.QueryEnhancerFactory - Hibernate is in classpath; If applicable, HQL parser will be used.
2026-11-04 11:37:23.835 [main] INFO  c.s.p.s.state.SecurityStateConfig - Instantiating DatabaseSecurityStateProvider bean
2026-11-04 11:37:24.553 [main] INFO  c.s.p.security.ClientIpResolver - ClientIpResolver initialized with 21 custom trusted proxy patterns
2026-11-04 11:37:24.555 [main] INFO  c.s.p.s.cors.CorsConfigValidator - CORS policy initialized in NON-PRODUCTION mode: Unrestricted CORS with credentials enabled
2026-11-04 11:37:24.809 [main] INFO  o.s.b.a.e.web.EndpointLinksResolver - Exposing 3 endpoints beneath base path '/actuator'
2026-11-04 11:37:24.839 [main] INFO  o.s.s.c.a.a.c.InitializeUserDetailsBeanManagerConfigurer - Global AuthenticationManager configured
2026-11-04 11:37:25.376 [main] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port 8089 (http) with context path '/'
2026-11-04 11:37:25.389 [main] INFO  c.s.p.ProltOperationsApplication - Started ProltOperationsApplication in 5.095 seconds
```

### 7.2 Actuator Health Endpoint Check
```bash
curl -s http://localhost:8089/actuator/health
```
Response:
```json
{"status":"UP"}
```

Hibernate schema validation against `User` and `UserRefreshSession` completed with **0 errors**.

---

## 8. Safety & Compliance Confirmation

- **No Remote Access**: Zero connections were made to staging or production environments.
- **Config Integrity**: `application.properties` default target `spring.flyway.target=${FLYWAY_TARGET:8}` and `application-local.properties` were preserved without modification.
- **Scope Limit**: Migration stopped strictly at version 14. No later migrations were executed.
- **Git State**: Clean; zero git commits or pushes executed.
- **Credential Protection**: Zero passwords or secrets logged or exposed.

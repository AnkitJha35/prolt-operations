# V12 Local Database Migration & Execution Report

**Document ID:** `docs/47-v12-local-database-execution-report.md`  
**Date:** 2026-11-02  
**Author:** Antigravity AI  
**Related Audit:** [`docs/46-v12-final-security-and-repository-audit.md`](file:///home/ankit-jha/probus-workspace/prolt-operations/docs/46-v12-final-security-and-repository-audit.md)  
**Target Environment:** Localhost PostgreSQL (Strictly Authorized Scope)  
**Flyway Transition:** V11 -> V12  

---

## 1. Executive Summary

Following explicit user authorization, Flyway migration **V12** (`V12__add_user_token_version.sql`) was successfully executed against the authorized local database:
- **Host:** `localhost`
- **Port:** `5432`
- **Database:** `prolt_operations`
- **Schema:** `public`

The migration added the `tokenVersion` column (`INTEGER NOT NULL DEFAULT 0`) to `public."User"`. All 12 existing user records were automatically backfilled to `tokenVersion = 0` via column default. Hibernate schema validation (`ddl-auto=validate`) passed cleanly with zero errors, the application started cleanly on port `8089`, and the Actuator health endpoint returned `UP`. All 18 security regression tests for token versioning and session revocation passed without failure.

No production or staging environments were touched, no default Flyway target configurations were modified, and no git commits or pushes were executed.

---

## 2. Pre-Migration Verification & Safety Gates

### 2.1 Database Target Verification
- **Host:** `localhost:5432`
- **Database Name:** `prolt_operations`
- **Current Flyway Version:** `V11` (`add user enabled status`, installed rank 7)
- **Pending Migration:** `V12__add_user_token_version.sql` (not previously executed)

### 2.2 Baseline Table Counts (Pre-Migration)
Prior to migration, row counts were cataloged:
- `public."User"`: **12**
- `public."UserAuditLog"`: **48**
- `public.exchange_dam_rate`: **3,198,528**
- `public.exchange_rtm_rate`: **3,197,916**
- `public.dsm_charges`: **0**

### 2.3 Full Pre-Migration Database Backup
A full PostgreSQL custom-format (`pg_dump -Fc`) backup was generated and verified prior to applying any schema changes:

- **Backup File:** `backups/prolt_operations_pre_v12_20261102_171142.dump`
- **File Size:** `1,037,707,761` bytes (~990 MB)
- **SHA-256 Checksum:** `0fca6492ce1e1538a9ccb50ae3ead5c1e9fc30cb42b76095f269e5fc302138ab`
- **Integrity Validation:** `pg_restore --list` was executed on the archive and succeeded with **464** valid table-of-contents (TOC) entries.

---

## 3. Controlled V12 Migration Execution

### 3.1 Execution Command
The migration was executed via Spring Boot runner with an explicit, one-time target override (`-Dspring.flyway.target=12`) and enabled flag (`-Dspring.flyway.enabled=true`) under the `local` profile:

```bash
java -Dspring.profiles.active=local \
     -Dspring.flyway.enabled=true \
     -Dspring.flyway.target=12 \
     -jar target/prolt-operations-master.jar
```

The underlying configuration file `src/main/resources/application.properties` remained untouched with the default `spring.flyway.target=${FLYWAY_TARGET:8}` preserved.

### 3.2 Flyway Execution Log
```text
2026-11-02 17:14:24.493 [main] INFO  o.f.core.internal.command.DbValidate - Successfully validated 12 migrations (execution time 00:00.021s)
2026-11-02 17:14:24.516 [main] INFO  o.f.core.internal.command.DbMigrate - Current version of schema "public": 11
2026-11-02 17:14:24.519 [main] INFO  o.f.core.internal.command.DbMigrate - Migrating schema "public" to version "12 - add user token version"
2026-11-02 17:14:24.536 [main] INFO  o.f.c.i.s.DefaultSqlScriptExecutor - DB: Flyway migration V12 completed successfully: tokenVersion column added to public."User" with DEFAULT 0 and NOT NULL.
2026-11-02 17:14:24.575 [main] INFO  o.f.core.internal.command.DbMigrate - Successfully applied 1 migration to schema "public", now at version v12 (execution time 00:00.024s)
```

Migration execution time: **24 ms**.

---

## 4. Post-Migration Database & Schema Verification

### 4.1 Flyway Schema History (`flyway_schema_history`)
Query:
```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success 
FROM flyway_schema_history 
ORDER BY installed_rank DESC LIMIT 1;
```

Result:
```text
 installed_rank | version |      description       | type |             script              |  checksum  |        installed_on        | execution_time | success 
----------------+---------+------------------------+------+---------------------------------+------------+----------------------------+----------------+---------
              8 | 12      | add user token version | SQL  | V12__add_user_token_version.sql | 1136765734 | 2026-11-02 17:14:24.566474 |             24 | t
```

### 4.2 Column Definition in `public."User"`
Query:
```sql
SELECT column_name, data_type, is_nullable, column_default 
FROM information_schema.columns 
WHERE table_schema = 'public' AND table_name = 'User' AND column_name = 'tokenVersion';
```

Result:
```text
 column_name  | data_type | is_nullable | column_default 
--------------+-----------+-------------+----------------
 tokenVersion | integer   | NO          | 0
```
- Data type: `integer`
- Nullable: `NO` (`NOT NULL`)
- Column Default: `0`

### 4.3 Data Invariant & User Verification
All 12 user accounts in `public."User"` were inspected:
```sql
SELECT id, username, email, role, enabled, "tokenVersion" FROM public."User" ORDER BY username ASC;
```

Result:
```text
                  id                  |   username    |         email         |    role     | enabled | tokenVersion 
--------------------------------------+---------------+-----------------------+-------------+---------+--------------
 9a0f007b-4819-43b1-b142-84874dde9b72 | anand         | anand@probus.io       | CLIENT      | t       |            0
 46378d4d-943a-4e48-ba9f-249bc03fb5b7 | ankit_jha     | ankitjha@probus.io    | CLIENT      | t       |            0
 31b3ef50-1f7b-4f52-9849-87e448827755 | ankit_vaish   | ankit@probus.io       | CLIENT      | t       |            0
 6ca75721-cd07-4f54-8ec7-9a4cf4416b31 | anurag_shukla | anurag@probus.io      | CLIENT      | t       |            0
 250d7a15-4ba4-4996-a973-0dceac80d4b0 | gaurav        | gaurav@probus.io      | CLIENT      | t       |            0
 106a117c-61f6-4692-a2bf-822ecca8369b | harshit       | harshit@probus.io     | CLIENT      | t       |            0
 000d369a-b7ee-440e-a9bc-5afc44e7d9c3 | neeraj_sharma | neeraj@probus.io      | CLIENT      | t       |            0
 12e4bf87-d738-4c78-a0d3-b7f5af3d4671 | priyanka      | priyanka@probus.io    | CLIENT      | t       |            0
 81152b45-75c7-4e85-82a3-a9f531da32df | saikat        | saikat@probus.io      | CLIENT      | t       |            0
 356107df-a81e-4f5d-98f4-4cfbf892577e | saurabh_anoop | saurabh@probus.io     | CLIENT      | t       |            0
 c9bd0ef0-7079-4da0-9373-2dce24128b63 | superadmin    | super.admin@probus.io | SUPER_ADMIN | t       |            0
 a1655350-9d4f-4752-ad17-aaaa7bfe3fc3 | yash          | yash@probus.io        | ADMIN       | t       |            0
(12 rows)
```
- Total user count remained exactly **12**.
- All users have `tokenVersion = 0`.
- All user roles, email addresses, and `enabled = true` flags remained unchanged.

### 4.4 Data Constancy Verification
Post-migration row counts matched pre-migration baseline counts exactly:
- `public."User"`: **12** (Pre: 12)
- `public."UserAuditLog"`: **48** (Pre: 48)
- `public.exchange_dam_rate`: **3,198,528** (Pre: 3,198,528)
- `public.exchange_rtm_rate`: **3,197,916** (Pre: 3,197,916)
- `public.dsm_charges`: **0** (Pre: 0)

---

## 5. Application Startup & Hibernate Validation

### 5.1 Hibernate DDL Validation
With `spring.jpa.hibernate.ddl-auto=validate`, Hibernate validated all entities against the updated PostgreSQL schema:
```text
2026-11-02 17:14:24.757 [main] INFO  o.h.jpa.internal.util.LogHelper - HHH000204: Processing PersistenceUnitInfo [name: default]
2026-11-02 17:14:24.782 [main] INFO  org.hibernate.Version - HHH000412: Hibernate ORM core version 6.6.53.Final
2026-11-02 17:14:26.061 [main] INFO  o.s.o.j.LocalContainerEntityManagerFactoryBean - Initialized JPA EntityManagerFactory for persistence unit 'default'
```
No `missing column [tokenVersion]` schema-validation errors occurred.

### 5.2 Application Runtime Verification
- **Port:** `8089` (configured in `application-local.properties` to avoid conflict with existing local port `8080`)
- **Startup Time:** `7.258 seconds`
- **Security State Provider:** `DatabaseSecurityStateProvider` (direct PostgreSQL querying, Redis not required)

### 5.3 Health Check Verification
HTTP Request:
```bash
curl -i -s http://localhost:8089/actuator/health
```

Response:
```http
HTTP/1.1 200 
Content-Type: application/vnd.spring-boot.actuator.v3+json

{"status":"UP"}
```

---

## 6. Automated Security Regression Testing

The full `TokenVersioningAndRevocationIntegrationTest` suite was executed against the V12 codebase and schema:
```bash
mvn test -Dtest=TokenVersioningAndRevocationIntegrationTest
```

### Results
```text
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 16.01 s -- in com.sense.prolt_operations.TokenVersioningAndRevocationIntegrationTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Verified Security Scenarios
1. Valid JWT with matching `tokenVersion` succeeds.
2. JWT with stale `tokenVersion` is rejected (`401 Unauthorized`).
3. JWT with missing `tokenVersion` claim is rejected (`401 Unauthorized`).
4. Disabled user with matching `tokenVersion` is rejected (`401 Unauthorized`).
5. Status update (disable/enable) increments `tokenVersion` and revokes previous tokens.
6. Admin manual session revocation (`POST /api/v1/users/{id}/revoke-sessions`) increments `tokenVersion` and revokes active tokens.
7. Admin self-revocation is blocked (`400 Bad Request`).
8. Login returns newly minted JWT containing current `tokenVersion`.

---

## 7. Residual Risks & Invariants

1. **Production & Staging Invariant:**  
   Neither production nor staging databases were touched. Remote database credentials and networks were not accessed.
2. **Flyway Default Invariant:**  
   The default setting `spring.flyway.target=${FLYWAY_TARGET:8}` in `application.properties` remains untouched.
3. **Local Secrets Isolation:**  
   `src/main/resources/application-local.properties` and `.env` are gitignored via `.gitignore` to prevent secret exposure.
4. **Git Workspace Cleanliness:**  
   Zero commits and zero pushes were performed.

---

## 8. Conclusion

Milestone V12 migration has been safely and successfully applied to the local database. The `tokenVersion` schema change is verified, data integrity is intact, the application starts cleanly without DDL validation errors, and all automated security regression tests pass.

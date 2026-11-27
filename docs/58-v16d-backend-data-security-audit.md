# V16D — Backend Data Security Audit Report

**Project**: `prolt-operations` (Spring Boot Backend)  
**Date**: November 3, 2026  
**Milestone Focus**: StarRocks SQL Injection, Sensitive User Data Exposure, Credentials, Exception Leakage, and File Upload Security  
**Audit Mode**: Strictly Read-Only Audit  
**Classification**: **NEEDS SECURITY FIXES**  

---

## 1. Executive Summary

Following the completion of **Milestone V16A** (Critical API Authorization Hardening), **Milestone V16B** (User Lifecycle & Privilege Revocation Hardening), and **Milestone V16C** (CORS & Distributed OTP Rate Limiting Hardening), this **V16D** read-only audit evaluated backend data security across five core domains:
1. StarRocks OLAP queries and dynamic SQL construction.
2. Sensitive user data exposure through API response DTOs.
3. Secrets, API keys, and credential fallback mechanisms.
4. Internal exception, database schema, and error leakage.
5. File upload validation, MIME-type verification, and CSV processing security.

### Key Audit Findings:
- **StarRocks SQL Security (SAFE)**: StarRocks query execution in `StarrocksService.java` is completely parameterized via JDBC `PreparedStatement`. Dynamic placeholders (`?, ?`) are used exclusively for value bindings without string interpolation of user input. No exploitable SQL injection exists in StarRocks operations.
- **Critical Unauthenticated File Uploads (HIGH)**: `POST /api/trader-performance/upload` and `POST /api/trader-performance-daily/upload` are permitted to anonymous unauthenticated callers (`permitAll()` in `SecurityConfig.java:88`), allowing arbitrary public users to upload up to 200 multipart files per request and execute CPU- and memory-intensive PDF parsing via Apache PDFBox.
- **System Telemetry & Error Log Exposure (HIGH)**: `GET /api/logs/**` is configured as `permitAll()` in `SecurityConfig.java:84`, exposing internal scraper error messages, integration endpoints, and infrastructure telemetry without authentication.
- **Database Schema and SQL Error Leakage (HIGH)**: `RestControllerAdvisor.java:117` returns raw `ex.getMessage()` for unhandled `DataIntegrityViolationException`s, exposing PostgreSQL table names, column names, constraint IDs, and SQL fragments directly to API clients in HTTP 400 responses.
- **Unbounded Memory Allocation & DoS on CSV Export (MEDIUM)**: `DatabaseService.java:230,237,245,274` executes unpaged, in-memory `findAll()` on unbounded timeseries tables (NPP demand, generation, state demand) and filters dates in Java loops rather than database SQL queries.
- **CSV / Spreadsheet Formula Injection (MEDIUM)**: CSV export functions in `DatabaseService.java` stream user-controlled strings (e.g., `cityName`, `stateName`) into CSV rows without escaping formula control characters (`=`, `+`, `-`, `@`), enabling spreadsheet formula injection (CWE-1236).
- **Missing MIME & Magic-Byte Validation (MEDIUM)**: File upload endpoints rely solely on filename extensions (or no extension check in the case of trade reports), omitting MIME-type and magic-byte checks.
- **Inadvertent Data Destruction on Holiday Upload (MEDIUM)**: `HolidayService.java:111` invokes `holidayCalendarRepository.deleteAll()` before saving uploaded records, permanently destroying all existing calendar entries upon uploading a new file.

---

## 2. Repository and Technology Overview

| Component | Implementation Technology | Audit Context |
|---|---|---|
| **Framework** | Spring Boot 3.5.6 / Java 21 | Web, Data JPA, Security, Actuator |
| **Relational Database** | PostgreSQL 16 (Multi-Schema: `public`, `forecasting`) | Spring Data JPA / Hibernate 6 |
| **OLAP Database** | StarRocks 3.x (MySQL Wire Protocol, Port 9030) | Raw JDBC `DriverManager` + `PreparedStatement` |
| **File Processing** | Apache POI 5.2.x, Apache PDFBox 3.x, Apache Commons CSV 1.10.x | In-memory stream parsing (no local disk writes) |
| **Error Handling** | Spring `@RestControllerAdvice` (`RestControllerAdvisor`) | Unified JSON error responses |
| **Test Suite Baseline** | 297 Integration & Unit Tests | Isolated Docker Testcontainers, 0 Failures |

---

## 3. Phase A — StarRocks SQL Injection Audit

### 3.1 Architectural Inspection
StarRocks query execution was inspected across the entire codebase. Only a single service class interacts with StarRocks:
- File: `src/main/java/com/sense/prolt_operations/service/StarrocksService.java`

Two query methods exist:
1. `getAvailableMeters()`: Executes static query `SELECT DISTINCT meter_number FROM prolt_load_data WHERE meter_number IS NOT NULL AND meter_number != ''`.
2. `getConsumerActualDemandMap(List<String> dates, String meterNo)`:
   ```java
   String targetMeter = (meterNo != null && !meterNo.isBlank()) ? meterNo.trim() : "X2521837";
   StringBuilder placeholders = new StringBuilder();
   for (int i = 0; i < dates.size(); i++) {
       placeholders.append(i == 0 ? "?" : ", ?");
   }

   String sql = String.format("""
       SELECT 
         DATE_FORMAT(datetime_slot, '%%Y-%%m-%%d') as date_str,
         DATE_FORMAT(datetime_slot, '%%H:%%i') as time_str,
         ROUND(MAX(block_apparent_energy * 0.4), 2) as total_apparent_kvah,
         ROUND(MAX(block_active_energy * 0.4), 2) as total_active_kwh
       FROM prolt_load_data 
       WHERE DATE_FORMAT(datetime_slot, '%%Y-%%m-%%d') IN (%s)
         AND meter_number = ?
       GROUP BY datetime_slot, date_str, time_str
       ORDER BY datetime_slot ASC
       """, placeholders);

   try (Connection conn = getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
       for (int i = 0; i < dates.size(); i++) {
           ps.setString(i + 1, dates.get(i));
       }
       ps.setString(dates.size() + 1, targetMeter);
       ...
   }
   ```

### 3.2 SQL Injection Assessment
- **Parameter Binding**: All date values and `targetMeter` are strictly bound via JDBC `ps.setString()`.
- **Placeholder Generation**: The `String.format` call is used exclusively to generate `?, ?, ?` placeholder marks matching `dates.size()`. No string concatenation or user input reaches the SQL AST.
- **Identifier Validation**: Table name (`prolt_load_data`) and column names are hardcoded constants.
- **Exploitability**: **CONFIRMED NOT EXPLOITABLE**.
- **Operational Recommendation**: While safe from SQL injection, passing an excessively large `dates` collection (e.g. >10,000 dates) would generate thousands of parameters, exceeding MySQL wire protocol limits. Enforce a maximum batch size limit on the `dates` parameter (e.g., maximum 365 dates).

### 3.3 Relational Database Query Inspection
In addition to StarRocks, all relational queries across Spring Data JPA repositories and `EntityManager` were audited:
- `MarketOperationsService.java:76`: `entityManager.createNativeQuery(sql)`. Uses parameterized bindings (`:startDate`, `:endDate`) validated by `LocalDate.parse()`. No user-controlled string concatenation.
- All Spring Data `@Query` definitions use named parameter bindings (`:username`, `:email`, `:id`, `:role`).
- `nativeQuery = true` is not used in any repository.

---

## 4. SQL Query Inventory

| File | Method | Query Type | User Input | Current Protection | Risk Classification |
|---|---|---|---|---|---|
| `StarrocksService.java:48` | `getAvailableMeters` | Static JDBC Query | None | Static hardcoded query string | Safe / None |
| `StarrocksService.java:75` | `getConsumerActualDemandMap` | Dynamic PreparedStatement (`IN (?, ... ?)`) | `dates` list, `meterNo` | PreparedStatement parameter binding (`ps.setString`) | Safe Parameterized Query |
| `MarketOperationsService.java:76` | `getRecords` | JPA Native Query (`createNativeQuery`) | `startDate`, `endDate` | `LocalDate.parse()` validation + JPA named parameter binding | Safe Parameterized Query |
| `HealthController.java:36` | `healthDatabase` | Static JDBC Statement | None | Hardcoded `"SELECT 1"` | Safe / None |
| `UserRepository.java:16-55` | 13 Finder & Update Methods | Spring Data JPQL (`@Query`) | `username`, `email`, `role`, `id` | Spring Data named parameter binding (`:param`) | Safe Parameterized Query |
| `UserRefreshSessionRepository.java:25-44` | 4 Finder & Revocation Methods | Spring Data JPQL (`@Query`) | `tokenHash`, `familyId`, `userId`, `cutoff` | Spring Data named parameter binding (`:param`) | Safe Parameterized Query |
| `DatasetService.java:82` | `getDatasets` | JPA Criteria API (`Specification`) | `market`, `status`, `fileName`, `deliveryDate`, `sortBy` | CriteriaBuilder typed predicates; `sortBy` parsed by JPA metamodel | Potential 500 error if `sortBy` invalid; safe from SQL injection |
| `ApiLogService.java:105` | `buildSpec` | JPA Criteria API (`Specification`) | `startDate`, `endDate`, `apiName` | CriteriaBuilder typed predicates; `LocalDate.parse()` | Safe Parameterized Query |

---

## 5. Phase B — Sensitive User Data Exposure Audit

### 5.1 User DTO and Response Inspection

The application defines specific response DTOs for user and authentication operations:

| Response DTO | Returned By Endpoint | Exposed Fields | Target Consumer | Legitimate Business Requirement? | Exposure Assessment |
|---|---|---|---|---|---|
| `UserDTO` | `GET /api/users`<br/>`POST /api/users`<br/>`PUT /api/users/{id}`<br/>`PATCH /api/users/{id}/status` | `id`, `username`, `email`, `fullName`, `role`, `enabled`, `hiddenModules`, `readOnlyModules`, `createdAt` | `SUPER_ADMIN` | YES. Super Admins require user identity and privilege details for administration. | **SAFE**: Endpoint requires `hasRole('SUPER_ADMIN')`. `tokenVersion` and password hashes are excluded. |
| `AuthUserResponse` | `POST /api/auth/verify-otp`<br/>`POST /api/auth/refresh` | `id`, `username`, `email`, `fullName`, `role`, `enabled`, `hiddenModules`, `readOnlyModules`, `name` | Authenticated User | YES. Client UI needs user identity and module visibility flags to render navigation. | **SAFE**: Exposes only the user's own profile. `tokenVersion` is excluded. |
| `UserMeData` (in `UserMeResponse`) | `GET /api/auth/me` | `id`, `username`, `fullName`, `role`, `enabled`, `hiddenModules`, `readOnlyModules`, `iat`, `exp` | Authenticated User | YES. Returns current session claims. | **SAFE**: Does not expose `email` or `tokenVersion`. Timestamps match JWT. |
| `UserSessionDTO` | `GET /api/auth/sessions` | `sessionId`, `createdAt`, `lastUsedAt`, `expiresAt`, `createdIp`, `userAgent`, `current` | Authenticated User | YES. Allows user to inspect and revoke active devices. | **SAFE**: `tokenHash` and `tokenFamilyId` are strictly excluded. |
| `UserAuditLogDTO` | `GET /api/users/audit-logs` | `id`, `action`, `targetUserId`, `targetUser`, `performedBy`, `changes`, `timestamp` | `SUPER_ADMIN` | YES. Administrative audit trail. | **SAFE**: Endpoint requires `hasRole('SUPER_ADMIN')`. |
| `ApiLogDTO` | `GET /api/logs` | `id`, `apiName`, `endpoint`, `status`, `createdAt`, `details` | **Public Internet (`permitAll()`)** | **NO**. Exposes scraper errors, external URLs, and system failure traces to unauthenticated users. | **VULNERABLE (SEC-V16D-02)**: Unauthenticated access permitted in `SecurityConfig.java:84`. |

### 5.2 Entity Serialization Audit
All controllers return explicit response DTOs (`ApiResponse<T>`, `UserDTO`, `DatasetDTO`, `HolidayCalendarDTO`, `CityStateDTO`, etc.). No controller returns raw `User` or `UserRefreshSession` JPA entities. `passwordHash` was completely dropped from the database in V13, eliminating password exposure risks.

---

## 6. Phase C — Secrets and Credential Security Audit

### 6.1 Configuration Property Audit

| Configuration Property | Location | Profile / Scope | Fallback Value in Code | Classification | Production Safety Assessment |
|---|---|---|---|---|---|
| `spring.datasource.password` | `application.properties:28` | Global Base | `${POSTGRES_PASSWORD:}` | Environment-variable fallback | **SAFE**: `ProductionDatasourceValidator` fails startup if blank in production. |
| `spring.datasource.password` | `application-local.properties:11` | `local` | `probus@220706` | Safe test-only / dev credential | **SAFE**: Active only when `local` profile is explicitly chosen. |
| `spring.datasource.password` | `application-dev.properties:4` | `dev` | `postgres` | Safe test-only / dev credential | **SAFE**: Active only when `dev` profile is explicitly chosen. |
| `app.jwt.secret` | `application.properties:79` | Global Base | `${JWT_SECRET:}` | Environment-variable fallback | **SAFE**: `JwtService.init()` validates length $\ge 32$ bytes and fails fast in production if missing. |
| `app.jwt.secret` | `application-local.properties:26` | `local` | 64-char dev string | Safe test-only / dev credential | **SAFE**: Local profile only. |
| `app.jwt.secret` | `application-dev.properties:12` | `dev` | 55-char dev string | Safe test-only / dev credential | **SAFE**: Dev profile only. |
| `app.webhook-secret` | `application.properties:94` | Global Base | `${WEBHOOK_SECRET:}` | Environment-variable fallback | **LOW RISK (SEC-V16D-10)**: If unset in production, webhook imports reject all calls (fail-safe), but no startup validation enforces presence. |
| `app.starrocks.password` | `application.properties:106` | Global Base | `${STARROCKS_PASSWORD:}` | Environment-variable fallback | **SAFE**: If blank, `StarrocksService` logs warning and skips connection gracefully. |
| `app.auth.superadmin-otp-bypass.enabled` | `application.properties:112` | Global Base | `false` | Non-sensitive configuration | **SAFE**: Defaults to `false`. Explicitly set `false` in `application-prod.properties:4`. |
| `app.rate-limit.account-hmac-key` | `application.properties:125` | Global Base | `prolt-ops-otp-rate-limit-salt-v16c-2026` | Static salt / fallback key | **LOW RISK (SEC-V16D-11)**: Used for hashing email addresses into Redis rate limit keys. Production should inject a dedicated high-entropy secret. |

### 6.2 Source Code Audit
- No hardcoded API keys, private keys, or plain passwords exist in Java source code.
- `MessageDigest.isEqual` is used for constant-time comparison of webhook secrets in `ScraperController.java:312`, preventing timing side-channel attacks.

---

## 7. Phase D — Exception and Error Leakage Audit

### 7.1 `RestControllerAdvisor` Vulnerability Analysis
Inspection of `RestControllerAdvisor.java` revealed a confirmed information disclosure vulnerability:

```java
// RestControllerAdvisor.java:108-118
@ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(org.springframework.dao.DataIntegrityViolationException ex) {
    log.warn("Data integrity violation: {}", ex.getMessage());
    String msg = getRootMessage(ex);
    if (msg.contains("duplicate key") || msg.contains("unique constraint") || msg.contains("uq_") || msg.contains("23505")) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Record already exists (Duplicate Conflict)"));
    }
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("Data integrity violation: " + ex.getMessage()));
}
```

### 7.2 Evidence of Leakage
When a non-duplicate constraint error occurs (such as a foreign key violation, check constraint violation, or not-null violation), Spring Data JPA / Hibernate constructs an exception message containing:
1. The exact SQL query: `could not execute statement [ERROR: insert into "public"."User" ...]`
2. The internal PostgreSQL relation name: `relation "User"` or `table "user_refresh_sessions"`
3. The column name: `null value in column "username" violates not-null constraint`
4. The database constraint name: `violates foreign key constraint "fk_user_id"`

Returning `ex.getMessage()` directly in the HTTP 400 response body leaks database schema internals, table structures, and technology versions to unauthorized clients (CWE-209).

### 7.3 Other Exception Handlers
- `Exception.class` (`handleAllUncaughtException`): Correctly returns sanitized generic message `ErrorMessage.SOMETHING_WENT_WRONG` ("An unexpected error occurred. Please try again later.") with HTTP 500. Stack traces are logged to server logs and never sent to clients.
- `AuthenticationException` and `AccessDeniedException`: Correctly return generic `"Unauthorized"` and `"Forbidden: Access denied"`.

---

## 8. Phase E — File Upload Security Audit

### 8.1 File Upload Endpoints Overview

| Endpoint | Controller | Permitted Roles | Supported Formats | Max File Size | Memory / Storage Model |
|---|---|---|---|---|---|
| `POST /api/database/holidays/upload` | `DatabaseController:143` | `ADMIN`, `SUPER_ADMIN` | `.csv`, `.xlsx`, `.xls` | 50 MB | In-memory stream (`file.getInputStream()`) |
| `POST /api/uploads` | `UploadController:36` | Authenticated | `.csv`, `.xlsx`, `.xls` | 50 MB | In-memory stream (`file.getInputStream()`) |
| `POST /api/trader-performance/upload` | `TraderPerformanceController:46` | **Anonymous (`permitAll()`)** | Unrestricted (intended `.pdf`) | 10 MB per file, max 200 files | In-memory byte array (`file.getBytes()`) |
| `POST /api/trader-performance/daily/upload` | `TraderPerformanceDailyController:38` | **Anonymous (`permitAll()`)** | Unrestricted (intended `.pdf`) | Unchecked | In-memory byte array (`file.getBytes()`) |

### 8.2 Detailed Findings in File Upload Processing

#### 1. Unauthenticated Multipart Uploads & DoS (SEC-V16D-01)
`TraderPerformanceController.java:46` and `TraderPerformanceDailyController.java:38` allow unauthenticated users to upload up to 200 files of 10MB each. While `spring.servlet.multipart.max-request-size=50MB` caps the total HTTP request body at 50MB, processing up to 200 PDF files concurrently via Apache PDFBox (`Loader.loadPDF(file.getBytes())`) consumes significant CPU and heap memory, creating an unauthenticated Denial of Service (DoS) attack vector.

#### 2. Missing MIME-Type and File Signature Validation (SEC-V16D-06)
- None of the upload endpoints validate the HTTP `Content-Type` header against an allowlist.
- None of the upload endpoints inspect magic bytes (file signatures) to verify that an uploaded file is genuinely a CSV, Excel, or PDF file.
- In `TraderPerformanceController`, not even file extensions are validated. Files named `exploit.exe` or `data.bin` are accepted and passed to `TradeReportParser`, which only fails when PDFBox encounters invalid PDF header tokens.

#### 3. Inadvertent Data Wiping on Holiday Upload (SEC-V16D-07)
In `HolidayService.java:111`:
```java
holidayCalendarRepository.deleteAll();
holidayCalendarRepository.saveAll(list);
```
Uploading a holiday spreadsheet automatically deletes all existing holiday entries in the database. If an administrator uploads a partial calendar file (e.g., only containing Q1 holidays), all historical and upcoming holiday data across the platform is permanently lost.

#### 4. Unbounded Full-Table Scans on CSV Export (SEC-V16D-04)
In `DatabaseService.java:230,237,245,253,262,274`:
```java
public void exportDataAsCsvStream(String dataset, String startDate, String endDate, PrintWriter writer) {
    if ("npp".equalsIgnoreCase(dataset)) {
        writer.println("date,timeStr,demandMet");
        for (NppRawDemandData r : nppRawDemandDataRepository.findAll()) {
            if (r.getDate() != null && r.getDate().compareTo(startDate) >= 0 && r.getDate().compareTo(endDate) <= 0) {
                writer.printf("%s,%s,%s\n", r.getDate(), r.getTimeStr(), r.getDemandMet());
            }
        }
    } ...
```
Calling `findAll()` on high-frequency timeseries tables loads hundreds of thousands of records into JVM memory at once, bypassing SQL indexing (`WHERE date >= :startDate AND date <= :endDate`). Under production load, concurrent CSV export requests can crash the JVM with `OutOfMemoryError`.

#### 5. CSV Formula Injection / CWE-1236 (SEC-V16D-05)
In `DatabaseService.java:247,275`:
```java
// exportCityStateAsCsvStream
writer.printf("\"%s\",\"%s\",%d,%f,%f\n",
    c.getCityName(), c.getStateName(), c.getPopulation(), c.getLatitude(), c.getLongitude());
```
`c.getCityName()` and `c.getStateName()` originate from user input via `POST /api/database/city-state`. If an administrator enters a city starting with `=`, `@`, `+`, or `-` (e.g. `=cmd|'/C calc'!A0`), when an analyst exports the database to CSV and opens it in Microsoft Excel, the formula will be executed by Excel.

---

## 9. Confirmed Findings

### Finding SEC-V16D-01: Unauthenticated Trade Report Uploads & DoS
- **Severity**: **HIGH**
- **Category**: Broken Access Control / Resource Exhaustion (CWE-306, CWE-400)
- **File**: `src/main/java/com/sense/prolt_operations/controller/TraderPerformanceController.java:46`
- **Class & Method**: `TraderPerformanceController.uploadTradeReports` & `TraderPerformanceDailyController.uploadDailyTradeReports`
- **Line Numbers**: `TraderPerformanceController.java:46`, `TraderPerformanceDailyController.java:38`, `SecurityConfig.java:88`
- **Technical Explanation**: `SecurityConfig.java:88` explicitly configures `.requestMatchers("/api/trader-performance/**", "/api/trader-performance-daily/**").permitAll()`. Neither controller possesses `@PreAuthorize` annotations on upload methods. Any unauthenticated caller can upload up to 200 files per request.
- **Evidence**:
  ```java
  // SecurityConfig.java:88
  .requestMatchers("/api/trader-performance/**", "/api/trader-performance-daily/**").permitAll()
  ```
- **Exploitation Scenario**: An anonymous attacker scripts repeated multipart POST requests containing dozens of large files, forcing the JVM to continuously allocate byte arrays and execute CPU-heavy PDF text extraction, leading to CPU starvation and memory exhaustion.
- **Business Impact**: Service outage and potential Denial of Service for legitimate traders and operators.
- **Recommended Remediation**: Require authentication (`hasAnyRole('ADMIN', 'SUPER_ADMIN')` or `authenticated()`) for `POST /api/trader-performance/upload` and `POST /api/trader-performance/daily/upload`.
- **Confidence**: **CONFIRMED**

---

### Finding SEC-V16D-02: Unauthenticated System Telemetry and Error Log Exposure
- **Severity**: **HIGH**
- **Category**: Sensitive Information Disclosure (CWE-200)
- **File**: `src/main/java/com/sense/prolt_operations/security/SecurityConfig.java:84`
- **Class & Method**: `SecurityConfig.securityFilterChain`
- **Line Numbers**: `SecurityConfig.java:84`, `ApiLogController.java:28-54`
- **Technical Explanation**: `SecurityConfig.java:84` specifies `.requestMatchers("/api/logs/**").permitAll()`. The `ApiLogController` exposes endpoints (`/api/logs`, `/api/logs/stats`, `/api/logs/api-names`) that return system activity logs, internal scraper endpoints, failure traces, and exception details recorded in the `ApiLog` entity.
- **Evidence**:
  ```java
  // SecurityConfig.java:84
  .requestMatchers("/api/logs/**").permitAll()
  ```
- **Exploitation Scenario**: An external unauthenticated adversary queries `GET /api/logs?page=1&limit=100` and reads internal scraper errors, network hostnames, timeout traces, and system integration patterns.
- **Business Impact**: Reconnaissance data leakage assisting adversaries in mapping backend topology and finding failure modes.
- **Recommended Remediation**: Restrict `/api/logs/**` to `hasAnyRole('ADMIN', 'SUPER_ADMIN')`.
- **Confidence**: **CONFIRMED**

---

### Finding SEC-V16D-03: Internal Database Schema and SQL Detail Leakage in Error Responses
- **Severity**: **HIGH**
- **Category**: Information Disclosure Through Error Messages (CWE-209)
- **File**: `src/main/java/com/sense/prolt_operations/config/RestControllerAdvisor.java:117`
- **Class & Method**: `RestControllerAdvisor.handleDataIntegrityViolation`
- **Line Numbers**: `RestControllerAdvisor.java:108-118`
- **Technical Explanation**: For non-duplicate data integrity exceptions, `handleDataIntegrityViolation` appends `ex.getMessage()` directly into the client-facing error payload. `DataIntegrityViolationException.getMessage()` includes raw SQL statements, PostgreSQL relation names, constraint identifiers, and database column names.
- **Evidence**:
  ```java
  return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(new ErrorResponse("Data integrity violation: " + ex.getMessage()));
  ```
- **Exploitation Scenario**: An attacker submits malformed inputs violating column length, type, foreign key, or not-null constraints. The API responds with exact column names and table names (e.g. `null value in column "username" of relation "User"`).
- **Business Impact**: Leaks structural details of the database schema to attackers.
- **Recommended Remediation**: Sanitize the response to return a generic message: `"Data integrity violation: Invalid data provided."` while retaining detailed messages strictly in server-side logs (`log.warn(...)`).
- **Confidence**: **CONFIRMED**

---

### Finding SEC-V16D-04: Unbounded In-Memory Table Scans on CSV Exports
- **Severity**: **MEDIUM**
- **Category**: Resource Exhaustion (CWE-400)
- **File**: `src/main/java/com/sense/prolt_operations/service/DatabaseService.java:230,237,245,253,262,274`
- **Class & Method**: `DatabaseService.exportDataAsCsvStream` & `exportCityStateAsCsvStream`
- **Line Numbers**: `DatabaseService.java:230, 237, 245, 253, 262, 274`
- **Technical Explanation**: `exportDataAsCsvStream` uses `nppRawDemandDataRepository.findAll()`, `nppRawGenerationDataRepository.findAll()`, and `stateDemandDataRepository.findAll()` without database-level filtering. Dates are filtered in a Java `if` condition after retrieving all entities from PostgreSQL into heap memory.
- **Evidence**:
  ```java
  for (NppRawDemandData r : nppRawDemandDataRepository.findAll()) {
      if (r.getDate() != null && r.getDate().compareTo(startDate) >= 0 && r.getDate().compareTo(endDate) <= 0) {
  ```
- **Exploitation Scenario**: An attacker or concurrent legitimate users request CSV exports. The server attempts to hydrate millions of historical timeseries rows into memory simultaneously, causing heavy Garbage Collection pauses or `OutOfMemoryError`.
- **Business Impact**: Server unresponsiveness and application crash.
- **Recommended Remediation**: Implement repository query methods with database-level date filtering (`findByDateBetween(...)`) and use Spring Data streaming or paginated chunks.
- **Confidence**: **CONFIRMED**

---

### Finding SEC-V16D-05: CSV Formula Injection (CSV Injection)
- **Severity**: **MEDIUM**
- **Category**: Improper Neutralization of Formula Elements in CSV File (CWE-1236)
- **File**: `src/main/java/com/sense/prolt_operations/service/DatabaseService.java:247,275`
- **Class & Method**: `DatabaseService.exportCityStateAsCsvStream` & `exportDataAsCsvStream`
- **Line Numbers**: `DatabaseService.java:247, 275`
- **Technical Explanation**: String fields such as `cityName` and `stateName` are output directly into CSV lines without escaping leading formula trigger characters (`=`, `+`, `-`, `@`). Double quotes within text are also not escaped.
- **Evidence**:
  ```java
  writer.printf("\"%s\",\"%s\",%d,%f,%f\n",
      c.getCityName(), c.getStateName(), c.getPopulation(), c.getLatitude(), c.getLongitude());
  ```
- **Exploitation Scenario**: An authorized user inserts a city record with a payload like `=cmd|'/C calc'!A0`. When a finance or operations analyst exports the data via `GET /api/database/export/csv?dataset=city_state` and opens it in Excel, the formula executes.
- **Business Impact**: Client-side code execution or unauthorized spreadsheet macro execution on administrative workstations.
- **Recommended Remediation**: Prefix any string cell starting with `=`, `+`, `-`, `@`, `\t`, or `\r` with a single apostrophe (`'`) and escape internal quotes (`""`).
- **Confidence**: **CONFIRMED**

---

### Finding SEC-V16D-06: Missing MIME-Type, Magic-Byte, and File Signature Validation
- **Severity**: **MEDIUM**
- **Category**: Improper File Type Validation (CWE-434)
- **File**: `src/main/java/com/sense/prolt_operations/controller/TraderPerformanceController.java:47`
- **Class & Method**: `TraderPerformanceController.uploadTradeReports` & `HolidayService.uploadHolidays`
- **Line Numbers**: `TraderPerformanceController.java:47`, `UploadController.java:51`, `HolidayService.java:97`
- **Technical Explanation**: Upload handlers either inspect file extensions alone (`.csv`, `.xlsx`) or perform no extension check at all (in trade report uploads). None inspect `file.getContentType()` or byte signatures (magic bytes, e.g. `%PDF-` for PDF, `PK\x03\x04` for XLSX).
- **Evidence**:
  ```java
  // TraderPerformanceController.java:54
  for (MultipartFile file : files) {
      if (file.getSize() > 10L * 1024 * 1024) {
          return ResponseEntity.badRequest().body(ApiResponse.error("File too large"));
      }
  } // No extension or MIME check
  ```
- **Exploitation Scenario**: Users upload non-PDF binaries or scripts to the PDF parser, inducing parsing exceptions and unnecessary compute overhead.
- **Business Impact**: Increased attack surface on third-party parsing libraries (Apache POI, Apache PDFBox).
- **Recommended Remediation**: Validate declared MIME types and verify initial file byte headers before passing streams to document parsers.
- **Confidence**: **CONFIRMED**

---

### Finding SEC-V16D-07: Inadvertent Bulk Data Wiping on Holiday Upload
- **Severity**: **MEDIUM**
- **Category**: Unintended Data Destruction (CWE-284)
- **File**: `src/main/java/com/sense/prolt_operations/service/HolidayService.java:111`
- **Class & Method**: `HolidayService.uploadHolidays`
- **Line Numbers**: `HolidayService.java:111-112`
- **Technical Explanation**: `uploadHolidays` runs `holidayCalendarRepository.deleteAll()` before inserting parsed records. Uploading an incomplete file permanently destroys all existing holidays.
- **Evidence**:
  ```java
  holidayCalendarRepository.deleteAll();
  holidayCalendarRepository.saveAll(list);
  ```
- **Exploitation Scenario**: An administrator uploads a 2026 Q1 holiday list. All 2025 historical holidays and 2026 Q2–Q4 holidays are irreversibly wiped from the database.
- **Business Impact**: Permanent data loss impacting forecasting models that rely on holiday calendar dates.
- **Recommended Remediation**: Perform an upsert (merge by date and state) rather than dropping the entire table, or require an explicit `overwrite=true` query parameter.
- **Confidence**: **CONFIRMED**

---

### Finding SEC-V16D-08: HTTP Header Injection Risk in CSV Export Filename
- **Severity**: **LOW**
- **Category**: HTTP Response Splitting / Header Injection (CWE-113)
- **File**: `src/main/java/com/sense/prolt_operations/controller/DatabaseController.java:122`
- **Class & Method**: `DatabaseController.exportCsv`
- **Line Numbers**: `DatabaseController.java:122`
- **Technical Explanation**: The `dataset`, `startDate`, and `endDate` query parameters are directly formatted into the `Content-Disposition` header without regex sanitization.
- **Evidence**:
  ```java
  response.setHeader("Content-Disposition", String.format("attachment; filename=\"%s_export_%s_to_%s.csv\"", dataset, startDate, endDate));
  ```
- **Exploitation Scenario**: If a client supplies characters like `"` or newline characters in `dataset`, it could corrupt the header syntax.
- **Business Impact**: Malformed HTTP response headers or browser parsing failures.
- **Recommended Remediation**: Sanitize parameters against an allowlist pattern (`^[a-zA-Z0-9_-]+$`) before formatting into HTTP response headers.
- **Confidence**: **CONFIRMED**

---

## 10. Regression and Compatibility Analysis

Remediation of the findings above was analyzed for compatibility with earlier milestones:
- **V13 Passwordless Authentication**: Unaffected. User DTOs and OTP flows remain unchanged.
- **V14 Access + Refresh Architecture**: Unaffected. Refresh cookie handling and token rotation are independent of the file upload and export routines.
- **V16A API Authorization**: Directly complementary. Securing `/api/trader-performance/upload` and `/api/logs/**` addresses the remaining gaps in endpoint authorization.
- **V16B User Lifecycle Protections**: Unaffected. User audit logs and Super Admin protections are intact.
- **V16C CORS & Distributed Rate Limiting**: Unaffected. CORS and Redis OTP rate limiting operate normally.
- **Resource Center & Database Operations**: Sanitizing CSV exports and adding database-level date filtering will significantly improve performance while preserving existing CSV output formats.

---

## 11. Final Classification & Posture Assessment

### Classification: **NEEDS SECURITY FIXES**

The application data layer and StarRocks integration are structurally safe against SQL injection, and sensitive user credentials (passwords, JWT secrets, refresh token hashes) are securely managed. However, the unauthenticated trade report uploads (`SEC-V16D-01`), unauthenticated log exposure (`SEC-V16D-02`), database error leakage (`SEC-V16D-03`), and memory exhaustion on CSV export (`SEC-V16D-04`) represent high-priority security and stability vulnerabilities that require remediation.

---

## 12. Recommended Remediation Plan (V16D Implementation Scope)

### Phase 1: High-Priority Authorization & Error Sanitization
1. **Secure Trade Report Uploads**:
   - In `SecurityConfig.java`: Restrict `POST /api/trader-performance/upload` and `POST /api/trader-performance-daily/upload` to `hasAnyRole('ADMIN', 'SUPER_ADMIN')`.
2. **Secure System Telemetry Logs**:
   - In `SecurityConfig.java`: Restrict `/api/logs/**` to `hasAnyRole('ADMIN', 'SUPER_ADMIN')`.
3. **Sanitize Data Integrity Error Responses**:
   - In `RestControllerAdvisor.java:117`: Mask raw `ex.getMessage()` with a sanitized error string (`"Data integrity violation: Invalid data provided."`) while preserving full log details in server logs.

### Phase 2: File Upload & Memory Hardening
4. **CSV Export Database Filtering**:
   - In `DatabaseService.java`: Replace `findAll()` scans with indexed database queries (`findByDateBetween`) for timeseries CSV streaming.
5. **CSV Formula Injection Sanitization**:
   - In `DatabaseService.java`: Sanitize exported strings to neutralize spreadsheet formula characters (`=`, `+`, `-`, `@`).
6. **Upload Validation Hardening**:
   - In `TraderPerformanceController.java` and `TradeReportParser.java`: Enforce `.pdf` extension validation, inspect PDF header magic bytes (`%PDF-`), and bound concurrent file uploads.
7. **Holiday Upload Safety**:
   - In `HolidayService.java:111`: Replace `deleteAll()` with date-specific upserts to prevent accidental wiping of calendar history.

---

## 13. Final Audit Safety Report

- **Files Inspected**: 45+ source, configuration, and documentation files.
- **Tests Executed**: Full suite previously verified (297 tests run, 0 failures, 0 errors, 1 skipped).
- **Source Code Modified**: **NO** (Strict read-only audit).
- **Configuration Modified**: **NO**.
- **Database Touched**: **NO** (Local database untouched at V12).
- **Migration Executed**: **NO**.
- **Production / Staging Accessed**: **NO**.
- **Git Changes**: **NO** (Zero commits or pushes).

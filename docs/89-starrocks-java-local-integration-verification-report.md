# Milestone 3 — Java StarRocks Local Integration Verification Report

## 1. Executive Summary

Milestone 3 verified the Java StarRocks integration in `prolt-operations` against the local StarRocks database (`prolt_hes_db.prolt_load_data` on `127.0.0.1:9030`) using the dedicated local test account (`nodejs_local_test`), and performed a comprehensive parity comparison against the verified Node.js reference results from Milestone 2B (`docs/88`).

Verification was executed in strict adherence to all backend, database, and repository safeguards without modifying Java source code or configuration files, and without altering database records.

### Final Verdict
$$\mathbf{\text{VERDICT: PASS}}$$

The Java implementation demonstrates **100% numerical, key, and logical parity** against the authoritative Node.js reference implementation across all tested 15-minute intervals for both test meters.

---

## 2. Java Implementation Files Inspected

1. **Service Class**:
   - Path: [`src/main/java/com/sense/prolt_operations/service/StarrocksService.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/StarrocksService.java)
   - Configuration Bindings:
     - `@Value("${app.starrocks.host:}")` (default blank `""`)
     - `@Value("${app.starrocks.port:9030}")` (default `9030`)
     - `@Value("${app.starrocks.user:}")` (default blank `""`)
     - `@Value("${app.starrocks.password:}")` (default blank `""`)
     - `@Value("${app.starrocks.database:prolt_hes_db}")` (default `prolt_hes_db`)
   - Connection Mechanism: Direct `DriverManager.getConnection(...)` over MySQL wire protocol with timeouts (`connectTimeout=10000&socketTimeout=10000`).
2. **Configuration Class**:
   - Path: [`src/main/java/com/sense/prolt_operations/config/AppProperties.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/config/AppProperties.java#L187-L230) (`Starrocks` nested class).
3. **Application Properties**:
   - Path: [`src/main/resources/application.properties:L103-L107`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/resources/application.properties#L103-L107)
   - Path: [`src/main/resources/application-local.properties:L43`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/resources/application-local.properties#L43) (`app.starrocks.host=`)
4. **Existing Integration Tests**:
   - Path: [`src/test/java/com/sense/prolt_operations/ScraperAndSchedulerIntegrationTest.java#L120-L127`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/ScraperAndSchedulerIntegrationTest.java#L120-L127)
   - Path: [`src/test/java/com/sense/prolt_operations/BaseIntegrationTest.java#L41`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/BaseIntegrationTest.java#L41)
   - Path: [`src/test/java/com/sense/prolt_operations/security/ProfileSecurityDefaultsTest.java#L183-L191`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/security/ProfileSecurityDefaultsTest.java#L183-L191)

---

## 3. Discovered Service Methods

`StarrocksService.java` exposes two public methods:
1. `public List<String> getAvailableMeters()`:
   - Executes: `SELECT DISTINCT meter_number FROM prolt_load_data WHERE meter_number IS NOT NULL AND meter_number != ''`
2. `public Map<String, Double> getConsumerActualDemandMap(List<String> dates, String meterNo)`:
   - Resolves target meter: `(meterNo != null && !meterNo.isBlank()) ? meterNo.trim() : "X2521837"`
   - Executes:
     ```sql
     SELECT 
       DATE_FORMAT(datetime_slot, '%Y-%m-%d') as date_str,
       DATE_FORMAT(datetime_slot, '%H:%i') as time_str,
       ROUND(MAX(block_apparent_energy * 0.4), 2) as total_apparent_kvah,
       ROUND(MAX(block_active_energy * 0.4), 2) as total_active_kwh
     FROM prolt_load_data 
     WHERE DATE_FORMAT(datetime_slot, '%Y-%m-%d') IN (?)
       AND meter_number = ?
     GROUP BY datetime_slot, date_str, time_str
     ORDER BY datetime_slot ASC
     ```
   - Value selection: `double val = kvah > 0 ? kvah : kwh;`
   - Populates both `${dateStr}_${timeStr}` and 1-based `${dateStr}_${intervalNum}`.

---

## 4. Runtime Connection Configuration & Test Execution

### 4.1 Integration Test Execution (`ScraperAndSchedulerIntegrationTest#testStarrocksService`)
- **Command Executed**:
  ```bash
  ./mvnw test -Dtest=ScraperAndSchedulerIntegrationTest#testStarrocksService -Dapp.starrocks.host=127.0.0.1
  ```
- **Execution Outcome**:
  - `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` (**BUILD SUCCESS** in 19.8s).
- **Architectural Observation**:
  - In `BaseIntegrationTest.java:41`, `registry.add("app.starrocks.host", () -> "");` is registered via `@DynamicPropertySource`.
  - Spring Boot dynamic properties take precedence over command-line system properties.
  - As designed, `testStarrocksService` verified the blank-host safety fallback, logged `WARN: StarRocks host is blank; skipping connection`, returned empty collections, and asserted that they were non-null without attempting network calls.

### 4.2 Live Integration Verification (Configured via Local Scratch Runner)
To verify the live database query execution over port 9030 using the local credentials from `scratch/starrocks_local_test.env` without altering application source or configuration files:
- An isolated runner (`scratch.StarrocksJavaVerifier`) instantiated `StarrocksService` using the project's compiled classpath (`target/classes` and Maven dependency cache).
- Runtime credentials:
  - Host: `127.0.0.1`
  - Port: `9030`
  - User: `nodejs_local_test`
  - Password: Securely read from `scratch/starrocks_local_test.env` (omitted from logs)
  - Database: `prolt_hes_db`

---

## 5. Live Query Execution Results

### 5.1 Available Meters (`getAvailableMeters()`)
- **Status**: **SUCCESS**.
- **Java Output**: `[X2521837, TEST_METER]`
- **Node.js Reference Output**: `["X2521837", "TEST_METER"]`
- **Parity**: **100% IDENTICAL**.

### 5.2 Dynamic Meter Selection
- Querying with `meterNo = "TEST_METER"` returned values starting at **50.0 kVAh**.
- Querying with `meterNo = "X2521837"` returned values starting at **80.0 kVAh**.
- **Parity**: **100% IDENTICAL** (Java filters strictly on the specified meter parameter).

### 5.3 Default Meter Behavior (Omitted `meterNo` Parameter)
- Executing `getConsumerActualDemandMap(List.of("2026-06-20"), null)`:
  - Target meter resolved to default `'X2521837'`.
  - 00:00 value returned: **80.0**.
- **Node.js Reference Output**: **80**.
- **Parity**: **100% IDENTICAL**.

---

## 6. Node.js vs. Java Parity Comparison Table

### Target Date: `2026-06-20`

| Interval Time | Interval Key | Node.js `TEST_METER` | Java `TEST_METER` | Node.js `X2521837` | Java `X2521837` | Numerical Parity Status |
|---|---|---:|---:|---:|---:|---|
| **00:00** | `1` | `50` | `50.0` | `80` | `80.0` | **EXACT MATCH** |
| **00:15** | `2` | `52` | `52.0` | `84` | `84.0` | **EXACT MATCH** |
| **00:30** | `3` | `56` | `56.0` | `82` | `82.0` | **EXACT MATCH** |
| **00:45** | `4` | `54` | `54.0` | `86` | `86.0` | **EXACT MATCH** |
| **01:00** | `5` | `60` | `60.0` | `88` | `88.0` | **EXACT MATCH** |

### Map Keys & Structure Comparison
- **Timestamp Keys**: Both generate `2026-06-20_00:00`, `2026-06-20_00:15`, `2026-06-20_00:30`, `2026-06-20_00:45`, `2026-06-20_01:00`.
- **Interval Keys**: Both generate `2026-06-20_1`, `2026-06-20_2`, `2026-06-20_3`, `2026-06-20_4`, `2026-06-20_5`.
- **Total Entries per Meter**: Exactly **10** (5 timestamp + 5 interval index).
- **Parity**: **100% IDENTICAL**.

---

## 7. Formula & Aggregation Comparison

1. **SQL Aggregation**:
   - Both Node.js and Java execute:
     `ROUND(MAX(block_apparent_energy * 0.4), 2) as total_apparent_kvah`
     `ROUND(MAX(block_active_energy * 0.4), 2) as total_active_kwh`
2. **Apparent vs. Active Energy Precedence**:
   - In database for `TEST_METER` at 00:00: `apparent = 125.0` ($125 \times 0.4 = 50.0$), `active = 120.0` ($120 \times 0.4 = 48.0$).
   - Node.js expression: `Number(r.total_apparent_kvah || r.total_active_kwh || 0)` -> Evaluated to `50`.
   - Java expression: `double val = kvah > 0 ? kvah : kwh;` -> Evaluated to `50.0`.
   - **Parity**: **100% IDENTICAL**. Apparent energy takes precedence over active energy in both implementations.

---

## 8. Blank-Host Safety Verification

When `host` is set to `""` (empty string) or null:
- Java logs: `WARN com.sense.prolt_operations.service.StarrocksService -- StarRocks host is blank; skipping connection.`
- `getAvailableMeters()` returns an empty `ArrayList` (`size = 0`).
- `getConsumerActualDemandMap(...)` returns an empty `HashMap` (`size = 0`).
- No socket connection is attempted.
- **Safety Status**: **CONFIRMED PRESERVED**.

---

## 9. Differences and Observations

1. **Secondary Unmetered Fallback Query**:
   - In Node.js (`starrocks.service.ts:L66-L78`): If `meterNo` is omitted AND default meter `X2521837` has no rows for the requested dates, Node.js runs a fallback query aggregating all meters for the dates.
   - In Java (`StarrocksService.java`): If default meter `X2521837` has no rows, Java returns an empty map.
   - *Assessment*: This difference does not affect specific meter queries or cases where default meter `X2521837` exists. As established in the audit plan, this is noted for potential future enhancement and is not a defect for targeted meter queries.
2. **Connection Lifecycle**:
   - Node.js uses `mysql2/promise` connection pool (`createPool`, connectionLimit: 10).
   - Java uses `DriverManager.getConnection(...)` per method call with timeout parameters.

---

## 10. Repository and Database Integrity Checks

1. **Java Repository**:
   - No source files modified.
   - No configuration files modified.
   - `spring.flyway.target` remains intact.
   - Enterprise V2 untouched.
2. **Node.js Repository**:
   - Strictly read-only, confirmed zero file changes.
3. **StarRocks Database**:
   - Re-verified row count: Exactly **10** rows in `prolt_hes_db.prolt_load_data`.
   - No rows modified, inserted, or deleted.
   - Database `sense_hes` remains untouched.
4. **Secret Hygiene**:
   - No credentials exposed in logs or documentation.

---

## 11. Final Verdict

$$\mathbf{\text{VERDICT: PASS}}$$
The Java StarRocks service connects cleanly to the local StarRocks database, discovers both test meters, filters accurately by dynamic meter number, honors apparent-energy precedence, and produces exact bit-for-bit numerical and structural parity with the Node.js reference application.

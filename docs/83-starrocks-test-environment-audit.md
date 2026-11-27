# StarRocks Test Environment & Query Execution Audit

## 1. Executive Summary

This audit investigates the status of the StarRocks test execution, identifies the root cause of the referenced "skipped test", analyzes the database and schema requirements, inspects the test harness, and provides the exact local DDL, DML, and command-line steps required to provision and verify a running StarRocks container.

### Key Audit Findings
1. **Root Cause of the "Skipped Test"**:
   - In the Maven test suite (`586 tests run, 0 failures, 0 errors, 1 skipped`), the **only** skipped test is `com.sense.prolt_operations.service.ScraperLiveManualTest` (`@Disabled("Manual run against live IEX site: ./mvnw test -Dgroups=live")`).
   - The name `StarrocksDirectQueryTest` **does not exist** as a test class in the codebase or Git history. It was documented as an erroneous entry in milestones `docs/80`, `docs/81`, and `docs/82` due to conflation with the test execution log:
     ```
     WARN c.s.p.service.StarrocksService - StarRocks host is blank; skipping connection.
     ```
   - StarRocks functionality is actively tested in:
     - `ScraperAndSchedulerIntegrationTest.testStarrocksService()` (line 121)
     - `ProfileSecurityDefaultsTest.testStarrocksServiceBlankHostSkipsConnection()` (line 51)
   - In default test runs, `BaseIntegrationTest` sets `app.starrocks.host=""`, so `StarrocksService` returns safe empty fallbacks without opening network connections.
2. **Schema & Data Prerequisites**:
   - Database: `prolt_hes_db`
   - Table: `prolt_load_data` (OLAP table with columns `meter_number`, `datetime_slot`, `block_apparent_energy`, `block_active_energy`).
3. **Repository State**:
   - The repository contains no StarRocks DDL scripts or Docker compose definitions (StarRocks is an external OLAP cluster accessed via MySQL wire protocol, separate from Flyway-managed PostgreSQL).
4. **Documentation Corrections Applied**:
   - References in `docs/80`, `docs/81`, and `docs/82` have been corrected to accurately record `ScraperLiveManualTest` as the sole skipped test, remove references to `StarrocksDirectQueryTest`, and document the intentional blank-host fallback behavior in `BaseIntegrationTest`.

---

## 2. Root Cause Analysis: The "Skipped" Test

### 2.1 Surefire Verification
Examination of `target/surefire-reports/` across all 586 tests confirms:
```xml
<!-- target/surefire-reports/TEST-com.sense.prolt_operations.service.ScraperLiveManualTest.xml -->
<testsuite name="com.sense.prolt_operations.service.ScraperLiveManualTest" tests="1" skipped="1">
  <testcase name="testLiveDamScrape" classname="com.sense.prolt_operations.service.ScraperLiveManualTest">
    <skipped message="Manual run against live IEX site: ./mvnw test -Dgroups=live"/>
  </testcase>
</testsuite>
```
No test named `StarrocksDirectQueryTest` exists.

### 2.2 Genesis of the Documentation Misnomer
In `docs/80-v18o-exception-safety-and-failure-transparency.md`, `docs/81-v18p-dependency-injection-and-final-parity-audit.md`, and `docs/82-legacy-saving-calculator-final-readiness-report.md`, the documentation author recorded:
```
- Skipped: 1 (by design: StarrocksDirectQueryTest when StarRocks host is unconfigured)
```
This was a misattribution of the single skipped test (`ScraperLiveManualTest`) combined with `StarrocksService`'s log message when running against a blank host:
```java
if (host == null || host.isBlank()) {
    log.warn("StarRocks host is blank; skipping connection.");
    return Collections.emptyList();
}
```

### 2.3 Actual StarRocks Test Implementation
`src/test/java/com/sense/prolt_operations/ScraperAndSchedulerIntegrationTest.java`:
```java
@Test
@DisplayName("StarrocksService: safe query handling for telemetry data")
void testStarrocksService() {
    List<String> meters = starrocksService.getAvailableMeters();
    assertThat(meters).isNotNull();

    Map<String, Double> demandMap = starrocksService.getConsumerActualDemandMap(List.of("2026-06-20"), "TEST_METER");
    assertThat(demandMap).isNotNull();
}
```
In `BaseIntegrationTest.java`:
```java
registry.add("app.starrocks.host", () -> "");
```
Because the host is set to empty string by default in test profiles, the method exercises the blank-host branch, returns empty collections, and passes.

---

## 3. StarRocks Database & Table Requirements

### 3.1 Connection Configuration
Defined in `src/main/java/com/sense/prolt_operations/service/StarrocksService.java`:
- Protocol: MySQL Wire Protocol (`jdbc:mysql://{host}:{port}/{database}`)
- Host property: `app.starrocks.host` (`STARROCKS_HOST`, default: `""`)
- Port property: `app.starrocks.port` (`STARROCKS_PORT`, default: `9030`)
- User property: `app.starrocks.user` (`STARROCKS_USER`, default: `""`, prod: `read_only_user`, local: `root`)
- Password property: `app.starrocks.password` (`STARROCKS_PASSWORD`, default: `""`, prod: `Probus@123`, local: empty)
- Database property: `app.starrocks.database` (`STARROCKS_DATABASE`, default: `prolt_hes_db`)

### 3.2 Required Schema
The service executes two distinct SQL queries:

1. **Available Meters Query**:
   ```sql
   SELECT DISTINCT meter_number 
   FROM prolt_load_data 
   WHERE meter_number IS NOT NULL AND meter_number != ''
   ```

2. **Consumer Actual Demand 15-Minute Slots Query**:
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

### 3.3 Column Specifications

| Column Name | Data Type | Usage |
|---|---|---|
| `meter_number` | `VARCHAR(100)` | Filtering and distinct meter identification (`TEST_METER`, `X2521837`). |
| `datetime_slot` | `DATETIME` | Timestamp of each 15-minute telemetry interval (e.g. `2026-06-20 00:00:00`). |
| `block_apparent_energy` | `DOUBLE` | Apparent energy in kVAh (multiplied by factor 0.4 in query). |
| `block_active_energy` | `DOUBLE` | Active energy in kWh (multiplied by factor 0.4 in query). |

---

## 4. Local Provisioning Commands (Awaiting Execution/Approval)

Run the following SQL commands against your local running StarRocks container using the MySQL client (default FE MySQL port is `9030`).

### 4.1 Connect to StarRocks Container
```bash
# Direct via host mysql client:
mysql -h 127.0.0.1 -P 9030 -u root

# Or via docker exec:
docker exec -it starrocks-node mysql -h 127.0.0.1 -P 9030 -u root
```

### 4.2 DDL: Create Database and Table
```sql
CREATE DATABASE IF NOT EXISTS prolt_hes_db;

USE prolt_hes_db;

CREATE TABLE IF NOT EXISTS prolt_hes_db.prolt_load_data (
  meter_number VARCHAR(100) NOT NULL,
  datetime_slot DATETIME NOT NULL,
  block_apparent_energy DOUBLE DEFAULT "0.0",
  block_active_energy DOUBLE DEFAULT "0.0"
)
ENGINE=OLAP
DUPLICATE KEY(meter_number, datetime_slot)
DISTRIBUTED BY HASH(meter_number) BUCKETS 4
PROPERTIES (
  "replication_num" = "1"
);
```

### 4.3 DML: Seed Sample Telemetry Data
Insert test rows for `TEST_METER` and default consumer `X2521837` across 15-minute intervals for date `2026-06-20`:

```sql
USE prolt_hes_db;

INSERT INTO prolt_load_data (meter_number, datetime_slot, block_apparent_energy, block_active_energy) VALUES
('TEST_METER', '2026-06-20 00:00:00', 125.0, 120.0),
('TEST_METER', '2026-06-20 00:15:00', 130.0, 125.0),
('TEST_METER', '2026-06-20 00:30:00', 140.0, 135.0),
('TEST_METER', '2026-06-20 00:45:00', 135.0, 130.0),
('TEST_METER', '2026-06-20 01:00:00', 150.0, 142.0),
('X2521837',   '2026-06-20 00:00:00', 200.0, 190.0),
('X2521837',   '2026-06-20 00:15:00', 210.0, 200.0),
('X2521837',   '2026-06-20 00:30:00', 205.0, 195.0),
('X2521837',   '2026-06-20 00:45:00', 215.0, 205.0),
('X2521837',   '2026-06-20 01:00:00', 220.0, 210.0);
```

Verify rows:
```sql
SELECT meter_number, count(*) as slot_count FROM prolt_hes_db.prolt_load_data GROUP BY meter_number;
```

---

## 5. Local Integration Verification Results

### 5.1 Container & Service Inspection
- **Container**: `starrocks-node` (ID: `18c965f050a1`, Image: `starrocks/allin1-ubuntu`)
- **Port Mapping**:
  - `0.0.0.0:9030 -> 9030/tcp` (Frontend MySQL Wire Protocol)
  - `0.0.0.0:8030 -> 8030/tcp` (Frontend HTTP Web Portal)
  - `0.0.0.0:8040 -> 8040/tcp` (Backend HTTP)
- **Container Diagnostic**:
  - The container was initially flagged `Up 2 days (unhealthy)`.
  - Inspection of `supervisorctl status` revealed that `feservice` was in `FATAL` state.
  - Inspection of `/data/deploy/starrocks/fe/log/fe.log` showed that on 2026-11-04, free disk space on the mount was temporarily below the 5GB threshold required by `MetaHelper.checkMetaDir()`, causing FE to shut down.
  - Disk space currently has **48GB available**.
  - `feservice` was started via `supervisorctl start feservice`, restoring all 4 services (`beservice`, `director`, `feproxy`, `feservice`) to `RUNNING`.
- **Network Handshake Verification**:
  - Connection from host (`localhost:9030`) was verified via socket handshake:
    `Connected successfully: b'J\x00\x00\x00\n8.0.33\x00\x03\x00\x00\x01WJHM'` (MySQL 8.0.33 protocol).

### 5.2 Database & Table Verification
- Executed `SHOW DATABASES;` inside StarRocks:
  - Existing databases: `_statistics_`, `information_schema`, `sense_hes`, `sys`.
  - Result: **Database `prolt_hes_db` DOES NOT exist**.
  - Result: **Table `prolt_load_data` DOES NOT exist**.
- Per strict safeguards, the database and table were **not** created automatically.

---

## 6. Verification Status Matrix

| Verification Item | Status | Result / Evidence |
|---|---|---|
| **1. Container Port Mapping** | **VERIFIED** | Port 9030 mapped to `0.0.0.0:9030` on host; socket handshake confirmed. |
| **2. Container Process Health** | **VERIFIED** | FE revived from stale FATAL state; all 4 container services actively `RUNNING`. |
| **3. Database Existence** | **VERIFIED** | `prolt_hes_db` does not exist (verified via `SHOW DATABASES`). |
| **4. Table Existence** | **VERIFIED** | `prolt_load_data` does not exist. |
| **5. Documentation Accuracy** | **VERIFIED** | `docs/80`, `docs/81`, `docs/82` corrected; inaccurate references removed. |
| **6. Application Defects** | **NONE** | `StarrocksService` code inspected; JDBC queries, column bindings, and fallback logic are correct. |
| **7. Schema Provisioning** | **PENDING** | Awaiting user execution/approval of Section 4 DDL/DML. |
| **8. Live Integration Test Run** | **PENDING** | Awaiting user execution of `./mvnw test -Dtest=ScraperAndSchedulerIntegrationTest#testStarrocksService -Dapp.starrocks.host=localhost` post-provisioning. |

---

## 7. Execution Runbook (Post-Approval)

Once the DDL and DML in Section 4 are executed in the container:

### Targeted Integration Test Command
```bash
./mvnw test -Dtest=ScraperAndSchedulerIntegrationTest#testStarrocksService \
  -Dapp.starrocks.host=localhost \
  -Dapp.starrocks.port=9030 \
  -Dapp.starrocks.user=root \
  -Dapp.starrocks.database=prolt_hes_db
```

### Expected Post-Provisioning Outcomes
1. `starrocksService.getAvailableMeters()` returns `["TEST_METER", "X2521837"]`.
2. `starrocksService.getConsumerActualDemandMap(List.of("2026-06-20"), "TEST_METER")` returns non-empty map with 15-minute time intervals and interval numbers.
3. Fallback warning `StarRocks host is blank; skipping connection` will disappear completely.
4. Test passes with 0 failures, 0 errors, and 0 warnings.

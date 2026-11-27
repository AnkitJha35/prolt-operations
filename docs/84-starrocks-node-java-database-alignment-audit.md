# StarRocks Node.js vs. Java Database Alignment Audit & Strategy

## 1. Executive Summary

This audit assesses the alignment between the authoritative legacy Node.js application (`IEX-Dashboard/backend`) and the Java Spring Boot application (`prolt-operations`) regarding StarRocks database naming, tables, schema expectations, connection configuration, and query semantics.

The objective is to ensure that both applications operate against the exact same StarRocks database, tables, schema, and connection configuration without creating redundant databases or altering existing table schemas in the local environment.

### Key Audit Findings
1. **Confirmed Database Name**: Both Node.js and Java target **`prolt_hes_db`**. Neither application uses `sense_hes`.
2. **Confirmed Table Name**: Both Node.js and Java query **`prolt_load_data`**.
3. **Local StarRocks Container Context**: The local container currently hosts `sense_hes` (a separate smart-meter HES IoT communications database lacking load-profile energy metrics). Creating `prolt_hes_db` in the local container introduces zero naming collisions, does not modify `sense_hes`, and allows both Node.js and Java to share the exact same database and table locally.
4. **Configuration Parity**: Both applications use the exact same environment variable names: `STARROCKS_HOST`, `STARROCKS_PORT`, `STARROCKS_USER`, `STARROCKS_PASSWORD`, and `STARROCKS_DATABASE`.
5. **Architectural Differences**:
   - **Host Fallback**: Node.js falls back to the remote production domain if unset; Java deliberately defaults to blank `""` to isolate tests and activate a safe blank-host fallback.
   - **Credentials**: Node.js has hardcoded fallback credentials in source code; Java defaults to empty for secret hygiene.
   - **Query Fallback**: Node.js implements a secondary unmetered fallback query when no meter is specified and the default meter `X2521837` has no rows; Java currently lacks this secondary fallback.

---

## 2. Phase 1 — Node.js Source-of-Truth Inspection

Inspection of `IEX-Dashboard/backend` was conducted in **STRICT READ-ONLY** mode.

### 2.1 Authoritative Code & Config Locations
- Primary Service: [`IEX-Dashboard/backend/src/services/starrocks.service.ts`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/backend/src/services/starrocks.service.ts)
- Migration Documentation:
  - [`IEX-Dashboard/docs/migration/05-configuration.md`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/docs/migration/05-configuration.md#L37-L41)
  - [`IEX-Dashboard/docs/migration/08-external-integrations.md`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/docs/migration/08-external-integrations.md#L145-L172)
- Calling Services:
  - [`backend/src/modules/forecast/forecast.service.ts`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/backend/src/modules/forecast/forecast.service.ts#L836)
  - [`backend/src/modules/trader-performance-daily/trader-performance-daily.service.ts`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/backend/src/modules/trader-performance-daily/trader-performance-daily.service.ts#L207)

### 2.2 Confirmed Node.js Connection Configuration
From `starrocks.service.ts:L8-L19`:
```typescript
host: process.env.STARROCKS_HOST || 'prolt-prod-starrocks.probussense.com',
port: Number(process.env.STARROCKS_PORT) || 9030,
user: process.env.STARROCKS_USER || 'read_only_user',
password: process.env.STARROCKS_PASSWORD || 'Probus@123',
database: process.env.STARROCKS_DATABASE || 'prolt_hes_db',
waitForConnections: true,
connectionLimit: 10,
queueLimit: 0,
connectTimeout: 10000
```

### 2.3 Database and Schema Name Verification
- **Target Database**: **`prolt_hes_db`**
- **Target Table**: **`prolt_load_data`**
- **Usage of `sense_hes`**: **None**. Grep of the entire `IEX-Dashboard` codebase confirmed zero references to `sense_hes`.

### 2.4 Node.js SQL Queries & Query Semantics
Node.js executes two methods:

#### A. `getAvailableMeters(): Promise<string[]>`
```sql
SELECT DISTINCT meter_number 
FROM prolt_load_data 
WHERE meter_number IS NOT NULL AND meter_number != ''
```

#### B. `getConsumerActualDemandMap(dates: string[], meterNo?: string): Promise<Map<string, number>>`
1. Primary Query (Target meter defaults to `'X2521837'` if `meterNo` is omitted):
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
2. Secondary Fallback Query (Triggered only when `rows.length === 0` AND caller did not pass an explicit `meterNo`):
   ```sql
   SELECT 
     DATE_FORMAT(datetime_slot, '%Y-%m-%d') as date_str,
     DATE_FORMAT(datetime_slot, '%H:%i') as time_str,
     ROUND(MAX(block_apparent_energy * 0.4), 2) as total_apparent_kvah,
     ROUND(MAX(block_active_energy * 0.4), 2) as total_active_kwh
   FROM prolt_load_data 
   WHERE DATE_FORMAT(datetime_slot, '%Y-%m-%d') IN (?)
   GROUP BY datetime_slot, date_str, time_str
   ORDER BY datetime_slot ASC
   ```

#### C. Data Types & Semantics
- `meter_number`: `VARCHAR` — Identifies the consumer meter.
- `datetime_slot`: `DATETIME` — Timestamp of 15-minute slot.
- `block_apparent_energy`: `DOUBLE` / numeric.
- `block_active_energy`: `DOUBLE` / numeric.
- **Conversion Factor**: Multiplied by `0.4` (`ROUND(MAX(energy * 0.4), 2)`) to scale 15-minute interval pulses into billing kVAh / kWh.
- **Value Resolution**: `const val = Number(r.total_apparent_kvah || r.total_active_kwh || 0);` (Apparent energy prioritized; active energy used as fallback).
- **Key Storage**: Stores both `${dateStr}_${timeStr}` (e.g. `2026-06-20_00:00`) and 1-based 15-minute slot number `${dateStr}_${intervalNum}` (e.g. `2026-06-20_1` through `2026-06-20_96`).

---

## 3. Phase 2 — Java Configuration & Code Comparison

### 3.1 Java Inspection
- Service: [`src/main/java/com/sense/prolt_operations/service/StarrocksService.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/StarrocksService.java)
- Configuration Group: [`AppProperties.java#Starrocks`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/config/AppProperties.java#L187-L230)
- Properties:
  - [`application.properties:L103-L107`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/resources/application.properties#L103-L107):
    ```properties
    app.starrocks.host=${STARROCKS_HOST:}
    app.starrocks.port=${STARROCKS_PORT:9030}
    app.starrocks.user=${STARROCKS_USER:}
    app.starrocks.password=${STARROCKS_PASSWORD:}
    app.starrocks.database=${STARROCKS_DATABASE:prolt_hes_db}
    ```
  - [`application-local.properties:L43`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/resources/application-local.properties#L43):
    ```properties
    app.starrocks.host=
    ```
  - [`BaseIntegrationTest.java:L41`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/BaseIntegrationTest.java#L41):
    ```java
    registry.add("app.starrocks.host", () -> "");
    ```

### 3.2 Side-by-Side Comparison Matrix

| Dimension | Node.js Legacy Reference (`IEX-Dashboard`) | Java Backend (`prolt-operations`) | Alignment Status | Notes / Impact |
|---|---|---|---|---|
| **Database Name** | `prolt_hes_db` | `prolt_hes_db` | **FULL ALIGNMENT** | Both configure `prolt_hes_db` by default. |
| **Table Name** | `prolt_load_data` | `prolt_load_data` | **FULL ALIGNMENT** | Both query `prolt_load_data`. |
| **Driver / Protocol** | `mysql2/promise` (MySQL wire protocol) | `jdbc:mysql://` (MySQL Connector/J) | **ALIGNED** | Both use port 9030 MySQL protocol. |
| **Host Property** | `process.env.STARROCKS_HOST` (default: remote prod) | `app.starrocks.host=${STARROCKS_HOST:}` (default: `""`) | **INTENTIONAL SAFETY DIFF** | Java uses blank host by default to trigger safe local test fallback. |
| **Port Property** | `process.env.STARROCKS_PORT` (default: `9030`) | `app.starrocks.port=${STARROCKS_PORT:9030}` | **FULL ALIGNMENT** | Both use 9030 by default. |
| **User Property** | `process.env.STARROCKS_USER` (default: `read_only_user`) | `app.starrocks.user=${STARROCKS_USER:}` (default: `""`) | **INTENTIONAL SECURITY DIFF** | Java avoids hardcoding production credentials in source code. |
| **Password Property** | `process.env.STARROCKS_PASSWORD` (default: hardcoded) | `app.starrocks.password=${STARROCKS_PASSWORD:}` (default: `""`) | **INTENTIONAL SECURITY DIFF** | Java adheres to secret safety guidelines. |
| **Query 1 (`getAvailableMeters`)** | `SELECT DISTINCT meter_number FROM prolt_load_data WHERE meter_number IS NOT NULL AND meter_number != ''` | Exact same SQL | **FULL ALIGNMENT** | Identical query and filtering. |
| **Query 2 (`getConsumerActualDemandMap` Primary)** | Filter by `IN (?) AND meter_number = ?`, `ROUND(MAX(val * 0.4), 2)` | Exact same SQL with dynamic placeholders | **FULL ALIGNMENT** | Identical grouping, scaling factor (0.4), and ordering. |
| **Query 3 (Unmetered Secondary Fallback)** | Present (queries date range without meter filter if meter query is empty and `meterNo` was unsupplied) | Missing in Java | **MINOR PARITY GAP** | Java does not implement the secondary unmetered fallback if `X2521837` is absent. |
| **Connection Pooling** | Singleton pool (`createPool`, limit: 10) | Single-use `DriverManager.getConnection(...)` per query | **PERFORMANCE DIFF** | Adequate for low-frequency queries; can be pooled via HikariCP if volume increases. |

---

## 4. Local StarRocks Container Inspection (`starrocks-node`)

### 4.1 Container Status & Ports
- Container Name: `starrocks-node` (Image: `starrocks/allin1-ubuntu`)
- Host Port Binding: `0.0.0.0:9030 -> 9030/tcp` (Frontend MySQL Wire Protocol)
- Service Status: All 4 internal services (`beservice`, `director`, `feproxy`, `feservice`) are actively **`RUNNING`**.
- Local Socket Handshake: Confirmed responding on `localhost:9030` (`MySQL 8.0.33` protocol handshake).

### 4.2 Database Inventory
Running `SHOW DATABASES;` inside `starrocks-node`:
- `_statistics_` (Internal StarRocks)
- `information_schema` (MySQL metadata)
- `sense_hes` (Probus Smart Meter HES IoT communications database)
- `sys` (MySQL system schema)

### 4.3 Why `sense_hes` is NOT the Target Database
Inspection of `sense_hes.amr_load_data` showed:
```
Field            Type
DATETIME_SLOT    datetime
meter_number     varchar(65533)
tor              datetime
sensor_time      datetime
server_time      datetime
extra_props      varchar(65533)
command_code     varchar(65533)
travel_time      int
```
- It contains sensor transport metrics (`travel_time`, `command_code`, `sensor_time`).
- It contains **zero** energy metrics (`block_apparent_energy`, `block_active_energy`).
- It is an operational table for smart meter network telemetry, not the billing load-profile store used by the IEX Analytics platform.
- Attempting to point either Node.js or Java to `sense_hes` would fail because the required columns do not exist.

### 4.4 Multi-Tenant Coexistence Analysis
- **Can the existing container support both applications without creating duplicate databases?**
  **YES.**
  - `prolt_hes_db` does not exist yet.
  - Creating `prolt_hes_db` with table `prolt_load_data` in the running container establishes the exact database and table that both Node.js and Java expect.
  - It creates **one single shared database** (`prolt_hes_db`) for both platforms.
  - It does not modify, rename, or interfere with `sense_hes`.

---

## 5. Precise Alignment Plan

### Principle 1: Node.js Authoritative Source of Truth
Both applications must target `prolt_hes_db.prolt_load_data`. The table schema must satisfy all columns referenced in Node.js:
- `meter_number` (`VARCHAR(100)`)
- `datetime_slot` (`DATETIME`)
- `block_apparent_energy` (`DOUBLE`)
- `block_active_energy` (`DOUBLE`)

### Principle 2: Safe Environment Precedence (Preserve Test Isolation)
- In production / staging, both Node.js and Java bind via environment variables:
  `STARROCKS_HOST`, `STARROCKS_PORT`, `STARROCKS_USER`, `STARROCKS_PASSWORD`, `STARROCKS_DATABASE`.
- In local development and unit testing:
  - Java's blank-host safety fallback (`app.starrocks.host=""`) remains intact so automated test suites (`./mvnw test`) pass in CI/CD without requiring an external StarRocks container.
  - When local developers or testers wish to connect to their local container, they pass `-Dapp.starrocks.host=localhost` or set `STARROCKS_HOST=localhost`.

### Principle 3: Address Secondary Fallback Parity Gap (Future Implementation)
When implementing Java updates:
- Port the secondary fallback query from `starrocks.service.ts:L66-L78` to `StarrocksService.java`:
  If `meterNo == null` and the query for default meter `X2521837` returns empty, execute the fallback query grouping across all meters for the specified dates.

---

## 6. Local Provisioning & Verification Steps

### Step 1: Provision `prolt_hes_db` in Local StarRocks
Execute the following DDL and DML in the running `starrocks-node` container:

```bash
docker exec -i starrocks-node mysql -h 127.0.0.1 -P 9030 -u root << 'EOF'
CREATE DATABASE IF NOT EXISTS prolt_hes_db;

USE prolt_hes_db;

CREATE TABLE IF NOT EXISTS prolt_load_data (
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
EOF
```

### Step 2: Verify in Node.js
Set local environment variables for Node.js (`IEX-Dashboard/backend`):
```bash
export STARROCKS_HOST=localhost
export STARROCKS_PORT=9030
export STARROCKS_USER=root
export STARROCKS_PASSWORD=""
export STARROCKS_DATABASE=prolt_hes_db
```
Node.js calls `StarrocksService.getAvailableMeters()` and receives `["TEST_METER", "X2521837"]`.

### Step 3: Verify in Java (`prolt-operations`)
Run targeted test with system properties overriding blank host:
```bash
./mvnw test -Dtest=ScraperAndSchedulerIntegrationTest#testStarrocksService \
  -Dapp.starrocks.host=localhost \
  -Dapp.starrocks.port=9030 \
  -Dapp.starrocks.user=root \
  -Dapp.starrocks.database=prolt_hes_db
```
Expected result: Test passes with 0 warnings; meters `["TEST_METER", "X2521837"]` and 15-minute slot demand points for `2026-06-20` returned.

---

## 7. Audit Conclusion & Safeguard Status

- **Database Name**: Confirmed as **`prolt_hes_db`** across both applications.
- **Table Name**: Confirmed as **`prolt_load_data`** across both applications.
- **Duplicate Databases Avoided**: Neither application uses `sense_hes`. One single `prolt_hes_db` database serves both.
- **Application Code Modified**: **None** (Audit only, preserving all safeguards).

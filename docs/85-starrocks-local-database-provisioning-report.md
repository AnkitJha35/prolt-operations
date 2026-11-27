# Milestone 1 — StarRocks Local Database Provisioning Report

## 1. Executive Summary

Milestone 1 successfully provisioned the local StarRocks database (`prolt_hes_db`) and table (`prolt_load_data`) required by both the Node.js application (`IEX-Dashboard/backend`) and the Java backend (`prolt-operations`).

All pre-provisioning safety checks, DDL execution, synthetic test data population, and schema verifications completed with zero errors. The existing `sense_hes` database remains completely untouched.

---

## 2. Container Health & Environment Status

- **Container Name**: `starrocks-node`
- **Container ID**: `18c965f050a1`
- **Image**: `starrocks/allin1-ubuntu`
- **Status**: `Up (healthy)`
- **Port Mapping**:
  - `0.0.0.0:9030 -> 9030/tcp` (Frontend MySQL Wire Protocol)
  - `0.0.0.0:8030 -> 8030/tcp` (Frontend Web UI / HTTP)
  - `0.0.0.0:8040 -> 8040/tcp` (Backend HTTP)
- **Supervisord Service Status**:
  - `beservice`: `RUNNING`
  - `director`: `RUNNING`
  - `feproxy`: `RUNNING`
  - `feservice`: `RUNNING`

---

## 3. Connection Verification

- **Host**: `127.0.0.1`
- **Port**: `9030`
- **Authentication**: `root` (no password)
- **Protocol Handshake**: Verified from host via MySQL client (`8.0.33` protocol banner).
- **Pre-existing Database State**:
  - Pre-existing databases: `_statistics_`, `information_schema`, `sense_hes`, `sys`.
  - Confirmed target database `prolt_hes_db` did **not** exist prior to provisioning.
  - Confirmed table `prolt_load_data` did **not** exist in any database.

---

## 4. Database & Table Creation Results

### 4.1 Database Creation
Executed:
```sql
CREATE DATABASE IF NOT EXISTS prolt_hes_db;
```
- **Status**: **SUCCESS** (`Query OK`).

### 4.2 Table Creation
Executed:
```sql
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
- **Status**: **SUCCESS** (`Query OK`).

---

## 5. Schema Verification

Query:
```sql
DESC prolt_hes_db.prolt_load_data;
```

### Result Schema Table:
| Field | Type | Null | Key | Default | Extra |
|---|---|---|---|---|---|
| `meter_number` | `varchar(100)` | `NO` | `true` | `NULL` | |
| `datetime_slot` | `datetime` | `NO` | `true` | `NULL` | |
| `block_apparent_energy` | `double` | `YES` | `false` | `0.0` | |
| `block_active_energy` | `double` | `YES` | `false` | `0.0` | |

---

## 6. Synthetic Data Population

### 6.1 Pre-Insertion Check
- Executed `SELECT COUNT(*) FROM prolt_hes_db.prolt_load_data;`
- Row count was **`0`** (table completely empty). Neither `TEST_METER` nor `X2521837` had prior rows.

### 6.2 Data Insertion
Executed:
```sql
INSERT INTO prolt_hes_db.prolt_load_data
(meter_number, datetime_slot, block_apparent_energy, block_active_energy)
VALUES
('TEST_METER', '2026-06-20 00:00:00', 125.0, 120.0),
('TEST_METER', '2026-06-20 00:15:00', 130.0, 125.0),
('TEST_METER', '2026-06-20 00:30:00', 140.0, 135.0),
('TEST_METER', '2026-06-20 00:45:00', 135.0, 130.0),
('TEST_METER', '2026-06-20 01:00:00', 150.0, 142.0),
('X2521837', '2026-06-20 00:00:00', 200.0, 190.0),
('X2521837', '2026-06-20 00:15:00', 210.0, 200.0),
('X2521837', '2026-06-20 00:30:00', 205.0, 195.0),
('X2521837', '2026-06-20 00:45:00', 215.0, 205.0),
('X2521837', '2026-06-20 01:00:00', 220.0, 210.0);
```
- **Status**: **SUCCESS** (`10 rows affected`).

---

## 7. Final Verification Queries & Results

### 7.1 Database Inventory (`SHOW DATABASES;`)
```
+--------------------+
| Database           |
+--------------------+
| _statistics_       |
| information_schema |
| prolt_hes_db       |
| sense_hes          |
| sys                |
+--------------------+
```

### 7.2 Table Inventory (`SHOW TABLES FROM prolt_hes_db;`)
```
+------------------------+
| Tables_in_prolt_hes_db |
+------------------------+
| prolt_load_data        |
+------------------------+
```

### 7.3 Meter Slot Aggregation (`GROUP BY meter_number`)
```sql
SELECT meter_number, COUNT(*) AS slot_count
FROM prolt_hes_db.prolt_load_data
GROUP BY meter_number;
```
```
+--------------+------------+
| meter_number | slot_count |
+--------------+------------+
| TEST_METER   |          5 |
| X2521837     |          5 |
+--------------+------------+
```

### 7.4 Detailed Row Records
```sql
SELECT *
FROM prolt_hes_db.prolt_load_data
ORDER BY meter_number, datetime_slot;
```
```
+--------------+---------------------+-----------------------+---------------------+
| meter_number | datetime_slot       | block_apparent_energy | block_active_energy |
+--------------+---------------------+-----------------------+---------------------+
| TEST_METER   | 2026-06-20 00:00:00 |                   125 |                 120 |
| TEST_METER   | 2026-06-20 00:15:00 |                   130 |                 125 |
| TEST_METER   | 2026-06-20 00:30:00 |                   140 |                 135 |
| TEST_METER   | 2026-06-20 00:45:00 |                   135 |                 130 |
| TEST_METER   | 2026-06-20 01:00:00 |                   150 |                 142 |
| X2521837     | 2026-06-20 00:00:00 |                   200 |                 190 |
| X2521837     | 2026-06-20 00:15:00 |                   210 |                 200 |
| X2521837     | 2026-06-20 00:30:00 |                   205 |                 195 |
| X2521837     | 2026-06-20 00:45:00 |                   215 |                 205 |
| X2521837     | 2026-06-20 01:00:00 |                   220 |                 210 |
+--------------+---------------------+-----------------------+---------------------+
```
- **Total Records**: Exactly **10**.

---

## 8. Errors & Unresolved Issues

- **Errors Encountered**: **None**.
- **Unresolved Issues**: **None**.
- **Safeguard Compliance**:
  - `sense_hes` tables remain completely intact (verified: 23 tables intact).
  - No application code was modified.
  - No PostgreSQL / Flyway / Enterprise V2 migrations were altered.
  - No remote production or staging endpoints were contacted.
  - No commits or pushes performed.

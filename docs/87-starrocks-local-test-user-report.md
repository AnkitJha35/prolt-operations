# Milestone 2A — Local StarRocks Read-Only Test User Provisioning Report

## 1. Executive Summary

Milestone 2A successfully created and verified a dedicated local-only StarRocks account (`nodejs_local_test`) to enable secure, unblocked read-only verification for the Node.js application without modifying application source code, configuration files, or database schemas, and without reusing or exposing production credentials.

### Final Verdict
$$\mathbf{\text{VERDICT: PASS}}$$

All pre-execution safety checks, account provisioning, least-privilege permission grants, read-only query validations, and negative access control tests completed with 100% success.

---

## 2. Container & Pre-Execution Checks

Prior to account creation, the local environment was verified:
1. **Container Health**: `starrocks-node` status is `Up (healthy)`.
2. **Port Accessibility**: Port `9030` is open and responsive on `127.0.0.1`.
3. **Target Database**: `prolt_hes_db` confirmed present.
4. **Target Table**: `prolt_hes_db.prolt_load_data` confirmed present.
5. **Existing Record Count**: Verified exactly **10** synthetic records are present in `prolt_hes_db.prolt_load_data` (5 for `TEST_METER`, 5 for `X2521837`).
6. **Prior User State**: Confirmed only `root@%` existed prior to this milestone.

---

## 3. Account Provisioning & Granted Permissions

### 3.1 Account Creation
- **Username**: `nodejs_local_test`
- **Host Scope**: `%` (local docker network / host bridge)
- **Password**: Generated strong, random 24-character credential stored strictly in temporary, restricted-permission local session storage (`scratch/starrocks_local_test.env`, `chmod 600`), completely excluded from Git and repository tracking. *(Password omitted per security guidelines).*
- **Command Executed**:
  ```sql
  CREATE USER IF NOT EXISTS 'nodejs_local_test'@'%' IDENTIFIED BY '[REDACTED_STRONG_SECRET]';
  GRANT SELECT ON prolt_hes_db.* TO 'nodejs_local_test'@'%';
  ```

### 3.2 Granted Permissions Verification (`SHOW GRANTS`)
```
+-------------------------+-----------------+-------------------------------------------------------------------------------------+
| UserIdentity            | Catalog         | Grants                                                                              |
+-------------------------+-----------------+-------------------------------------------------------------------------------------+
| 'nodejs_local_test'@'%' | default_catalog | GRANT SELECT ON ALL TABLES IN DATABASE prolt_hes_db TO USER 'nodejs_local_test'@'%' |
+-------------------------+-----------------+-------------------------------------------------------------------------------------+
```
- **Administrative Privileges**: None.
- **Write Privileges (`INSERT`, `UPDATE`, `DELETE`)**: None.
- **DDL Privileges (`CREATE`, `DROP`, `ALTER`)**: None.
- **Cross-Database Access**: Strictly limited to `prolt_hes_db`.

---

## 4. Permission & Query Verification (Authenticated as `nodejs_local_test`)

The new user was authenticated over MySQL protocol on `127.0.0.1:9030`:

### 4.1 Connection & Liveness (`SELECT 1;`)
- **Status**: **SUCCESS** (`1`).

### 4.2 Database Visibility (`SHOW DATABASES;`)
```
+--------------------+
| Database           |
+--------------------+
| information_schema |
| prolt_hes_db       |
+--------------------+
```
- Note: Database `sense_hes` is **not visible** to this account.

### 4.3 Table Row Count (`SELECT COUNT(*) FROM prolt_hes_db.prolt_load_data;`)
```
+-------------+
| total_count |
+-------------+
|          10 |
+-------------+
```
- **Status**: **SUCCESS** (Exactly 10 rows).

### 4.4 Meter Aggregation (`GROUP BY meter_number`)
```sql
SELECT meter_number, COUNT(*) AS slot_count
FROM prolt_hes_db.prolt_load_data
GROUP BY meter_number;
```
```
+--------------+------------+
| meter_number | slot_count |
+--------------+------------+
| X2521837     |          5 |
| TEST_METER   |          5 |
+--------------+------------+
```
- **Status**: **SUCCESS**.

### 4.5 Negative Access Control Tests
1. **Access to `sense_hes`**:
   - Query: `SELECT * FROM sense_hes.amr_load_data LIMIT 1;`
   - Result: **DENIED** (`ERROR 5203 (42000): Access denied; you need (at least one of) the SELECT privilege(s) on TABLE amr_load_data`).
2. **Write Operation on `prolt_hes_db`**:
   - Query: `INSERT INTO prolt_hes_db.prolt_load_data (meter_number, datetime_slot) VALUES ('DENIED_TEST', '2026-06-20 00:00:00');`
   - Result: **DENIED** (`ERROR 5203 (42000): Access denied; you need (at least one of) the INSERT privilege(s) on TABLE prolt_load_data`).

---

## 5. Safeguards Verification Summary

1. **Existing Records Preserved**: Re-queried `prolt_hes_db.prolt_load_data` as `root`; confirmed all 10 records remain unchanged.
2. **`sense_hes` Intact**: All 23 tables in `sense_hes` remain untouched.
3. **Application Repositories**: Neither Node.js (`IEX-Dashboard`) nor Java (`prolt-operations`) files were modified.
4. **Secret Hygiene**: Password is not committed to Git, not included in reports, and not stored in `.env`.
5. **No Integration Tests Run**: Node.js and Java tests were not executed.

---

## 6. Verdict

$$\mathbf{\text{VERDICT: PASS}}$$
The dedicated local test account `nodejs_local_test` is active, least-privilege verified, and ready for use in subsequent Node.js connectivity verification.

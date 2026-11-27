# Milestone 2 — Node.js StarRocks Local Connectivity Verification Report

## 1. Executive Summary

Milestone 2 attempted read-only runtime connectivity verification between the existing Node.js reference application (`IEX-Dashboard/backend`) and the newly provisioned local StarRocks database (`prolt_hes_db.prolt_load_data` on `127.0.0.1:9030`).

Verification was conducted in **STRICT READ-ONLY** mode without modifying any files in either repository and without modifying StarRocks database tables or records.

### Final Verdict
$$\mathbf{\text{VERDICT: BLOCKED}}$$

Execution could not complete due to two specific blockers identified in the Node.js implementation and repository state:
1. **Authentication Blocker (Logical OR Fallback)**: The Node.js pool configuration evaluates `password: process.env.STARROCKS_PASSWORD || '[DEFAULT_SECRET]'`. Because empty string `""` is falsy in JavaScript, setting `STARROCKS_PASSWORD=` causes Node.js to evaluate the truthy fallback secret and attempt authentication with a password (`using password: YES`), which the local passwordless `root` user in StarRocks immediately rejects (`ER_ACCESS_DENIED_ERROR (1045)`).
2. **Compiled Artifact Staleness Blocker**: The existing compiled artifact `dist/services/starrocks.service.js` (built 2026-09-30) is out of date with `src/services/starrocks.service.ts` (updated 2026-10-02). The compiled `dist/` artifact lacks `getAvailableMeters()` and hardcodes `meter_number = 'X2521837'`. Rebuilding `dist/` is prohibited by the milestone safeguards.

---

## 2. Repository & Source Files Inspected

1. **Source TypeScript File**:
   - Path: [`IEX-Dashboard/backend/src/services/starrocks.service.ts`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/backend/src/services/starrocks.service.ts)
   - Last Modified: `2026-10-02 11:50:37`
   - Content: Defines `StarrocksService` with `getAvailableMeters()` and `getConsumerActualDemandMap(dates, meterNo)`.
2. **Compiled JavaScript Output**:
   - Path: [`IEX-Dashboard/backend/dist/services/starrocks.service.js`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/backend/dist/services/starrocks.service.js)
   - Last Modified: `2026-09-30 13:12:53` (stale build)
   - Content: Defines `StarrocksService` with only `getConsumerActualDemandMap(dates)`.
3. **Package Configuration**:
   - Path: [`IEX-Dashboard/backend/package.json`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/backend/package.json)
   - Scripts: `build: "npm run swagger && tsc && cp src/swagger_output.json dist/swagger_output.json"`, `start: "node dist/server.js"`.
   - Installed Dependencies: `mysql2 ^3.23.3`, `dotenv ^16.4.5`.
   - Dev Dependencies: `ts-node ^10.9.2`, `typescript ^6.0.3`.

---

## 3. Discovered Method Exports

### In `src/services/starrocks.service.ts`:
- `public static async getAvailableMeters(): Promise<string[]>`
- `public static async getConsumerActualDemandMap(dates: string[], meterNo?: string): Promise<Map<string, number>>`

### In `dist/services/starrocks.service.js`:
- `static async getConsumerActualDemandMap(dates): Promise<Map<string, number>>`
- *Note: `getAvailableMeters()` is completely absent from `dist/`.*

---

## 4. Runtime Execution Commands Used

Runtime execution was attempted using transient shell environment variables without writing to `.env` or any configuration files:

### Attempt 1: Using Existing Compiled Artifact (`dist/`)
```bash
STARROCKS_HOST=127.0.0.1 \
STARROCKS_PORT=9030 \
STARROCKS_USER=root \
STARROCKS_PASSWORD= \
STARROCKS_DATABASE=prolt_hes_db \
node -e '
  const { StarrocksService } = require("./dist/services/starrocks.service");
  StarrocksService.getConsumerActualDemandMap(["2026-06-20"]).then(map => {
    console.log("dist map size:", map.size);
  });
'
```

### Attempt 2: Using In-Memory TypeScript Execution (`src/` via `ts-node`)
```bash
STARROCKS_HOST=127.0.0.1 \
STARROCKS_PORT=9030 \
STARROCKS_USER=root \
STARROCKS_PASSWORD= \
STARROCKS_DATABASE=prolt_hes_db \
./node_modules/.bin/ts-node -e '
  import { StarrocksService } from "./src/services/starrocks.service";
  StarrocksService.getAvailableMeters().then(meters => {
    console.log("src meters:", meters);
  });
'
```

---

## 5. Connection & Query Results

### 5.1 Connection Result
- **Status**: **FAILED (Access Denied)**.
- **Error Code**: `ER_ACCESS_DENIED_ERROR (1045)`
- **SQL State**: `28000`
- **Error Message**: `Access denied for user 'root' (using password: YES)`

### 5.2 Root Cause of Connection Failure
In `src/services/starrocks.service.ts:L12` and `dist/services/starrocks.service.js:L16`:
```typescript
password: process.env.STARROCKS_PASSWORD || 'Probus@123',
```
In JavaScript, the expression `"" || 'Probus@123'` evaluates to `'Probus@123'` because empty string is falsy.
- The environment variable was correctly passed as `STARROCKS_PASSWORD=`.
- Because logical OR (`||`) was used instead of nullish coalescing (`??`), Node.js substituted the hardcoded fallback password.
- StarRocks container has only user `'root'@'%'` without a password.
- StarRocks rejected the authentication attempt because a non-empty password was supplied.

### 5.3 Available Meters Results
- Unable to execute via `dist/` because `getAvailableMeters()` does not exist in `dist/services/starrocks.service.js`.
- Attempting execution via `src/` caught `ER_ACCESS_DENIED_ERROR` and returned `[]`.

### 5.4 Meter Data Query & Aggregation Verification
- Both `dist/` and `src/` execution returned an empty `Map(0)` due to authentication rejection at connection initialization.
- Query execution against `prolt_load_data` could not take place over the connection.

---

## 6. Returned Data Structure Analysis (Static Inspection)

From the implementation in `starrocks.service.ts`:
- Returns a JavaScript `Map<string, number>`.
- **Timestamp Keys**: `${dateStr}_${timeStr}` (e.g. `2026-06-20_00:00`).
- **Interval Keys**: `${dateStr}_${intervalNum}` (e.g. `2026-06-20_1` through `2026-06-20_96`, where interval index is calculated as `Math.floor((hours * 60 + minutes) / 15) + 1`).
- **Energy Scaling Formula**:
  `ROUND(MAX(block_apparent_energy * 0.4), 2) as total_apparent_kvah`
  `ROUND(MAX(block_active_energy * 0.4), 2) as total_active_kwh`
- **Value Resolution**:
  `const val = Number(r.total_apparent_kvah || r.total_active_kwh || 0);`
  Apparent energy is prioritized; if 0 or null, active energy is used.

---

## 7. Safeguards & Repository State Verification

1. **Node.js Repository Untouched**:
   - `git status` on `IEX-Dashboard` confirmed zero files were created, modified, staged, or deleted.
2. **Java Backend Untouched**:
   - No Java source, resource, or test files were modified.
   - Zero Java integration tests were executed.
3. **Database Untouched**:
   - Confirmed no `INSERT`, `UPDATE`, `DELETE`, `ALTER`, or `DROP` statements were executed.
   - Verified row counts in `prolt_hes_db.prolt_load_data` remain exactly **10**.
   - `sense_hes` remains completely untouched.
4. **Secret Hygiene**:
   - No passwords or secrets exposed in reports or logs.

---

## 8. Minimum Safe Next Steps to Unblock

To enable Node.js to authenticate against the local StarRocks container, one of the following non-invasive options is required:

### Option A (Recommended — Server-Side User Provisioning):
Provision a local user in the StarRocks container matching Node.js's fallback expectations without touching any application repository:
```sql
CREATE USER IF NOT EXISTS 'read_only_user'@'%' IDENTIFIED BY 'Probus@123';
GRANT SELECT ON prolt_hes_db.* TO 'read_only_user'@'%';
```
*Benefits*: Requires zero code edits to Node.js; allows Node.js to connect seamlessly with its built-in defaults or explicit `STARROCKS_USER=read_only_user`.

### Option B (Node.js Code Fix — Post-Milestone Approval):
Update `starrocks.service.ts` to use nullish coalescing:
```typescript
password: process.env.STARROCKS_PASSWORD ?? 'Probus@123',
```
and recompile `dist/` with `npm run build`.

---

## 9. Final Verdict

$$\mathbf{\text{VERDICT: BLOCKED}}$$
- Blocker 1: JavaScript `||` fallback sends non-empty password to passwordless local root user.
- Blocker 2: Compiled `dist/` artifact is stale and missing `getAvailableMeters()`.
- Repository and database safeguards fully preserved.

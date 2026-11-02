# Node-to-Java Migration: Conversation Handover

**Date:** 2026-10-26  
**Scope:** `IEX-Dashboard/backend` (Node/TypeScript) to `prolt-operations` (Spring Boot / Java 21)

This is a plain-language record of the migration discussion, decisions, work completed, verification performed, and items intentionally left for a later development plan.

---

## 1. Executive summary

The Java service has completed the main backend migration, the Accounts/Flyway hardening work, and several targeted Node-behavior parity fixes. The codebase currently has **118 tracked integration and unit tests** (with one intentionally skipped live/manual test).

The remaining meaningful work is not general unfinished migration work. It is primarily a **product and data-contract decision**: whether Java must reproduce Node's Resource Center tariff/charge fields exactly, and how new environments should receive approved master data.

No production data has been changed, exported, imported, or deleted during this work.

---

## 2. What was completed before this conversation

The migration phases recorded in `docs/18-progress.md` were completed:

- Spring Boot application foundation, JPA entities, repositories, PostgreSQL, Flyway migrations, and Testcontainers tests.
- Authentication, RBAC, JWT, OTP, rate limiting, upload limits, security headers, Swagger/Actuator restrictions, and production-safe configuration.
- Playwright scraping, market-data ingestion, schedulers, StarRocks support, calculation services, tariff calculations, PDF parsing, and report uploads.
- Endpoint parity, observability, and full-parity integration coverage.
- Accounts/Flyway cleanup: no seeded production accounts, runtime admin provisioning, password policy, Flyway baseline controls, and tests independent of fixed seed users.

The Accounts/Flyway stages were committed before the work described below:

| Commit | Work |
|---|---|
| `34cc451` | Accounts 1 |
| `fc75a6b` | Accounts 2 |
| `a71030c` | Accounts 3 |
| `d952258` | Accounts 4 |
| `62b3e71` | Accounts 5: verification and cleanup |

---

## 3. Questions raised and answers

### 3.1 How master data was loaded in Node

Node has two separate mechanisms:

1. **Old seed scripts** for initial tariff/charge/city data. These expect local CSV/XLSX files that are not present in the repository. Some are destructive: they delete existing tariff or charge rows before importing replacement data.
2. **Resource Center APIs** for normal operation:
   - `GET /api/resource-center/:resourceType`
   - `POST /api/resource-center/:resourceType`
   - `POST /api/resource-center/:resourceType/bulk`
   - `PUT /api/resource-center/:resourceType/:id`
   - `DELETE /api/resource-center/:resourceType/:id`

The bulk route accepts a **JSON array**, not a CSV/XLSX file upload. A client/UI must convert a spreadsheet to JSON before calling it.

### 3.2 Is there an API to upload master data?

Yes, the Node JSON bulk API above is the normal API route for bulk data. Java exposes the same route shape. It is not a direct spreadsheet upload API.

### 3.3 Is production data already present?

A read-only inspection of the configured PostgreSQL database found master-data rows, including city/state, tariffs, and charges. The connection appeared to be a Docker/private-network database, so it was not assumed to be production solely from that configuration.

The inspection did not write, export, or modify any data.

### 3.4 Decision made during the discussion

The user instructed that Java should follow Node's behavior for now, and that unresolved product/data questions should be put in a separate document for later. The user later chose to leave production/master-data planning for the next development plan.

Those deferred decisions are maintained in `docs/19-deferred-decisions.md`.

---

## 4. Work completed during this conversation

### 4.1 OTP parity and delivery behavior

Changed Java to match Node's input semantics:

- OTP email and OTP code values are no longer trimmed or converted to lowercase.
- Super-admin bypass comparison remains case-sensitive.
- An SMTP/mail failure now occurs after OTP storage and returns the same generic HTTP 500-style message used by Node: `Something went very wrong!`.

Intentional production safeguards remain:

- OTP values are not logged in production.
- Memory fallback and the fixed bypass are prevented in the production profile.

Added `OtpServiceTest` coverage for exact matching, bypass behavior, mail failure ordering, and safe logging.

### 4.2 JWT and `/api/auth/me` parity

Java JWTs and `/api/auth/me` were aligned to Node:

- Removed Java-only JWT `email` and `sub` claims.
- Kept Node-compatible claims: `id`, `username`, `role`, `hiddenModules`, `readOnlyModules`, `iat`, and `exp`.
- Removed Java-only `/me` response properties including `email`, `name`, and duplicate `data`.

Focused authentication integration tests passed: **15/15**.

### 4.3 Docker and Playwright runtime parity

The original Java Alpine image was unsuitable for a system Chromium/Playwright runtime. The Docker image now:

- Uses Debian Bookworm as the runtime layer.
- Includes Chromium at `/usr/bin/chromium`.
- Sets `PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium` for the existing scraper.
- Copies the Temurin Java 21 runtime into the final image.

The built image was verified successfully:

```text
OpenJDK 21.0.12.1 LTS
Chromium 156.0.8078.47 built on Debian GNU/Linux 12
```

### 4.4 Resource Center bulk API boundary parity

Java `POST /api/resource-center/:resourceType/bulk` was adjusted to match Node at the request boundary:

- Resource type is checked before body parsing.
- Only a JSON array is accepted.
- An empty array succeeds and reports zero uploaded records.
- Fully blank object rows are ignored.
- Non-array input and unsupported resources return Node-compatible messages.

This work changed no master data.

### 4.5 City/State API parity

Java `/api/database/city-state` now matches Node more closely:

- Read results are ordered by state name, then city name.
- City and state names are trimmed on create.
- Numeric strings are accepted for population and coordinates.
- Null/missing required values return `Missing required fields`.
- A duplicate city/state combination returns `City and State entry already exists.`.

The focused `FullParityIntegrationTest` suite passed after these changes: **10 tests, 0 failures**.

---

## 5. Verification performed

| Verification | Result |
|---|---|
| Full offline build before recent parity additions | `./mvnw clean verify -o`: 116 tests passed, 0 failures/errors, 1 skipped |
| JWT/auth focused suite | 15 tests passed |
| Resource Center focused suite | 9 tests passed after bulk-boundary work |
| City/State focused suite | 10 tests passed after City/State work |
| Container runtime check | Java 21 and Chromium both launch inside built Docker image |
| Git whitespace check | No `git diff --check` errors |

The tracked test count is now **118 passing tests plus 1 skipped live/manual test**. A new complete offline suite should be run once the next planned body of work is finished.

---

## 6. Deferred items: do not implement without a decision

### D-01: Initial master-data loading

The old Node seed files are absent. Their scripts can delete all existing tariff/charge records before loading files, so they must not be recreated or run without:

- the approved source files or approved database export;
- a per-environment loading plan; and
- explicit authorization for any replacement/deletion behavior.

### D-02: Operator workflow for master-data updates

Decide whether operators will use:

- the JSON Resource Center API directly;
- a UI that converts CSV/XLSX to JSON; or
- a controlled import tool with review, rollback, and audit controls.

### D-03: Resource Center field-schema authority

Node's Prisma models/validator and Java's JPA entities do not currently have identical tariff/charge fields. For example, Node's `state-tariff` accepts fields including `stateCode`, `tod`, `todName`, `season`, `todStartHour`, `todEndHour`, `baseEnergyCharges`, `todRate`, and `energyCharges`; Java currently persists a different set of fields including `consumerCategory`, `energyRate`, `supplyVoltage`, and `todChargePercent`.

Before strict field-level parity or bulk work in a shared environment, approve:

1. The authoritative target database schema.
2. A field-by-field Node-to-Java mapping for every Resource Center type.
3. A versioned migration and compatibility-test plan.

---

## 7. Recommended next development plan

1. **Approve the Resource Center schema authority and mapping.** This is the main outstanding development decision.
2. Implement the approved field mapping in Java entities, validation, database migrations, and parity tests.
3. Decide the initial master-data source and operator upload workflow for new environments.
4. Run the full offline Maven verification suite and Docker build after the schema work.
5. Prepare a controlled cutover plan for any shared/production environment; do not run destructive seed behavior unless explicitly approved.

---

## 8. Key documents and files

| Document | Purpose |
|---|---|
| `docs/16-conversion-questions.md` | Full conversion findings, compatibility notes, and intentional deviations |
| `docs/17-conversion-log.md` | Chronological implementation log |
| `docs/18-progress.md` | Completed phases and test tracking |
| `docs/19-deferred-decisions.md` | Decisions requiring product/data-owner approval |
| `README.md` | Build, runtime, Flyway cutover, and Docker operator guidance |

---

## 9. Current status

The Java migration is functionally advanced and its core platform work is complete. The remaining development is concentrated around a deliberate boundary: **master-data schema and operational ownership**, not a lack of a general API or missing basic backend capability.

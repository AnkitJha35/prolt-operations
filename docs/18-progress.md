# Migration Progress Tracker

This document tracks the execution status of each phase defined in `14-target-architecture-and-migration-plan.md`.

| Phase | Phase Name | Status | Started At | Completed At | Notes |
|---|---|---|---|---|---|
| Phase 1 | Core Foundation & Flyway Baseline (Core Foundation, Database Schema & JPA Domain Model) | Done | 2026-10-22 16:15 | 2026-10-22 16:39 | Complete Maven setup, Flyway V1-V5 migrations, 58 JPA entities + 5 enums, 58 Spring Data JPA repositories, HikariCP, Testcontainers verification passing |
| Phase 2 | Security & Identity Subsystem (Security, Authentication & User Management) | Done | 2026-10-22 16:41 | 2026-10-22 16:54 | Spring Security 6, JJWT 0.12.6, Bucket4j rate limiter (10/15m), OtpService (Redis + memory fallback + superadmin backdoor 749261), Auth & User controllers, RBAC (SUPER_ADMIN guard), 15/15 integration tests passing (AUTH-01..03, USER-01) |
| Phase 3 | Market Telemetry & Ingestion Pipelines (Market Telemetry, Scrapers & Background Schedulers) | Done | 2026-10-22 17:00 | 2026-10-22 17:38 | Scraper service (Playwright + RestClient/Jsoup), StarRocks integration, CronJobScheduler, Dataset/Dashboard controllers, 33/33 tests passing |
| Phase 4 | Tariff Engine & Calculation Services (Tariff Engines & Business Calculations) | Done | 2026-10-22 17:40 | 2026-10-22 17:55 | Landing price service, savings calculator v1 & v2, POI streaming excel exports, BigDecimal calculations, 39/39 tests passing |
| Phase 6 | Full Parity Verification & Zero-Downtime Cutover (Parity Verification, Observability & Cutover) | Done | 2026-10-22 18:00 | 2026-10-22 18:25 | All 117 endpoints active, 7 remaining subsystems ported, Actuator + Prometheus observability configured, FullParityIntegrationTest passing, 51/51 project tests passing (100% success) |

---

## Hardening Phases

| Hardening | Title | Status | Completed At | Notes |
|---|---|---|---|---|
| Hardening 1 | Remove invented routes and stale code | Done | 2026-10-23 09:30 | Removed `/api/proposal`, `/api/dataset`, `/api/api-log`, and stale OTP checks |
| Hardening 2 | Fail-closed security defaults | Done | 2026-10-23 10:15 | Fail-closed OTP bypass and memory fallback, strict JWT secret validation, `ProfileSecurityDefaultsTest` |
| Hardening 3 | Authentication parity | Done | 2026-10-23 10:45 | Exact parity with Node authMiddleware across all 18 route files, constant-time webhook verification, `AuthenticationParityIntegrationTest` (5 tests) |
| Hardening 4 | Fixed-window rate limiting parity | Done | 2026-10-23 11:20 | Custom `FixedWindowRateLimiter`, rightmost `X-Forwarded-For` client IP resolution, auth limiter (10/15m) & upload limiter (30/1h), `RateLimitingParityIntegrationTest` (6 tests) |
| Hardening 5 | Upload and body limits | Done | 2026-10-23 11:45 | Tomcat connector 250-part/10k param configuration, 10MB/25MB/50MB file limits, `UploadLimitsIntegrationTest` (5 tests) |
| Hardening 6 | Actuator, Swagger and security headers | Done | 2026-10-23 12:05 | 11 Helmet security headers parity, Swagger disabled in prod, Actuator endpoints restricted, `ActuatorAndSwaggerHardeningTest` (2 tests) |
| Hardening 7 | Landing price service error and client parity | Done | 2026-10-23 12:13 | Upstream IEX India timeouts, removed mock fallbacks, exact 500 error body parity, WireMock tests (`LandingPriceParityIntegrationTest`, 3 tests) |
| Hardening 8 | Trader performance user auditing | Done | 2026-10-23 12:20 | Safe null principal handling, exact `createdBy`/`updatedBy` parity (null when unauthenticated), `TraderPerformanceAuditingIntegrationTest` (4 tests) |
| Hardening 9 | Report Only: Fallbacks, substitute data, and route contract | Done | 2026-10-23 12:25 | Fully documented PDF script error handling, synthetic scraper blocks, template fallbacks, and 404 vs 401 route contract in `docs/16-conversion-questions.md` |

---

## Ingestion & Data Integrity Phases

| Ingestion | Title | Status | Completed At | Notes |
|---|---|---|---|---|
| Ingestion 1 | Secrets and defaults | Done | 2026-10-23 21:00 | Removed all credentials/secrets defaults from `application.properties`; dev defaults in `application-dev.properties`; `ProductionDatasourceValidator`; StarRocks blank host guard; SEC-08..SEC-11 tests |
| Ingestion 2 | Scraper parity and row parsers | Done | 2026-10-23 21:30 | Pure row parsers (`parseDamRows`, etc.), Playwright options aligned with Puppeteer, removed `generateSyntheticBlocks`/jsoup/fixtures, REC persistence guard, `ScraperRowParserTest` |
| Ingestion 3 | Trade report parser | Done | 2026-10-23 22:10 | Unified `TradeReportParser` in pure Java PDFBox with `setSortByPosition(true)`, removed fallback synthetic metadata, wired into `TraderPerformanceService` and `TraderPerformanceDailyService`, unit tests (`TradeReportParserTest`), golden comparison against all 28 real PDFs (`TradeReportGoldenComparisonTest`) |
| Ingestion 4 | Verification and final progress | Done | 2026-10-23 22:15 | Offline `./mvnw clean verify` passing; verified git grep absence of removed secrets/hosts, synthetic blocks, offline mode, fixtures, and jsoup; updated progress tracking |

---

## Accounts and Flyway Phases

| Accounts | Title | Status | Completed At | Notes |
|---|---|---|---|---|
| Accounts 1 | Clean Flyway seed migrations and remove seeded data claims | Done | 2026-10-26 11:00 | Cleaned `V5` with explanatory comments & `SELECT 1;`; honest placeholder comments for `V2`/`V3`/`V4`; removed seed claims from README/conversion log; documented historical V5 hash risks and Node `prisma/seed.ts` analysis |
| Accounts 2 | AppRunner admin user provisioning and password policy | Done | 2026-10-26 11:20 | Overwrite branch deleted, non-blank check, Role.ADMIN, password validation (>=12 chars, != username), throws in prod / skips in non-prod, `AppRunnerTest` (8 tests) |
| Accounts 3 | Tests no longer use seeded users and introduce test user helper | Done | 2026-10-26 11:45 | Introduced `TestUserHelper` and `BaseIntegrationTest` convenience methods; decoupled all test suites from seeded accounts; fresh migration test in `RepositoryIntegrationTest`; zero bcrypt literals in test suite |
| Accounts 4 | Flyway baseline configuration, documentation, and migration tests | Done | 2026-10-26 12:00 | Configured `baseline-on-migrate` & `baseline-version` properties; authoring operator runbook in `README.md`; `FlywayBaselineIntegrationTest` (2 tests) |
| Accounts 5 | Verification and cleanup | Done | 2026-10-26 12:05 | Full offline `./mvnw clean verify -o` (112 tests passing, 0 failures, 1 skipped); verified `git grep` zero bcrypt matches; updated progress and conversion log |

---

## OTP parity and delivery safety

| Work item | Status | Completed At | Notes |
|---|---|---|---|
| Exact OTP input semantics and mail failure parity | Done | 2026-10-26 14:25 | Removed trimming/case normalization; mail failure now returns generic 500 after OTP storage; production-safe logging and bypass restrictions retained; `OtpServiceTest` added. |
| JWT and `/api/auth/me` payload parity | Done | 2026-10-26 14:30 | Removed Java-only JWT `email`/`sub` claims and `/me` `email`/`name`/`data` properties; retained Node payload fields including `iat` and `exp`. |
| Container runtime for Playwright scraper | Done | 2026-10-26 14:40 | Replaced Alpine runtime with Debian-based Temurin and installed system Chromium configured at `/usr/bin/chromium`. |
| Resource Center bulk request boundaries | Done | 2026-10-26 15:40 | Matched Node's empty-array and blank-row behavior, body type validation, and unsupported-resource error precedence; no master data changed. |
| City/State registry validation and ordering | Done | 2026-10-26 15:45 | Matched Node ordering, input normalization, numeric-string conversion, required-field validation, and duplicate-entry response. |

---

## Resource Center API Parity Phases

| Phase | Title | Status | Completed At | Notes |
|---|---|---|---|---|
| Resource Center 1 | Complete CRUD field mapping | Done | 2026-10-26 17:15 | Full entity field mapping across all 9 resource types (`persistSingleRecord`, `updateSingleRecord`); `ResourceCenterController` accepts string IDs; aligned audit log camelCase model names; `ResourceCenterIntegrationTest` (RC-01..RC-08) |
| Resource Center 2 | Serialization and numeric normalization | Done | 2026-10-26 17:30 | `@JsonProperty("ctu_charges_rs_per_kwh")` and `@JsonAlias` on `CtuCharges`; regex-based cleaning `(?i)[%₹,\s]|rs\.?` for currency, percent, and formatting strings; `ResourceCenterIntegrationTest` (RC-09, RC-10) |
| Resource Center 3 | Domain validation and duplicate error handling | Done | 2026-10-26 17:44 | `@ExceptionHandler(DataIntegrityViolationException.class)` translates unique key collisions (`23505`, `uq_`) to HTTP 409 Conflict with `"Record already exists (Duplicate Conflict)"`; domain validation and bounds checks; flexible month parsing; `ResourceCenterIntegrationTest` (RC-11, RC-12) |
| Resource Center 4 | Bulk upsert semantics | Done | 2026-10-26 17:48 | In-place entity updates on bulk items with matching `id`, new record creation otherwise; `@Transactional` atomicity on bulk batch; `ResourceCenterIntegrationTest` (RC-13, RC-14) |
| Resource Center 5 | Documentation and final verification | Done | 2026-10-26 17:55 | Offline `./mvnw clean verify -o` passed with 132 tests passing (1 skipped live manual test, 0 failures); updated progress tracking, conversion log, and deferred decisions register |

## Deferred decisions register

- Added `docs/19-deferred-decisions.md` to isolate product/data-source questions from completed migration work. Node remains the authority until each entry is decided.
- D-03 resolved via Option A (Canonical Schema Alignment) in `docs/20-resource-center-schema-audit.md` and verified in Resource Center API Parity implementation.

---

## Production Readiness and Safety Audits

| Phase | Title | Status | Completed At | Notes |
|---|---|---|---|---|
| Phase 8 | Local Staging Deployment & E2E Verification | Done | 2026-10-27 13:00 | Docker Compose staging environment (Java 21 Temurin, Postgres 16, Redis 7, Chromium); 52/52 smoke tests; RTO 8.26s local recovery; `docs/25-staging-deployment-execution-report.md` |
| Phase 9 | Production Cutover Readiness Review | Done | 2026-10-27 14:00 | Conditional GO assessment; identified baseline v5 risk, single-writer matrix, master-data identity; `docs/26-production-cutover-readiness.md` |
| Phase 10 | Production Migration Safety Audit & Cutover Simulation Planning | Done | 2026-10-27 14:35 | Flyway/Prisma schema audit (Baseline v5 vs 3 historic migrations); Single-Writer Transition Matrix (`RUN_SCRAPER=false` verified); Master-data un-keyed duplicate hazard analysis; PDF upload 5-key response contract verified; smoke test assertion corrected (52/52 PASS, 5.75 MWh); 14-point Cutover Simulation Runbook; `docs/27-production-migration-safety-audit.md` |

**Total Integration & Unit Tests**: 132 passing tests across all modules (1 skipped live manual test).


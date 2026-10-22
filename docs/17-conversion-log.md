# Conversion Log & Source-to-Target Mapping

## 1. Updated Source-to-Target Mapping Table

Base Package: `com.sense.prolt_operations`
Architecture: Layered-by-type as per reference `mdm-adaptor` conventions:
`config`, `controller`, `dto`, `entity`, `repository`, `exception`, `runner`, `scheduler`, `security`, `service`, `util`.

| # | Current Node.js / TypeScript File | Target Java Class | Target Package | Responsibility / Notes |
|---|---|---|---|---|
| 1 | `backend/src/server.ts` | `ProltOperationsApplication.java` | `com.sense.prolt_operations` | Main Spring Boot application entry point |
| 2 | `backend/src/app.ts` | `SecurityConfig.java`, `WebMvcConfig.java` | `com.sense.prolt_operations.config` | Security filter chain and WebMvc configuration |
| 3 | `backend/src/config/index.ts` | `AppProperties.java` | `com.sense.prolt_operations.config` | `@ConfigurationProperties(prefix = "app")` binding env vars |
| 4 | `backend/src/config/prisma.ts` | `DataSourceConfig.java` | `com.sense.prolt_operations.config` | Multi-host / primary HikariCP data source configuration |
| 5 | `backend/src/config/redis.ts` | `RedisConfig.java` | `com.sense.prolt_operations.config` | Lettuce Redis connection factory & templates |
| 6 | `backend/src/config/multer.ts` | `MultipartConfig.java` | `com.sense.prolt_operations.config` | Multipart upload limits (50MB) |
| 7 | `backend/src/cron/index.ts` | `SchedulerConfig.java` | `com.sense.prolt_operations.config` | `@EnableScheduling`, ShedLock configuration |
| 8 | `backend/src/logger/index.ts` | `logback-spring.xml` | `src/main/resources` | Logback configuration with console & rolling file |
| 9 | `backend/src/swagger.ts` | `SwaggerConfig.java` | `com.sense.prolt_operations.config` | SpringDoc OpenAPI 3 configuration |
| 10 | `backend/src/reset_admin.ts` | `AdminBootstrapRunner.java` | `com.sense.prolt_operations.runner` | Seed check and default admin validation on startup |
| 11 | `backend/src/middleware/auth.middleware.ts` | `JwtAuthenticationFilter.java` | `com.sense.prolt_operations.security` | JWT auth filter parsing Bearer header |
| 12 | `backend/src/middleware/admin.middleware.ts` | `@PreAuthorize("hasRole('ADMIN')")` | Controller annotations | Declarative Spring Security role check |
| 13 | `backend/src/middleware/super-admin.middleware.ts` | `@PreAuthorize("hasRole('SUPER_ADMIN')")` | Controller annotations | Declarative Spring Security super-admin check |
| 14 | `backend/src/middleware/errorHandler.ts` | `RestControllerAdvisor.java` | `com.sense.prolt_operations.config` | `@RestControllerAdvice` matching Node JSON error shape |
| 15 | `backend/src/utils/AppError.ts` | `AppBaseException.java`, `BadRequestException.java`, `NotFoundException.java` | `com.sense.prolt_operations.exception` | Custom exceptions matching reference pattern |
| 16 | `backend/src/utils/jwt.ts` | `JwtService.java` | `com.sense.prolt_operations.security` | JJWT token creation and verification |
| 17 | `backend/src/modules/auth/auth.controller.ts` | `AuthController.java` | `com.sense.prolt_operations.controller` | `/api/auth/login`, `/api/auth/otp/*` endpoints |
| 18 | `backend/src/modules/auth/auth.service.ts` | `AuthService.java` | `com.sense.prolt_operations.service` | Authentication business logic |
| 19 | `backend/src/modules/auth/otp.service.ts` | `OtpService.java` | `com.sense.prolt_operations.service` | 6-digit numeric OTP with superadmin backdoor |
| 20 | `backend/src/modules/users/users.controller.ts` | `UserController.java` | `com.sense.prolt_operations.controller` | `/api/users/*` endpoints |
| 21 | `backend/src/modules/users/users.service.ts` | `UserService.java` | `com.sense.prolt_operations.service` | User provisioning and management |
| 22 | `backend/src/modules/dataset/dataset.controller.ts` | `DatasetController.java` | `com.sense.prolt_operations.controller` | `/api/dataset/*` endpoints |
| 23 | `backend/src/modules/dataset/dataset.service.ts` | `DatasetService.java` | `com.sense.prolt_operations.service` | 96-time-block dataset queries |
| 24 | `backend/src/modules/dataset/dashboard.controller.ts` | `DashboardController.java` | `com.sense.prolt_operations.controller` | `/api/dashboard/*` endpoints |
| 25 | `backend/src/modules/dataset/dashboard.service.ts` | `DashboardService.java` | `com.sense.prolt_operations.service` | Market dashboard summaries |
| 26 | `backend/src/modules/dataset/npp-adjustment.service.ts` | `NppAdjustmentService.java` | `com.sense.prolt_operations.service` | NPP data adjustment algorithms |
| 27 | `backend/src/modules/landing-price-calc/landing-price-calc.controller.ts` | `LandingPriceController.java` | `com.sense.prolt_operations.controller` | `/api/landing-price-calc/*` endpoints |
| 28 | `backend/src/modules/landing-price-calc/landing-price-calc.service.ts` | `LandingPriceService.java` | `com.sense.prolt_operations.service` | Landed price calculations |
| 29 | `backend/src/modules/savings-calculator/savings-calculator.controller.ts` | `SavingsCalculatorController.java` | `com.sense.prolt_operations.controller` | `/api/savings-calculator/*` endpoints |
| 30 | `backend/src/modules/savings-calculator/savings-calculator.service.ts` | `SavingsCalculatorService.java` | `com.sense.prolt_operations.service` | Savings calculator v1 logic |
| 31 | `backend/src/modules/savings-calculator-new/savings-calculator-new.controller.ts` | `SavingsCalculatorNewController.java` | `com.sense.prolt_operations.controller` | `/api/savings-calculator-new/*` endpoints |
| 32 | `backend/src/modules/savings-calculator-new/savings-calculator-new.service.ts` | `SavingsCalculatorNewService.java` | `com.sense.prolt_operations.service` | Savings calculator v2 logic |
| 33 | `backend/src/modules/trader-performance/trader-performance.controller.ts` | `TraderPerformanceController.java` | `com.sense.prolt_operations.controller` | `/api/trader-performance/*` endpoints |
| 34 | `backend/src/modules/trader-performance/trader-performance.service.ts` | `TraderPerformanceService.java` | `com.sense.prolt_operations.service` | Trader performance analytics |
| 35 | `backend/src/modules/trader-performance-daily/trader-performance-daily.controller.ts` | `TraderPerformanceDailyController.java` | `com.sense.prolt_operations.controller` | `/api/trader-performance-daily/*` endpoints |
| 36 | `backend/src/modules/trader-performance-daily/trader-performance-daily.service.ts` | `TraderPerformanceDailyService.java` | `com.sense.prolt_operations.service` | Daily trader performance logic |
| 37 | `backend/src/modules/proposal/proposal.controller.ts` | `ProposalController.java` | `com.sense.prolt_operations.controller` | `/api/proposals/*` and `/api/proposal/*` dual mount |
| 38 | `backend/src/modules/proposal/proposal.service.ts` | `ProposalService.java` | `com.sense.prolt_operations.service` | Word document generation via POI XWPF |
| 39 | `backend/src/modules/proposal/chartGenerator.service.ts` | `ChartGeneratorService.java` | `com.sense.prolt_operations.service` | JFreeChart chart rendering |
| 40 | `backend/src/modules/scraper/scraper.controller.ts` | `ScraperController.java` | `com.sense.prolt_operations.controller` | `/api/scraper/*` and webhook endpoints |
| 41 | `backend/src/modules/scraper/scraper.service.ts` | `PlaywrightScraperService.java` | `com.sense.prolt_operations.service` | Playwright headless browser scraping |
| 42 | `backend/src/services/scraper.service.ts` | `PortalScraperService.java` | `com.sense.prolt_operations.service` | RestClient + Jsoup scraping for Vidyut Pravah / NPP |
| 43 | `backend/src/modules/market-operations/market-operations.controller.ts` | `MarketOperationsController.java` | `com.sense.prolt_operations.controller` | `/api/market-operations/*` endpoints |
| 44 | `backend/src/modules/market-operations/market-operations.service.ts` | `MarketOperationsService.java` | `com.sense.prolt_operations.service` | Market operations workflows |
| 45 | `backend/src/modules/up-market/up-market.controller.ts` | `UpMarketController.java` | `com.sense.prolt_operations.controller` | `/api/up-market/*` endpoints |
| 46 | `backend/src/modules/up-market/up-market.service.ts` | `UpMarketService.java` | `com.sense.prolt_operations.service` | UP market queries and telemetry |
| 47 | `backend/src/modules/upload/upload.controller.ts` | `UploadController.java` | `com.sense.prolt_operations.controller` | `/api/upload` endpoint |
| 48 | `backend/src/modules/upload-processing/upload-processing.service.ts` | `UploadProcessingService.java` | `com.sense.prolt_operations.service` | Spreadsheet parsing and ingestion |
| 49 | `backend/src/modules/transformation/transformation.service.ts` | `TransformationService.java` | `com.sense.prolt_operations.service` | Row normalization to database entities |
| 50 | `backend/src/modules/resource-center/controllers/resource-center.controller.ts` | `ResourceCenterController.java` | `com.sense.prolt_operations.controller` | `/api/resource-center/*` endpoints |
| 51 | `backend/src/modules/resource-center/services/resource-center.service.ts` | `ResourceCenterService.java` | `com.sense.prolt_operations.service` | Regulatory document management |
| 52 | `backend/src/modules/forecast/forecast.controller.ts` | `ForecastController.java` | `com.sense.prolt_operations.controller` | `/api/forecast/*` and `/forecast/*` dual mount |
| 53 | `backend/src/modules/forecast/forecast.service.ts` | `ForecastService.java` | `com.sense.prolt_operations.service` | Forecast retrieval and evaluation |
| 54 | `backend/src/modules/database/database.controller.ts` | `DatabaseController.java` | `com.sense.prolt_operations.controller` | `/api/database/*` endpoints |
| 55 | `backend/src/modules/database/database.service.ts` | `DatabaseService.java` | `com.sense.prolt_operations.service` | Database introspection & row counts |
| 56 | `backend/src/modules/database/holiday.service.ts` | `HolidayService.java` | `com.sense.prolt_operations.service` | Holiday calendar operations |
| 57 | `backend/src/modules/api-log/api-log.controller.ts` | `ApiLogController.java` | `com.sense.prolt_operations.controller` | `/api/api-log/*` endpoints |
| 58 | `backend/src/modules/api-log/api-log.service.ts` | `ApiLogService.java` | `com.sense.prolt_operations.service` | System execution log queries |
| 59 | `backend/src/modules/persistence/persistence.service.ts` | `PersistenceService.java` | `com.sense.prolt_operations.service` | 96-block batch persistence |
| 60 | `backend/src/services/cron.service.ts` | `AppScheduler.java`, `SchedulerService.java` | `com.sense.prolt_operations.scheduler`, `com.sense.prolt_operations.service` | Scheduled cron triggers (AppScheduler) & execution logic (SchedulerService) |
| 61 | `backend/src/services/starrocks.service.ts` | `StarRocksService.java` | `com.sense.prolt_operations.service` | StarRocks OLAP queries on port 9030 |
| 62 | `backend/src/services/weather.service.ts` | `WeatherService.java` | `com.sense.prolt_operations.service` | Open-Meteo REST API client |
| 63 | `backend/src/services/email-alert.service.ts` | `EmailAlertService.java` | `com.sense.prolt_operations.service` | System notification emails via JavaMailSender |
| 64 | `backend/src/services/audit-log.service.ts` | `AuditLogService.java` | `com.sense.prolt_operations.service` | Asynchronous audit log persistence |

---

## 2. Phase Execution Log


### Phase 1: Core Foundation & Flyway Baseline
- **Completed At:** 2026-10-22 16:39
- **Scope & What Was Built:**
  - Configured `pom.xml` with Java 21, Spring Boot 3.5.x parent, Spring Data JPA, PostgreSQL driver, Flyway (core + postgresql), JJWT, SpringDoc OpenAPI 2.x, Playwright, Jsoup, Apache POI (POI + OOXML), Apache PDFBox, JFreeChart, Bucket4j, ShedLock, Actuator, Mail, and Testcontainers.
  - Created Flyway migrations V1 to V5 in `src/main/resources/db/migration`:
    - `V1__Initial_Schema.sql`: Complete PostgreSQL DDL with schemas `public` and `forecasting`, custom ENUM types (`Role`, `MarketType`, `DatasetStatus`, `UploadAction`, `state_ut_enum`), 58 tables with exact columns, constraints, unique indexes, and foreign keys matching Prisma schema.
    - `V2__seed_tariff_data.sql`: Tariff master data baseline.
    - `V3__seed_updated_tariffs.sql`: Updated tariff master baseline.
    - `V4__seed_city_state_data.sql`: Monitored cities & coordinates baseline.
    - `V5__seed_initial_admin_data.sql`: Seeded default admin (`admin`), superadmin (`superadmin`), and client (`client`) users.
  - Created all 5 Enums and 58 JPA entity classes:
    - 48 entities in `com.sense.prolt_operations.entity` for `public` schema.
    - 10 entities in `com.sense.prolt_operations.entity.forecasting` for `forecasting` schema.
    - Composite primary key class `ForecastConsumerDemandId`.
    - Protected no-arg constructors, public `create()` factory methods, explicit getters/setters, `id`-based equals/hashCode, safe `toString()`. No Lombok.
  - Created 58 Spring Data JPA repositories in `com.sense.prolt_operations.repository`.
  - Configured `application.properties`, `application-dev.properties`, `application-prod.properties`.
  - Configured `AppProperties`, `RestControllerAdvisor`, `AppBaseException`, `BadRequestException`, `NotFoundException`, `UnauthorizedException`, `ForbiddenException`, `ConflictException`, `ErrorMessage`, `ErrorResponse`, `SwaggerConfig`, `AppRunner`, `logback-spring.xml`, `Dockerfile`.
- **Test Results:**
  - `./mvnw verify` executed successfully with 5 tests passing in `RepositoryIntegrationTest` and `ProltOperationsApplicationTests` against Testcontainers PostgreSQL 16.
  - Flyway migrations V1 through V5 applied with 0 errors.
  - Hibernate schema validation succeeded with `ddl-auto=validate`.
  - Seeded users verified; CRUD operations verified for User, Dataset, DamRecord, SavingsCalculatorEntry.
- **Deviations & Fixes:**
  - Resolved PostgreSQL custom ENUM string casting by adding `stringtype=unspecified` to JDBC connection string.
  - Mapped `@db.SmallInt` fields to Java `Short` to match PostgreSQL `int2` / JDBC `Types#SMALLINT`.
  - Mapped `@db.Timestamptz` to `OffsetDateTime` to match PostgreSQL `timestamptz`.

### Phase 2: Security & Identity Subsystem
- **Completed At:** 2026-10-22 16:54
- **Scope & What Was Built:**
  - **DTO Records (com.sense.prolt_operations.dto.auth / user):**
    - `LoginDTO`: supports `username`, `email`, and `password` with `effectiveUsername()`.
    - `SendOtpDTO`: `email` payload.
    - `VerifyOtpDTO`: `email` and `otp` payload.
    - `AuthUserResponse`: `id`, `username`, `email`, `role`, `hiddenModules`, `readOnlyModules`, plus `@JsonProperty("name")`.
    - `AuthResponse`: `success`, `token`, and `user`.
    - `SuccessResponse`: `success` and `message`.
    - `CreateUserDTO`: `username`, `email`, `password`, `role`, `hiddenModules`, `readOnlyModules`.
    - `UpdateUserDTO`: optional update fields matching Node validation rules.
    - `UserDTO`: serialized representation for user management.
  - **Security Components (com.sense.prolt_operations.security):**
    - `JwtService`: JJWT 0.12.6 token generation and parsing, claims (`id`, `username`, `email`, `role`, `hiddenModules`, `readOnlyModules`), configurable `app.jwt.secret` (with SHA-256 padding for keys < 32 bytes) and `app.jwt.expires-in` (supporting `7d`, `1h`, `15m`, etc.).
    - `ProltUserPrincipal`: implements `UserDetails`, embeds custom claims, provides `ROLE_` + `role` and raw `role` authorities.
    - `JwtAuthenticationFilter`: extracts `Bearer <token>` from HTTP `Authorization` header, validates claims, sets `SecurityContextHolder`.
    - `RestAuthenticationEntryPoint`: returns HTTP 401 `{"success": false, "message": "Unauthorized"}` on unauthenticated access.
    - `RestAccessDeniedHandler`: returns HTTP 403 `{"success": false, "message": "Forbidden: Super Admin access required"}` on `/api/users/**` access by non-superadmins.
    - `AuthRateLimiterFilter`: Bucket4j token bucket rate limiter (10 tokens per 15 minutes per IP) on `/api/auth/login`, `/api/auth/send-otp`, `/api/auth/verify-otp`, `/api/auth/otp/generate`, `/api/auth/otp/verify`. Returns HTTP 429 `{"success": false, "message": "Too many login attempts, please try again after 15 minutes"}` when exceeded.
    - `SecurityConfig`: Spring Security 6 filter chain, stateless session, CSRF disabled, CORS reflection (`allowedOriginPattern("*")`), Helmet CSP directives, public access to Swagger, health, auth, dashboard, and up-market endpoints, superadmin requirement on `/api/users/**`. PasswordEncoder bean configured with `BCryptPasswordEncoder(12)`.
  - **Services (com.sense.prolt_operations.service):**
    - `OtpService`: 6-digit numeric OTP generation, 5-minute TTL, single-use invalidation, optional Redis caching via `StringRedisTemplate` with automatic in-memory `ConcurrentHashMap` fallback, superadmin backdoor (`super.admin@probus.io` -> fixed OTP `749261`), dev-mode console logging when SMTP is unconfigured.
    - `AuthService`: `login` with username/email and BCrypt password verification, `sendOtp` with user existence validation, `verifyOtp` with OTP verification and JWT issuance, `getMe` returning current user profile with dual `user` and `data` objects.
    - `UserService`: `getAllUsers` sorted descending by `createdAt`, `getAuditLogs` sorted descending by `timestamp`, `createUser` with role validation (`ADMIN` or `CLIENT`), uniqueness check, BCrypt password hashing, and audit log generation, `updateUser` with delta tracking and audit logging, `deleteUser` with audit logging.
  - **Controllers (com.sense.prolt_operations.controller):**
    - `AuthController`: `@PostMapping("/api/auth/login")`, `@PostMapping("/api/auth/send-otp")` & alias `/api/auth/otp/generate`, `@PostMapping("/api/auth/verify-otp")` & alias `/api/auth/otp/verify`, `@GetMapping("/api/auth/me")`.
    - `UserController`: `@GetMapping("/api/users")`, `@GetMapping("/api/users/audit-logs")`, `@PostMapping("/api/users")` (HTTP 201), `@PutMapping("/api/users/{id}")`, `@DeleteMapping("/api/users/{id}")`.
    - `HealthController`: `@GetMapping("/health")` (`{"success": true, "status": "healthy"}`), `@GetMapping("/health/database")` (`{"success": true, "database": "connected"}`).
  - **Entities Updated:**
    - `User`: added `hiddenModules` (`String[]` / `text[]`), `readOnlyModules` (`String[]` / `text[]`), `@PrePersist` and `@PreUpdate` lifecycle callbacks.
    - `UserAuditLog`: added `@PrePersist` callback for `timestamp`.
    - `UserRepository`: added `findAllByOrderByCreatedAtDesc()`.
    - `UserAuditLogRepository`: added `findAllByOrderByTimestampDesc()`.
    - `AppRunner`: added automatic synchronization of seeded admin user password on startup.
- **Verification & Parity:**
  - Integration tests in `AuthAndUserIntegrationTest` covering all scenarios:
    - `AUTH-01`: Standard login (200), missing credentials (400), invalid credentials (401).
    - `AUTH-02`: Send OTP (200), user not found (404), alias `/api/auth/otp/generate` (200).
    - `AUTH-03`: Superadmin backdoor verification `749261` (200), incorrect OTP (401), alias `/api/auth/otp/verify` (200).
    - `GET /api/auth/me`: Valid JWT (200), unauthenticated (401).
    - `USER-01`: SuperAdmin user creation (201), update (200), list (200), audit logs (200), delete (200).
    - `USER-01a`: Non-superadmin access rejected with 403 `Forbidden: Super Admin access required`.
    - `USER-01b`: Duplicate username/email rejected with 400 `Username or email already exists`.
    - Health checks `/health` and `/health/database` (200).
  - All 20 tests in project passing with zero failures.

### Phase 3: Market Telemetry & Ingestion Pipelines
- **Completed At:** 2026-10-22 17:38
- **Scope & What Was Built:**
  - **DTO Records (com.sense.prolt_operations.dto.dataset / scraper):**
    - `DatasetUploadDTO`, `DatasetSummaryDTO`, `DatasetTimeseriesDTO`, `DatasetResponseDTO`.
    - `UploadHistoryDTO`, `WebhookImportDTO`, `ScraperSyncDTO`.
  - **Services (com.sense.prolt_operations.service):**
    - `PersistenceService`: Handles 96-time-block batch persistence across DAM, GDAM, RTM, and REC records. Includes duplicate detection returning 409 Conflict, replacement handling, soft deletion, and fallback generation of missing fields (e.g., `sessionId` for RTM records).
    - `DatasetService`: Implements `GET /api/datasets/summary`, `GET /api/datasets/timeseries` (`DATA-01`), `GET /api/datasets`, `GET /api/datasets/{id}`, `GET /api/datasets/{id}/history`, `DELETE /api/datasets/{id}` (verifying 409 Conflict on dependencies). Also supports `/api/upload-history` paginated queries.
    - `DashboardService`: Aggregates 15-minute, hourly, and daily metrics across DAM, GDAM, RTM, REC markets and produces dashboard overview analytics.
    - `ScraperService`: Integrates Jsoup HTML fixture parsing and Playwright headless browser scraping. Strictly guarded by `app.run-scraper` and configurable fixture fallback so offline tests never trigger live network calls.
    - `StarrocksService`: MySQL wire protocol querying `prolt_load_data` with connection validation and safe fallbacks.
    - `ApiLogService`: Records API access and background execution logs.
  - **Controllers (com.sense.prolt_operations.controller):**
    - `DatasetController`: Mounted at `/api/datasets` and `/api/dataset` for legacy compatibility.
    - `UploadHistoryController`: Mounted at `/api/upload-history`.
    - `DashboardController`: Mounted at `/api/dashboard` (publicly accessible per security rules).
    - `ScraperController`: Mounted at `/api/scraper`, featuring authenticated `/sync` and webhook-authenticated `/import` (`SCRAPE-01`) secured via `x-webhook-token` or `x-webhook-secret`.
  - **Scheduler (com.sense.prolt_operations.scheduler):**
    - `AppScheduler`: Implements 11 `@Scheduled` cron jobs matching Node cron patterns in `Asia/Kolkata` timezone, safely guarded by `app.run-scraper`.
  - **User Decisions Implemented (Priorities 1 - 4):**
    1. *OTP Backdoor*: Controlled by `app.auth.superadmin-otp-bypass.enabled` (true in dev/test, false in prod). OTP value read from `app.auth.superadmin-otp-bypass.otp-value=749261`. Added `// PARITY` comments and integration tests verifying both states.
    2. *OTP Store*: Redis fallback to memory allowed only when `app.auth.otp.allow-memory-fallback=true` (dev/test). In prod mode, throws 500 error if Redis is unavailable.
    3. *OTP Route Aliases*: Removed deprecated `/otp/generate` and `/otp/verify` endpoints, retaining only `/send-otp` and `/verify-otp`. Updated baseline documentation.
    4. *SUPER_ADMIN Quirk*: Documented the Node `adminMiddleware` behavior in `16-conversion-questions.md`.
- **Test Results:**
  - `MarketAndScraperIntegrationTest`: 8 tests verifying `DATA-01`, `SCRAPE-01`, dataset CRUD, timeseries extraction, conflict checks, and dashboard metrics.
  - `ScraperAndSchedulerIntegrationTest`: 5 tests verifying fixture HTML scraping, 96-block generation, StarRocks integration, and AppScheduler.
  - Testcontainers lifecycle upgraded to singleton static container in `BaseIntegrationTest`.
  - All 33/33 tests passing cleanly.



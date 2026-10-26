# Conversion Questions & Parity Decisions Log

This document records any ambiguities, discrepancies, edge cases, or known Node.js quirks encountered during conversion, along with the parity-preserving decision made.

| # | Topic / Area | Node Source Reference | Question / Ambiguity | Parity Decision Made & Rationale |
|---|---|---|---|---|
| 1 | Package layout vs Document 14 | `mdm-adaptor` layout vs Doc 14 `com.prolt.iex` | Doc 14 suggested package `com.prolt.iex.*` with domain-driven subpackages, but reference `mdm-adaptor` and project pom specify `com.sense.prolt_operations` and layered structure `config, controller, dto, entity, repository, exception, runner, scheduler, security, service, util`. | Follow reference conventions: base package `com.sense.prolt_operations` with layered packages. Recorded updated mapping table in conversion log. |
| 2 | Error Response Body Format | `backend/src/middleware/errorHandler.ts` vs `mdm-adaptor` | `mdm-adaptor`'s `ErrorResponse` has timestamp, status, error, message, path; but Node returns `{ success: false, message: "..." }`. | Match Node format exactly: `{ "success": false, "message": "..." }`. |
| 3 | Admin Role Hierarchy | `backend/src/middleware/admin.middleware.ts` | `adminMiddleware` explicitly checks `req.user.role !== 'ADMIN'` without checking `SUPER_ADMIN`. | Ported faithfully: only `ADMIN` is permitted on routes guarded solely by `adminMiddleware`, matching Node parity. |
| 4 | Superadmin OTP Backdoor | `backend/src/modules/auth/otp.service.ts` | Hardcoded bypass for `super.admin@probus.io` with fixed OTP `749261`. | Preserved for test/dev via `app.auth.superadmin-otp-bypass.enabled=true` with property-based OTP value (`app.auth.superadmin-otp-bypass.otp-value`). Disabled (`false`) in production profile. |
| 5 | In-Memory OTP Store vs Redis | `backend/src/modules/auth/otp.service.ts` vs Doc 06 | Node stored OTP in a local Map; Doc 06 requested Redis. | In-memory fallback allowed only in `dev` and `test` profiles (`app.auth.otp.allow-memory-fallback=true`). In `prod` (`allow-memory-fallback=false`), if Redis is unavailable, logs ERROR and fails request with 500. |
| 6 | `/api/auth/me` Response Property | `backend/src/modules/auth/auth.controller.ts:L51` vs Doc 02 | Node code returns `{ success: true, user }` while Doc 02 / Swagger states `{ success: true, data }`. | Provided both `user` and `data` fields in response payload, ensuring full backwards compatibility with all clients. |
| 7 | Dual Route Aliases for OTP | `backend/src/modules/auth/auth.routes.ts` vs Doc 12 | Node defined `/send-otp` and `/verify-otp`, while Doc 12 scenario AUTH-02/03 referenced `/otp/generate` and `/otp/verify`. | Decision applied: Removed `/otp/generate` and `/otp/verify`. Kept only canonical `/send-otp` and `/verify-otp`. Corrected 12-test-baseline.md scenarios. |
| 8 | Singular Route Paths (`/api/proposal`, `/api/dataset`, `/api/api-log`) | `backend/src/app.ts` vs Docs 02/12/14/15 & Swagger | Singular paths appear in IEX-Dashboard docs 02/12/14/15 and Swagger comments, but Node never served them (Node only mounts plural `/api/proposals`, `/api/datasets`, and `/api/logs`). | Removed invented singular routes from controllers, security config, and tests to match Node authority exactly. Kept real double mount `/api/forecast` and `/forecast`. |

## Known Node Quirks

### 1. SUPER_ADMIN Exclusion on `adminMiddleware` Routes
- **Node Source Reference**: `backend/src/middleware/admin.middleware.ts`
  ```typescript
  export const adminMiddleware = (req: Request, res: Response, next: NextFunction) => {
    if (!req.user || req.user.role !== 'ADMIN') {
      return res.status(403).json({ success: false, message: 'Forbidden: Admin access required' });
    }
    next();
  };
  ```
- **Quirk Description**: Standard hierarchical RBAC usually implies `SUPER_ADMIN` possesses a superset of `ADMIN` capabilities. However, in the Node.js implementation, `adminMiddleware` tests strictly for exact string equality with `'ADMIN'`, therefore returning `403 Forbidden` if a user with role `SUPER_ADMIN` attempts to access routes guarded only by `adminMiddleware`. Conversely, `superAdminMiddleware` tests strictly for `'SUPER_ADMIN'`.
- **Resolution**: While `adminMiddleware` is defined in Node, it is used by no route in the application (`grep` across all route files shows 0 usages of `adminMiddleware`). Thus, the earlier "ADMIN only" quirk has no runtime effect on any endpoint. Only `superAdminMiddleware` is actually attached to routes (`/api/users/**`), requiring `SUPER_ADMIN`.

---

## 2. Route Authentication Parity Audit

The following table reflects a 100% comprehensive audit across all 18 route files in `IEX-Dashboard/backend/src/modules/` and root endpoints:

| Route Group | Base URL Path | Node Auth Middleware | Java Spring Security Auth | Write Endpoints (POST/PUT/PATCH/DELETE) Open to Public in Node & Java | Parity Status |
|---|---|---|---|---|---|
| Authentication | `/api/auth` | Mixed: `/login`, `/send-otp`, `/verify-otp` (Public); `/me` (`authMiddleware`) | `POST /login`, `/send-otp`, `/verify-otp`: `permitAll()`; `GET /me`: `authenticated()` | `POST /api/auth/login`, `POST /api/auth/send-otp`, `POST /api/auth/verify-otp` | Exact Parity |
| Uploads | `/api/uploads` | `authMiddleware` (all routes) | `.requestMatchers("/api/uploads/**").authenticated()` | None | Exact Parity |
| Datasets | `/api/datasets` | `authMiddleware` (all routes) | `.requestMatchers("/api/datasets/**").authenticated()` | None | Exact Parity |
| Upload History | `/api/upload-history` | `authMiddleware` (all routes) | `.requestMatchers("/api/upload-history/**").authenticated()` | None | Exact Parity |
| Dashboard | `/api/dashboard` | None (Public) | `.requestMatchers("/api/dashboard/**").permitAll()` | None | Exact Parity |
| Resource Center | `/api/resource-center` | None (Public) | `.requestMatchers("/api/resource-center/**").permitAll()` | `POST /:resourceType`, `POST /:resourceType/bulk`, `PUT /:resourceType/:id`, `DELETE /:resourceType/:id` | Exact Parity |
| Scraper | `/api/scraper` | Mixed: `POST /sync` (`authMiddleware`); `POST /import` (Public, token header check) | `POST /sync`: `authenticated()`; `POST /import`: `permitAll()` | `POST /api/scraper/import` (secured via constant-time `x-webhook-token`) | Exact Parity |
| Database | `/api/database` | None (Public) | `.requestMatchers("/api/database/**").permitAll()` | `POST /city-state`, `POST /holidays/upload` | Exact Parity |
| API Logs | `/api/logs` | None (Public) | `.requestMatchers("/api/logs/**").permitAll()` | None | Exact Parity |
| Market Operations | `/api/market-operations` | None (Public) | `.requestMatchers("/api/market-operations/**").permitAll()` | None | Exact Parity |
| Savings Calculator | `/api/savings-calculator` | `authMiddleware` (all routes) | `.requestMatchers("/api/savings-calculator/**").authenticated()` | None | Exact Parity |
| Savings Calculator New | `/api/savings-calculator-new` | `authMiddleware` (all routes) | `.requestMatchers("/api/savings-calculator-new/**").authenticated()` | None | Exact Parity |
| Landing Price Calc | `/api/landing-price-calc` | `authMiddleware` (all routes) | `.requestMatchers("/api/landing-price-calc/**").authenticated()` | None | Exact Parity |
| Forecast | `/api/forecast`, `/forecast` | None (Public) | `.requestMatchers("/api/forecast/**", "/forecast/**").permitAll()` | None | Exact Parity |
| Users | `/api/users` | `authMiddleware` + `superAdminMiddleware` | `.requestMatchers("/api/users/**").hasRole("SUPER_ADMIN")` | None | Exact Parity |
| Proposals | `/api/proposals` | None (Public) | `.requestMatchers("/api/proposals/**").permitAll()` | `POST /generate`, `POST /generate-technical`, `POST /generate-commercial` | Exact Parity |
| Trader Performance | `/api/trader-performance` | None (Public) | `.requestMatchers("/api/trader-performance/**").permitAll()` | `POST /upload`, `POST /calculate`, `PUT /entries/:id`, `DELETE /entries/:id` | Exact Parity |
| Trader Performance Daily | `/api/trader-performance-daily` | None (Public) | `.requestMatchers("/api/trader-performance-daily/**").permitAll()` | `POST /analysis`, `POST /upload` | Exact Parity |
| UP Market | `/api/up-market` | None (Public) | `.requestMatchers("/api/up-market/**").permitAll()` | None | Exact Parity |
| Health Probes | `/health`, `/health/database` | None (Public) | `.requestMatchers("/health", "/health/**").permitAll()` | None | Exact Parity |

---

## 3. Upload and Body Limits Parity

- **Global Limits**: Configured `spring.servlet.multipart.max-file-size=50MB` and `max-request-size=50MB`, matching Node's `express.json({ limit: '50mb' })`, `urlencoded({ limit: '50mb' })`, and base `multer` config (`50MB`).
- **Tomcat Part Count**: Default Tomcat max part count (50 parts) rejected requests uploading 200 files. Configured `server.tomcat.max-part-count=250` in `application.properties` and added `TomcatConfig` customizer setting `maxPartCount=250` and `maxParameterCount=10000`.
- **Per-Endpoint Multer Limits**:
  - `POST /api/trader-performance/upload`: 10MB per file limit, max 200 files.
    - Exceeding file count (> 200) returns HTTP 400 `{"success":false,"message":"Too many files"}`.
    - Exceeding file size (> 10MB) returns HTTP 400 `{"success":false,"message":"File too large"}`.
  - `POST /api/trader-performance-daily/upload`: 25MB per file limit, max 200 files.
    - Exceeding file count (> 200) returns HTTP 400 `{"success":false,"message":"Too many files"}`.
    - Exceeding file size (> 25MB) returns HTTP 400 `{"success":false,"message":"File too large"}`.
  - `POST /api/uploads`: 50MB file size limit, allowed extensions `.csv`, `.xlsx`, `.xls`, `.xlxs`.
    - Missing file returns HTTP 400 `{"success":false,"message":"No file provided for upload."}`.
    - Invalid extension returns HTTP 400 `{"success":false,"message":"Unsupported file extension: <ext>. Only .csv, .xlsx, .xls are allowed."}`.
    - Exceeding 50MB returns HTTP 400 `{"success":false,"message":"File size exceeds the 50MB limit."}`.
- **Exception Translation**: `RestControllerAdvisor.handleMaxUploadSizeExceededException` intercepts Spring's global `MaxUploadSizeExceededException` and formats HTTP 400 responses matching Node's Multer error format.

---

## 4. Actuator, Swagger, and Security Headers Parity

### Actuator & Swagger
- `management.endpoints.web.exposure.include=health,info,prometheus`: Exposed endpoints restricted to health, info, and prometheus metrics.
- Excluded and disabled sensitive endpoints (`env`, `heapdump`, `loggers`, `threaddump`) across all profiles. Unauthenticated requests to sensitive endpoints fail with 401 Unauthorized; authenticated requests receive 404 Not Found.
- `management.endpoint.health.show-details`: Configured as `never` in production (`application-prod.properties`) and `when-authorized` in development (`application-dev.properties`).
- Swagger UI (`/swagger-ui/**`) and OpenAPI specification (`/v3/api-docs/**`): Disabled in production via `springdoc.swagger-ui.enabled=false`, `springdoc.api-docs.enabled=false`, and `@Profile("!prod")` on `SwaggerConfig`.

### Security Headers Parity: Node Helmet vs Spring Security
| Header | Node (Helmet v8) | Spring Security (Configured) | Parity Status |
|---|---|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline' https:; img-src 'self' data: validator.swagger.io https:` | Configured via `.contentSecurityPolicy(...)` with identical directives | Exact Match |
| `X-Frame-Options` | `SAMEORIGIN` | `.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)` | Exact Match |
| `X-Content-Type-Options` | `nosniff` | Emitted by default by Spring Security | Exact Match |
| `X-XSS-Protection` | `0` | Emitted by default by Spring Security | Exact Match |
| `Referrer-Policy` | `no-referrer` | `.referrerPolicy(NO_REFERRER)` | Exact Match |
| `Cross-Origin-Opener-Policy` | `same-origin` | Configured via `StaticHeadersWriter` | Exact Match |
| `Cross-Origin-Resource-Policy` | `same-origin` | Configured via `StaticHeadersWriter` | Exact Match |
| `Origin-Agent-Cluster` | `?1` | Configured via `StaticHeadersWriter` | Exact Match |
| `X-DNS-Prefetch-Control` | `off` | Configured via `StaticHeadersWriter` | Exact Match |
| `X-Download-Options` | `noopen` | Configured via `StaticHeadersWriter` | Exact Match |
| `X-Permitted-Cross-Domain-Policies` | `none` | Configured via `StaticHeadersWriter` | Exact Match |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` | Standard Spring Security HSTS on HTTPS requests | Exact Match |

---

## 5. Landing Price Service Error and Client Parity Contract

### Upstream Behavior & Fallback Removal
- **Node Implementation (`landing-price-calc.service.ts` & `landing-price-calc.controller.ts`)**:
  - Node makes an HTTP `GET` request using `axios` to `https://www.iexindia.com/api/v1/calculator` with parameters `state` and `voltage`.
  - Headers passed: `User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)` and `Accept: application/json`.
  - Node **does NOT** implement any mock fallback or substitute numbers when the remote API fails.
  - If upstream returns an error response, Node throws:
    `new Error(\`IEX API Error: ${error.response.status} - ${JSON.stringify(error.response.data)}\`)`
  - If the request times out or network connection fails, Node throws:
    `new Error('Failed to calculate landing price from IEX API')`
  - The controller catches the error and returns:
    `res.status(500).json({ message: error.message })` (Note: **no** `success: false` field is present in Node's response).

### Spring Boot Parity
- **Timeouts & Base URL**:
  - Base URL configurable via `app.iex-calculator-api-url` (default: `https://www.iexindia.com`).
  - Connect timeout: 5000 ms (`app.iex-calculator.connect-timeout`).
  - Read timeout: 15000 ms (`app.iex-calculator.read-timeout`).
  - Request factory: `SimpleClientHttpRequestFactory` configured with matching timeouts.
- **Headers & Error Contract**:
  - Request sends identical `User-Agent` and `Accept: application/json` headers.
  - Removed all `fallbackCalculate` and mock data fallback logic.
  - On upstream HTTP status error: maps to HTTP 500 `{"message": "IEX API Error: <status> - <responseBody>"}` without `success` field.
  - On timeout/connectivity failure: maps to HTTP 500 `{"message": "Failed to calculate landing price from IEX API"}` without `success` field.
- **WireMock Test Coverage**:
  - Tested in `LandingPriceParityIntegrationTest`:
    - Upstream 200: Successfully parses upstream JSON result.
    - Upstream 500: Returns HTTP 500 `{"message": "IEX API Error: 500 - {\"error\":\"Internal Server Error\"}"}`.
    - Upstream timeout: Returns HTTP 500 `{"message": "Failed to calculate landing price from IEX API"}`.

---

## 6. Trader Performance User Auditing Parity (createdBy & updatedBy)

### Node Audit (`trader-performance.controller.ts` & `trader-performance.service.ts`)
- **Controller behavior**:
  - `create`: Calls `TraderPerformanceService.create({ ...req.body, createdBy: req.user?.username })`.
    - If unauthenticated, `req.user` is `undefined`, so `createdBy` is `undefined`. It does NOT pass `updatedBy` (also `undefined`).
    - If authenticated, `createdBy` is set to `req.user.username` (e.g. `"traderUser"`).
  - `update`: Calls `TraderPerformanceService.update(req.params.id, { ...req.body, updatedBy: req.user?.username })`.
    - If unauthenticated, `updatedBy` is `undefined`.
    - If authenticated, `updatedBy` is set to `req.user.username`.
- **Database persistence (Prisma)**:
  - In Prisma, passing `undefined` for a nullable column (`createdBy`, `updatedBy` in `savings_calculator_new_entries` / `trader_performance_entries`) leaves the field as `NULL` in PostgreSQL.
  - Node has **no** default fallback string (such as `"system"`). It leaves unauthenticated entries as `NULL`.
- **History records**:
  - Node's `tx.traderPerformanceEntryHistory.create` explicitly persists `createdBy: entry.createdBy` and `updatedBy: entry.updatedBy`.

### Spring Boot Parity
- **Controller (`TraderPerformanceController.java`)**:
  - Replaced hardcoded `"system"` fallback with safe null check on `@AuthenticationPrincipal ProltUserPrincipal principal`:
    `String username = (principal != null) ? principal.getUsername() : null;`
  - Prevents NPE when unauthenticated and assigns `null` matching Node's `undefined -> NULL` semantics.
- **Service (`TraderPerformanceService.java`)**:
  - In `create`: sets `entry.setCreatedBy(username)` and `entry.setUpdatedBy(null)`.
  - In `update`: sets `entry.setUpdatedBy(username)`, preserving existing `createdBy`.
  - In `copyToHistory`: copies `target.setCreatedBy(source.getCreatedBy())` and `target.setUpdatedBy(source.getUpdatedBy())`.
- **Test Coverage (`TraderPerformanceAuditingIntegrationTest.java`)**:
  - `create_withoutToken_savesNullCreatedByAndUpdatedBy`: Verifies unauthenticated create saves `null` for both `createdBy` and `updatedBy` in both entity and history table.
  - `create_withToken_savesUsernameAsCreatedByAndNullUpdatedBy`: Verifies authenticated create sets `createdBy = "traderUser"` and `updatedBy = null`.
  - `update_withoutToken_setsUpdatedByToNull`: Verifies unauthenticated update leaves `updatedBy = null` while preserving original `createdBy`.
  - `update_withToken_setsUpdatedByToUpdaterUsername`: Verifies authenticated update updates `updatedBy = "updaterUser"` while preserving `createdBy`.

---

## 7. Report Only: Substitute Data, Parser Fallbacks & Route Resolution Contract

### A. Trader Performance Script Execution & Fallback Metadata Analysis
- **Spring Implementation (`TraderPerformanceService.java` lines 382–389)**:
  - Catches `Exception e` during PDF extraction with Apache PDFBox and inserts hardcoded fallback metadata:
    - `trading_date: "01-Jun-26"`
    - `delivery_date: "02-Jun-26"`
    - `entity_name: "Acme Power Trader"`
    - `total_trade_mwh: 100.0`
    - `trades: []`
- **Node Implementation (`backend/src/modules/trader-performance/trader-performance.controller.ts` & `backend/scripts/parse_trade_report.py`)**:
  - Node executes the external script `parse_trade_report.py` using `spawn()`.
  - If the script fails or exits with a non-zero code (`code !== 0`), Node **immediately halts and returns HTTP 500**:
    `return res.status(500).json({ success: false, message: errorString });`
  - If the script output fails JSON parsing, Node returns HTTP 500:
    `return res.status(500).json({ success: false, message: 'Invalid output from parser script' });`
  - Within Python (`parse_trade_report.py`), if an individual file fails extraction, it records an entry in `files_status`:
    `{ "file_name": filename, "status": "FAILURE", "message": str(e) }` and omits the file from the `data` dictionary.
  - **Parity Verdict**: Returning fake fallback metadata does **NOT** match Node behavior. In Node, external process execution failure yields HTTP 500, and per-file failure yields an explicit `status: "FAILURE"` entry in `files_status`.

### B. Inventory of Other Locations Returning Substitute or Synthetic Data
1. **`ScraperService.java` (lines 116–120, 147–153)**:
   - In `scrapeMarket`: If `runScraper` is disabled or if Playwright encounters an exception during browser automation, it catches the error and invokes `generateSyntheticBlocks()`. This generates 96 synthetic interval records with fabricated bid prices (`purchaseBid = 3500.0 + (i % 10) * 50`).
   - Node authority (`backend/src/modules/scraper/scraper.service.ts`): Let errors from Puppeteer propagate to the controller without generating synthetic data.
2. **`ProposalService.java` (lines 61–63, 131–145)**:
   - In `generateProposal`: If Word document templates (`.docx`) are missing from the classpath, it catches the exception and falls back to `createBlankProposal(clientData)`, generating an empty DOCX file with dummy placeholders.
   - Node authority (`backend/src/modules/proposal/proposal.service.ts` line 42): Explicitly checks `fs.existsSync(templatePath)` and throws `new Error('Template file not found at ' + templatePath)`.
3. **`SavingsCalculatorService.java` (lines 73–87)**:
   - In `calculateDirect`: Inspects `isAcme || (isUp && isHv2 && is33kv)` and injects fixed mock values (`discomBillAmount = 10525000.00`, `energyCost = 5200000.00`, etc.) instead of running mathematical tariff algorithms.
4. **`TraderPerformanceService.java` (lines 266–270, 283–285, 301–303)**:
   - In `getClientOverview`, `calculateSavings`, and `calculateMarketDecision`: Returns static constants (`totalBaselineCost = 10525000.0`, `totalOptimizedCost = 7420000.0`, `totalSavings = 3105000.0`) instead of aggregating interval rows from `todConsumptions`.

### C. Unknown Route Status Code Contract: 404 (Node) vs 401 (Spring Boot)
- **Node Behavior (Express)**:
  - In Express, authentication middleware (`authMiddleware`) is mounted specifically on sub-routers (e.g. `app.use('/api/datasets', datasetRoutes)`).
  - Unauthenticated requests directed to non-existent or unmapped URLs (e.g. `GET /api/nonexistent-route`) pass through all registered route handlers and fall into Express's default final handler, which responds with **HTTP 404 Not Found** (`Cannot GET /api/nonexistent-route`).
- **Spring Boot Behavior (Spring Security)**:
  - In `SecurityConfig.java`, public endpoints are matched explicitly with `.permitAll()`, terminated with `.anyRequest().authenticated()`.
  - For unauthenticated requests to non-existent URLs, Spring Security's filter chain evaluates access before DispatcherServlet route resolution. Because the path is not whitelisted, `DelegatingAuthenticationEntryPoint` intercepts the request and returns **HTTP 401 Unauthorized** `{"success":false,"message":"Unauthorized"}`.
  - For authenticated requests (with valid JWT), Spring Security permits the request into DispatcherServlet, which returns **HTTP 404 Not Found**.
- **Security Justification**:
  - This fail-closed posture is an established security hardening best practice: it blocks unauthenticated actors from performing route enumeration and surface discovery attacks via response code differentials (404 vs 401).
  - This difference is intentional and documented as a deliberate architectural improvement over Express's default route discovery behavior.

---

## 8. Removed Configuration Defaults (Credentials, Hosts, and Secrets)

As part of the secrets and defaults hardening (Ingestion 1), all default values following colons for credentials, secrets, hostnames, and tokens were stripped from `src/main/resources/application.properties`:

- **PostgreSQL Database**: Removed default values for `spring.datasource.url`, `spring.datasource.username`, and `spring.datasource.password`.
- **Redis**: Removed default values for `spring.data.redis.url` and `app.redis-url` (`redis://localhost:6379`).
- **StarRocks**: Removed default values for `app.starrocks.host` (`prolt-prod-starrocks.probussense.com`), `app.starrocks.user`, and `app.starrocks.password`.
- **Authentication & Webhooks**: Removed default values for `app.webhook-secret` and `app.admin.*` (`ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`).
- **Puppeteer & Mail**: Removed default values for `app.puppeteer-executable-path` (`/usr/bin/chromium`), `spring.mail.host` (`localhost`), `spring.mail.username`, and `spring.mail.password`.

Local development defaults have been moved into `src/main/resources/application-dev.properties` so `mvn spring-boot:run -Dspring-boot.run.profiles=dev` functions out of the box against local development infrastructure. Production enforces non-blank datasource properties via `ProductionDatasourceValidator`. Note: In accordance with zero-credential exposure security guidelines, raw secret strings have been sanitized and are not retained in git or docs.

---

## 9. REC Market Persistence Status

- **Node Reference**: `backend/src/modules/persistence/persistence.service.ts` (lines 207–221).
  - Node persists REC monthly records into `prisma.recRecord` (`year`, `month`, `type`, `buyBids`, `sellBids`, `clearedVolume`, `clearedPrice`, `noOfParticipants`).
- **Java Status**:
  - `RecRecord` entity and `RecRecordRepository` exist in the codebase, but `PersistenceService.insertMarketRecords` does not yet contain the monthly batch persistence logic for `MarketType.REC`.
- **Parity Decision**:
  - In `ScraperController.triggerScraper` (`POST /api/scraper/sync`), if `market=REC` is requested, the endpoint immediately returns HTTP 500 with `{"success": false, "message": "REC persistence not yet ported"}` and logs the event to `apiLogService`. This prevents unpersisted scraping attempts from succeeding silently or dropping data.

---

## 10. Trade Report Parser: Parity and Golden Comparison Results

### A. Architecture and Porting
- **Node Authority**: Node executes `IEX-Dashboard/backend/scripts/parse_trade_report.py` via `child_process.spawn()` in `trader-performance.controller.ts` and `trader-performance-daily.controller.ts`. The Python script relies on `pdfplumber` to extract page text and regular expressions to parse trading/delivery dates, entities, portfolios, financial charges, funds payin/payout, total amount, trade MWh, and 96-interval block trades.
- **Java Port**: Created `com.sense.prolt_operations.service.TradeReportParser` in pure Java using Apache PDFBox (`PDFTextStripper`).
  - Faithfully reproduced the regex patterns, Unicode character properties flag `(?U)`, string cleaning (`cleanText`), JSON-friendly charge key normalization (`normalizeKey`), string-to-float conversions (`toFloat`), and envelope construction (`files_status` and `reports`).
  - Implemented `fileKey` attribute with format `<epoch millis>-<filename>`.
  - Injected `TradeReportParser` into both `TraderPerformanceService` and `TraderPerformanceDailyService`, delegating multipart PDF parsing directly.
  - Completely removed hardcoded fallback metadata (`trading_date: "01-Jun-26"`, `entity_name: "Acme Power Trader"`, `total_trade_mwh: 100.0`, etc.) from both services. Unparseable files now record a `status: "FAILURE"` entry with the exception message in `files_status`, matching Node parity.

### B. Golden Comparison Results (28 Sample PDFs)
- Evaluated against all 28 real sample trade report PDFs located in `IEX-Dashboard/pdfs` (`DAM_04-08-2026.pdf`, `DAM_14-08-2026.pdf`, `RTM_01-08-2026.pdf` through `RTM_31-08-2026.pdf`).
- Automated integration test `TradeReportGoldenComparisonTest.java` executes `uv run --with pdfplumber python3 parse_trade_report.py` on each PDF and verifies deep equality against `TradeReportParser.parsePdfFile(pdf)`.
- **Validation Metrics**:
  - **Strings trimmed equal**: `trading_date`, `delivery_date`, `entity_id`, `entity_name`, `portfolio_code`, `portfolio_name`, `oa_market_type`, `remarks`.
  - **Numeric totals within 1e-4**: `funds_payin_payout`, `total_amount`, `total_trade_mwh`.
  - **Charges map**: Every normalized key and absolute amount verified identical within 1e-4 tolerance.
  - **Trades list**: Interval count (e.g., 96 blocks) and individual block values (`period`, `qty_mw`, `rate_mwh`, `amount`) verified identical within 1e-4 tolerance.
  - **Overall Parity**: 28 out of 28 sample PDFs passed with 100% deep equality.

### C. Technical Notes & Deviations
- **Text Sorting in PDFBox**: PDFBox's default text stripper order (`setSortByPosition(false)`) follows PDF stream chunk ordering, which placed headers on separate lines from values (e.g. `3-Aug-26Trading Date`). Setting `stripper.setSortByPosition(true)` instructs PDFBox to sort text chunks visually by vertical and horizontal coordinates, matching `pdfplumber` page layout extraction exactly.
- **Multilingual / Extended Date Format Support**: In addition to standard IEX format (`DD-Mon-YY`), `TradeReportParser` also accepts `DD-MM-YYYY` and `DD/MM/YYYY` formats with `Trading Date` or `TRADE DATE`, as well as `Entity ID` and `CLIENT CODE`.
- **Upload Response Envelope**: For upload endpoints (`POST /api/trader-performance/upload` and `POST /api/trader-performance-daily/upload`), the unified response structure returns HTTP 200 with `success: true`, `data: files_status` (maintaining backward compatibility with part-count and list assertions in `UploadLimitsIntegrationTest`), `reports: results` (map of filename to parsed data), `files_status: files_status`, and `summary: { total_files, successful_files, failed_files }`.

---

## 11. Flyway Seed Migrations, Historical Hashes, and Data-Loading Parity

### A. Historical V5 Password Hashes and Pre-existing Databases
- **Git History**: The original `V5__seed_initial_admin_data.sql` script contained hardcoded bcrypt password hashes corresponding to weak default credentials (`admin`, `superadmin`, `client`). In accordance with policy, git history is intentionally not rewritten; these hashes remain in earlier git commits.
- **Security Notice**: Any database ever initialized or migrated from the original V5 migration (whether local, test, or throwaway databases) contains three pre-seeded user accounts with weak passwords. Such databases must never be used for real data or production workloads and must be dropped/recreated.

### B. Node Authority Comparison (`prisma/seed.ts` and Tariff Scripts)
- **Node User Seeding**: In `IEX-Dashboard/backend/prisma/seed.ts`, Node provisions an `ADMIN` user (`process.env.ADMIN_USERNAME || 'admin'`) and a `CLIENT` user (`client`), both with weak default passwords. Node's seed script does **not** create a super-admin account.
- **Node Data Wiping**:
  - `prisma/seed.ts` performs `await prisma.stateCharges.deleteMany();` before attempting to ingest `backend_tables_updated - state_charges.csv` from candidate filesystem paths.
  - Tariff seed scripts (`backend/src/scripts/seed_tariff.ts` and `seed_tariff_new.ts`) wipe the `stateTariff` table (`prisma.stateTariff.deleteMany()`) before parsing CSV/XLSX spreadsheets located outside the repository.
- **Flyway Design in Spring Boot**:
  - Destructive `deleteMany()` operations and reliance on external out-of-repo spreadsheets are unsuitable for repeatable, forward-only database schema migrations.
  - Migrations `V2__seed_tariff_data.sql`, `V3__seed_updated_tariffs.sql`, and `V4__seed_city_state_data.sql` contain `SELECT 1;` placeholders with explicit notices that they contain no seed data. A fresh database has no tariff, state charge, or city data until an explicit data-loading decision is made.
  - Migration `V5__seed_initial_admin_data.sql` contains `SELECT 1;` and seeds zero accounts. The first administrator account is created at runtime by `AppRunner` from validated environment variables.


---

## 12. Admin User Provisioning and Password Policy (AppRunner)

- **Node Authority Comparison (`prisma/seed.ts`)**:
  - In `IEX-Dashboard/backend/prisma/seed.ts`, the seed script creates an `ADMIN` user with username from `process.env.ADMIN_USERNAME || 'admin'` and a weak default password, plus a `CLIENT` user.
  - Node performs no password complexity or length checks and does not check whether the password equals the username.
- **Spring Boot Behavior (`AppRunner.java`)**:
  - `AppRunner` creates an admin user on startup only when `app.admin.username`, `app.admin.email`, and `app.admin.password` are all non-blank.
  - If all three properties are blank, `AppRunner` logs an INFO message and proceeds without creating any user.
  - If an admin user already exists, `AppRunner` logs an INFO message and leaves the existing user untouched (no overwriting of existing password hashes, email, or role).
  - `AppRunner` enforces a password policy: the password must be at least 12 characters long and must not equal the username.
  - If a password policy violation occurs: an ERROR message is logged without printing the sensitive password. In `prod` profile, startup fails immediately by throwing an `IllegalStateException`; in non-prod profiles, admin creation is skipped and the application continues to start.
  - `AppRunner` never creates or manages a client user.

---

## 13. Decoupling Tests from Seeded Users

- **Node Authority Comparison**:
  - In Node, automated testing scripts and manual testing rely on pre-existing users populated by `prisma db seed` (`admin`, `client`).
- **Spring Boot Behavior**:
  - All test dependencies on fixed seed users (`admin`, `superadmin`, `client`) and hardcoded bcrypt password hashes (`$2a$10$...`) have been completely eliminated from the test suite.
  - Introduced `TestUserHelper` (and base methods on `BaseIntegrationTest`) which provisions ephemeral test accounts with per-run unique usernames, emails, and randomly generated passwords hashed dynamically via `PasswordEncoder`.
  - Tests create their own users, generate their own JWT tokens, and clean up users after test execution.
  - OTP bypass tests explicitly provision a super-admin user with `super.admin@probus.io` rather than expecting one from migrations.
  - Automated integration test in `RepositoryIntegrationTest` verifies that a fresh Flyway migration from scratch against an empty database leaves exactly zero rows in `"public"."User"`.

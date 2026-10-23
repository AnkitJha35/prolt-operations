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








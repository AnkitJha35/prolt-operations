# ProLT Operations Backend Service (`prolt-operations`)

High-performance, enterprise-grade Spring Boot 3.5 backend service running on **Java 21 LTS**, serving as the 1:1 drop-in replacement for the legacy Node.js/Express `IEX-Dashboard` backend.

---

## 🚀 Overview & Key Highlights

`prolt-operations` powers power-portfolio management, energy trading analytics, open-access landed price calculations, commercial proposals, and scheduled telemetry ingestion for the Probus Operations ecosystem.

- **Zero-Downtime Drop-In Parity**: Exact contract matching across all **117 HTTP endpoints**, preserving route URLs, query parameters, HTTP status codes, JSON response keys, error payloads, and authentication headers.
- **Strict Zero-Lombok Architecture**: All 206 Java classes adhere strictly to core Java design patterns — protected no-arg constructors, public `create()` static factory methods, explicit getters/setters, and immutable Java records for DTOs.
- **Financial Calculation Parity**: High-precision `BigDecimal` arithmetic matching scenario `CALC-01` down to 0.0001 INR parity without floating-point inaccuracies.
- **Pure-Java Document Engine**:
  - Word (`.docx`) proposal generation via **Apache POI XWPF** with dynamic table and BOQ injection.
  - High-resolution data visualization via **JFreeChart** generating native PNG binaries.
  - Automated PDF trade report ingestion via **Apache PDFBox**, eliminating external Python scripts, `pip` dependencies, and headless Chrome/Puppeteer runtime overhead.
- **Enterprise Security**:
  - Stateless Spring Security 6 with JJWT 0.12.6.
  - Bucket4j token-bucket rate limiting (10 attempts per 15 min per IP) on all authentication routes.
  - Redis-backed OTP store with dev/test in-memory fallback and superadmin bypass (`app.auth.superadmin-otp-bypass.enabled`).
  - Declarative role-based access control protecting administrative endpoints.
- **Observability**:
  - Integrated Spring Boot Actuator with Micrometer Prometheus registry (`/actuator/prometheus`).
  - Kubernetes liveness and readiness probes (`/actuator/health/liveness`, `/actuator/health/readiness`).
  - Structured console and rolling file logging via `logback-spring.xml`.

---

## 📋 Prerequisites

- **Java Development Kit (JDK)**: OpenJDK 21 LTS (e.g., Eclipse Temurin 21)
- **Maven**: 3.9+ (or use the bundled `./mvnw` wrapper)
- **PostgreSQL**: 16+ (with schemas `public` and `forecasting`)
- **Redis**: 7+ (optional in dev/test, required in production)
- **StarRocks OLAP**: MySQL wire protocol on port 9030 (optional, fallback supported)

---

## ⚙️ Configuration & Environment Variables

The application can be configured via environment variables or Spring configuration profiles (`application-dev.properties`, `application-prod.properties`).

| Environment Variable | Default Value | Description |
|---|---|---|
| `PORT` | `8000` | HTTP listening port |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile (`dev`, `prod`, `test`) |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/prolt_operations?stringtype=unspecified` | PostgreSQL JDBC connection URL |
| `DATABASE_USER` | `postgres` | Database username |
| `DATABASE_PASSWORD` | `postgres` | Database password |
| `REDIS_HOST` | `localhost` | Redis server hostname |
| `REDIS_PORT` | `6379` | Redis server port |
| `REDIS_PASSWORD` | *(empty)* | Redis authentication password |
| `JWT_SECRET` | `prolt-super-secret-jwt-key-minimum-32-bytes!` | Secret key for JJWT signing |
| `JWT_EXPIRES_IN` | `7d` | JWT lifetime (`7d`, `24h`, `60m`) |
| `SUPERADMIN_EMAIL` | `super.admin@probus.io` | Default superadmin identifier |
| `SUPERADMIN_OTP_BYPASS_ENABLED` | `true` (dev) / `false` (prod) | Superadmin test OTP bypass switch |
| `SUPERADMIN_OTP_BYPASS_VALUE` | `749261` | Superadmin fixed verification OTP |
| `ALLOW_MEMORY_FALLBACK` | `true` (dev) / `false` (prod) | Allow in-memory OTP fallback if Redis is down |
| `APP_RUN_SCRAPER` | `false` | Enable automated headless scraping crons |
| `STARROCKS_HOST` | `localhost` | StarRocks OLAP host |
| `STARROCKS_PORT` | `9030` | StarRocks MySQL client port |
| `IEX_CALCULATOR_API_URL` | `https://api.iexindia.com` | External landed price calculator base URL |

---

## 🛠️ Build & Run

### 1. Build from Source
```bash
# Package the executable JAR
./mvnw clean package -DskipTests

# Run full integration test suite (using Testcontainers PostgreSQL)
./mvnw test
```

### 2. Run Locally with Spring Boot
```bash
./mvnw spring-boot:run
```

### 3. Run with Docker
```bash
# Build Docker image
docker build -t prolt-operations:latest .

# Run container
docker run -d \
  -p 8000:8000 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL="jdbc:postgresql://postgres-host:5432/prolt_operations?stringtype=unspecified" \
  -e DATABASE_USER="prolt_user" \
  -e DATABASE_PASSWORD="secret_password" \
  -e REDIS_HOST="redis-host" \
  -e JWT_SECRET="your-production-secure-32-byte-secret-key" \
  --name prolt-operations \
  prolt-operations:latest
```

---

## 📡 API Endpoint Catalogue (117 Endpoints)

All endpoints maintain 1:1 route signature, method, and query param parity with legacy Express routing:

### 1. Authentication & Identity (`/api/auth`, `/api/users`)
- `POST /api/auth/login` - Username/email & password login with JWT token generation
- `POST /api/auth/send-otp` - Dispatch 6-digit numeric OTP to registered user email
- `POST /api/auth/verify-otp` - Verify OTP and issue JWT session token
- `GET /api/auth/me` - Retrieve current authenticated principal with permission sets
- `GET /api/users` - List system users (Super Admin only)
- `POST /api/users` - Create new administrator or client user (Super Admin only)
- `PUT /api/users/{id}` - Update user properties & module permissions
- `DELETE /api/users/{id}` - Revoke and remove user account
- `GET /api/users/audit-logs` - Query user modification audit trail

### 2. Telemetry Ingestion & Datasets (`/api/datasets`, `/api/uploads`)
- `POST /api/uploads` - Multipart spreadsheet ingestion (DAM, GDAM, RTM, REC)
- `GET /api/uploads/health` - Health probe for upload storage subsystem
- `GET /api/datasets` - List datasets with date and market filters
- `GET /api/datasets/{id}` - Detailed 96-time-block interval data
- `GET /api/datasets/{id}/history` - Historical dataset revision logs
- `DELETE /api/datasets/{id}` - Soft-delete dataset (with dependency checks)
- `GET /api/datasets/summary` - Aggregated daily & monthly market statistics
- `GET /api/datasets/timeseries` - 15-minute time-block timeseries extraction (`DATA-01`)
- `GET /api/upload-history` - Paginated file upload audit logs

### 3. Dashboards & Market Operations (`/api/dashboard`, `/api/market-operations`, `/api/up-market`)
- `GET /api/dashboard/overview` - Multi-market overview metrics
- `GET /api/dashboard/daily-trends` - Intraday price & volume movement
- `GET /api/market-operations` - Full outer join DAM/RTM/GDAM block comparison (Blocks 1-96)
- `GET /api/up-market/{market}` - UP regional market analytics (`N1`, `N2`, `N3`, `UP`)

### 4. Tariffs & Financial Calculation Engines (`/api/landing-price-calc`, `/api/savings-calculator`)
- `POST /api/landing-price-calc/calculate` - Open-access landed price computation (`CALC-01`)
- `GET /api/landing-price-calc/consumer-category` - State tariff category discovery
- `GET /api/landing-price-calc/voltage` - Supported supply voltage levels
- `GET /api/savings-calculator` - Query savings assessment entries
- `POST /api/savings-calculator` - Create client savings assessment entry
- `GET /api/savings-calculator/{id}` - Retrieve entry details
- `PUT /api/savings-calculator/{id}` - Update entry with automatic version incrementing
- `DELETE /api/savings-calculator/{id}` - Remove entry (Super Admin only)
- `POST /api/savings-calculator/{id}/calculate` - Recalculate savings model
- `POST /api/savings-calculator/{id}/calculate-market-decision` - Optimal market procurement split
- `GET /api/savings-calculator/{id}/demand-shift-insights` - Peak-to-off-peak shifting analytics
- `GET /api/savings-calculator/{id}/export-excel` - Generate styled POI `.xlsx` savings report
- `GET /api/savings-calculator/{id}/demand-shift-insights/export-excel` - Export demand shifting report
- `GET /api/savings-calculator-new/entries` - Enterprise V2 savings assessment records
- `GET /api/savings-calculator-new/entries/resource-defaults` - Resolve state-specific FPPA & fixed charges
- `POST /api/trader-performance/upload` - Apache PDFBox trade report ingestion
- `POST /api/trader-performance-daily/upload` - Daily trader telemetry ingestion

### 5. Document & Proposal Generation (`/api/proposals`)
- `POST /api/proposals/generate` - Generate generic Word (`.docx`) proposal
- `POST /api/proposals/generate-technical` - Generate technical proposal with embedded JFreeChart pictures
- `POST /api/proposals/generate-commercial` - Generate commercial proposal with dynamic BOQ injection

### 6. Resource Center Master Tables (`/api/resource-center`)
- Supported resources: `region-state`, `discom-list`, `ists-charges`, `iex-fees`, `prolt-margin`, `ctu-charges`, `state-charges`, `state-tariff`, `fppa-charges`.
- `GET /api/resource-center/{resourceType}` - Query master records
- `POST /api/resource-center/{resourceType}` - Create single master record
- `POST /api/resource-center/{resourceType}/bulk` - Bulk upsert master records
- `PUT /api/resource-center/{resourceType}/{id}` - Update master record
- `DELETE /api/resource-center/{resourceType}/{id}` - Delete master record

### 7. Database, Holidays & Forecasts (`/api/database`, `/api/forecast`)
- `GET /api/database/demand/all-india` - National grid demand telemetry
- `GET /api/database/demand/state-wise` - State-wise demand telemetry
- `GET /api/database/generation` - Generation mix by fuel source
- `GET /api/database/weather` - Open-Meteo weather parameters
- `GET /api/database/city-state` - Monitored geographic locations
- `GET /api/database/export/csv` - Streaming CSV export of demand & generation datasets
- `GET /api/database/holidays` - Holiday calendar query
- `POST /api/database/holidays/upload` - Bulk replace holiday calendar via CSV/Excel
- `GET /api/forecast/dam-price` - Model-based price forecasting
- `GET /api/forecast/demand` - Consumer & national demand forecast
- `GET /api/forecast/generation` - Solar and wind generation forecast

### 8. System Logging & Health (`/api/logs`, `/health`, `/actuator`)
- `GET /api/logs` - System API access logs & execution metrics
- `GET /health` - Base application health probe (`{"success": true, "status": "healthy"}`)
- `GET /health/database` - Database connectivity probe (`{"success": true, "database": "connected"}`)
- `GET /actuator/health` - Spring Actuator component health checks
- `GET /actuator/health/liveness` - Kubernetes liveness probe
- `GET /actuator/health/readiness` - Kubernetes readiness probe
- `GET /actuator/prometheus` - Prometheus metric scraping endpoint

---

## 🧪 Testing & Verification

The test suite leverages **Testcontainers** to spin up isolated PostgreSQL 16 instances, verifying schema migrations, transaction boundaries, and repository queries against actual PostgreSQL mechanics:

```bash
# Execute the complete integration test suite
./mvnw test
```

### Key Integration Test Suites:
1. `RepositoryIntegrationTest`: Flyway V1-V5 migration execution and entity CRUD operations.
2. `AuthAndUserIntegrationTest`: JWT auth, Superadmin bypass, rate limiting, and RBAC (`AUTH-01..03`, `USER-01`).
3. `MarketAndScraperIntegrationTest`: 96-block ingestion, duplicate conflicts, and timeseries extraction (`DATA-01`, `SCRAPE-01`).
4. `ScraperAndSchedulerIntegrationTest`: Scraper service parity, table row parsing, and AppScheduler cron routines.
5. `TradeReportParserTest` & `TradeReportGoldenComparisonTest`: Pure Java trade report PDF extraction parity against 28 sample IEX PDFs.
6. `CalculationAndTariffIntegrationTest`: Exact mathematical parity for open-access landed costs (`CALC-01`, `CALC-01a`).
7. `ProposalAndDocumentIntegrationTest`: Apache POI Word generation and JFreeChart PNG rendering.
8. `FullParityIntegrationTest`: End-to-end verification of all subsystems, health checks, and Prometheus metrics.

**Test Run Metrics**:
- **Total Tests**: 102
- **Failures**: 0
- **Errors**: 0
- **Pass Rate**: 100%

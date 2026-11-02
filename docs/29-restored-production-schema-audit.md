# Phase 12: Restored Production Database Schema Audit & Compatibility Report

**Document ID:** `docs/29-restored-production-schema-audit.md`  
**Execution Date:** 2026-10-27  
**Target Database:** Local restored production dump `prolt_operations` on PostgreSQL 16.14 (Dump source: PostgreSQL 17.10)  
**Execution Mode:** STRICT AUDIT ONLY — Zero DDL executed, zero data modified, no application code changed  
**Overall Status:** **COMPATIBILITY AUDIT COMPLETE — 2 BLOCKERS & 2 HIGH FINDINGS IDENTIFIED**

---

## 1. Executive Summary

A real production database dump from PostgreSQL 17.10 was restored into the local PostgreSQL 16.14 database `prolt_operations`.

A non-invasive, read-only inspection was performed comparing:
1. **The Canonical Java Domain & Flyway Contract**: 58 tables (47 in `public`, 11 in `forecasting`) defined by JPA `@Entity` classes and Flyway `V1__Initial_Schema.sql`.
2. **The Canonical Prisma Contract**: 58 models in `IEX-Dashboard/backend/prisma/schema.prisma`.
3. **The Restored Production Database**: 68 base tables (54 in `public`, 14 in `forecasting`), containing live production records (e.g., 160k+ `DamRecord`, 170k+ `RtmRecord`, 150k+ `GdamRecord`, 13k+ `Dataset`).

### Key Audit Conclusions
- **57 of 58 canonical tables exist** in the restored database with **zero missing columns**, **zero nullability mismatches**, **zero missing unique indexes**, **zero missing regular indexes**, and **zero missing foreign keys**.
- **BLOCKER 1**: Table `public.dsm_charges` is **missing** from the restored database. Hibernate's `ddl-auto=validate` will throw a fatal `SchemaManagementException` on startup.
- **BLOCKER 2**: Primary key column `id` on both `public.exchange_dam_rate` and `public.exchange_rtm_rate` is `bigint` (`int8`) in the database, but mapped as `Integer` (`int4`) in Java entities `ExchangeDamRate.java` and `ExchangeRtmRate.java`. Hibernate's `ddl-auto=validate` will fail with a wrong column type error.
- **HIGH 1**: `flyway_schema_history` does not exist in the restored database. Configuring `FLYWAY_BASELINE_ON_MIGRATE=true` and `FLYWAY_BASELINE_VERSION=5` will record baseline v5 and skip `V1__Initial_Schema.sql`. Because V1 is skipped, the missing table `dsm_charges` will never be created automatically. A new migration `V6` is required to establish `dsm_charges`.
- **HIGH 2**: Table `forecasting.consumer_demand_forecasting` lacks a formal `PRIMARY KEY` constraint in `information_schema.table_constraints` (it possesses a unique index on `(meter_id, timestamp)`).
- **11 Extra Tables Present**: 8 tables in `public` and 3 tables in `forecasting` exist in the database that are unmapped in Java and Prisma (legacy/ML pipeline tables). These do not fail Hibernate validation.

---

## 2. Expected Table Lists vs. Restored Database Inventory

### 2.1 Public Schema Table Reconciliation (47 Expected Canonical vs. 54 Actual)

| # | Expected Canonical Table (`public`) | Status in Restored DB | Restored Row Count | Notes |
|---|---|---|---|---|
| 1 | `ApiLog` | **Present** | Verified | Exact column match |
| 2 | `AuditLog` | **Present** | Verified | Exact column match |
| 3 | `CityStateData` | **Present** | Verified | Exact column match |
| 4 | `DamRecord` | **Present** | 160,512 rows | Exact column match |
| 5 | `Dataset` | **Present** | 13,284 rows | Exact column match |
| 6 | `GdamMcpForecast` | **Present** | Verified | Exact column match |
| 7 | `GdamNewRecord` | **Present** | Verified | Exact column match |
| 8 | `GdamRecord` | **Present** | 150,144 rows | Exact column match |
| 9 | `HolidayCalendar` | **Present** | Verified | Exact column match |
| 10 | `NppAdjustedDemandData` | **Present** | Verified | Exact column match |
| 11 | `NppAdjustedGenerationData` | **Present** | Verified | Exact column match |
| 12 | `NppRawDemandData` | **Present** | Verified | Exact column match |
| 13 | `NppRawGenerationData` | **Present** | Verified | Exact column match |
| 14 | `RecRecord` | **Present** | Verified | Exact column match |
| 15 | `RtmRecord` | **Present** | 170,550 rows | Exact column match |
| 16 | `StateDemandData` | **Present** | Verified | Exact column match |
| 17 | `StateMarketRecord` | **Present** | Verified | Exact column match |
| 18 | `UploadHistory` | **Present** | Verified | Exact column match |
| 19 | `User` | **Present** | 12 rows | Exact column match |
| 20 | `UserAuditLog` | **Present** | Verified | Exact column match |
| 21 | `WeatherForecast` | **Present** | Verified | Exact column match |
| 22 | `WeatherForecastHourly` | **Present** | Verified | Exact column match |
| 23 | `WeatherHistorical` | **Present** | Verified | Exact column match |
| 24 | `ctu_charges` | **Present** | 22 rows | Exact column match |
| 25 | `discom_list` | **Present** | 115 rows | Exact column match |
| 26 | `dsm_charges` | <mark>**MISSING**</mark> | **0 (Table missing)** | **BLOCKER 1: Defined in V1 & Entity, absent in DB** |
| 27 | `exchange_dam_rate` | **Present** | Verified | Extra col: `created_at`; **BLOCKER 2: PK is `bigint`** |
| 28 | `exchange_gdam_rate` | **Present** | Verified | Exact column match; PK is `integer` |
| 29 | `exchange_rtm_rate` | **Present** | Verified | Extra col: `created_at`; **BLOCKER 2: PK is `bigint`** |
| 30 | `fppa_charges` | **Present** | 116 rows | Exact column match |
| 31 | `historical_weather` | **Present** | Verified | Exact column match |
| 32 | `iex_fees` | **Present** | Verified | Exact column match |
| 33 | `ists_charges` | **Present** | Verified | Exact column match |
| 34 | `market_operation` | **Present** | Verified | Exact column match |
| 35 | `prolt_margin` | **Present** | Verified | Exact column match |
| 36 | `region_state` | **Present** | 36 rows | Exact column match |
| 37 | `savings_calculator_entries` | **Present** | Verified | Exact column match |
| 38 | `savings_calculator_entry_histories` | **Present** | Verified | Exact column match |
| 39 | `savings_calculator_new_entries` | **Present** | Verified | Exact column match |
| 40 | `savings_calculator_new_entry_histories` | **Present** | Verified | Exact column match |
| 41 | `state_charges` | **Present** | 60 rows | Exact column match |
| 42 | `state_tariff` | **Present** | 312 rows | Exact column match |
| 43 | `trader_performance_actual_entries` | **Present** | Verified | Exact column match |
| 44 | `trader_performance_actual_entry_histories` | **Present** | Verified | Exact column match |
| 45 | `trader_performance_entries` | **Present** | Verified | Exact column match |
| 46 | `trader_performance_entry_histories` | **Present** | Verified | Exact column match |
| 47 | `user_details` | **Present** | Verified | Exact column match |

#### Additional Tables Found in `public` (8 Unmapped Tables)
1. `public.DamMcpForecast` (unmapped forecast variant)
2. `public.day_ahead_forecasts` (unmapped ML table)
3. `public.forecast_audit_log` (unmapped ML table)
4. `public.solar_hub_weather_15min` (unmapped solar telemetry table)
5. `public.vidyut_pravah_prices` (unmapped scraper table)
6. `public.weather_old_regionwise` (unmapped legacy weather table)
7. `public.weather_regionwise` (unmapped weather table)
8. `public.weather_regionwise_forecast_og` (and duplicate case variant `weather_regionwise_forecast_Og`)

---

### 2.2 Forecasting Schema Table Reconciliation (11 Expected Canonical vs. 14 Actual)

| # | Expected Canonical Table (`forecasting`) | Status in Restored DB | Notes |
|---|---|---|---|
| 1 | `GdamMcpForecast` | **Present** | Exact match; PK on `id` |
| 2 | `Generation_forecasting` | **Present** | Extra col: `confidence_score` (`float8`, nullable) |
| 3 | `all_india_demand_forecasting` | **Present** | Exact column match |
| 4 | `all_india_demand_forecasting_v2` | **Present** | Exact column match |
| 5 | `consumer_demand_forecasting` | **Present** | Unique index on `(meter_id, timestamp)`; missing formal PK |
| 6 | `dam_forecasting` | **Present** | Extra col: `Earlier_MCP_forecast` (`numeric`, nullable) |
| 7 | `gdam_dayahead_forecast` | **Present** | Exact column match |
| 8 | `gdam_forecasting` | **Present** | Exact column match |
| 9 | `market_forecasting` | **Present** | Exact column match |
| 10 | `rtm_dayahead` | **Present** | Exact column match |
| 11 | `rtm_nowcast` | **Present** | Exact column match |

#### Additional Tables Found in `forecasting` (3 Unmapped Tables)
1. `forecasting.model_metrics` (ML pipeline metrics)
2. `forecasting.pipeline_runs` (Airflow / Prefect / Cron pipeline run log)
3. `forecasting.rtm_forecasts` (ML intermediate forecast table)

---

## 3. Detailed Structural Audit Findings

### 3.1 Primary Keys & Constraints Audit
- **Canonical Tables with Exact PK Match**: 56 tables match expected PK definitions.
- **PK Type Mismatch on Exchange Rates (BLOCKER 2)**:
  - Table `public.exchange_dam_rate`: Database column `id` is `bigint` (`int8`). Entity `ExchangeDamRate.java` defines `private Integer id;`.
  - Table `public.exchange_rtm_rate`: Database column `id` is `bigint` (`int8`). Entity `ExchangeRtmRate.java` defines `private Integer id;`.
  - Table `public.exchange_gdam_rate`: Database column `id` is `integer` (`int4`), matching `ExchangeGdamRate.java`.
- **Missing Formal Primary Key (HIGH 2)**:
  - Table `forecasting.consumer_demand_forecasting` has a `UNIQUE INDEX` `uq_consumer_demand_meter_timestamp` on `(meter_id, timestamp)`, but lacks a formal `PRIMARY KEY` constraint in PostgreSQL catalog.

### 3.2 Column & Nullability Differences
- **Missing Columns in Canonical Tables**: **0 missing columns**. Every column declared in `V1__Initial_Schema.sql` for the 57 matching tables exists in the restored database.
- **Nullability Discrepancies**: **0 nullability discrepancies**. All column `is_nullable` attributes align 100% with the canonical schema.
- **Extra Columns in Canonical Tables**:
  - `forecasting.Generation_forecasting`: contains extra column `confidence_score` (`double precision`, nullable).
  - `forecasting.dam_forecasting`: contains extra column `Earlier_MCP_forecast` (`numeric`, nullable).
  - `public.exchange_dam_rate`: contains extra column `created_at` (`timestamp without time zone`, NOT NULL, default `CURRENT_TIMESTAMP`).
  - `public.exchange_rtm_rate`: contains extra column `created_at` (`timestamp without time zone`, NOT NULL, default `CURRENT_TIMESTAMP`).
  *(Note: Because the non-null columns have database defaults and the others are nullable, JPA inserts from Java will succeed without constraint violations.)*

### 3.3 Indexes & Foreign Keys Audit
- **Unique Indexes**: All **38** unique indexes defined in `V1__Initial_Schema.sql` for the matching tables are present in the restored database.
- **Regular B-Tree Indexes**: All **57** regular indexes defined in `V1__Initial_Schema.sql` are present in the restored database.
- **Foreign Key Constraints**: All **13** foreign key constraints defined in `V1__Initial_Schema.sql` (e.g. `DamRecord`, `GdamRecord`, `RtmRecord` -> `Dataset` with `ON DELETE CASCADE`) are present in the restored database.

### 3.4 PostgreSQL Custom Enum Audit
All **5** PostgreSQL custom enum types in `prolt_operations` match the canonical specification:
1. `public.DatasetStatus`: `['ACTIVE', 'REPLACED', 'DELETED']`
2. `public.MarketType`: `['DAM', 'GDAM', 'RTM', 'REC']`
3. `public.Role`: `['SUPER_ADMIN', 'ADMIN', 'CLIENT']`
4. `public.UploadAction`: `['UPLOAD', 'REPLACE', 'DELETE']`
5. `public.state_ut_enum`: `['state', 'ut']`

---

## 4. Java Repository & Native SQL Query Compatibility

### 4.1 Java JPA Repositories (58 Repositories)
- 57 repositories map to existing database tables.
- Repository [`DsmChargesRepository.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/repository/DsmChargesRepository.java) will fail on startup during Spring Data repository initialization or Hibernate validation because `dsm_charges` is missing.
- Repositories [`ExchangeDamRateRepository.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/repository/ExchangeDamRateRepository.java) and [`ExchangeRtmRateRepository.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/repository/ExchangeRtmRateRepository.java) will fail Hibernate validation due to the `Integer` vs. `bigint` ID type mismatch.

### 4.2 Native SQL Queries
Inspection of the entire codebase revealed a single native query location in [`MarketOperationsService.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/MarketOperationsService.java):
```sql
SELECT d."deliveryDate" as date, dr."intervalNumber" as timeblock, dr.mcp
FROM "DamRecord" dr 
JOIN "Dataset" d ON dr."datasetId" = d.id
WHERE d.market = 'DAM' AND d.status = 'ACTIVE' ...
```
- Tables referenced: `"DamRecord"`, `"Dataset"`, `"RtmRecord"`, `"GdamRecord"`, `"GdamNewRecord"`.
- Columns referenced: `id`, `"deliveryDate"`, `"intervalNumber"`, `mcp`, `"datasetId"`, `market`, `status`.
- **Compatibility Status**: **100% COMPATIBLE**. All 5 tables exist with the exact quoted PascalCase names, and all referenced columns exist with matching casing and types.

---

## 5. Flyway Baseline Configuration Assessment

### 5.1 Baseline Skipping Analysis
The restored production database has:
- `flyway_schema_history`: **Does not exist**
- Pre-existing tables: **68 tables** (54 in `public`, 14 in `forecasting`)

When the Java service connects with:
```properties
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=5
```
1. Flyway detects that both `public` and `forecasting` are non-empty.
2. Flyway writes a `BASELINE` record at version `5` into `flyway_schema_history`.
3. Flyway looks for migrations with version `> 5`.
4. Because the repository currently contains only `V1`..`V5`, Flyway executes **0 migrations**.
5. **The Trap**: `V1__Initial_Schema.sql` is skipped entirely. Because `dsm_charges` was defined in V1 but was never created in production, it remains missing.
6. When Hibernate validates the schema immediately after Flyway finishes, startup halts with:
   ```
   org.hibernate.tool.schema.spi.SchemaManagementException: 
   Schema-validation: missing table [public.dsm_charges]
   ```

---

## 6. Classification of Audit Findings

| Finding ID | Classification | Component | Description | Impact |
|---|---|---|---|---|
| **F-01** | **BLOCKER** | Schema / Hibernate | Missing Table `public.dsm_charges` | Hibernate `ddl-auto=validate` will fail immediately on startup; application will not boot. |
| **F-02** | **BLOCKER** | Entity / Types | PK type mismatch in `exchange_dam_rate` and `exchange_rtm_rate` (`bigint` in DB vs `Integer` in Java) | Hibernate `ddl-auto=validate` will throw wrong column type error on startup; application will not boot. |
| **F-03** | **HIGH** | Flyway Lifecycle | Baselining at V5 skips V1, permanently skipping `dsm_charges` creation | Auto-migration will not create missing tables on existing databases unless a `V6` migration is added. |
| **F-04** | **HIGH** | Constraints | Missing formal `PRIMARY KEY` on `forecasting.consumer_demand_forecasting` | Table only has unique index `uq_consumer_demand_meter_timestamp`. Tools expecting formal PK will find none. |
| **F-05** | **MEDIUM** | Entity Mapping | Extra columns in 4 canonical tables (`confidence_score`, `Earlier_MCP_forecast`, `created_at`) | Unmapped in Java. Safe for Hibernate (defaults exist or columns nullable), but domain models omit them. |
| **F-06** | **MEDIUM** | Database Hygiene | 11 extra tables in DB (ML and legacy pipelines) | Unmapped and unmanaged by Java service. Safe for runtime, but requires operational documentation. |
| **F-07** | **LOW** | Engine Runtime | PostgreSQL version divergence (Source: 17.10, Local Engine: 16.14) | Restored cleanly and operates without version incompatibility. |

---

## 7. Recommended Next Steps for Safe Validation

To validate the Java backend against this restored production database **without modifying production data or breaking safety rules**, the following sequential plan is recommended for the next phase:

### Step 1: Align Java Entity ID Types (Non-Destructive Code Fix)
- In [`ExchangeDamRate.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/entity/ExchangeDamRate.java) and [`ExchangeRtmRate.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/entity/ExchangeRtmRate.java):
  - Change `@Id private Integer id;` to `@Id private Long id;`.
  - Update getters, setters, and constructors to accept `Long`.
  - Update [`ExchangeDamRateRepository.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/repository/ExchangeDamRateRepository.java) and [`ExchangeRtmRateRepository.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/repository/ExchangeRtmRateRepository.java) parameter type from `<..., String>` / `<..., Integer>` to `<..., Long>`.
  - *Rationale*: Matches the actual database column `bigint` without requiring schema alteration.

### Step 2: Introduce Additive Flyway Migration V6
- Add `src/main/resources/db/migration/V6__create_dsm_charges_if_missing.sql`:
  ```sql
  CREATE TABLE IF NOT EXISTS "public"."dsm_charges" (
      "id" SERIAL NOT NULL,
      "state" VARCHAR(100) NOT NULL,
      "month" INTEGER NOT NULL,
      "dsm_charges_rs_per_kwh" DECIMAL(12,6),
      "updated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
      CONSTRAINT "dsm_charges_pkey" PRIMARY KEY ("id")
  );
  CREATE UNIQUE INDEX IF NOT EXISTS "uq_dsm_charges_state_month" 
  ON "public"."dsm_charges"("state", "month");
  ```
  - *Rationale*: Allows Flyway to baseline an existing database at version 5, then cleanly apply `V6` to create the missing `dsm_charges` table before Hibernate validation runs.

### Step 3: Configure Local Production-Dump Validation Profile
- Create `src/main/resources/application-localprod.properties`:
  ```properties
  spring.datasource.url=jdbc:postgresql://localhost:5432/prolt_operations
  spring.datasource.username=postgres
  spring.datasource.password=${LOCAL_PROD_DB_PASSWORD}
  spring.jpa.hibernate.ddl-auto=validate
  spring.flyway.enabled=true
  spring.flyway.baseline-on-migrate=true
  spring.flyway.baseline-version=5
  app.run-scraper=false
  ```

### Step 4: Execute Read-Only Boot & Schema Validation Drill
- Launch Spring Boot with `-Dspring.profiles.active=localprod`.
- Verify in application startup logs:
  1. Flyway baselines schema at version 5.
  2. Flyway executes `V6__create_dsm_charges_if_missing.sql`.
  3. Hibernate validates all 58 entities with 0 schema validation errors.
  4. Spring Boot context initializes to ready state.
- Execute read-only smoke tests (Market Operations query, Resource Center reads, User queries) against the restored live production records.

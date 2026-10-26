# Resource Center Schema Audit & Migration Plan

**Document:** `docs/20-resource-center-schema-audit.md`  
**Date:** 2026-10-26  
**Scope:** Resource Center Master Data Subsystem (`IEX-Dashboard/backend` Node/TypeScript vs `prolt-operations` Spring Boot/Java 21)  
**Status:** Audit & Planning Phase Complete — Awaiting Product / Data Owner Approval

---

## A. Executive Summary

This audit evaluates the Resource Center data models, API contracts, database schemas, validation layers, frontend UI consumers, and business-logic calculation dependencies across both the Node.js/TypeScript authority (`IEX-Dashboard/backend`) and the Spring Boot Java implementation (`prolt-operations`).

### 1. Key Finding on the "Known Tariff-Schema Mismatch"
Previous migration documentation noted a substantial discrepancy where Node's `state-tariff` allegedly accepted fields such as `stateCode`, `tod`, `todName`, `season`, `todStartHour`, `todEndHour`, `baseEnergyCharges`, `todRate`, and `energyCharges`, whereas Java persisted `consumerCategory`, `energyRate`, `supplyVoltage`, `todChargePercent`, and `baseEnergyRate`.

**Direct source-code and database inspection reveals the root cause of this discrepancy:**
- In the Node repository, **an internal divergence existed between `resource-center.validator.ts` and the rest of the application**.
- Specifically, the Prisma model in [`schema.prisma`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/backend/prisma/schema.prisma#L302-L323), the PostgreSQL database table `state_tariff`, the seed scripts [`seed_tariff.ts`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/backend/src/scripts/seed_tariff.ts#L84-L98) & [`seed_tariff_new.ts`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/backend/src/scripts/seed_tariff_new.ts#L66-L80), the React frontend interfaces in [`resourceCenter.types.ts`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/frontend/src/pages/resource-center/types/resourceCenter.types.ts#L79-L94) & [`resourceConfig.tsx`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/frontend/src/pages/admin/resource-center/config/resourceConfig.tsx#L196-L230), and the calculation engines in [`savings-calculator.service.ts`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/backend/src/modules/savings-calculator/savings-calculator.service.ts#L740-L765) **all use the exact same field set as Java**: `state`, `consumerCategory`, `subCategory`, `supplyVoltageCategory`, `supplyVoltage`, `month`, `todStartTime`, `todEndTime`, `baseEnergyRate`, `baseEnergyUnit`, `todChargePercent`, and `energyRate`.
- The alternative field set (`todStartHour`, `baseEnergyCharges`, etc.) existed **solely inside Node's `resource-center.validator.ts`**. In fact, had an incoming HTTP request passed that validator in Node, Prisma would have rejected the insert because those fields do not exist on the database table or Prisma client delegate.
- Consequently, **Java's entity schema is 100% aligned with Node's actual database schema, frontend UI, and calculation services**.

### 2. Critical Implementation Gaps Identified in Java
While the Java entity model and database tables are structurally sound, the audit identified three concrete functional gaps in Java's current [`ResourceCenterService.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/ResourceCenterService.java):
1. **Incomplete Field Mapping in `persistSingleRecord` and `updateSingleRecord`:**
   - In `state-tariff`, Java's service currently only reads and saves 7 of the 15 fields, leaving NOT NULL database columns (`supply_voltage`, `tod_start_time`, `tod_end_time`, etc.) unset on create.
   - In update operations across 7 of the 9 resource types, Java only updates a minor subset of fields (e.g., updating only `stateName` and `regionName` for `region-state`, ignoring `regionCode`, `regionalGrid`, and `stateCode`).
2. **Missing Input Validation & Sanitization Engine:**
   - Java lacks the string cleaning routines present in Node (e.g. stripping `₹`, `%`, `rs`, commas, whitespace from numbers).
   - Java lacks range and format validation for months (`1-12` or `YYYYMM`) and percentages (`0-100`).
3. **Bulk Upsert & Error Translation Parity:**
   - Node's bulk API checks for `payload.id` and performs an `upsert`, whereas Java's bulk API performs only `create`.
   - Node translates unique constraint collisions (`P2002`) to `HTTP 409 Conflict`, while Java propagates a Spring `DataIntegrityViolationException` resulting in `HTTP 500`.

---

## B. Resource Inventory

The system defines exactly 9 Resource Center master tables in [`resource-center.constants.ts`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/backend/src/modules/resource-center/constants/resource-center.constants.ts) and Java's [`ResourceCenterService.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/ResourceCenterService.java#L151-L157):

| Resource ID | Node Model (`schema.prisma`) | Java Entity (`com.sense.prolt_operations.entity`) | Database Table (`public`) | Primary Key | Natural / Unique Key |
|---|---|---|---|---|---|
| `region-state` | `RegionState` | `RegionState` | `public.region_state` | `id` (SERIAL) | `state_code` (`uq_region_state_code`) |
| `discom-list` | `DiscomList` | `DiscomList` | `public.discom_list` | `id` (SERIAL) | `code`, `legal_name` |
| `ists-charges` | `IstsCharges` | `IstsCharges` | `public.ists_charges` | `id` (SERIAL) | `start_date`, `end_date` (`uq_ists_charges_dates`) |
| `iex-fees` | `IexFees` | `IexFees` | `public.iex_fees` | `id` (SERIAL) | `month` (`uq_iex_fees_month`) |
| `prolt-margin` | `ProltMargin` | `ProltMargin` | `public.prolt_margin` | `id` (SERIAL) | `month`, `customer_id` (`uq_prolt_margin_month_cust`) |
| `ctu-charges` | `CtuCharges` | `CtuCharges` | `public.ctu_charges` | `id` (SERIAL) | `state`, `month` (`uq_ctu_charges_state_month`) |
| `state-charges` | `StateCharges` | `StateCharges` | `public.state_charges` | `id` (SERIAL) | Overlap group key across `from_date`..`to_date` |
| `state-tariff` | `StateTariff` | `StateTariff` | `public.state_tariff` | `id` (SERIAL) | `state`, `consumer_category`, `sub_category`, `supply_voltage_category`, `supply_voltage`, `month`, `tod_start_time`, `tod_end_time` (`uq_state_tariff_composite`) |
| `fppa-charges` | `FppaCharges` | `FppaCharges` | `public.fppa_charges` | `id` (SERIAL) | `state`, `discom`, `month` (`uq_fppa_charges_state_discom_month`) |

*Related Master Resource*: `city-state` (`CityStateData` / `public.city_state_data`), managed under `/api/database/city-state`.

---

## C. Field-Mapping Matrix

### 1. `region-state`

| Node Field | Node DB Type | Java Field | Java DB Type | Mapping Status | Data-Loss Risk | Evidence |
|---|---|---|---|---|---|---|
| `id` | `Int` (SERIAL PK) | `id` | `Integer` (SERIAL PK) | EXACT_MATCH | None | Node: `schema.prisma:199`, Java: `RegionState.java:17` |
| `regionalGrid` | `VarChar(30)` | `regionalGrid` | `String` (VARCHAR) | EXACT_MATCH | Medium (Java update ignores) | Node: `schema.prisma:200`, Java: `RegionState.java:20`, `ResourceCenterService:327` |
| `regionCode` | `VarChar(10)` | `regionCode` | `String` (VARCHAR) | EXACT_MATCH | Medium (Java update ignores) | Node: `schema.prisma:201`, Java: `RegionState.java:23`, `ResourceCenterService:327` |
| `regionName` | `VarChar(50)` | `regionName` | `String` (VARCHAR) | EXACT_MATCH | None | Node: `schema.prisma:202`, Java: `RegionState.java:26`, `ResourceCenterService:227` |
| `stateName` | `VarChar(50)` NOT NULL | `stateName` | `String` (VARCHAR) | EXACT_MATCH | None | Node: `schema.prisma:203`, Java: `RegionState.java:29`, `ResourceCenterService:228` |
| `stateCode` | `VarChar(10)` UNIQUE | `stateCode` | `String` (VARCHAR) | EXACT_MATCH | High (Java create & update ignore) | Node: `schema.prisma:204`, Java: `RegionState.java:32`, `ResourceCenterService:226` |
| `stateOrUt` | `state_ut_enum` (`state`/`ut`) | `stateOrUt` | `StateUtEnum` | EXACT_MATCH | High (Java create & update ignore) | Node: `schema.prisma:205`, Java: `RegionState.java:36`, `ResourceCenterService:226` |
| `updatedAt` | `DateTime` | `updatedAt` | `LocalDateTime` | EXACT_MATCH | Low | Node: `schema.prisma:206`, Java: `RegionState.java:39` |

### 2. `discom-list`

| Node Field | Node DB Type | Java Field | Java DB Type | Mapping Status | Data-Loss Risk | Evidence |
|---|---|---|---|---|---|---|
| `id` | `Int` (SERIAL PK) | `id` | `Integer` (SERIAL PK) | EXACT_MATCH | None | Node: `schema.prisma:217`, Java: `DiscomList.java:29` |
| `code` | `VarChar(20)` UNIQUE | `code` | `String` (VARCHAR) | EXACT_MATCH | Medium (Java update ignores) | Node: `schema.prisma:213`, Java: `DiscomList.java:15`, `ResourceCenterService:331` |
| `legalName` | `VarChar(255)` UNIQUE | `legalName` | `String` (VARCHAR) | EXACT_MATCH | None | Node: `schema.prisma:214`, Java: `DiscomList.java:18`, `ResourceCenterService:235` |
| `stateCode` | `VarChar(100)` | `stateCode` | `String` (VARCHAR) | EXACT_MATCH | None | Node: `schema.prisma:215`, Java: `DiscomList.java:21`, `ResourceCenterService:236` |
| `discomType` | `VarChar(30)` | `discomType` | `String` (VARCHAR) | EXACT_MATCH | Medium (Java update ignores) | Node: `schema.prisma:216`, Java: `DiscomList.java:24`, `ResourceCenterService:331` |
| `updatedAt` | `DateTime` | `updatedAt` | `LocalDateTime` | EXACT_MATCH | None | Node: `schema.prisma:218`, Java: `DiscomList.java:32` |

### 3. `ists-charges`

| Node Field | Node DB Type | Java Field | Java DB Type | Mapping Status | Data-Loss Risk | Evidence |
|---|---|---|---|---|---|---|
| `id` | `Int` (SERIAL PK) | `id` | `Integer` (SERIAL PK) | EXACT_MATCH | None | Node: `schema.prisma:225`, Java: `IstsCharges.java:19` |
| `startDate` | `Date` NOT NULL | `startDate` | `LocalDate` | EXACT_MATCH | None | Node: `schema.prisma:228`, Java: `IstsCharges.java:28`, `ResourceCenterService:245` |
| `endDate` | `Date` NOT NULL | `endDate` | `LocalDate` | EXACT_MATCH | None | Node: `schema.prisma:227`, Java: `IstsCharges.java:25`, `ResourceCenterService:246` |
| `istsLossPercent` | `Decimal(6,4)` NOT NULL | `istsLossPercent` | `BigDecimal` | EXACT_MATCH | None | Node: `schema.prisma:226`, Java: `IstsCharges.java:22`, `ResourceCenterService:244` |
| `updatedAt` | `DateTime` | `updatedAt` | `LocalDateTime` | EXACT_MATCH | None | Node: `schema.prisma:229`, Java: `IstsCharges.java:31` |

### 4. `iex-fees`

| Node Field | Node DB Type | Java Field | Java DB Type | Mapping Status | Data-Loss Risk | Evidence |
|---|---|---|---|---|---|---|
| `id` | `Int` (SERIAL PK) | `id` | `Integer` (SERIAL PK) | EXACT_MATCH | None | Node: `schema.prisma:238`, Java: `IexFees.java:18` |
| `month` | `Int` UNIQUE | `month` | `Integer` | EXACT_MATCH | None | Node: `schema.prisma:239`, Java: `IexFees.java:21`, `ResourceCenterService:252` |
| `exchangeFees` | `Decimal(12,4)` | `exchangeFees` | `BigDecimal` | EXACT_MATCH | None | Node: `schema.prisma:240`, Java: `IexFees.java:24`, `ResourceCenterService:253` |
| `exchangeFeesGst` | `Decimal(12,4)` | `exchangeFeesGst` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:241`, Java: `IexFees.java:27`, `ResourceCenterService:342` |
| `nldcApplicationFees` | `Decimal(12,4)` | `nldcApplicationFees` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:242`, Java: `IexFees.java:30`, `ResourceCenterService:342` |
| `nldcSchedulingFees` | `Decimal(12,4)` | `nldcSchedulingFees` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:243`, Java: `IexFees.java:33`, `ResourceCenterService:342` |
| `sldcSchedulingFees` | `Decimal(12,4)` | `sldcSchedulingFees` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:244`, Java: `IexFees.java:36`, `ResourceCenterService:342` |
| `otherFixCharges` | `Decimal(12,4)` | `otherFixCharges` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:245`, Java: `IexFees.java:39`, `ResourceCenterService:342` |
| `updatedAt` | `DateTime` | `updatedAt` | `LocalDateTime` | EXACT_MATCH | None | Node: `schema.prisma:246`, Java: `IexFees.java:42` |

### 5. `prolt-margin`

| Node Field | Node DB Type | Java Field | Java DB Type | Mapping Status | Data-Loss Risk | Evidence |
|---|---|---|---|---|---|---|
| `id` | `Int` (SERIAL PK) | `id` | `Integer` (SERIAL PK) | EXACT_MATCH | None | Node: `schema.prisma:259`, Java: `ProltMargin.java:36` |
| `month` | `Int` NOT NULL | `month` | `Integer` | EXACT_MATCH | None | Node: `schema.prisma:253`, Java: `ProltMargin.java:16`, `ResourceCenterService:264` |
| `customerId` | `VarChar(50)` NOT NULL | `customerId` | `String` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:254`, Java: `ProltMargin.java:19`, `ResourceCenterService:347` |
| `tradingMargin` | `Decimal(12,4)` | `tradingMargin` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:255`, Java: `ProltMargin.java:22`, `ResourceCenterService:347` |
| `tradingMarginGst` | `Decimal(12,4)` | `tradingMarginGst` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:256`, Java: `ProltMargin.java:25`, `ResourceCenterService:347` |
| `proltMargin` | `Decimal(12,4)` | `proltMargin` | `BigDecimal` | EXACT_MATCH | None | Node: `schema.prisma:257`, Java: `ProltMargin.java:28`, `ResourceCenterService:268` |
| `proltMarginGst` | `Decimal(12,4)` | `proltMarginGst` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:258`, Java: `ProltMargin.java:31`, `ResourceCenterService:347` |
| `updatedAt` | `DateTime` | `updatedAt` | `LocalDateTime` | EXACT_MATCH | None | Node: `schema.prisma:260`, Java: `ProltMargin.java:39` |

### 6. `ctu-charges`

| Node Field | Node DB Type | Java Field | Java DB Type | Mapping Status | Data-Loss Risk | Evidence |
|---|---|---|---|---|---|---|
| `id` | `Int` (SERIAL PK) | `id` | `Integer` (SERIAL PK) | EXACT_MATCH | None | Node: `schema.prisma:268`, Java: `CtuCharges.java:18` |
| `state` | `VarChar(50)` NOT NULL | `state` | `String` | EXACT_MATCH | None | Node: `schema.prisma:269`, Java: `CtuCharges.java:21`, `ResourceCenterService:275` |
| `month` | `Int` NOT NULL | `month` | `Integer` | EXACT_MATCH | Medium (Java update ignores) | Node: `schema.prisma:270`, Java: `CtuCharges.java:24`, `ResourceCenterService:352` |
| `ctu_charges_rs_per_kwh` | `Decimal(12,6)` | `ctuChargesRsPerKwh` | `BigDecimal` | RENAME_REQUIRED (JSON property) | Medium (Frontend expects snake_case) | Node: `schema.prisma:271`, Java: `CtuCharges.java:27`, Frontend: `resourceConfig.tsx:148` |
| `updatedAt` | `DateTime` | `updatedAt` | `LocalDateTime` | EXACT_MATCH | None | Node: `schema.prisma:272`, Java: `CtuCharges.java:30` |

### 7. `state-charges`

| Node Field | Node DB Type | Java Field | Java DB Type | Mapping Status | Data-Loss Risk | Evidence |
|---|---|---|---|---|---|---|
| `id` | `Int` (SERIAL PK) | `id` | `Integer` (SERIAL PK) | EXACT_MATCH | None | Node: `schema.prisma:280`, Java: `StateCharges.java:19` |
| `state` | `VarChar(100)` NOT NULL | `state` | `String` | EXACT_MATCH | None | Node: `schema.prisma:281`, Java: `StateCharges.java:22`, `ResourceCenterService:283` |
| `category` | `VarChar(100)` | `category` | `String` | EXACT_MATCH | None | Node: `schema.prisma:282`, Java: `StateCharges.java:25`, `ResourceCenterService:284` |
| `subCategory` | `VarChar(255)` | `subCategory` | `String` | EXACT_MATCH | None | Node: `schema.prisma:283`, Java: `StateCharges.java:28`, `ResourceCenterService:285` |
| `supplyVoltageCategory` | `VarChar(100)` | `supplyVoltageCategory` | `String` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:284`, Java: `StateCharges.java:31`, `ResourceCenterService:357` |
| `voltageLevel` | `VarChar(100)` | `voltageLevel` | `String` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:285`, Java: `StateCharges.java:34`, `ResourceCenterService:357` |
| `fromDate` | `Date` NOT NULL | `fromDate` | `LocalDate` | EXACT_MATCH | None | Node: `schema.prisma:286`, Java: `StateCharges.java:37`, `ResourceCenterService:288` |
| `toDate` | `Date` NOT NULL | `toDate` | `LocalDate` | EXACT_MATCH | None | Node: `schema.prisma:287`, Java: `StateCharges.java:40`, `ResourceCenterService:289` |
| `demandFixedChargeKvaPerMonthRs` | `Decimal(12,6)` | `demandFixedChargeKvaPerMonthRs` | `BigDecimal` | EXACT_MATCH | High (Java create & update ignore) | Node: `schema.prisma:288`, Java: `StateCharges.java:43`, `ResourceCenterService:281` |
| `crossSubsidy` | `Decimal(12,6)` | `crossSubsidy` | `BigDecimal` | EXACT_MATCH | None | Node: `schema.prisma:289`, Java: `StateCharges.java:46`, `ResourceCenterService:290` |
| `distributionWheelingCharges` | `Decimal(12,6)` | `distributionWheelingCharges` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:290`, Java: `StateCharges.java:49`, `ResourceCenterService:357` |
| `stuCharges` | `Decimal(12,6)` | `stuCharges` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:291`, Java: `StateCharges.java:52`, `ResourceCenterService:357` |
| `stuLossPercent` | `Decimal(12,6)` | `stuLossPercent` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:292`, Java: `StateCharges.java:55`, `ResourceCenterService:357` |
| `wheelingLossPercent` | `Decimal(12,6)` | `wheelingLossPercent` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:293`, Java: `StateCharges.java:58`, `ResourceCenterService:357` |
| `additionalCharge` | `Decimal(12,6)` | `additionalCharge` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:294`, Java: `StateCharges.java:61`, `ResourceCenterService:357` |
| `discom` | `VarChar(100)` | `discom` | `String` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:296`, Java: `StateCharges.java:67`, `ResourceCenterService:357` |
| `updatedAt` | `DateTime` | `updatedAt` | `LocalDateTime` | EXACT_MATCH | None | Node: `schema.prisma:295`, Java: `StateCharges.java:64` |

### 8. `state-tariff`

#### Comparison Against Node Database & Frontend UI:

| Canonical Field | Node DB Type | Java Field | Java DB Type | Mapping Status | Data-Loss Risk | Evidence |
|---|---|---|---|---|---|---|
| `id` | `Int` (SERIAL PK) | `id` | `Integer` (SERIAL PK) | EXACT_MATCH | None | Node: `schema.prisma:306`, Java: `StateTariff.java:26` |
| `state` | `VarChar(100)` NOT NULL | `state` | `String` | EXACT_MATCH | None | Node: `schema.prisma:304`, Java: `StateTariff.java:18`, `ResourceCenterService:302` |
| `consumerCategory` | `VarChar(50)` NOT NULL | `consumerCategory` | `String` | EXACT_MATCH | None | Node: `schema.prisma:310`, Java: `StateTariff.java:39`, `ResourceCenterService:304` |
| `subCategory` | `VarChar(255)` NOT NULL | `subCategory` | `String` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:305`, Java: `StateTariff.java:22`, `ResourceCenterService:366` |
| `supplyVoltageCategory` | `VarChar(100)` NOT NULL | `supplyVoltageCategory` | `String` | EXACT_MATCH | CRITICAL (Java create & update ignore) | Node: `schema.prisma:313`, Java: `StateTariff.java:48`, `ResourceCenterService:300` |
| `supplyVoltage` | `VarChar(50)` NOT NULL | `supplyVoltage` | `String` | EXACT_MATCH | CRITICAL (Java create & update ignore) | Node: `schema.prisma:312`, Java: `StateTariff.java:45`, `ResourceCenterService:300` |
| `month` | `Int` NOT NULL | `month` | `Integer` | EXACT_MATCH | Medium (Java update ignores) | Node: `schema.prisma:303`, Java: `StateTariff.java:16`, `ResourceCenterService:308` |
| `todStartTime` | `VarChar(20)` NOT NULL | `todStartTime` | `String` | EXACT_MATCH | CRITICAL (Java create & update ignore) | Node: `schema.prisma:316`, Java: `StateTariff.java:57`, `ResourceCenterService:300` |
| `todEndTime` | `VarChar(20)` NOT NULL | `todEndTime` | `String` | EXACT_MATCH | CRITICAL (Java create & update ignore) | Node: `schema.prisma:315`, Java: `StateTariff.java:54`, `ResourceCenterService:300` |
| `baseEnergyRate` | `Decimal(12,6)` NOT NULL | `baseEnergyRate` | `BigDecimal` | EXACT_MATCH | High (Java update ignores) | Node: `schema.prisma:308`, Java: `StateTariff.java:33`, `ResourceCenterService:307` |
| `baseEnergyUnit` | `VarChar(10)` NOT NULL | `baseEnergyUnit` | `String` | EXACT_MATCH | CRITICAL (Java create & update ignore) | Node: `schema.prisma:309`, Java: `StateTariff.java:36`, `ResourceCenterService:300` |
| `todChargePercent` | `Int` NOT NULL | `todChargePercent` | `Integer` | EXACT_MATCH | CRITICAL (Java create & update ignore) | Node: `schema.prisma:314`, Java: `StateTariff.java:51`, `ResourceCenterService:300` |
| `energyRate` | `Decimal(12,6)` NOT NULL | `energyRate` | `BigDecimal` | EXACT_MATCH | None | Node: `schema.prisma:311`, Java: `StateTariff.java:42`, `ResourceCenterService:306` |
| `discom` | `VarChar(100)` | `discom` | `String` | EXACT_MATCH | None | Node: `schema.prisma:317`, Java: `StateTariff.java:60`, `ResourceCenterService:303` |
| `consumptionMonth` | `Int` | `consumptionMonth` | `Integer` | EXACT_MATCH | Low (Optional historical tag) | Node: `schema.prisma:318`, Java: `StateTariff.java:63` |
| `updatedAt` | `DateTime` | `updatedAt` | `LocalDateTime` | EXACT_MATCH | None | Node: `schema.prisma:307`, Java: `StateTariff.java:30` |

#### Comparison of Node Validator Phantom Fields:

| Phantom Node Validator Field | Node Validator Type | Equivalent DB Column | Equivalent Java Field | Status / Semantic Difference | Lossless Mapping? |
|---|---|---|---|---|---|
| `stateCode` | String | `state` (full name) | `state` | SEMANTIC_MISMATCH (Validator expects stateCode e.g. "UP", but DB and Frontend store full name "Uttar Pradesh") | Lossless via `region_state` lookup table |
| `tod` | String | `tod_start_time` & `tod_end_time` | `todStartTime`, `todEndTime` | SEMANTIC_MISMATCH (Validator had single label e.g. "TOD1"; DB stores explicit start/end times) | No, time boundaries cannot be inferred from arbitrary labels |
| `todName` | String | Computed in services (`${todStartTime}-${todEndTime}`) | Computed in services | MISSING_IN_DB (Derived calculation property) | Derived property |
| `season` | String | Inferred in services from `month` | Inferred in services from `month` | MISSING_IN_DB (Derived calculation property) | Derived property |
| `todStartHour` | Number | `tod_start_time` | `todStartTime` | TYPE_MISMATCH (Integer hour vs HH:mm string) | Lossless (`hour -> String.format("%02d:00", hour)`) |
| `todEndHour` | Number | `tod_end_time` | `todEndTime` | TYPE_MISMATCH (Integer hour vs HH:mm string) | Lossless (`hour -> String.format("%02d:00", hour)`) |
| `baseEnergyCharges` | Number | `base_energy_rate` | `baseEnergyRate` | RENAME_REQUIRED (Charges vs Rate naming) | Exact numerical equivalent |
| `todRate` | Number | `tod_charge_percent` & `energy_rate` | `todChargePercent`, `energyRate` | SEMANTIC_MISMATCH (`todRate` was either percent or adjusted rate) | Depends on interpretation |
| `energyCharges` | Number | `energy_rate` | `energyRate` | RENAME_REQUIRED (Charges vs Rate naming) | Exact numerical equivalent |

### 9. `fppa-charges`

| Node Field | Node DB Type | Java Field | Java DB Type | Mapping Status | Data-Loss Risk | Evidence |
|---|---|---|---|---|---|---|
| `id` | `Int` (SERIAL PK) | `id` | `Integer` (SERIAL PK) | EXACT_MATCH | None | Node: `schema.prisma:732`, Java: `FppaCharges.java:18` |
| `state` | `VarChar(100)` NOT NULL | `state` | `String` | EXACT_MATCH | None | Node: `schema.prisma:733`, Java: `FppaCharges.java:21`, `ResourceCenterService:314` |
| `discom` | `VarChar(200)` | `discom` | `String` | EXACT_MATCH | None | Node: `schema.prisma:737`, Java: `FppaCharges.java:33`, `ResourceCenterService:315` |
| `month` | `Int` NOT NULL | `month` | `Integer` | EXACT_MATCH | Medium (Java update ignores) | Node: `schema.prisma:734`, Java: `FppaCharges.java:24`, `ResourceCenterService:316` |
| `billingMonth` | `Int` | `billingMonth` | `Integer` | EXACT_MATCH | Medium (Java update ignores) | Node: `schema.prisma:738`, Java: `FppaCharges.java:36`, `ResourceCenterService:317` |
| `fppaChargePercent` | `Decimal(12,6)` | `fppaChargePercent` | `BigDecimal` | EXACT_MATCH | None | Node: `schema.prisma:735`, Java: `FppaCharges.java:27`, `ResourceCenterService:318` |
| `updatedAt` | `DateTime` | `updatedAt` | `LocalDateTime` | EXACT_MATCH | None | Node: `schema.prisma:736`, Java: `FppaCharges.java:30` |

---

## D. Validation and API Parity

### 1. HTTP Endpoint Parity
Both services mount identical endpoints under `/api/resource-center`:
- `GET /api/resource-center/:resourceType` (HTTP 200)
- `POST /api/resource-center/:resourceType` (HTTP 201)
- `POST /api/resource-center/:resourceType/bulk` (HTTP 201)
- `PUT /api/resource-center/:resourceType/:id` (HTTP 200)
- `DELETE /api/resource-center/:resourceType/:id` (HTTP 200)

Both services expose these routes publicly without mandatory JWT authentication (matching Node `app.ts:86` and Java `SecurityConfig`).

### 2. Request Validation Comparison

| Validation Dimension | Node Behavior (`validatePayload`) | Java Current Behavior (`ResourceCenterService`) | Parity Status |
|---|---|---|---|
| **Resource Type Check** | Rejects unknown resource types with `400 "Unsupported resource type"` | Rejects with `400 "Unsupported resource type"` | EXACT_PARITY |
| **Empty Payload** | Returns `null`, filtered out in bulk, rejects single create with `400 "Payload cannot be empty"` | Rejects single create with `400 "Payload cannot be empty"`; filters blank objects in bulk | EXACT_PARITY |
| **Bulk Array Boundary** | Rejects non-array with `400 "Payload must be an array of records"` | Rejects non-array with `400 "Payload must be an array of records"` | EXACT_PARITY |
| **Empty Bulk Array** | Accepts `[]` and returns `201 "Bulk uploaded 0 records successfully"` with empty data array | Accepts `[]` and returns `201 "Bulk uploaded 0 records successfully"` with empty data array | EXACT_PARITY |
| **Required Fields** | Throws `400 "Validation error: <field> is required"` based on `REQUIRED_FIELDS` map | Does not validate required fields at API boundary; fails on DB NOT NULL violation | GAP (Java missing pre-validation) |
| **Month Formatting** | Accepts numbers, month names ("June 2026"), `YYYY-MM`, `MM/YYYY`, normalizes to integer `1-12` or `YYYYMM` | Converts to integer via simple parsing; no month-name or regex pattern parser | GAP (Java lacks flexible month normalization) |
| **Currency & Units Cleaning** | Strips `₹`, `%`, `rs`, commas, whitespace from numeric strings | Parses strings with `new BigDecimal(str)`, which throws `NumberFormatException` if symbols exist | GAP (Java fails on formatted inputs) |
| **Numeric Boundaries** | Rejects negative numbers (`< 0`) except `fppaChargePercent`; enforces `<= 100` for loss percentages | No range checks | GAP (Java allows invalid negative or >100% loss) |
| **Enum Validation** | Enforces `stateOrUt` is strictly `'state'` or `'ut'` | Maps to `StateUtEnum` via `valueOf()`; throws unmapped IllegalArgumentException on mismatch | GAP (Error message format difference) |
| **Unknown Fields** | Strips all keys not in `VALID_FIELDS` | Ignores unmapped keys during entity construction | EQUIVALENT |
| **Discom Parentheses** | Strips `\s*\(.*\)\s*` (e.g. `"NPCL (Uttar Pradesh)" -> "NPCL"`) | Strips `\s*\(.*\)\s*` identically in `sanitizePayload` | EXACT_PARITY |
| **Date Overlap Detection** | Rejects overlapping date ranges in `state-charges` and `ists-charges` with `400` | Rejects overlapping date ranges identically in `checkDateOverlaps` | EXACT_PARITY |

---

## E. Database Constraints and Migration Impact

### 1. Database Index Catalog
All unique constraints are identical in PostgreSQL `schema.prisma` and Flyway [`V1__Initial_Schema.sql`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/resources/db/migration/V1__Initial_Schema.sql):
- `uq_region_state_code`: `region_state(state_code)`
- `uq_discom_list_code`: `discom_list(code)`
- `uq_discom_list_name`: `discom_list(legal_name)`
- `uq_ists_charges_dates`: `ists_charges(start_date, end_date)`
- `uq_iex_fees_month`: `iex_fees(month)`
- `uq_prolt_margin_month_cust`: `prolt_margin(month, customer_id)`
- `uq_ctu_charges_state_month`: `ctu_charges(state, month)`
- `uq_state_tariff_composite`: `state_tariff(state, consumer_category, sub_category, supply_voltage_category, supply_voltage, month, tod_start_time, tod_end_time)`
- `uq_fppa_charges_state_discom_month`: `fppa_charges(state, discom, month)`

### 2. Migration Impact Analysis
- **If Option A (Canonical Alignment) is chosen**:
  - **Zero database migrations required**.
  - No column additions, renames, type changes, or deletions.
  - The existing database tables, columns, indexes, and constraints remain 100% intact.
  - The existing database data remains completely untouched and safe.
- **If Option C (New Schema) is chosen**:
  - Requires writing a new Flyway migration `V6__alter_state_tariff.sql`.
  - Requires adding new columns, writing custom data-conversion scripts to translate existing `todStartTime`/`todEndTime` into hours, updating 6 downstream calculation and export services, and modifying frontend React table and form configs.

---

## F. Business-Logic Dependencies

### 1. Tariff Engine (`SavingsCalculatorService` & `SavingsCalculatorNewService`)
- Directly queries `state_tariff` using:
  - `state`: matched against client state formats.
  - `consumerCategory`: matched against client category (e.g., `'HV-2'`, `'Large Industrial'`).
  - `supplyVoltageCategory`: matched against client voltage (e.g., `'11kV'`, `'33kV'`).
  - `subCategory`: matched with substring matching.
  - `month`: matched with calendar month or `consumptionMonth`.
- Reads `todStartTime` and `todEndTime` to parse time boundaries (`parseHour(t.todStartTime)`) and matches 15-minute time-blocks across the day.
- Reads `energyRate` and `baseEnergyRate` to compute Discom landing cost and potential Open Access savings.
- **None of the calculation engines reference `todStartHour`, `baseEnergyCharges`, `todRate`, or `season` from the validator.**

### 2. Trader Performance Engine (`TraderPerformanceService`)
- Queries `state_tariff` with identical parameters to construct the 96-slot TOU/TOD banking ledger.
- Relies on `todStartTime` and `todEndTime` to partition banked energy vs Discom spillover.

### 3. Frontend Resource Center Management UI
- [`resourceConfig.tsx`](file:///home/ankit-jha/probus-workspace/IEX-Dashboard/frontend/src/pages/admin/resource-center/config/resourceConfig.tsx#L196-L230) renders tables and input forms strictly using the canonical fields:
  - Table columns: `State`, `Consumer Category`, `Sub Category`, `Supply Voltage Category`, `Supply Voltage`, `Month`, `TOD Start Time`, `TOD End Time`, `Base Energy Rate`, `Unit`, `TOD Charge %`, `Energy Rate`.
  - Form dialogs: Input fields for all 12 canonical fields.

---

## G. Master-Data Loading Audit

### 1. Legacy Node Seed Scripts
The Node repository contains four historical seed scripts:
- `prisma/seed.ts`:
  - Contains destructive `await prisma.stateCharges.deleteMany();`.
  - Attempts to read `backend_tables_updated - state_charges.csv` (absent from repo).
- `src/scripts/seed_tariff.ts`:
  - Contains destructive `await prisma.stateTariff.deleteMany({});`.
  - Attempts to read `/Users/yashgupta/IEX-Dashboard/aayaya.csv` (absent from repo).
- `src/scripts/seed_tariff_new.ts`:
  - Contains destructive `await prisma.stateTariff.deleteMany({});`.
  - Attempts to read `/Users/yashgupta/IEX-Dashboard/state-tariff-correct.csv` (absent from repo).
- `src/scripts/seedCityStateData.ts`:
  - Contains upsert logic reading `Untitled spreadsheet (3).xlsx` (absent from repo).

### 2. Safety Guidelines for Master Data
> [!CAUTION]
> Under no circumstances should legacy seed scripts be executed or ported with destructive deletion (`deleteMany`) behavior.
> The source CSV/XLSX files are absent from the repository. No master data should be wiped or guessed.

### 3. Operational Ingestion Contract
- Normal master-data updates should proceed through the official Resource Center JSON bulk API (`POST /api/resource-center/:resourceType/bulk`).
- A client-side or administrative tool should convert Excel/CSV files to JSON payloads before calling the bulk API.
- All operations should execute inside database transactions with duplicate conflict translation.

---

## H. Compatibility Risks

| Risk ID | Severity | Category | Description | Mitigation |
|---|---|---|---|---|
| **RISK-01** | **CRITICAL** | Data Persistence | Java `persistSingleRecord` for `state-tariff` omits NOT NULL fields (`supplyVoltage`, `todStartTime`, etc.), causing SQL insert failures if called. | Update Java's mapping to populate all 15 canonical fields from request payload. |
| **RISK-02** | **CRITICAL** | Data Loss | Java `updateSingleRecord` ignores most fields during PUT updates across 7 resource types, silently dropping user updates. | Update Java's update handlers to inspect and apply updates for all entity fields. |
| **RISK-03** | **HIGH** | API Parity | Node's bulk API performs `upsert` when `payload.id` is present, while Java always creates new records. | Update Java's `createBulkResourceRecords` to perform upsert by ID when ID is provided. |
| **RISK-04** | **HIGH** | Error Handling | Database unique constraint violations in Java throw `DataIntegrityViolationException` resulting in HTTP 500 rather than Node's HTTP 409 Conflict. | Catch `DataIntegrityViolationException` in `RestControllerAdvisor` or service and return HTTP 409 with `"Record already exists (Duplicate Conflict)"`. |
| **RISK-05** | **MEDIUM** | JSON Serialization | Node Prisma serializes `ctu-charges` with snake_case `ctu_charges_rs_per_kwh`, which the frontend expects, but Java serializes camelCase `ctuChargesRsPerKwh`. | Add `@JsonProperty("ctu_charges_rs_per_kwh")` or Jackson alias to `CtuCharges.java`. |
| **RISK-06** | **MEDIUM** | Input Sanitization | Payloads with currency symbols (`₹`), commas, or percentages fail in Java with `NumberFormatException`. | Add string cleaning in Java service to strip formatting symbols before numeric parsing. |
| **RISK-07** | **LOW** | Audit Logging | Node logs `tableName` as camelCase `modelName` (`"stateTariff"`), while Java logs kebab-case `resourceType` (`"state-tariff"`). | Align Java's audit log `tableName` or keep kebab-case if acceptable to monitoring. |

---

## I. Proposed Target-Schema Options

### Option A: Canonical Database & Domain Model Alignment (RECOMMENDED)
- **Concept:** Formally approve the existing PostgreSQL / Prisma / Flyway / Frontend UI schema as the authoritative contract.
- **Changes in Java:**
  - Update `persistSingleRecord` and `updateSingleRecord` in `ResourceCenterService.java` to map all canonical fields.
  - Add input sanitization (strip currency/percent symbols) and validation (month 1-12/YYYYMM, percentages 0-100).
  - Add `@JsonProperty("ctu_charges_rs_per_kwh")` to `CtuCharges`.
  - Add atomic upsert support to `createBulkResourceRecords`.
  - Map unique constraint violations to HTTP 409.
- **Database Impact:** **ZERO migrations required**. Existing data and schema 100% preserved.
- **Downstream Impact:** Zero changes required to calculation services, schedulers, or frontend.
- **Effort:** Low (1 day).
- **Maintainability:** High — eliminates phantom validator confusion permanently.

### Option B: Dual-Contract Compatibility Layer
- **Concept:** Retain the current PostgreSQL schema, but add bi-directional property aliasing in Java so requests can supply either canonical fields (`consumerCategory`, `todStartTime`, `energyRate`) OR phantom validator fields (`category`, `todStartHour`, `energyCharges`).
- **Changes in Java:** Add alias resolution in `ResourceCenterService` (`todStartTime` fallback to `String.format("%02d:00", todStartHour)`, etc.).
- **Database Impact:** Zero migrations required.
- **Downstream Impact:** Minimal; provides flexibility if unknown third-party API clients used the validator names.
- **Effort:** Low-Medium.

### Option C: Canonical V2 Schema Migration
- **Concept:** Modify the database schema to introduce new columns (`tod_start_hour`, `tod_end_hour`, `tod_name`, etc.) and deprecate `tod_start_time`/`tod_end_time`.
- **Database Impact:** High — requires Flyway `V6` migration, table alter, and backfilling data.
- **Downstream Impact:** High — requires rewriting query and calculation logic in `SavingsCalculatorService`, `SavingsCalculatorNewService`, `TraderPerformanceService`, POI Excel exporters, and React frontend components.
- **Data-Loss Risk:** High (risk of precision loss going from HH:mm to integer hour).
- **Recommendation:** **Not recommended**.

---

## J. Approval Checklist

Before implementing schema or service adjustments, the product owner and data owner must approve:

- [ ] **Decision 1: Target Contract for `state-tariff`**
  - **Proposed:** Adopt **Option A** (Canonical Schema: `consumerCategory`, `subCategory`, `supplyVoltageCategory`, `supplyVoltage`, `month`, `todStartTime`, `todEndTime`, `baseEnergyRate`, `baseEnergyUnit`, `todChargePercent`, `energyRate`).
- [ ] **Decision 2: Property Naming for `ctu-charges`**
  - **Proposed:** Align Java JSON response to `ctu_charges_rs_per_kwh` to maintain 100% parity with Node and the React frontend.
- [ ] **Decision 3: Bulk API Upsert Behavior**
  - **Proposed:** When a record in the bulk array contains an `id`, perform an atomic upsert; when `id` is omitted, perform an insert.
- [ ] **Decision 4: Initial Master-Data Source (D-01)**
  - **Proposed:** Defer master-data loading until an approved database export or validated spreadsheets are officially provided.
- [ ] **Decision 5: Operator Workflow for Master Data (D-02)**
  - **Proposed:** Keep the JSON bulk API `/api/resource-center/:resourceType/bulk` as the backend contract, with client-side/CLI spreadsheet conversion.

---

## K. Implementation Plan (Post-Approval)

Once approved, implementation will proceed through the following ordered, testable steps:

1. **Step 1: Entity & Serialization Parity**
   - Add `@JsonProperty("ctu_charges_rs_per_kwh")` on `CtuCharges.ctuChargesRsPerKwh`.
   - Ensure all entity getters/setters and serializations match the expected frontend contracts.
2. **Step 2: Complete Field Mapping in `ResourceCenterService`**
   - Update `persistSingleRecord` to populate 100% of persisted entity fields across all 9 resource types.
   - Update `updateSingleRecord` to handle full updates for all entity fields.
3. **Step 3: Validation & Sanitization Engine**
   - Implement `sanitizeNumeric` to strip `₹`, `%`, `rs`, commas, and whitespace.
   - Implement `validateMonth` (1-12 or YYYYMM) and percentage boundary checks.
   - Implement required-field checks prior to repository persistence.
4. **Step 4: Bulk Upsert & Error Translation**
   - Update `createBulkResourceRecords` to execute `upsert` when `id` is present.
   - Map `DataIntegrityViolationException` to HTTP 409 Conflict with `"Record already exists (Duplicate Conflict)"`.
5. **Step 5: Automated Testing & Verification**
   - Add integration tests covering full CRUD, bulk upsert, input sanitization, and 409 duplicate handling across all 9 resource types.
   - Run full `./mvnw clean verify -o` suite to ensure zero regressions.

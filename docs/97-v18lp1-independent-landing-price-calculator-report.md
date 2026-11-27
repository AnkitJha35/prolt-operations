# Milestone V18LP.1 — Independent Landing Price Calculator Execution Report

**Repository**: `prolt-operations`  
**Execution Date**: November 27, 2026  
**Node.js Reference**: `IEX-Dashboard/backend`  
**Milestone**: `V18LP.1`  
**Final Verdict**: `V18LP.1 COMPLETE`

---

## 1. Executive Summary & Objective

In accordance with Milestone V18LP.1, an independent, in-house **Landing Price Calculator** capability has been designed, implemented, and verified in the `prolt-operations` Java backend.

Prior to this milestone:
- The backend only provided an external HTTP web-proxy (`LandingPriceCalcController.java` / `LandingPriceService.java`) forwarding queries to the external IEX website (`https://www.iexindia.com/api/v1/calculator`). If the external website was unreachable, calculation failed.
- True landed price calculations across DAM, RTM, GDAM, and DISCOM were locked inside private methods of `SavingsCalculatorService` (`calculateSlotLandingCosts`) and `Hv2SlotProcessor`, requiring a full 12-month `SavingsCalculatorEntry` and complete bill consumption models.
- Standalone landing price computation with typed DTO contracts and dedicated REST APIs was completely absent.

Milestone V18LP.1 achieves:
1. **Dedicated Service Layer**: Implemented `LandingPriceCalculatorService` independent from Saving Calculator orchestration.
2. **Typed Data Transfer Contracts**: Created `LandingPriceCalculationRequest` and `LandingPriceCalculationResponse` supporting multi-market procurement (DAM, RTM, GDAM), statutory adders, compound transmission losses, and comparative DISCOM tariffs.
3. **Independent REST API**: Exposed `/api/landing-price-calculator/calculate` supporting both JSON `POST` requests and query-parameter `GET` requests (matching Node.js conventions).
4. **Parity with Node.js Authority**: Reused shared calculation components (`LandedCostCalculator`, `ElectricalLossCalculator`, `RegulatoryChargeResolver`, `NpclTariffSchedule`) to maintain 1:1 mathematical fidelity with `IEX-Dashboard/backend`.
5. **Comprehensive Automated Verification**: 10 focused unit tests, 4 integration tests, and 46 regression tests passing with 100% success.

---

## 2. Existing Architecture Audit

Before implementation, existing backend calculation architecture was audited:
- **`LandedCostCalculator.java`**: Pure arithmetic utility computing `(MCP + RegCharges) / LossCoeff`, charge summation, and NPCL rebate discount. (Reused)
- **`ElectricalLossCalculator.java`**: Evaluates compound grid loss coefficient `(1 - ists/100) * (1 - stu/100) * (1 - wheeling/100)` with 99.9% capping and negative clamping. (Reused)
- **`RegulatoryChargeResolver.java`**: Resolves ISTS date ranges, matches `StateTariff` ToD intervals (with overnight wrap-around), and computes DISCOM base tariffs with kVAh power factor scaling. (Reused)
- **`NpclTariffSchedule.java`**: Evaluates UPERC NPCL HV-2 Winter/Summer ToD rate multipliers (+15% peak surcharge, -15% off-peak rebate). (Reused)
- **`SavingsCalculatorDataAccessService.java`**: Aggregates database queries for `StateCharges`, `CtuCharges`, `IstsCharges`, and `StateTariff`. (Reused)
- **`SavingsCalculatorService.calculateDirect()`**: Audited as an isolated prospect benchmark method returning hardcoded "Acme Steel Mills" values. Per Section 5 of the specification, it was preserved untouched for V18LP.2.
- **`LandingPriceService.java` / `LandingPriceCalcController.java`**: Preserved untouched as the external web proxy at `/api/landing-price-calc/*`.

---

## 3. Node.js Source Identification & Method Mapping

The Node.js reference repository `IEX-Dashboard/backend` served as the single authoritative source of truth.

### 3.1 Node.js Calculation Execution Paths Identified
1. **`savings-calculator-new.service.ts` (Lines 1581–1620)**:
   - Evaluates ISTS loss for delivery date.
   - Converts MCP from ₹/MWh to ₹/kWh: `mcp / 1000`.
   - Computes compound transmission loss: `lossCoeff = (1 - ists/100) * (1 - stu/100) * (1 - wheeling/100)`.
   - Sums all regulatory charges: `ctu + stu + wheeling + OTHER_CHARGES + EXCHANGE_FEES + GST_EXCHANGE + TRADER_MARGIN + GST_TRADER_MARGIN + crossSubsidy + additionalSurcharge`.
   - Computes landed exchange price: `(mcp + totalCharges) / lossCoeff`.
   - Filters OA eligibility by sanctioned load (`>= 1000 kW` for DAM/RTM, GDAM eligible for all).
2. **`savings-calculator.service.ts` (Lines 836–930)**:
   - Resolves ToD tariff slabs, FPPA adder, Electricity Duty, and NPCL prompt payment discount (`* 0.90 * 0.99 = 0.891`).
3. **Legacy HV-2 Busbar Path (`savings-calculator.service.ts:826-828`)**:
   - Compares raw `MCP / 1000` directly with DISCOM tariffs without regulatory charges or losses. Preserved as `CalculationMode.BUSBAR_COMPARISON`.

### 3.2 Method Mapping Table

| Node.js Reference Method | Java Implementation Method | Formula / Behavior | Parity Status |
| :--- | :--- | :--- | :---: |
| `calcExchangeLanding` (`savings-calculator-new.service.ts:1591`) | `LandedCostCalculator.calculateExchangeLanding` | `(MCP + RegChargesSum) / LossCoeff` | **VERIFIED** |
| `lossCoefficient` (`savings-calculator-new.service.ts:1596`) | `ElectricalLossCalculator.calculateLossCoefficient` | `(1 - ists/100) * (1 - stu/100) * (1 - wheeling/100)` | **VERIFIED** |
| `regionalCharges` (`savings-calculator-new.service.ts:1597`) | `LandedCostCalculator.calculateRegulatoryChargesSum` | Sum of CTU, STU, Wheeling, Other, Fee, GST, Margin, GST, CSS, AS | **VERIFIED** |
| `matchingIsts` (`savings-calculator-new.service.ts:1582`) | `RegulatoryChargeResolver.resolveIstsLossPercent` | Look up `ists_charges` by `deliveryDate` between `startDate` and `endDate` | **VERIFIED** |
| `resolveDiscomTariff` (`savings-calculator.service.ts:836`) | `RegulatoryChargeResolver.resolveDiscomTariff` | Match `StateTariff` by ToD hour/month, divide by PF if kVAh | **VERIFIED** |
| `NPCL Prompt Rebate` (`savings-calculator.service.ts:930`) | `LandedCostCalculator.applyNpclDiscount` | `grossDiscom * 0.90 * 0.99 = grossDiscom * 0.891` | **VERIFIED** |
| `Open Access Eligibility` (`savings-calculator-new.service.ts:1607`) | `LandingPriceCalculatorService.calculate` | DAM & RTM require `>= 1000 kW`; GDAM open to all | **VERIFIED** |
| `Busbar Shortcut` (`savings-calculator.service.ts:828`) | `computeMarketLanding(..., BUSBAR_COMPARISON)` | Returns raw `mcpRsPerKwh` directly | **VERIFIED** |

---

## 4. Implementation Details

### 4.1 Request DTO: `LandingPriceCalculationRequest.java`
- Package: `com.sense.prolt_operations.dto.calculator`
- Annotated with `@JsonIgnoreProperties(ignoreUnknown = true)` and `@JsonAlias` to accept query params, legacy names, and standard JSON keys:
  - `state` / `stateCode` (Required, `@NotBlank`)
  - `consumerCategory` / `category` (Required, `@NotBlank`)
  - `voltageLevel` / `voltage` (Required, `@NotBlank`)
  - `discom` (Optional, e.g. "NPCL")
  - `deliveryDate` / `date` (Optional, defaults to current date)
  - `timeblock` / `slot` (Optional 1..96, defaults to 1)
  - `mcpRsPerKwh` / `iexPrice` (Optional ₹/kWh)
  - `damMcpRsPerMwh` / `damMcp` (Optional ₹/MWh)
  - `rtmMcpRsPerMwh` / `rtmMcp` (Optional ₹/MWh)
  - `gdamMcpRsPerMwh` / `gdamMcp` (Optional ₹/MWh)
  - `sanctionedLoadKw` / `loadKw` (Optional, defaults to 1000 kW)
  - `powerFactor` (Optional, defaults to 0.99)
  - `applyElectricityDuty` (Optional boolean, defaults to false)
  - `traderMarginRsPerKwh` (Optional, defaults to 0.02)
  - `calculationMode` (`OPEN_ACCESS_LANDED` or `BUSBAR_COMPARISON`, defaults to `OPEN_ACCESS_LANDED`)

### 4.2 Response DTO: `LandingPriceCalculationResponse.java`
- Structured, transparent output sections:
  - **Metadata**: State, DISCOM, category, voltage, deliveryDate, timeblock, calculationMode.
  - **`LossBreakdown`**: `istsLossPercent`, `stuLossPercent`, `wheelingLossPercent`, `compoundLossCoefficient`.
  - **`RegulatoryChargesBreakdown`**: `ctuCharge`, `stuCharge`, `wheelingCharge`, `otherCharges`, `exchangeFees`, `gstExchange`, `traderMargin`, `gstTraderMargin`, `crossSubsidy`, `additionalSurcharge`, `totalRegulatoryCharges`.
  - **`MarketRateDetail` (per market: DAM, RTM, GDAM)**: `mcpRsPerKwh`, `lossAdjustedMcp`, `landedPriceRsPerKwh`, `eligibleForOa`.
  - **`DiscomTariffBreakdown`**: `baseRate`, `todSlab`, `fppaAdder`, `dutyAdder`, `npclRebateDiscount`, `effectiveDiscomLandingPrice`.
  - **`ComparisonSummary`**: `cheapestSource`, `bestMarketPrice`, `discomPrice`, `spreadVsDiscomRsPerKwh`, `potentialSavingsPercent`.

### 4.3 Service: `LandingPriceCalculatorService.java`
- Fully decoupled from `SavingsCalculatorService` and `SavingsCalculatorEntry`.
- Injects shared components via Spring dependency injection.
- Reuses `SavingsCalculatorDataAccessService` to resolve database entities (`StateCharges`, `CtuCharges`, `IstsCharges`, `StateTariff`, `FppaCharges`).
- Handles graceful fallback to `RegulatoryFallbackConstants` when database entries are missing for a given state or date.
- Correctly avoids Java autoboxing NPEs on null inputs.

### 4.4 Controller: `LandingPriceCalculatorController.java`
- Base path: `@RequestMapping("/api/landing-price-calculator")`
- Endpoints:
  - `POST /api/landing-price-calculator/calculate` (`@Valid @RequestBody LandingPriceCalculationRequest`)
  - `GET /api/landing-price-calculator/calculate` (`@Valid @ModelAttribute LandingPriceCalculationRequest`)
- Secured in `SecurityConfig.java`: `.requestMatchers("/api/landing-price-calc/**", "/api/landing-price-calculator/**").authenticated()`.

---

## 5. Verification & Test Suite Results

### 5.1 Unit Tests (`LandingPriceCalculatorServiceTest`)
File: `src/test/java/com/sense/prolt_operations/service/calculator/LandingPriceCalculatorServiceTest.java`
- `testNullRequestThrows`: Verifies `BadRequestException` on null request.
- `testMissingStateThrows`: Verifies validation error when state is missing.
- `testMissingCategoryThrows`: Verifies validation error when consumerCategory is missing.
- `testMissingVoltageThrows`: Verifies validation error when voltageLevel is missing.
- `testValidOpenAccessCalculation`: Verifies full end-to-end multi-market calculation with exact compound loss (`0.8949024`), regulatory charges sum (`1.9972`), and landed prices against mathematical expected values.
- `testNonOpenAccessThreshold`: Verifies that loads `< 1000 kW` mark DAM and RTM as ineligible, while GDAM remains eligible.
- `testBusbarComparisonMode`: Verifies `BUSBAR_COMPARISON` mode returns raw MCPs.
- `testNpclRebateDiscount`: Verifies that NPCL DISCOM tariffs receive the `0.891` prompt rebate discount.
- `testFallbackWhenDatabaseEmpty`: Verifies that absent database records gracefully trigger standard regulatory fallbacks.
- `testZeroMcpHandling`: Verifies landed cost computation when MCP is `0.0`.
- **Result**: **10 / 10 tests passed.**

### 5.2 Controller Integration Tests (`LandingPriceCalculatorControllerTest`)
File: `src/test/java/com/sense/prolt_operations/LandingPriceCalculatorControllerTest.java`
- `testUnauthenticatedReturns401`: Verifies unauthenticated requests return HTTP 401.
- `testGetCalculation`: Verifies `GET /api/landing-price-calculator/calculate` with query parameters returns HTTP 200 with complete JSON schema.
- `testPostCalculation`: Verifies `POST /api/landing-price-calculator/calculate` with JSON body returns HTTP 200.
- `testMissingStateReturns400`: Verifies missing required state returns HTTP 400 Bad Request.
- **Result**: **4 / 4 tests passed.**

### 5.3 Regression Test Suite Results
Ran all calculation test suites:
- `LandedCostCalculatorTest`: 5 passed
- `ElectricalLossCalculatorTest`: 7 passed
- `RegulatoryChargeResolverTest`: 24 passed
- `LandingPriceParityIntegrationTest`: 3 passed
- `SavingsCalculatorNumericalParityTest`: 7 passed
- `LandingPriceCalculatorServiceTest`: 10 passed
- `LandingPriceCalculatorControllerTest`: 4 passed
- **Total Tests Executed**: **60 tests passed, 0 failures, 0 errors.**
- **Build Status**: `./mvnw test-compile`: **BUILD SUCCESS**.

---

## 6. Business Logic Parity Matrix

| Feature / Behavior | Node.js Reference Implementation | Java Implementation | Parity Status |
| :--- | :--- | :--- | :---: |
| **Compound Grid Loss Formula** | `(1 - ists/100) * (1 - stu/100) * (1 - wh/100)` | `ElectricalLossCalculator.calculateLossCoefficient` | **VERIFIED** |
| **Grid Loss Capping & Clamping** | `Math.min(loss, 99.9)` | Capped at 99.9%, clamped at 0.0% | **VERIFIED** |
| **Landed Cost Equation** | `(MCP + RegCharges) / LossCoeff` | `LandedCostCalculator.calculateExchangeLanding` | **VERIFIED** |
| **Regulatory Charges Sum** | 10 adders (CTU, STU, WH, Other, Fee, GST, Margin, GST, CSS, AS) | `LandedCostCalculator.calculateRegulatoryChargesSum` | **VERIFIED** |
| **Open Access Threshold** | DAM/RTM require `>= 1000 kW` | Enforced via `sanctionedLoadKw >= minOaKw` | **VERIFIED** |
| **Green Energy OA (GDAM)** | Open to all loads (< 1000 kW allowed) | GDAM marked `eligibleForOa = true` | **VERIFIED** |
| **DISCOM Tariff ToD Matching** | Hour/month interval matching with wrap-around | `RegulatoryChargeResolver.resolveDiscomTariff` | **VERIFIED** |
| **kVAh Power Factor Adjustment** | `rate / pf` when unit is kVAh | Evaluated in `RegulatoryChargeResolver` | **VERIFIED** |
| **DISCOM FPPA Adder** | `baseTariff * (fppaPercent / 100)` | Evaluated from `fppa_charges` | **VERIFIED** |
| **DISCOM Electricity Duty** | `(baseTariff + fppa) * (dutyPercent / 100)` | Evaluated with statutory 7.5% default | **VERIFIED** |
| **NPCL Rebate Discount** | `grossDiscom * 0.90 * 0.99 = 0.891` | `LandedCostCalculator.applyNpclDiscount` | **VERIFIED** |
| **Busbar Shortcut Mode** | Raw `MCP / 1000` | Supported via `CalculationMode.BUSBAR_COMPARISON` | **VERIFIED** |
| **IEX API Proxy (`/api/landing-price-calc`)** | Proxies to `www.iexindia.com` | `LandingPriceCalcController.java` preserved | **VERIFIED** |

---

## 7. Known Limitations & Scope Boundaries

1. **V18LP.2 Benchmark Replacement**: `SavingsCalculatorService.calculateDirect()` retains its legacy hardcoded "Acme Steel Mills" profile. Refactoring `calculateDirect` to consume `LandingPriceCalculatorService` belongs to Milestone V18LP.2 as specified in Section 5.
2. **V18LP.3 Caching**: Regulatory charge lookups in `SavingsCalculatorDataAccessService` query the database on each request without in-memory interval-tree caching. High-performance indexed caching belongs to Milestone V18LP.3.
3. **External IEX Scraper Proxy**: `/api/landing-price-calc/*` continues to proxy external web calls as originally designed; the new capability at `/api/landing-price-calculator/*` provides the clean, independent, in-house alternative.

---

## 8. Files Created and Modified

### Created Files:
1. `src/main/java/com/sense/prolt_operations/dto/calculator/LandingPriceCalculationRequest.java`
2. `src/main/java/com/sense/prolt_operations/dto/calculator/LandingPriceCalculationResponse.java`
3. `src/main/java/com/sense/prolt_operations/service/calculator/LandingPriceCalculatorService.java`
4. `src/main/java/com/sense/prolt_operations/controller/LandingPriceCalculatorController.java`
5. `src/test/java/com/sense/prolt_operations/service/calculator/LandingPriceCalculatorServiceTest.java`
6. `src/test/java/com/sense/prolt_operations/LandingPriceCalculatorControllerTest.java`
7. `docs/97-v18lp1-independent-landing-price-calculator-report.md`

### Modified Files:
1. `src/main/java/com/sense/prolt_operations/security/SecurityConfig.java` (added `/api/landing-price-calculator/**` to authenticated routes)

---

## 9. Confirmation of Strict Safeguards

- [x] **Node.js repository (`IEX-Dashboard/backend`) remained strictly READ-ONLY** (0 modifications).
- [x] **Enterprise V2 Calculator (`SavingsCalculatorNewService.java`) is 100% untouched** (verified `git diff` is empty).
- [x] **Saving Calculator business formulas remained unchanged**.
- [x] **StarRocks integration remained untouched**.
- [x] **Zero database migrations introduced**; PostgreSQL schema unchanged.
- [x] **Flyway target remained unchanged** (`spring.flyway.target=${FLYWAY_TARGET:8}`).
- [x] **No frontend/UI files modified**.
- [x] **Zero Git commits or pushes executed**.
- [x] **No production or staging environments accessed**.

---

## 10. Final Verdict

# **`V18LP.1 COMPLETE`**

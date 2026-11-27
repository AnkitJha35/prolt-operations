# Milestone V18L.2 — Typed Internal Models & Unchecked Cast Elimination Report

**Execution Date:** November 5, 2026  
**Status:** Completed & Verified  
**Primary Module:** Java Legacy Saving Calculator (`prolt-operations`)  
**Reference Authoritative Source:** Node.js Legacy Application (`IEX-Dashboard/backend`)  
**Scope:** Phase 2 (Second Part) of the V18J Prioritized Refactoring Roadmap  

---

## 1. Executive Summary

Milestone **V18L.2** successfully eliminates weakly-typed internal calculation data pipelines in the Java Legacy Saving Calculator, replacing raw maps (`Map<String, Object>`), untyped slot arrays, and nested dictionary lookups with strongly typed, immutable Java records.

Key achievements include:
1. **Strongly Typed Internal Calculation Contracts**:
   - Replaced untyped `Map<String, Object> shiftInsights` with `DemandShiftInsights`, containing immutable collections of `TodShiftSummaryItem` and `ShiftedSlotItem`.
   - Replaced untyped monthly calculation output maps with `MonthlyMarketDecisionResult`, encapsulating the 31 financial, volumetric, and operational fields, including typed `TodSlabSummary` and `OaDetailedResult`.
   - Introduced `OaDetailedResult` and `OaLedgerTotals` records, replacing nested `oaDetailed` maps with strongly typed accessors.
2. **Complete Unchecked Cast Elimination**:
   - Eliminated **100% of `@SuppressWarnings("unchecked")` instances** in `src/main/java/com/sense/prolt_operations/service/calculator/` and `SavingsCalculatorService.java`. The count in the calculator module dropped from 4 to **0**.
   - Safeguarded external boundary serialization (`toMap()`) and deserialization (`fromMap(Map<?, ?>)`) using pattern matching and defensive conversion without requiring unchecked cast suppressions.
3. **Multi-Month Aggregation & Functional Pipeline Type Safety**:
   - Converted `ClientOverviewAggregator` and `DemandShiftOptimizer` multi-month rollup loops to consume strongly typed function providers (`Function<String, MonthlyMarketDecisionResult>` and `Function<String, DemandShiftInsights>`), eliminating repeated string-based map lookups (`CalculatorUtils.getDouble`).
   - Retained backward-compatible adapter overloads across all service boundaries (`SavingsCalculatorService`, `Hv2SavingsCalculator`, `ClientOverviewAggregator`, `DemandShiftOptimizer`) to ensure zero disruption to REST endpoints, test suites, or external callers.
4. **Zero Regressions & Exact Parity**:
   - Maintained 1:1 business behavior, mathematical fidelity, and data shape parity with Node.js Legacy (`custom-saving-calc.service.js`).
   - The entire test suite passed: **502 tests executed, 0 failures, 0 errors, 1 skipped by design**.

---

## 2. Inventory of `@SuppressWarnings("unchecked")` & Final Disposition

During the V18J audit, 14 instances of `@SuppressWarnings("unchecked")` were cataloged across the `prolt-operations` codebase. Four of these instances were directly located within the Legacy Saving Calculator internal contracts (`service/calculator/`).

### Disposition Table

| File | Line (Pre-V18L.2) | Scope | Pre-V18L.2 Reason | V18L.2 Disposition | Final State |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `CalculatorUtils.java` | 35 | Calculator | `(Map<String, Object>) obj` inside `getMap()` | Replaced with type-safe entry-set copier copying String keys | **Eliminated** |
| `ForwardBankingAllocator.java` | 14 | Calculator | `(List<Map<String, Object>>) shiftInsights.get("slotsData")` | Replaced with typed `DemandShiftInsights.findSlot(date, timeblock)` | **Eliminated** |
| `Hv2SavingsCalculator.java` | 99 | Calculator | `(List<Map<String, Object>>) shiftInsights.get("todShiftSummary")` | Replaced with typed `DemandShiftInsights.getNewEnergyForTod(tod)` | **Eliminated** |
| `DemandShiftOptimizer.java` | 52 | Calculator | `(List<Map<String, Object>>)` and `(Map<String, Object>)` in slot shift | Replaced with typed `calculateDemandShift` consuming `MarketSlotCalculation` | **Eliminated** |
| Boundary Deserializers | N/A | Calculator | New record `fromMap` methods (`DemandShiftInsights`, etc.) | Designed with wildcard `Map<?, ?>` and `instanceof List<?>` pattern matching | **Zero Warnings** |
| Non-Calculator Modules | Various | External | Trade reports, proposals, scraper, security, redis rate limiting | Out of calculator scope; documented in V18J audit | Preserved |

**Net Result**: Zero `@SuppressWarnings("unchecked")` annotations remain in `src/main/java/com/sense/prolt_operations/service/calculator/` or `SavingsCalculatorService.java`.

---

## 3. Model Architecture & Data Flow

```mermaid
flowchart TD
    subgraph External_Boundary [External HTTP / Database Boundaries]
        Req[REST Request / Entry Entity]
        MapIn[Raw Consumptions Map]
    end

    subgraph Calculator_Typed_Pipeline [Strongly Typed Calculation Pipeline]
        MDC[MarketDecisionContext\n(Immutable Record)]
        SlotCalc[List<MarketSlotCalculation>\n(96 Slots Dispatched)]
        SMA[SevenMarketArbitrator]
        RTM[RtmContiguitySolver]
        FBA[ForwardBankingAllocator]
        OAC[OperationalOverheads]
        FLR[FinancialLedgerReconciler]
        MMDR[MonthlyMarketDecisionResult\n(28 financial fields + OaDetailedResult)]
        DSO[DemandShiftOptimizer]
        DSI[DemandShiftInsights\n(todShiftSummary + slotsData)]
        HV2[Hv2SavingsCalculator]
    end

    subgraph Multi_Month_Aggregators [Multi-Month Functional Rollups]
        COA[ClientOverviewAggregator\nFunction<String, MonthlyMarketDecisionResult>]
        DSA[DemandShiftOptimizer.calculateDemandShiftAllMonths\nFunction<String, DemandShiftInsights>]
    end

    subgraph Output_Boundary [Wire Serialization Boundary]
        MapOut[Response Map / DTO\n(toMap() Contract)]
    end

    Req --> MDC
    MapIn --> MDC
    MDC --> SlotCalc
    SlotCalc --> SMA --> RTM --> FBA --> OAC --> FLR
    FLR --> MMDR
    MMDR --> DSO
    DSO --> DSI
    DSI --> FBA
    DSI --> HV2
    MMDR --> COA
    DSI --> DSA
    MMDR --> MapOut
    DSI --> MapOut
```

---

## 4. Node.js Reference Parity Verification

The Node.js Legacy implementation (`IEX-Dashboard/backend/src/services/custom-saving-calc.service.js`) was referenced to ensure strict structural, volumetric, and calculation parity:

1. **`DemandShiftInsights.slotsData` Fields (Node.js lines 2030–2050)**:
   - Node.js structure: `{ date, timeblock, tod, originalEnergy, newEnergy, costPerKwh, marketSource, shouldBuyFromMarket, marketEnergy, discomEnergy, damMcp, rtmMcp, gdamMcp }`.
   - Node.js does *not* include `consumedMarketEnergy` in `slotsData`. Java model `ShiftedSlotItem` matches this schema 1:1.
2. **Fast Slab Lookup (Node.js lines 1522–1530)**:
   - Node.js performs: `shiftInsights.todShiftSummary.find((t) => t.tod === groupKey)`.
   - Java implementation: `DemandShiftInsights.findTodSummary(String tod)` and `DemandShiftInsights.getNewEnergyForTod(String tod)` provide exact matching semantics.
3. **Slot Lookup for Forward Banking (Node.js lines 1950–1960)**:
   - Node.js performs: `shiftInsights.slotsData.find((ss) => ss.date === s.date && ss.timeblock === s.timeblock)`.
   - Java implementation: `DemandShiftInsights.findSlot(String date, Integer timeblock)` provides exact matching semantics.
4. **Baseline Discom Fallback When Shift Insights Absent**:
   - When demand shift insights are absent or disabled (`useShiftedProfile = false`), both Node.js and Java seamlessly fall back to unshifted consumption without null-pointer hazards.

---

## 5. Internal Calculation Contracts & Record Definitions

### 5.1 `DemandShiftInsights` & Sub-Models

Located in `com.sense.prolt_operations.service.calculator.model`:

- **`DemandShiftInsights`**:
  ```java
  public record DemandShiftInsights(
          String clientId,
          String clientName,
          double sanctionedLoadKw,
          double maxEnergyPerSlot,
          double originalTotalCost,
          double newTotalCost,
          double savingsAchieved,
          double shiftedEnergy,
          double potentialSavingsRs,
          double shiftableCapacityMw,
          String recommendedWindow,
          List<String> peakHours,
          List<String> solarHours,
          List<TodShiftSummaryItem> todShiftSummary,
          List<ShiftedSlotItem> slotsData
  )
  ```
  - Provides `findTodSummary(String tod)`, `getNewEnergyForTod(String tod)`, `findSlot(String date, Integer timeblock)`, `empty(clientId, clientName)`, `toMap()`, and defensive `fromMap(Map<?, ?>)`.

- **`TodShiftSummaryItem`**:
  ```java
  public record TodShiftSummaryItem(
          String tod,
          double originalEnergy,
          double newEnergy,
          double diff,
          double originalMarketEnergy,
          double newMarketEnergy
  )
  ```

- **`ShiftedSlotItem`**:
  ```java
  public record ShiftedSlotItem(
          String date,
          int timeblock,
          String tod,
          double originalEnergy,
          double newEnergy,
          double costPerKwh,
          String marketSource,
          boolean shouldBuyFromMarket,
          double marketEnergy,
          double discomEnergy,
          Double damMcp,
          Double rtmMcp,
          Double gdamMcp
  )
  ```

### 5.2 `MonthlyMarketDecisionResult` & Sub-Models

- **`MonthlyMarketDecisionResult`**:
  ```java
  public record MonthlyMarketDecisionResult(
          String clientId,
          String clientName,
          String state,
          String month,
          List<MarketSlotCalculation> slotsData,
          double totalEnergyKwh,
          double totalMarketEnergyKwh,
          double totalConsumerBusEnergyKwh,
          double totalBaselineCost,
          double fppaPercent,
          double fppaCharge,
          double fppaChargeAfterOA,
          double totalLandedExchangeCost,
          double totalDiscomAfterProlt,
          double baselineEnergyCharges,
          double discomEnergyChargesAfterOA,
          double demandAndFixedChargesApplied,
          double electricityDutyAfterOA,
          double totalSavings,
          double grossSavings,
          double demandCharge,
          double electricityDuty,
          double arrearAmount,
          double currentLpsc,
          double miscellaneousCharges,
          String discom,
          double peakDemand,
          double powerFactor,
          double demandChargeRate,
          List<TodSlabSummary> todSummaries,
          OaDetailedResult oaDetailed
  )
  ```

- **`OaDetailedResult` & `OaLedgerTotals`**:
  ```java
  public record OaDetailedResult(
          List<OaBreakdownItem> breakdown,
          double dailyFixedOverhead,
          double nldcSchedulingCost,
          double sldcSchedulingCost,
          double bidApplicationFees,
          int totalDaysTraded,
          OaLedgerTotals totals
  )
  ```
  ```java
  public record OaLedgerTotals(
          double cssCharge,
          double cssRate,
          double rpoCharge,
          double pocCharge,
          double stuCharge,
          double dcCharge,
          double iexFee,
          double traderMargin,
          double traderMarginGst,
          double proltMarginCost,
          double consultancyFee,
          double probusPlatformFee,
          double nocFee,
          double regFee,
          double consumerBusUnits
  )
  ```

---

## 6. Unchecked Cast Elimination & Defensive Boundary Handling

### 6.1 Type-Safe `CalculatorUtils.getMap`
Previously, `CalculatorUtils.getMap` relied on `@SuppressWarnings("unchecked")` and `(Map<String, Object>) obj`. It was refactored to perform a safe map copy using `entrySet()`:
```java
public static Map<String, Object> getMap(Map<?, ?> map, String key) {
    if (map == null || key == null) return null;
    Object val = map.get(key);
    if (val instanceof Map<?, ?> m) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : m.entrySet()) {
            if (entry.getKey() != null) {
                result.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return result;
    }
    return null;
}
```

### 6.2 Wildcard Deserialization (`Map<?, ?>`)
All boundary `fromMap` factory methods (`MarketSlotCalculation.fromMap`, `TodSlabSummary.fromMap`, `OaBreakdownItem.fromMap`, `OaLedgerTotals.fromMap`, `OaDetailedResult.fromMap`, `TodShiftSummaryItem.fromMap`, `ShiftedSlotItem.fromMap`, `DemandShiftInsights.fromMap`, `MonthlyMarketDecisionResult.fromMap`) accept `Map<?, ?> map`. Pattern matching with `instanceof Map<?, ?> m` allows direct delegation without casting to `(Map<String, Object>)`:
```java
if (map.get("slotsData") instanceof List<?> list) {
    for (Object obj : list) {
        if (obj instanceof Map<?, ?> m) {
            slotList.add(MarketSlotCalculation.fromMap(m));
        }
    }
}
```

---

## 7. Multi-Month Aggregation & Function Pipeline Type Safety

### 7.1 `ClientOverviewAggregator`
Previously, multi-month rollups executed over 25 string-based dictionary lookups per month (`CalculatorUtils.getDouble(result, "totalSavings")`, etc.).

`ClientOverviewAggregator` now operates on strongly typed records:
```java
MonthlyMarketDecisionResult result = monthlyMarketDecisionProvider.apply(month);
double netSavings = result.totalSavings();
OaDetailedResult oaDetailed = result.oaDetailed();
OaLedgerTotals totals = oaDetailed != null ? oaDetailed.totals() : OaLedgerTotals.empty();
double proltMarginCost = totals.proltMarginCost();
double grossSavings = netSavings + proltMarginCost;

aggregatedCosts.addCssCharge(totals.cssCharge());
aggregatedCosts.addRpoCharge(totals.rpoCharge());
// ... directly reading typed doubles with zero map lookup overhead
```

### 7.2 `DemandShiftOptimizer.calculateDemandShiftAllMonths`
Multi-month demand shift insights aggregation now directly reads typed records:
```java
DemandShiftInsights res = monthlyShiftProvider.apply(month);
originalTotalCost += res.originalTotalCost();
newTotalCost += res.newTotalCost();
savingsAchieved += res.savingsAchieved();
shiftedEnergy += res.shiftedEnergy();

for (TodShiftSummaryItem item : res.todShiftSummary()) {
    // strongly typed accumulation
}
```

---

## 8. Test Suite Verification & Coverage Analysis

### 8.1 New Unit Tests Created
- **`DemandShiftInsightsTest.java`** (5 tests):
  - `testTodShiftSummaryItem`: Verifies construction, getters, `toMap()`, `fromMap()`, and null fallback.
  - `testShiftedSlotItem`: Verifies slot fields, `toMap()`, `fromMap()`, and default market source.
  - `testDemandShiftInsightsRoundtrip`: Verifies 15 properties, `findTodSummary()`, `getNewEnergyForTod()`, `findSlot()`, serialization, and roundtrip deserialization.
  - `testDemandShiftInsightsEmpty`: Verifies empty factory defaults, null safety, and empty map resilience.
  - `testDemandShiftInsightsImmutability`: Confirms that internal collections throw `UnsupportedOperationException` on modification.
- **`MonthlyMarketDecisionResultTest.java`** (4 tests):
  - `testOaLedgerTotals`: Verifies 15 ledger fields, `empty()` factory, `toMap()`, and `fromMap()`.
  - `testOaDetailedResult`: Verifies nested breakdown list, scheduling costs, totals, and roundtrip deserialization.
  - `testMonthlyMarketDecisionResultRoundtrip`: Verifies 31 financial/volumetric fields, immutability, `toMap()`, and `fromMap()`.
  - `testMonthlyMarketDecisionResultEmpty`: Verifies null and empty map safety.

### 8.2 Execution Results
- **Targeted Model & Component Tests**:
  `DemandShiftInsightsTest`, `MonthlyMarketDecisionResultTest`, `DemandShiftOptimizerTest`, `ForwardBankingAllocatorTest`, `Hv2SavingsCalculatorTest`, `ClientOverviewAggregatorTest`  
  **Result:** 24 passed, 0 failures, 0 errors.
- **Parity & Integration Tests**:
  `SavingsCalculatorNumericalParityTest`, `SevenMarketArbitratorTest`, `RtmContiguitySolverTest`, `OperationalOverheadCalculatorTest`, `CalculatorUtilsTest`, `CalculatorModelsTest`, `CalculatorConstantsTest`, `CalculatorConfigurationPrecedenceIntegrationTest`  
  **Result:** 43 passed, 0 failures, 0 errors.
- **Full Project Test Suite**:  
  **Total Tests Executed:** **502**  
  **Failures:** **0**  
  **Errors:** **0**  
  **Skipped:** **1** (`testFlywayMigrationsAgainstRealDatabase` skipped as designed when database is already at V14)  
  **Build Status:** `BUILD SUCCESS` (execution time: 46.046 s)

---

## 9. Architectural Safeguards & Risk Review

1. **Enterprise V2 Isolation**:
   - `SavingsCalculatorNewService.java` remained **100% untouched**.
2. **Node.js Legacy Read-Only Status**:
   - `IEX-Dashboard/backend` was accessed in strictly read-only mode; zero modifications were introduced.
3. **Database Integrity**:
   - Zero database migrations, schema alterations, or data manipulations were performed.
   - Flyway remains at version V14, with test constraints configured via `spring.flyway.target=${FLYWAY_TARGET:8}`.
4. **Git State**:
   - Zero Git commits or pushes were executed. Working tree remains clean and reviewable.
5. **No Regressions**:
   - All legacy API contracts (`Map<String, Object>`) are fully preserved via `toMap()` serializers and backward-compatible adapter overloads.

---

## 10. Readiness for Milestone V18M (Method Decomposition)

With Milestone V18L.2 completed:
1. All subcomponent contracts (`DemandShiftOptimizer`, `ClientOverviewAggregator`, `FinancialLedgerReconciler`, `ForwardBankingAllocator`, `Hv2SavingsCalculator`, `SavingsCalculatorService`) now exchange strongly typed, immutable Java records.
2. The internal calculator pipeline is free of `@SuppressWarnings("unchecked")` and nested map traversal hazards.
3. The codebase is cleanly prepared for **Milestone V18M**, which will focus on decomposing large calculation methods (including `calculateSavingsHV2` and slot landing loops) into modular, single-responsibility calculation stages.

---

# Milestone V18K — Legacy Saving Calculator Code Hygiene & Naming Refactoring Report

**Document Reference**: `docs/74-v18k-code-hygiene-and-naming-refactoring.md`  
**Date**: November 2026  
**Status**: Completed  
**Milestone**: V18K (Phase 1 of V18J Refactoring Roadmap)  
**Target Module**: Java Legacy Saving Calculator (`prolt-operations`)  

---

## 1. Executive Summary

Milestone **V18K** implements Phase 1 of the approved V18J refactoring roadmap ([73-v18j-prioritized-refactoring-roadmap.md](file:///home/ankit-jha/probus-workspace/prolt-operations/docs/73-v18j-prioritized-refactoring-roadmap.md)).

This milestone executed strict, low-risk code hygiene improvements, eradicated duplicate imports, replaced cryptic single-letter variables with descriptive domain terms, clarified benchmark test fixtures, and eliminated magic numbers without modifying underlying business algorithms, numerical formulas, or public API contracts.

---

## 2. Files Modified

| File | Type | Changes Made |
| :--- | :---: | :--- |
| [`SavingsCalculatorService.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/SavingsCalculatorService.java) | Service | Removed 9 duplicated imports; documented benchmark industrial fixture in `calculateDirect`; aligned boolean variable `shouldBuyFromMarket` with model field; renamed `sCalc` to `slotCalc`. |
| [`DemandShiftOptimizer.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/DemandShiftOptimizer.java) | Component | Replaced cryptic variables `s`, `orig`, `sm` with `rawSlotMap`, `shiftableSlot`, `originalRawSlot`, and `shiftedSlotMap`. |
| [`CalculatorUtils.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/CalculatorUtils.java) | Component | Replaced single-letter/cryptic variables `obj`, `m`, `k`, `v`, `r`, `cur`, `b` with descriptive domain names (`todItem`, `todMap`, `consumptionKey`, `entryKey`, `entryValue`, `projection`, `currentDate`, `slotNumber`). |
| [`ClientOverviewAggregator.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/ClientOverviewAggregator.java) | Component | Extracted named constant `DEFAULT_CONSUMER_BUS_LOSS_RATIO = 0.1211`; replaced `m`, `res`, `oaDet`, `t`, `e` with `monthRecord`, `monthResult`, `openAccessDetailed`, `openAccessTotals`, `ex`. |
| [`Hv2SavingsCalculator.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/Hv2SavingsCalculator.java) | Component | Replaced loop variables `rec`, `i`, `t` with `slotProjection`, `istsCharge`, and `tariff`. |
| [`DemandShiftOptimizerTest.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/test/java/com/sense/prolt_operations/service/calculator/DemandShiftOptimizerTest.java) | Test | Renamed test fixture variables `s1`, `s2` to self-explanatory `expensiveSlot` and `cheapSlot`. |

---

## 3. Variable Renames Inventory (Before vs. After)

### 3.1 `DemandShiftOptimizer.java`
| Context | Original Variable | Refactored Variable | Rationale |
| :--- | :--- | :--- | :--- |
| Line 61 (`optimizeSlots`) | `Map<String, Object> s` | `Map<String, Object> rawSlotMap` | Clarifies that the map represents an untyped slot projection. |
| Line 186 (`outSlots` loop) | `ShiftableSlot s` | `ShiftableSlot shiftableSlot` | Avoids single-letter iteration variable. |
| Line 187 (`outSlots` loop) | `Map<String, Object> orig` | `Map<String, Object> originalRawSlot` | Clarifies that it references the unshifted slot from `rawSlots`. |
| Line 188 (`outSlots` loop) | `Map<String, Object> sm` | `Map<String, Object> shiftedSlotMap` | Meaningful name for the generated output slot map. |

### 3.2 `CalculatorUtils.java`
| Context | Original Variable | Refactored Variable | Rationale |
| :--- | :--- | :--- | :--- |
| Line 186 (`getSlabConsumption`) | `Object obj` | `Object todItem` | Identifies individual ToD list elements. |
| Line 187 (`getSlabConsumption`) | `Map<?, ?> m` | `Map<?, ?> todMap` | Identifies deserialized ToD mapping. |
| Lines 196, 212 (`getSlabConsumption`)| `String k` | `String consumptionKey` | Distinguishes consumption map keys from generic map keys. |
| Lines 224-225 (`getSlabConsumption`)| `String k`, `Object v` | `String entryKey`, `Object entryValue` | Clear key-value pair naming. |
| Line 245 (`toInternalSlots`) | `r -> new InternalRateSlot(...)` | `projection -> new InternalRateSlot(...)` | Meaningful stream parameter for `ExchangeRateSlotProjection`. |
| Line 257 (`toInternalSlots`) | `LocalDate cur` | `LocalDate currentDate` | Standard date naming. |
| Line 259 (`toInternalSlots`) | `for (int b = 1; b <= 96; b++)` | `for (int slotNumber = 1; slotNumber <= 96; slotNumber++)` | Clarifies timeblock / slot index (1 to 96). |

### 3.3 `ClientOverviewAggregator.java`
| Context | Original Variable | Refactored Variable | Rationale |
| :--- | :--- | :--- | :--- |
| Line 134 (`aggregateClientOverview`)| `ClientOverviewMonthRecord m` | `ClientOverviewMonthRecord monthRecord` | Descriptive record identifier. |
| Line 193 (`aggregateMarketDecision`)| `Map<String, Object> res` | `Map<String, Object> monthResult` | Explicit monthly decision output name. |
| Line 209 (`aggregateMarketDecision`)| `Map<String, Object> oaDet` | `Map<String, Object> openAccessDetailed` | Descriptive domain name for Open Access breakdown. |
| Line 211 (`aggregateMarketDecision`)| `Map<String, Object> t` | `Map<String, Object> openAccessTotals` | Explicit name for Open Access totals. |
| Lines 127, 241 (Exceptions) | `catch (Exception e)` | `catch (Exception ex)` | Follows Java standard exception naming convention. |

### 3.4 `Hv2SavingsCalculator.java`
| Context | Original Variable | Refactored Variable | Rationale |
| :--- | :--- | :--- | :--- |
| Line 165 (`calculateSavingsHV2`) | `InternalRateSlot rec` | `InternalRateSlot slotProjection` | Meaningful slot rate projection naming. |
| Line 170 (`calculateSavingsHV2`) | `for (IstsCharges i : ...)` | `for (IstsCharges istsCharge : ...)` | Meaningful ISTS loop variable. |
| Line 206 (`calculateSavingsHV2`) | `for (StateTariff t : ...)` | `for (StateTariff tariff : ...)` | Meaningful tariff loop variable. |

---

## 4. Method, Boolean & Fixture Naming Improvements

### 4.1 Boolean Alignment: `shouldBuyFromMarket`
In `SavingsCalculatorService.java` (line 857):
```java
// Before:
boolean shouldBuy = bestMarketLanding > 0 && bestMarketLanding < discomLanding;
MarketSlotCalculation sCalc = new MarketSlotCalculation();
// ...
sCalc.shouldBuyFromMarket = shouldBuy;

// After:
boolean shouldBuyFromMarket = bestMarketLanding > 0 && bestMarketLanding < discomLanding;
MarketSlotCalculation slotCalc = new MarketSlotCalculation();
// ...
slotCalc.shouldBuyFromMarket = shouldBuyFromMarket;
```
The local variable name now matches the public model field `MarketSlotCalculation.shouldBuyFromMarket` exactly.

### 4.2 Benchmark Fixture Documentation in `calculateDirect`
In `SavingsCalculatorService.java`:
- Added explicit Javadoc clarifying that `"Acme Steel Mills"` (and UP HV-2 33kV) is a standardized benchmark industrial profile matching legacy proposal baselines.
- Renamed local flag `isAcme` $\to$ `isAcmeBenchmark`.

### 4.3 Test Fixture Clarity in `DemandShiftOptimizerTest.java`
```java
// Before:
Map<String, Object> s1 = new LinkedHashMap<>(); // Expensive slot
Map<String, Object> s2 = new LinkedHashMap<>(); // Cheap slot

// After:
Map<String, Object> expensiveSlot = new LinkedHashMap<>();
Map<String, Object> cheapSlot = new LinkedHashMap<>();
```
Eliminated ambiguous `s1` and `s2` in favor of clear test intent.

---

## 5. Imports & Code Hygiene

### 5.1 Duplicated Import Removal
In `SavingsCalculatorService.java`, lines 51-59 duplicated lines 41-49:
```java
// Removed duplicated block:
import com.sense.prolt_operations.service.calculator.ClientOverviewAggregator;
import com.sense.prolt_operations.service.calculator.DemandShiftOptimizer;
import com.sense.prolt_operations.service.calculator.ElectricalLossCalculator;
import com.sense.prolt_operations.service.calculator.FinancialLedgerReconciler;
import com.sense.prolt_operations.service.calculator.ForwardBankingAllocator;
import com.sense.prolt_operations.service.calculator.Hv2SavingsCalculator;
import com.sense.prolt_operations.service.calculator.LandedCostCalculator;
import com.sense.prolt_operations.service.calculator.NpclTariffSchedule;
import com.sense.prolt_operations.service.calculator.OperationalOverheadCalculator;
```
The file now contains a clean, non-redundant import section.

### 5.2 Magic Number Elimination: `0.1211`
In `ClientOverviewAggregator.java`, line 105 contained an inlined magic number `0.1211` for the consumer bus loss fallback ratio.
Extracted into a documented constant:
```java
/**
 * Default cumulative loss ratio fallback (12.11%) applied when consumer bus energy is absent.
 */
private static final double DEFAULT_CONSUMER_BUS_LOSS_RATIO = 0.1211;
```

---

## 6. Intentionally Deferred Items

In strict compliance with the scope constraints of Milestone V18K (Phase 1):
1. **Domain Enums (`EnergyMarketType`, `EnergySource`)**: Deferred to Phase 2 (Milestone **V18L**).
2. **Typed Java Records (`DemandShiftInsights`, `MonthlyMarketDecisionResult`)**: Deferred to Phase 2 (Milestone **V18L**).
3. **Method Decomposition of `calculateSavingsHV2` (318 LOC)**: Deferred to Phase 3 (Milestone **V18M**).
4. **Shared Tariff / ISTS Method Extraction**: Deferred to Phase 3 (Milestone **V18M**).
5. **Slot Loop Performance / Pre-Grouping**: Deferred to Phase 4 (Milestone **V18N**).
6. **Exception Handling & Audited Fallbacks**: Deferred to Phase 5 (Milestone **V18O**).
7. **Constructor Overload Deprecation in `SavingsCalculatorService`**: Deferred to Phase 6 (Milestone **V18P**).

---

## 7. Verification Results

### 7.1 Targeted Calculator Unit & Parity Tests
```bash
./mvnw test -Dtest=SavingsCalculatorNumericalParityTest,Hv2SavingsCalculatorTest,DemandShiftOptimizerTest,CalculatorUtilsTest,ClientOverviewAggregatorTest
```
- **Results**: 25 tests run, 0 failures, 0 errors, 0 skipped.
- **Outcome**: 100% Passed.

### 7.2 Full Repository Regression Suite
```bash
./mvnw test
```
- **Results**: **417 tests run, 0 failures, 0 errors, 1 skipped by design (`ScraperLiveManualTest`)**.
- **Outcome**: **BUILD SUCCESS** (46.906 s).

### 7.3 Numerical Parity Confirmation
All numerical assertions across:
- `SavingsCalculatorNumericalParityTest` (Cases 1 & 2 for `getFlooredMaxEnergyPerSlot`, landed cost arbitration, banking multipliers)
- `CalculatorPropertiesTest` (Validation and canonical defaults)
- `Hv2SavingsCalculatorTest` (NPCL seasonal ToD rate scheduling)
- `DemandShiftOptimizerTest` (Energy shifting and potential savings)
passed with exact 1:1 mathematical equivalence to the pre-refactoring state.

---

## 8. Final Safeguard Verification

- [x] **Only approved code hygiene and naming changes were made.**
- [x] **Enterprise V2 Calculator (`SavingsCalculatorNewService.java`) remains 100% untouched.**
- [x] **Node.js reference repository (`IEX-Dashboard/backend`) remains 100% untouched.**
- [x] **No API endpoint contracts or JSON schemas modified.**
- [x] **Database remains at Flyway V14 with test constraint `spring.flyway.target=${FLYWAY_TARGET:8}` preserved.**
- [x] **No Git commits or pushes executed.**

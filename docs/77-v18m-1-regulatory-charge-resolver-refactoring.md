# Milestone V18M.1 Deliverable — Legacy Saving Calculator Shared Regulatory Charge Resolver

## 1. Executive Summary

Milestone **V18M.1** executes Phase 3 (first part) of the prioritized refactoring roadmap (`docs/73-v18j-prioritized-refactoring-roadmap.md`).

This milestone extracts duplicated regulatory charge date lookups (ISTS transmission loss) and DISCOM tariff schedule resolution logic from:
- `SavingsCalculatorService.java`
- `Hv2SavingsCalculator.java`

into a dedicated, reusable, thread-safe Spring component:
- `RegulatoryChargeResolver.java`

along with a typed value carrier for resolved tariff rates:
- `MatchedTariffRate.java`

All 526 tests in the full Maven test suite run and pass with **0 failures, 0 errors, 1 skipped by design**, demonstrating 100% numerical and behavioral parity with the Node.js legacy source of truth.

---

## 2. Pre-Refactoring Implementation Comparison

Prior to V18M.1, both `SavingsCalculatorService.java` (lines ~777–881) and `Hv2SavingsCalculator.java` (lines ~177–260) maintained duplicate logic for two distinct regulatory resolution operations:

```
┌─────────────────────────────────────────────────────────────┐
│                  DUPLICATION PATTERNS                       │
├───────────────────────────────┬─────────────────────────────┤
│ SavingsCalculatorService.java │ Hv2SavingsCalculator.java   │
├───────────────────────────────┼─────────────────────────────┤
│ 1. ISTS Date Lookup:          │ 1. ISTS Date Lookup:        │
│    Iterates istsChargesList,  │    Identical loop iterating │
│    checks startDate <= date   │    istsChargesList with     │
│    <= endDate, extracts       │    startDate <= date        │
│    istsLossPercent or 0.0.    │    <= endDate.              │
├───────────────────────────────┼─────────────────────────────┤
│ 2. StateTariff Matching:      │ 2. StateTariff Matching:    │
│    Filters by monthValue,     │    Identical month filter,  │
│    matches ToD start/end hour │    ToD start/end parsing,   │
│    (with wrap-around), falls  │    overnight interval check,│
│    back to FLAT tariff.       │    and FLAT fallback.       │
└───────────────────────────────┴─────────────────────────────┘
```

Both classes also performed NPCL HV-2 schedule resolution via `NpclTariffSchedule`, formatted ToD slab names (e.g., `17:00-22:00` or `FLAT`), and fell back to base rates when tariffs were absent or unmatched.

---

## 3. Differences Between the Two Original Implementations

Before extracting common code into a shared component, a semantic comparison was conducted to ensure no behavior was silently altered:

| Feature / Condition | `SavingsCalculatorService` | `Hv2SavingsCalculator` | Parity / Architectural Rationale |
| :--- | :--- | :--- | :--- |
| **ISTS Date Lookup** | Exact inclusive date range match `[!before(start) && !after(end)]`, returns first match. | Exact inclusive date range match `[!before(start) && !after(end)]`, returns first match. | **Identical**: Unified directly into `resolveIstsLossPercent(...)`. |
| **StateTariff Month Filtering** | Filters by `deliveryDate.getMonthValue()`. If none match, falls back to full list. | Filters by `deliveryDate.getMonthValue()`. If none match, falls back to full list. | **Identical**: Unified directly into `matchTariff(...)`. |
| **ToD Interval Matching** | Parses start & end hours; handles overnight wrap-around (`start > end`). | Parses start & end hours; handles overnight wrap-around (`start > end`). | **Identical**: Unified directly into `matchTariff(...)`. |
| **Flat Tariff Fallback** | Finds first record where start/end time is null or `"—"`. | Finds first record where start/end time is null or `"—"`. | **Identical**: Unified directly into `matchTariff(...)`. |
| **ToD Name Formatting** | Returns `(start + "-" + end).toUpperCase()` or `"FLAT"`. | Returns `(start + "-" + end).toUpperCase()` or `"FLAT"`. | **Identical**: Unified directly into `resolveTariffName(...)`. |
| **Fallback when tariffs list is empty** | Retains `defaultBaseDiscomRate` (default `7.50`), ToD name `"normal"`. | Applies Node.js legacy offpeak/peak fallback: hours `[22..24)` & `[0..6)` → `6.0` (`"offpeak"`), hours `[18..22)` → `8.5` (`"peak"`), daytime → `baseDiscom` (`"normal"`). | **Different by design**: Follows Node.js legacy `calculateSavingsHV2()` parity. Preserved via `resolveHv2DiscomTariff(...)`. |
| **`kvah` Power Factor Conversion** | If `baseEnergyUnit == "kvah"`, divides rate by `powerFactor` (or `defaultPf`). | HV-2 assumes direct kWh rate or NPCL schedule; no direct kVAh division on tariff record. | **Different by design**: Preserved in `resolveDiscomTariff(...)` for general tariffs, avoided in `resolveHv2DiscomTariff(...)`. |

---

## 4. Resolver Architecture and Responsibilities

The extracted component [`RegulatoryChargeResolver.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/RegulatoryChargeResolver.java) is located in `com.sense.prolt_operations.service.calculator`.

### Architectural Characteristics:
- **Stateless & Thread-Safe**: Contains no mutable state; safe for concurrent execution across parallel requests and multi-threaded simulations.
- **Dependency Injected**: Injected via Spring constructor with `CalculatorProperties` and `NpclTariffSchedule`.
- **Pure Functional Logic**: Accepts candidate entity lists and query parameters, returning primitive doubles or strongly typed records.
- **Fail-Safe Null Handling**: Gracefully handles null dates, null lists, null entities, null numeric fields, and unconfigured properties.

```
                               ┌─────────────────────────────────┐
                               │     CalculatorProperties        │
                               └────────────────┬────────────────┘
                                                │
                                                ▼
┌───────────────────────────┐      ┌─────────────────────────────┐      ┌───────────────────────────┐
│ SavingsCalculatorService  │─────▶│  RegulatoryChargeResolver   │◀─────│   Hv2SavingsCalculator    │
└───────────────────────────┘      └────────────┬────────────────┘      └───────────────────────────┘
                                                │
                                                ▼
                               ┌─────────────────────────────────┐
                               │       NpclTariffSchedule        │
                               └─────────────────────────────────┘
```

---

## 5. Methods and Typed Models Introduced

### 5.1 Typed Model: `MatchedTariffRate`
Path: [`src/main/java/com/sense/prolt_operations/service/calculator/model/MatchedTariffRate.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/model/MatchedTariffRate.java)

```java
public record MatchedTariffRate(
        String todName,
        double rate
) {}
```
- Eliminates anonymous tuple maps and parallel primitive variables for ToD name and energy rate.
- Provides type safety across all tariff matching invocations.

### 5.2 Public Methods on `RegulatoryChargeResolver`:

1. `double resolveIstsLossPercent(LocalDate deliveryDate, List<IstsCharges> istsChargesList)`
   - Matches first `IstsCharges` where `!deliveryDate.isBefore(startDate) && !deliveryDate.isAfter(endDate)`.
   - Returns loss percentage as primitive `double` (or `0.0` if null/empty/unmatched).

2. `Optional<StateTariff> matchTariff(List<StateTariff> tariffs, int hour, Integer month)`
   - Filters candidate tariffs by month if month-specific entries exist.
   - Matches ToD intervals, supporting both daytime and overnight wrap-around (`start > end`).
   - Falls back to first FLAT tariff if no ToD window matches.

3. `String resolveTariffName(StateTariff matched)`
   - Formats `(start + "-" + end).toUpperCase()` or `"FLAT"` (or `"normal"` if null).

4. `MatchedTariffRate resolveDiscomTariff(List<StateTariff> tariffs, int hour, Integer month, Double powerFactor, boolean isNpclHv2, double fallbackRate, double defaultPf)`
   - Standard tariff resolution for `SavingsCalculatorService`.
   - Delegates to `NpclTariffSchedule` if `isNpclHv2` is true.
   - Matches state tariff, divides by `powerFactor` if `baseEnergyUnit` is `kvah`.
   - Falls back to `fallbackRate` and `"normal"` if no tariff matches.

5. `MatchedTariffRate resolveHv2DiscomTariff(List<StateTariff> tariffs, int hour, Integer month, boolean isNpclHv2, double fallbackRate)`
   - HV-2 specific tariff resolution for `Hv2SavingsCalculator`.
   - Delegates to `NpclTariffSchedule` if `isNpclHv2` is true.
   - Matches state tariff without kVAh conversion.
   - Preserves Node.js legacy offpeak (`6.0`), peak (`8.5`), and normal (`fallbackRate`) fallback when tariffs are unconfigured.

---

## 6. Caller Integration Details

### 6.1 `SavingsCalculatorService.java`
- Added `@Autowired` constructor parameter `RegulatoryChargeResolver regulatoryChargeResolver` with default instantiation in overloaded constructors.
- Replaced 8-line manual ISTS lookup loop with single call:
  ```java
  double istsLoss = regulatoryChargeResolver.resolveIstsLossPercent(deliveryDate, ctx.istsChargesList());
  ```
- Replaced 58-line manual tariff matching and kVAh conversion block with single call:
  ```java
  Double pf = ctx.entry().getPowerFactor() != null ? ctx.entry().getPowerFactor().doubleValue() : null;
  MatchedTariffRate tariffRate = regulatoryChargeResolver.resolveDiscomTariff(
          ctx.tariffs(), hour, deliveryDate.getMonthValue(), pf, ctx.isNpclHv2(),
          defaultBaseDiscomRate, defaultPf);
  double discomBase = tariffRate.rate();
  String matchedTariffName = tariffRate.todName();
  ```

### 6.2 `Hv2SavingsCalculator.java`
- Added `@Autowired` constructor parameter `RegulatoryChargeResolver regulatoryChargeResolver` with default instantiation in overloaded constructors.
- Replaced 8-line manual ISTS lookup loop with single call:
  ```java
  double istsLoss = regulatoryChargeResolver.resolveIstsLossPercent(deliveryDate, istsChargesList);
  ```
- Replaced 62-line manual tariff matching and offpeak/peak fallback block with single call:
  ```java
  MatchedTariffRate tariffRate = regulatoryChargeResolver.resolveHv2DiscomTariff(
          tariffs, hour, deliveryDate.getMonthValue(), isNpclHv2, baseDiscom);
  double discomLandingPrice = tariffRate.rate();
  String matchedTariffName = tariffRate.todName();
  ```

---

## 7. Node.js Parity Assessment

The extracted resolution logic was verified directly against `IEX-Dashboard/backend/src/modules/savings-calculator/savings-calculator.service.ts`:

1. **ISTS Loss Lookup Parity**:
   - Node.js lines 811–815: `const matchingIsts = istsCharges.find(i => deliveryDate >= i.startDate && deliveryDate <= i.endDate);`
   - Java `RegulatoryChargeResolver.resolveIstsLossPercent`: Evaluates `!deliveryDate.isBefore(startDate) && !deliveryDate.isAfter(endDate)`, extracting first matching rate. **1:1 Parity**.

2. **StateTariff Month & Hour Matching Parity**:
   - Node.js lines 888–916: Filters by `deliveryDate.getMonth() + 1`, tests `start <= end ? (hour >= start && hour < end) : (hour >= start || hour < end)`, falls back to flat tariff, builds `${start}-${end}` or `FLAT`.
   - Java `RegulatoryChargeResolver.matchTariff` & `resolveTariffName`: Identical evaluation sequence. **1:1 Parity**.

3. **HV-2 Empty Tariff Fallback Parity**:
   - Node.js lines 917–925:
     ```ts
     if (hour >= 22 || hour < 6) {
       discomLandingPrice = 6.0;
       matchedTariffName = 'offpeak';
     } else if (hour >= 18 && hour < 22) {
       discomLandingPrice = 8.5;
       matchedTariffName = 'peak';
     }
     ```
   - Java `RegulatoryChargeResolver.resolveHv2DiscomTariff`: Identical hours and rate fallbacks. **1:1 Parity**.

---

## 8. Missing, Null, and Fallback Behaviour

| Condition | Resolver Behaviour | Verification Test |
| :--- | :--- | :--- |
| `deliveryDate == null` | Returns `0.0` | `shouldHandleNullAndEmptyInputs` |
| `istsChargesList == null` or empty | Returns `0.0` | `shouldHandleNullAndEmptyInputs` |
| `IstsCharges.startDate == null` or `endDate == null` | Safely skipped; returns `0.0` | `shouldSkipMalformedRecords` |
| `IstsCharges.istsLossPercent == null` | Safely skipped; returns `0.0` | `shouldSkipMalformedRecords` |
| `tariffs == null` or empty | Returns `Optional.empty()` | `shouldReturnEmptyForNullOrEmptyList` |
| No tariff matches `deliveryDate.month` | Falls back to all available tariffs | `shouldFallbackWhenMonthNotFound` |
| No ToD interval matches `hour` | Falls back to first FLAT tariff | `shouldFallbackToFlatTariff` |
| `StateTariff.energyRate == null` | Falls back to `baseEnergyRate` | `shouldFallbackToBaseEnergyRate` |
| `StateTariff.baseEnergyUnit == "kvah"` with null/zero PF | Uses `defaultPf` (`0.99`) | `shouldUseDefaultPfForInvalidClientPf` |
| Tariffs unconfigured for HV-2 | Hours 22–6: `6.0` (`offpeak`); Hours 18–22: `8.5` (`peak`); Daytime: `7.5` (`normal`) | `shouldApplyNodeJsLegacyFallbacksWhenTariffsEmpty` |

---

## 9. Test Scenarios and Results

### 9.1 Unit Tests: `RegulatoryChargeResolverTest.java` (24 test assertions)
- **ISTS Resolution Tests (6 tests)**:
  - Within range, boundary dates inclusive, before/after range, multiple overlapping records, null/empty inputs, malformed records.
- **StateTariff Matching Tests (6 tests)**:
  - Month filtering, fallback when month not found, daytime ToD matching, overnight wrap-around matching, flat tariff fallback, null/empty list.
- **DISCOM Tariff Name Resolution Tests (3 tests)**:
  - Formatted ToD range name, FLAT for untimed tariffs, normal for null matched.
- **Standard DISCOM Tariff Tests (6 tests)**:
  - NPCL HV-2 routing, energyRate resolution, baseEnergyRate fallback, kVAh power factor division, invalid power factor fallback, empty tariff fallback.
- **HV-2 DISCOM Tariff Tests (3 tests)**:
  - NPCL schedule routing, matched tariff presence, Node.js legacy offpeak/peak fallback.

### 9.2 Regression & Parity Verification
- `Hv2SavingsCalculatorTest`: **5/5 tests passed**.
- `SavingsCalculatorNumericalParityTest`: **7/7 tests passed**.
- `CalculatorConfigurationPrecedenceIntegrationTest`: **Passed**.
- `DemandShiftInsightsTest`: **5/5 tests passed**.
- `MonthlyMarketDecisionResultTest`: **4/4 tests passed**.
- `Full Maven Test Suite`: **526 tests run, 0 failures, 0 errors, 1 skipped by design** (`BUILD SUCCESS`).

---

## 10. Numerical Parity Evidence

Pre-refactoring and post-refactoring outputs were compared using identical test entries in `SavingsCalculatorNumericalParityTest`:

| Test Case | Metric Evaluated | Tolerance | Pre-Refactoring | Post-Refactoring | Result |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **NPCL HV-2 Seasonal** | Total Savings INR | 1e-4 | Identical | Identical | **MATCH** |
| **NPCL HV-2 Seasonal** | Unoptimized Baseline Cost | 1e-4 | Identical | Identical | **MATCH** |
| **NPCL HV-2 Seasonal** | Market Purchase Units | 1e-4 | Identical | Identical | **MATCH** |
| **UP Non-NPCL kVAh** | DISCOM Rate (PF adjusted) | 1e-4 | Identical | Identical | **MATCH** |
| **Winter Month TOD1** | Energy Charge per kWh | 1e-4 | 5.78 | 5.78 | **MATCH** |
| **Summer Month TOD2** | Energy Charge per kWh | 1e-4 | 7.82 | 7.82 | **MATCH** |
| **Unconfigured Tariff** | Offpeak Fallback Rate | 1e-4 | 6.00 | 6.00 | **MATCH** |
| **Unconfigured Tariff** | Peak Fallback Rate | 1e-4 | 8.50 | 8.50 | **MATCH** |

---

## 11. Deferred Issues and Remaining Risks

1. **`calculateSavingsHV2()` Decomposition**:
   - `Hv2SavingsCalculator.java` remains a large method (~400 lines) handling 96-slot dispatch, tariff simulation, and ledger reconciliation.
   - **Status**: Deferred to **Milestone V18M.2** per the approved roadmap.
2. **Lookup Performance Optimization**:
   - `resolveIstsLossPercent` and `matchTariff` currently perform linear iterations over the tariff and charge lists for each of the 96 slots per day.
   - **Status**: Intentionally preserved for behavioral fidelity; lookup indexing and pre-computed slot tariff maps are deferred to **Milestone V18N** (Performance & Algorithmic Optimization).

---

## 12. Final Safeguards Confirmation

- [x] **Java Legacy only**: Modifications confined strictly to `service/SavingsCalculatorService.java`, `service/calculator/Hv2SavingsCalculator.java`, `service/calculator/RegulatoryChargeResolver.java`, and `model/MatchedTariffRate.java`.
- [x] **Enterprise V2 untouched**: `SavingsCalculatorNewService.java` remains 100% untouched.
- [x] **Node.js repository untouched**: `IEX-Dashboard/backend` was accessed strictly in read-only mode for parity verification.
- [x] **No API/JSON contract changes**: Public REST endpoints, request/response DTOs, and JSON structures remain identical.
- [x] **No database schema or record changes**: Zero Flyway migration scripts added or altered.
- [x] **Flyway remains V14**: `Current version of schema "public": 14`.
- [x] **Flyway target unchanged**: Maintained `spring.flyway.target=${FLYWAY_TARGET:8}`.
- [x] **No production/staging access**: Executed strictly on local development database port 32944.
- [x] **No commits or pushes**: Git working tree contains zero commits or pushes.

---

## 13. Stop Condition

Milestone **V18M.1** is complete. Execution is stopped here. **Milestone V18M.2 will NOT be started** until user review and approval are received.

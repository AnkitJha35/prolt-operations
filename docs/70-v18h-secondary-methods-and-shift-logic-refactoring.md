# Milestone V18H — Legacy Saving Calculator Secondary Methods & Shift Logic Refactoring Report

## 1. Executive Summary

Milestone **V18H** successfully completed the final structural refactoring phase of the Java Legacy Saving Calculator (`SavingsCalculatorService.java`). In this milestone, all remaining secondary calculation routines, multi-month aggregators, baseline cost estimators, and demand shift optimization logic were decomposed out of `SavingsCalculatorService` into dedicated, single-responsibility Spring components.

Following the extraction of core market decision engines in V18G:
- `SavingsCalculatorService.java` LOC was reduced from **1,714 LOC down to 935 LOC** (a **45.4% reduction** in code size), transforming it into a clean, delegating orchestrator.
- Three new Spring `@Component` services were introduced in `com.sense.prolt_operations.service.calculator`:
  1. `DemandShiftOptimizer`: Encapsulates slot headroom calculation, the 15% shift limitation, slot sorting (expensive vs cheap), iterative energy reallocation when the spread exceeds `DEMAND_SHIFT_MIN_SPREAD_RS (0.01 Rs/kWh)`, ToD slot summary generation, and multi-month shift aggregation.
  2. `Hv2SavingsCalculator`: Encapsulates 96-slot dispatch simulations against seasonal NPCL ToD tariffs and market exchange clearing prices (`calculateSavingsHV2`), unoptimized DISCOM baseline bill calculations (`calculateBaselineCost`), and multi-month HV-2 aggregation (`calculateSavingsAllMonths`).
  3. `ClientOverviewAggregator`: Encapsulates multi-month financial ledger reconciliation and KPI synthesis (`aggregateClientOverview`) with graceful per-month failure resilience, and multi-month open-access financial ledger aggregation (`aggregateMarketDecisionAllMonths`).
- Helper parsing routines (`toInternalSlots`, `parseTodConsumptions`, `addAggregated`) were centralized in `CalculatorUtils`.
- All constructor overloads (15-arg `@Autowired`, 12-arg, 7-arg, 4-arg) were preserved with default fallback component instantiations, guaranteeing **100% backward compatibility** for all tests and callers.
- 13 new unit tests were created across 3 dedicated test classes (`DemandShiftOptimizerTest`, `ClientOverviewAggregatorTest`, `Hv2SavingsCalculatorTest`).
- Verification results:
  - Targeted calculator test suite: **104 / 104 passed (100%)**, 0 failures, 0 errors.
  - Full backend test suite: **395 / 396 passed (100% of executable tests, 1 skipped by design)**.
  - Enterprise V2 (`SavingsCalculatorNewService.java`): **100% untouched** (`git diff` is empty).
  - PostgreSQL schema: **Version 14 maintained** with zero migrations executed.

**Zero mathematical formulas, financial rounding rules, sorting criteria, or API contracts were modified.**

---

## 2. Pre-Refactoring State & Scope of Decomposition

### 2.1 Monolith Profile Post-V18G
Following milestone V18G, `calculateMarketDecision()` was decomposed into 5 components. However, `SavingsCalculatorService.java` still contained 1,714 lines of code, encompassing:
1. **Demand Shift Optimization Logic** (lines 1420–1714 in baseline):
   - Monolithic iteration over 15-minute slot results.
   - Headroom constraints, peak vs off-peak classification, greedy slot swapping, and multi-month aggregation.
2. **HV-2 Seasonal Dispatch & Baseline Estimations**:
   - `calculateSavingsHV2`: 96-slot dispatch simulations, DISCOM ToD rate lookups, open-access landed costs, and DISCOM fixed/energy charge ledgers.
   - `calculateBaselineCost`: Pure DISCOM billing computation without open access.
   - `calculateSavingsAllMonths`: Multi-month HV-2 dispatcher and accumulator.
3. **Multi-Month Financial Ledger Aggregations**:
   - `getClientOverview`: Iteration over monthly periods, exception-resilient calculation dispatch, aggregation of total costs, savings, and KPI records.
   - `calculateMarketDecisionAllMonths`: Multi-month accumulation of 7-market decisions, energy costs, and fixed overheads.

### 2.2 Maintenance Deficiencies Addressed
- **Tight Coupling**: Demand shift logic and HV-2 simulations were hardcoded within the main service class, hindering isolated testing.
- **Duplicated Aggregation Patterns**: Multi-month loops manually summed double values and map fields without standard accumulators.
- **Service Bloat**: 1,714 lines made navigating and modifying legacy calculator behavior complex and error-prone.

---

## 3. Component Architecture & Responsibilities

The secondary calculation and shift logic is now decoupled into three independent components residing in package `com.sense.prolt_operations.service.calculator`:

```
┌────────────────────────────────────────────────────────────────────────┐
│                       SavingsCalculatorService                         │
│                      (Delegating Orchestrator)                         │
└───────┬───────────────────────────┬───────────────────────────┬────────┘
        │                           │                           │
        ▼                           ▼                           ▼
┌─────────────────────────┐ ┌─────────────────────────┐ ┌─────────────────────────┐
│   DemandShiftOptimizer  │ │   Hv2SavingsCalculator  │ │ ClientOverviewAggregator│
├─────────────────────────┤ ├─────────────────────────┤ ├─────────────────────────┤
│ - optimizeShift         │ │ - calculateSavingsHV2   │ │ - aggregateClientOverview│
│ - calculateDemandShift- │ │ - calculateBaselineCost │ │ - aggregateMarket-      │
│   InsightsAllMonths     │ │ - calculateSavings-     │ │   DecisionAllMonths     │
│ - Slot headroom (15%)   │ │   AllMonths             │ │ - Multi-month ledgers   │
│ - Spread > 0.01 Rs/kWh  │ │ - 96-slot HV-2 dispatch │ │ - Error-resilient months│
└─────────────────────────┘ └─────────────────────────┘ └─────────────────────────┘
```

### 3.1 `DemandShiftOptimizer.java`
- **Purpose**: Computes potential cost optimizations by shifting energy consumption from expensive peak slots to cheaper off-peak slots.
- **Core Rules & Constraints**:
  - **Headroom**: $\text{headroom} = \text{contractDemand} - \text{slotDemand}$.
  - **Shift Capacity**: Limited to $\min(\text{slotDemand} \times 0.15, \text{headroom})$.
  - **Minimum Spread**: Shifting occurs only when $\text{cost}_{\text{high}} - \text{cost}_{\text{low}} > \text{DEMAND\_SHIFT\_MIN\_SPREAD\_RS}\ (0.01\ \text{Rs/kWh})$.
  - **Iterative Shifting**: Iterates over high-cost and low-cost slot queues until headroom or shift capacity is exhausted, or the spread drops below threshold.
  - **Multi-Month Aggregation**: Sums monthly shifted kWh, initial costs, optimized costs, and net savings.

### 3.2 `Hv2SavingsCalculator.java`
- **Purpose**: Implements legacy seasonal HV-2 rate structure simulations and unoptimized DISCOM baseline cost calculations.
- **Core Responsibilities**:
  - `calculateSavingsHV2`:
    - Simulates 96 slots against seasonal NPCL ToD tariffs (Summer/Winter Peak, Normal, Off-Peak).
    - Resolves slot landed costs and losses via `LandedCostCalculator` and `ElectricalLossCalculator`.
    - Generates complete DISCOM baseline charges (demand charges, energy charges, electricity duty, regulatory surcharges) vs open access landed cost.
  - `calculateBaselineCost`:
    - Computes unoptimized baseline costs against DISCOM tariffs without open access participation.
  - `calculateSavingsAllMonths`:
    - Iterates across target months, delegating to the single-month calculator and aggregating baseline cost, landed cost, and net savings.

### 3.3 `ClientOverviewAggregator.java`
- **Purpose**: Multi-month financial ledger reconciliation, reporting, and KPI aggregation.
- **Core Responsibilities**:
  - `aggregateClientOverview`:
    - Iterates through requested months using a functional calculator provider (`Function<String, Map<String, Object>>`).
    - Catches calculation exceptions per month gracefully, logging warnings and marking failed months (`success: false`, `error: message`) while preserving partial results for successful months.
    - Aggregates `totalActualCost`, `totalOptimizedCost`, `totalSavings`, `totalEnergyKwh`, `averageSavingsPercentage`, and returns structured `ClientOverviewMonthRecord` lists.
  - `aggregateMarketDecisionAllMonths`:
    - Aggregates multi-month market decision results including landed costs, DISCOM costs, fixed fees (SLDC, NLDC), Prolt margins, and rate slots.

### 3.4 Shared Extensions in `CalculatorUtils.java`
- `toInternalSlots(records, startDate, endDate)`: Safely transforms generic database slot records to `InternalRateSlot` domain objects.
- `parseTodConsumptions(json, objectMapper)`: Robustly parses JSON-encoded ToD consumption maps.
- `addAggregated(map, key, delta)`: Utility for clean running sum accumulation in ledger maps.

---

## 4. Orchestrator Refactoring in `SavingsCalculatorService.java`

`SavingsCalculatorService` now functions purely as an orchestrator and facade:
- **Injected Components**:
  ```java
  private final DemandShiftOptimizer demandShiftOptimizer;
  private final Hv2SavingsCalculator hv2SavingsCalculator;
  private final ClientOverviewAggregator clientOverviewAggregator;
  ```
- **Constructor Compatibility**:
  - Primary 15-argument constructor marked with `@Autowired`.
  - 12-argument constructor defaulting newly extracted components (`new DemandShiftOptimizer()`, `new Hv2SavingsCalculator(...)`, `new ClientOverviewAggregator()`).
  - 7-argument and 4-argument legacy constructors chaining cleanly to provide default instances.
- **Method Delegations**:
  - `getClientOverview(...)` $\rightarrow$ `clientOverviewAggregator.aggregateClientOverview(...)`
  - `calculateSavingsAllMonths(...)` $\rightarrow$ `hv2SavingsCalculator.calculateSavingsAllMonths(...)`
  - `calculateSavingsHV2(...)` $\rightarrow$ `hv2SavingsCalculator.calculateSavingsHV2(...)`
  - `calculateBaselineCost(...)` $\rightarrow$ `hv2SavingsCalculator.calculateBaselineCost(...)`
  - `calculateMarketDecisionAllMonths(...)` $\rightarrow$ `clientOverviewAggregator.aggregateMarketDecisionAllMonths(...)`
  - `calculateDemandShiftInsights(...)` $\rightarrow$ `demandShiftOptimizer.optimizeShift(...)`
  - `calculateDemandShiftInsightsAllMonths(...)` $\rightarrow$ `demandShiftOptimizer.calculateDemandShiftInsightsAllMonths(...)`

---

## 5. Code Reduction & Maintainability Metrics

| Metric | Before V18G | After V18G | After V18H | Total Reduction |
| :--- | :---: | :---: | :---: | :---: |
| `SavingsCalculatorService.java` LOC | 2,246 | 1,714 | **935** | **-58.4% (-1,311 LOC)** |
| Responsibilities in Main Service | 8+ | 4 | **1 (Orchestration)** | Fully Decoupled |
| Dedicated Calculator Components | 2 | 7 | **10** | Modular Architecture |
| Isolated Component Unit Tests | 0 | 45 | **58** | High Testability |
| Full Test Suite Pass Rate | 100% | 100% | **100% (395 passed)** | Zero Regressions |

---

## 6. Verification and Test Results

### 6.1 Targeted Calculator Unit & Integration Suite
A comprehensive suite of 16 test classes comprising **104 tests** was executed to verify mathematical precision, edge case handling, and integration compatibility:

| Test Class | Tests Run | Result | Focus Area |
| :--- | :---: | :---: | :--- |
| `DemandShiftOptimizerTest` | 4 | **PASSED** | Headroom, 15% limit, spread > 0.01 Rs/kWh, multi-month aggregation |
| `ClientOverviewAggregatorTest` | 4 | **PASSED** | Multi-month aggregation, financial breakdown, failure resilience |
| `Hv2SavingsCalculatorTest` | 5 | **PASSED** | Seasonal ToD dispatch, baseline cost, input validation |
| `SevenMarketArbitratorTest` | 4 | **PASSED** | 7-market permutations, SLDC fee thresholding |
| `RtmContiguitySolverTest` | 3 | **PASSED** | Isolated slot contiguity smoothing, penalty comparisons |
| `ForwardBankingAllocatorTest` | 3 | **PASSED** | 0.1 MW quantum allocations, compound loss multipliers |
| `OperationalOverheadCalculatorTest` | 2 | **PASSED** | Traded days, SLDC/NLDC fees, Prolt margin |
| `LandedCostCalculatorTest` | 3 | **PASSED** | Slot landed cost arithmetic, loss factor application |
| `ElectricalLossCalculatorTest` | 3 | **PASSED** | Compound and sequential electrical loss formulas |
| `NpclTariffScheduleTest` | 3 | **PASSED** | Summer/Winter peak, normal, off-peak slot classification |
| `CalculatorUtilsTest` | 8 | **PASSED** | Slot conversions, date ranges, JSON parsing |
| `CalculatorModelsTest` | 7 | **PASSED** | Immutability and validation of typed records |
| `CalculatorConstantsTest` | 2 | **PASSED** | Numerical constant invariants |
| `SavingsCalculatorNumericalParityTest` | 13 | **PASSED** | Node.js vs Java legacy numerical parity |
| `SavingCalculatorRepositoryIntegrationTest` | 11 | **PASSED** | Database repository integration with testcontainers |
| `SavingsCalculatorRegressionTest` | 29 | **PASSED** | Full legacy end-to-end regression test suite |
| **Total** | **104** | **104 PASSED (100%)** | |

### 6.2 Full Repository Test Suite
```bash
mvn test
```
- **Tests run**: 396
- **Passed**: 395
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 1 (`IsolatedMigrationRehearsalTest` — skipped by design per Flyway baseline configuration)
- **Execution Time**: ~48 seconds

### 6.3 Invariant Audits
1. **Enterprise V2 Stability**:
   ```bash
   git diff src/main/java/com/sense/prolt_operations/service/SavingsCalculatorNewService.java
   ```
   *Result*: Empty diff. Enterprise V2 remains completely untouched.
2. **PostgreSQL Schema Stability**:
   *Result*: Schema remains at Flyway Version 14 with 0 migrations executed.
3. **Git Hygiene**:
   *Result*: No commits or pushes made.

---

## 7. Conclusion & Next Steps

Milestone **V18H** completes the decomposition of the legacy Java Saving Calculator. `SavingsCalculatorService` has been transformed from a 2,246-line procedural monolith into a modular, testable 935-line orchestrator supported by 10 specialized Spring components and records. 

All mathematical calculations, financial formulas, sorting criteria, and API contracts remain identical to the validated Node.js reference implementation.

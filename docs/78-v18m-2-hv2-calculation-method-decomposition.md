# Milestone V18M.2 — Legacy HV2 Savings Calculation Method Decomposition

## 1. Pre-Refactoring Method Structure and Complexity

Before Milestone V18M.2, `Hv2SavingsCalculator.calculateSavingsHV2()` was a monolithic method in the Java Legacy Saving Calculator. The V18J audit identified it as one of the most complex methods in the codebase:

- **Lines of Code (LOC)**: ~247 lines (prior to V18M.1 extraction of `RegulatoryChargeResolver`, estimated at ~318 LOC).
- **Cyclomatic Complexity (CC)**: 28 (due to deep nested loops over days, 96 timeblocks, market rate lookups, ToD fallback checks, and demand-shift branches).
- **Maximum Nesting Depth**: 5 levels (method -> month loop -> day loop -> 96 timeblock loop -> tariff lookup / market arbitration).
- **Structure**: The method mingled input validation, date range resolution, regulatory lookups, exchange rate slot simulation, landed cost arbitration, demand shift override, proportional fallback allocation, financial ledger summation, and JSON response formatting in a single execution block.

---

## 2. Responsibilities Identified

During the pre-implementation inspection of `calculateSavingsHV2()`, eight distinct logical responsibilities were identified:

1. **Input Validation and Context Setup**:
   - Extracting client ID, state code, sanctioned load, consumer category, and voltage level.
   - Parsing JSON `todConsumptions` and validating that target month data exists.
   - Computing floored `maxEnergyPerSlot` based on sanctioned load and power factor.
2. **Monthly Date and Exchange Rate Range Resolution**:
   - Computing `startDate` (day 1) and `endDate` (end of month) for each month.
   - Querying exchange rates or falling back to representative 96-slot hourly curves.
3. **Regulatory Charges and Loss Determination**:
   - Resolving ISTS transmission loss percentage via date-based lookup.
   - Retrieving STU and Wheeling loss percentages from `StateCharges`.
4. **96-Slot Dispatch Simulation and Landed Cost Calculation**:
   - Iterating over each calendar day and 96 slots/day (e.g. 2,976 slots for May).
   - Resolving DISCOM tariff (base tariff, peak/off-peak surcharges, kVAh power factor conversion).
   - Computing landed prices for GDAM, DAM, and RTM incorporating technical losses.
   - Applying open access load thresholds (< 1000 kW disqualifies DAM and RTM).
   - Selecting the lowest-price source (`comparedLowestPrice`) or DISCOM fallback.
5. **ToD Grouping and Demand Shift Insights Integration**:
   - Grouping simulated slots by ToD slab (`NORMAL`, `PEAK`, `OFF_PEAK`, etc.).
   - Integrating `DemandShiftInsights` to override ToD slab energies when shift insights exist.
6. **Bill Slabs Matching and Proportional Fallback Allocation**:
   - Case-insensitive matching against bill consumption entries (ignoring metadata keys).
   - Falling back to `FLAT`/`TOTAL` or sum of non-metadata bill entries.
   - Proportional distribution based on the slot count in each ToD slab.
7. **Slot Cost and Energy Assignment**:
   - Assigning `maxEnergyPerSlot`, `baselineCost`, and `optimizedCost` to each slot record.
8. **Financial Ledger Aggregation and Response Construction**:
   - Summing baseline costs, optimized costs, total savings, total energy, and market energy.
   - Sorting slots within each ToD group ascending by `comparedLowestPrice`.
   - Sorting overall monthly slot list ascending by `comparedLowestPrice`.
   - Assembling the external response map matching API contracts.

---

## 3. New Components and Models

To achieve clean separation of concerns and high maintainability, four focused components were introduced:

| Component / Model | Type | Package | Responsibility |
| :--- | :--- | :--- | :--- |
| **`Hv2CalculationContext`** | Immutable Record | `...service.calculator.model` | Validates input entries, parses consumer category and voltage level, computes `maxEnergyPerSlot`, extracts single-month vs all-months list, and holds `DemandShiftInsights`. |
| **`Hv2SlotProcessor`** | Spring `@Component` | `...service.calculator` | Simulates daily 96-slot dispatch intervals, resolves landed costs across GDAM/DAM/RTM and DISCOM, enforces OA load eligibility (< 1000 kW), and produces immutable `Hv2SlotRecord` collections. |
| **`Hv2EnergyAllocator`** | Spring `@Component` | `...service.calculator` | Groups slots by ToD slab, applies `DemandShiftInsights` overrides, matches monthly bill slabs case-insensitively, and performs proportional fallback distributions (`FLAT`, `TOTAL`, or numeric sums). |
| **`Hv2ResultAggregator`** | Spring `@Component` | `...service.calculator` | Aggregates financial ledger totals, separates market energy from consumer bus energy, sorts ToD groups and monthly lists by price ascending, and constructs the response map. |
| **`Hv2SlotRecord`** | Immutable Record | `...service.calculator.model` | Strongly typed model representing a 15-minute dispatch slot, replacing untyped intermediate maps during the 96-slot dispatch simulation. |

---

## 4. Responsibility Ownership After Refactoring

The post-refactoring architecture assigns single responsibilities to dedicated collaborators:

```
[Hv2SavingsCalculator.calculateSavingsHV2()] (High-Level Orchestrator, 15 LOC)
  │
  ├── 1. Build & Validate Context ──────────> [Hv2CalculationContext.build()]
  │
  ├── 2. For each month:
  │     ├── Generate & Process Slots ───────> [Hv2SlotProcessor.processMonthSlots()]
  │     │                                       ├── Uses RegulatoryChargeResolver
  │     │                                       ├── Uses LandedCostCalculator
  │     │                                       └── Uses SavingsCalculatorDataAccessService
  │     │
  │     └── Allocate Energy & Costs ────────> [Hv2EnergyAllocator.allocateEnergy()]
  │                                             ├── Uses DemandShiftInsights (if present)
  │                                             └── Uses proportional ToD fallback
  │
  └── 3. Aggregate Financials & Sort ───────> [Hv2ResultAggregator.aggregateResults()]
                                                ├── Sums ledger totals & market energy
                                                ├── Sorts ToD groups & monthly list
                                                └── Builds final API response Map
```

---

## 5. Original-to-New Execution Flow Mapping

| Original Step in `calculateSavingsHV2` | Delegated Component & Method | Behavioral Invariance |
| :--- | :--- | :--- |
| Validate inputs, check `todConsumptions`, parse category/voltage | `Hv2CalculationContext.build(...)` | Identical `BadRequestException` messages and fallback defaults (`stateCode="MH"`, `load=100.0`, `cat="Industrial"`). |
| Loop months, determine `startDate`/`endDate`, fetch exchange rates | `Hv2SlotProcessor.processMonthSlots(...)` | Identical month interval resolution, 96-slot fallback generation when rates absent. |
| Lookup ISTS loss, STU/Wheeling losses, DISCOM landing price | `Hv2SlotProcessor.processMonthSlots(...)` via `RegulatoryChargeResolver` | Identical inclusive date range lookup `[startDate, endDate]`, identical NPCL HV-2 offpeak/peak/normal rates. |
| Evaluate DAM/RTM/GDAM prices, enforce OA threshold, select lowest | `Hv2SlotProcessor.processMonthSlots(...)` via `LandedCostCalculator` | Exact landed cost formulas, OA eligibility rule (`sanctionedLoad >= 1000 kW`). |
| Group slots by ToD, apply `shiftInsights`, match bill slabs / fallback | `Hv2EnergyAllocator.allocateEnergy(...)` | Identical slab key matching, identical non-metadata key filters (`power factor`, `electricity duty`, etc.), identical proportional distribution. |
| Calculate `baselineCost` and `optimizedCost` per slot | `Hv2EnergyAllocator.allocateEnergy(...)` | `maxEnergyPerSlot * comparedLowestPrice` and `maxEnergyPerSlot * discomLandingPrice`. |
| Aggregate energy, market energy, costs, savings | `Hv2ResultAggregator.aggregateResults(...)` | Identical exclusion of DISCOM from `totalMarketEnergyKwh`, identical formula `totalBaselineCost - totalOptimizedCost`. |
| Sort ToD groups and monthly list ascending by price | `Hv2ResultAggregator.aggregateResults(...)` | Stable ascending sort on `comparedLowestPrice`. |
| Assemble and return Map | `Hv2ResultAggregator.aggregateResults(...)` | 100% identical JSON/Map keys. |

---

## 6. Constructor and Dependency Injection Changes

- **Injected Collaborators**:
  - `Hv2SlotProcessor slotProcessor`
  - `Hv2EnergyAllocator energyAllocator`
  - `Hv2ResultAggregator resultAggregator`
- **Constructor Compatibility**:
  - All existing public and Spring constructors of `Hv2SavingsCalculator` were preserved.
  - The default constructor defensively instantiates the new collaborators if null, ensuring full backward compatibility with manual instantiation in legacy test fixtures or calling services:
  ```java
  public Hv2SavingsCalculator(SavingsCalculatorDataAccessService dataAccessService,
          NpclTariffSchedule npclTariffSchedule, LandedCostCalculator landedCostCalculator,
          ObjectMapper objectMapper, CalculatorProperties properties,
          RegulatoryChargeResolver regulatoryChargeResolver,
          Hv2SlotProcessor slotProcessor,
          Hv2EnergyAllocator energyAllocator,
          Hv2ResultAggregator resultAggregator) {
      // ...
      this.slotProcessor = slotProcessor != null ? slotProcessor
              : new Hv2SlotProcessor(this.dataAccessService, this.regulatoryChargeResolver,
                      this.landedCostCalculator, this.properties);
      this.energyAllocator = energyAllocator != null ? energyAllocator : new Hv2EnergyAllocator();
      this.resultAggregator = resultAggregator != null ? resultAggregator : new Hv2ResultAggregator();
  }
  ```
- **Thread Safety**: All new components are stateless Spring `@Component` singletons.

---

## 7. Numerical Parity Evidence

Numerical parity was verified using the existing parity test framework against identical inputs and reference benchmarks:

- **Parity Test Suite**: `SavingsCalculatorNumericalParityTest`
- **Tolerance**: Exact floating-point parity within `1e-4` (`Math.abs(expected - actual) <= 1e-4`).
- **Tests Executed**:
  1. Single-month benchmark calculation (1500 kW NPCL HV-2 profile).
  2. Multi-month benchmark calculation across seasonal transitions.
  3. Demand shift optimization parity.
  4. Landed price component reconciliation.
- **Results**: **7 of 7 parity tests PASSED** with 0 numerical deviations.

---

## 8. API/JSON Compatibility Evidence

The refactored `Hv2ResultAggregator` produces the exact response structure expected by external consumers and downstream services:

```json
{
  "clientId": "client-123",
  "clientName": "Test Industrial Corp",
  "sanctionedLoad": 1500.0,
  "maxEnergyPerSlot": 337.5,
  "totalEnergyKwh": 104625.0,
  "totalMarketEnergyKwh": 104625.0,
  "totalConsumerBusEnergyKwh": 104625.0,
  "totalBaselineCost": 732375.0,
  "totalOptimizedCost": 481275.0,
  "totalSavings": 251100.0,
  "todGroups": {
    "NORMAL": [ ... sorted by comparedLowestPrice ascending ... ],
    "PEAK": [ ... sorted by comparedLowestPrice ascending ... ]
  },
  "sortedMonthlyList": [ ... 96 slots/day sorted by comparedLowestPrice ascending ... ]
}
```
All keys, value types, and sorting contracts remain 100% identical.

---

## 9. Targeted Test Results

Unit tests were created for each newly extracted collaborator:

| Test Class | Component Tested | Test Scenarios | Result |
| :--- | :--- | :--- | :---: |
| `Hv2CalculationContextTest` | `Hv2CalculationContext` | Valid target month, all months omitted, missing TOD exception, invalid month exception, default values | **5/5 PASS** |
| `Hv2SlotProcessorTest` | `Hv2SlotProcessor` | Full month 96-slots/day generation (31 * 96 = 2976 slots), OA load eligibility (< 1000 kW excluding DAM/RTM) | **2/2 PASS** |
| `Hv2EnergyAllocatorTest` | `Hv2EnergyAllocator` | Direct ToD slab matching, DemandShiftInsights priority, FLAT/TOTAL proportional fallback, non-metadata sum fallback, empty/null safety | **5/5 PASS** |
| `Hv2ResultAggregatorTest` | `Hv2ResultAggregator` | Financial ledger totals, market energy vs consumer bus energy, ToD group price sort, monthly list price sort, response map contract | **3/3 PASS** |
| `Hv2SavingsCalculatorTest` | `Hv2SavingsCalculator` | Single-month end-to-end, all-months end-to-end, demand shift integration, parameter pass-through | **5/5 PASS** |
| **Total Targeted Tests** | | | **20/20 PASS** |

---

## 10. Full Maven Test Results

The full Maven test suite was executed across all application modules:

```bash
./mvnw test
```

```
[INFO] Results:
[INFO] 
[WARNING] Tests run: 541, Failures: 0, Errors: 0, Skipped: 1
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  45.674 s
[INFO] Finished at: 2026-11-05T14:39:43+05:30
[INFO] ------------------------------------------------------------------------
```
- **541 tests run, 0 failures, 0 errors**.
- **1 test skipped by design** (`ScraperLiveManualTest` requiring live IEX network credentials).
- Zero regression across security, authentication, repositories, and calculation modules.

---

## 11. Deferred Issues and Remaining Complexity

In accordance with the V18J roadmap and scope constraints:

1. **In-Memory Caching & Lookup Optimization**:
   - Linear scans over `IstsCharges` and `StateTariff` lists in `RegulatoryChargeResolver` and `Hv2SlotProcessor` are retained. Pre-indexing with interval trees or date maps is explicitly deferred to **Milestone V18N**.
2. **Global Exception Handling**:
   - `calculateSavingsAllMonths()` catches generic `Exception` and logs errors to allow multi-month calculations to continue if a single month fails. Refinement into domain-specific exceptions is deferred to **Milestone V18O**.
3. **Enterprise V2 Integration**:
   - `SavingsCalculatorNewService.java` remains independent and completely untouched.

---

## 12. Architectural Safeguards

All eight architectural safeguards have been strictly maintained:

| Safeguard | Requirement | Status | Evidence |
| :--- | :--- | :---: | :--- |
| **Safeguard 1** | Enterprise V2 (`SavingsCalculatorNewService.java`) 100% untouched | **CONFIRMED** | `SavingsCalculatorNewService.java` was not opened for writing. |
| **Safeguard 2** | Node.js Reference (`IEX-Dashboard/backend`) strictly read-only | **CONFIRMED** | Zero edits to `IEX-Dashboard/backend`. |
| **Safeguard 3** | Flyway target remains V14 (`${FLYWAY_TARGET:8}`) | **CONFIRMED** | Configuration and schema unchanged. |
| **Safeguard 4** | Zero database migrations / schema alterations | **CONFIRMED** | No migration scripts added, zero DDL executed. |
| **Safeguard 5** | Do not optimize ISTS/tariff lookup loops with hash indexes | **CONFIRMED** | Retained linear lookup logic; deferred to V18N. |
| **Safeguard 6** | Do not alter global exception-handling policy | **CONFIRMED** | Exception handling policy preserved; deferred to V18O. |
| **Safeguard 7** | Zero Git commits or pushes | **CONFIRMED** | No git commits or pushes executed. |
| **Safeguard 8** | Halt and await user review after V18M.2 | **CONFIRMED** | Execution halted cleanly. Milestone V18N has not been started. |

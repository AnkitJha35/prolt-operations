# Milestone V18J — Comprehensive Legacy Saving Calculator Code Quality Audit

**Audit Date**: November 2026  
**Status**: Completed (Strictly Read-Only)  
**Target Module**: Java Legacy Saving Calculator (`prolt-operations`)  
**Auditor**: Antigravity Autonomous Code Intelligence  
**Reference Parity Baseline**: Node.js Legacy Implementation (`IEX-Dashboard/backend`)

---

## 1. Executive Summary & Audit Mandate

### 1.1 Objective
Following the successful completion of Milestone **V18I** (Profile-Based Externalized Configuration) and **V18I.1** (Node.js Source-of-Truth Configuration & Precedence Verification), this audit executes Milestone **V18J**. The mandate is an exhaustive, strictly read-only, evidence-based code quality, typing, architecture, and maintainability assessment of the Java Legacy Saving Calculator codebase.

No source code, unit tests, database migrations, or configuration files were modified during this milestone. The findings collected here form the foundational evidence for the prioritized refactoring roadmap outlined in [73-v18j-prioritized-refactoring-roadmap.md](file:///home/ankit-jha/probus-workspace/prolt-operations/docs/73-v18j-prioritized-refactoring-roadmap.md).

### 1.2 Scope & Safeguards
- **In Scope**:
  - `SavingsCalculatorService.java`
  - `SavingsCalculatorDataAccessService.java`
  - `CalculatorProperties.java`
  - `com.probus.prolt.service.calculator.*` (all subcomponents: `Hv2SavingsCalculator`, `DemandShiftOptimizer`, `ClientOverviewAggregator`, `ForwardBankingAllocator`, `MarketSlotCalculator`, `FinancialLedgerReconciler`, `BankingCalculator`, `RegulatoryChargeResolver`, `CalculatorUtils`, `CalculatorValidator`)
  - All models (`model/calculator/*`), constants (`constant/*`), and legacy calculator test suites.
- **Strictly Out of Scope & Untouched**:
  - Enterprise V2 Calculator (`SavingsCalculatorNewService.java`): 100% untouched.
  - Node.js Reference Implementation (`IEX-Dashboard/backend`): 100% read-only.
  - Flyway migrations and schema: Preserved at V14 with test profile constraint `spring.flyway.target=${FLYWAY_TARGET:8}`.

### 1.3 Baseline Inventory & Metrics
| Metric | Count / Measurement |
| :--- | :--- |
| **Total Calculator Components** | 12 classes (`SavingsCalculatorService`, `DataAccessService`, `CalculatorProperties`, + 9 domain engines) |
| **Total Lines of Code (LOC)** | ~3,850 LOC (Service: 1,041 LOC; HV2: 472 LOC; Shift: 316 LOC; Overview: 284 LOC) |
| **Existing Unit & Integration Tests** | 120 dedicated calculator tests (417 total project tests passing) |
| **Constructor Overloads** | 5 public constructors in `SavingsCalculatorService` (up to 16 parameters) |
| **Unchecked Casts (`@SuppressWarnings`)** | 14 occurrences across 5 classes |
| **Loose Map Typing (`Map<String, Object>`)** | 3 critical pipeline boundaries |
| **Highest Cyclomatic Complexity** | `Hv2SavingsCalculator.calculateSavingsHV2` (Cyclomatic Complexity: 28, Nesting: 5) |

---

## 2. Audit Dimension A: Variable and Naming Quality Inventory

### 2.1 Cryptic and Single-Letter Identifiers
Single-letter identifiers and truncated variable names degrade readability and make static analysis difficult:
- **`b`**: [CalculatorUtils.java:259](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/CalculatorUtils.java#L259) — Lambda parameter for `Double b` in `calculateDirect` bill calculation.
- **`s`**: [DemandShiftOptimizer.java:61-90](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/DemandShiftOptimizer.java#L61-L90) — Loop parameter iterating over candidate shiftable slots.
- **`orig`**: [DemandShiftOptimizer.java:186](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/DemandShiftOptimizer.java#L186) — Unclear if it denotes original slot cost or original demand.
- **`sm`**: [DemandShiftOptimizer.java:202](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/DemandShiftOptimizer.java#L202) — Abbreviation for `shiftedSlotMap`.
- **`k`, `v`**: [CalculatorUtils.java:188-192](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/CalculatorUtils.java#L188-L192) and [ClientOverviewAggregator.java:144](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/ClientOverviewAggregator.java#L144) — Map entry loops iterating over high-level financial summary maps.
- **`e`**: [ClientOverviewAggregator.java:127, 241](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/ClientOverviewAggregator.java#L127) — Exception parameter swallowed without meaningful context.

### 2.2 Ambiguous and Misleading Method Names
- **`calculateSavings` vs `calculateSavingsHV2` vs `calculateMarketDecision`**:
  - `calculateSavings` in `SavingsCalculatorService` delegates directly to `hv2SavingsCalculator.calculateSavingsHV2`. This is a legacy single-market 96-slot dispatch routine.
  - In contrast, `calculateMarketDecision` executes the primary multi-market forward-banking optimization engine across 7 market scenarios. The naming implies `calculateSavings` is the primary entry point, when in production modern callers rely on `calculateMarketDecision`.
- **`CalculatorUtils.toInternalSlots`**:
  - The method name indicates a straightforward projection mapping. In reality, it inspects whether the database projection list is empty; if empty, it synthesizes 96 mock slots with hardcoded prices (4.50, 4.35, 4.20 Rs/kWh). A more truthful name is `resolveOrSynthesizeSlots`.

### 2.3 Inconsistent Boolean and Accessor Naming
- **`shouldBuy` vs `shouldBuyFromMarket`**:
  - In `SavingsCalculatorService:870`, the boolean flag is named `shouldBuy`.
  - In `MarketSlotCalculation`, the field is named `shouldBuyFromMarket`.
  - In `Hv2SavingsCalculator:265`, the variable is named `dispatchMarket`.
- **`isNpclHv2`**:
  - In `MarketDecisionContext`, `isNpclHv2` is evaluated as `isNpclHv2()`. This actually guards seasonal ToD peak hour shifting (NPCL summer/winter ToD rules). A clearer domain name is `isNpclSeasonalTodApplicable`.

---

## 3. Audit Dimension B: Raw Strings and Enum / Value Object Opportunities

### 3.1 Closed-Vocabulary Raw Strings
Throughout the calculator pipeline, several core domain concepts are handled as raw `String` literals rather than strongly typed Java enums:

1. **Market Type**:
   - Strings: `"DAM"`, `"RTM"`, `"GDAM"`
   - Found in: `MarketSlotCalculation`, `MarketOption`, `SavingsCalculatorService`, `ForwardBankingAllocator`.
   - **Opportunity**: `EnergyMarketType` enum (`DAM`, `RTM`, `GDAM`).
2. **Energy Source**:
   - Strings: `"DAM"`, `"RTM"`, `"GDAM"`, `"DISCOM"`
   - Found in: `MarketSlotCalculation.selectedMarket`, `MarketOption.marketType`.
   - **Opportunity**: `EnergySource` enum (`DAM`, `RTM`, `GDAM`, `DISCOM`).
3. **ToD Slab Classification**:
   - Strings: `"peak"`, `"offpeak"`, `"normal"`, `"FLAT"`, `"TOD1"`, `"TOD2"`, `"TOD3"`, `"TOD4"`, `"TOD5"`, `"TOD6"`
   - Found in: `CalculatorUtils.getSlabConsumption`, `Hv2SavingsCalculator`, `SavingsCalculatorService`.
   - **Opportunity**: `ToDSlabType` enum or a validated Value Object `ToDSlabIdentifier`.

### 3.2 Semi-Dynamic Codes & Value Object Opportunities
- **DISCOM Codes**:
  - While DISCOMs can expand dynamically, NPCL has hardcoded tariff logic (`isNpclHv2` checks in `MarketDecisionContext`).
  - Raw strings (`"NPCL"`, `"UPPCL"`, `"DVVNL"`, `"PVVNL"`, `"MVVNL"`, `"PuVVNL"`) are checked using `.equalsIgnoreCase("NPCL")`.
  - **Opportunity**: `DiscomCode` value object or constant registry to prevent typographical errors.
- **Charge Codes**:
  - Regulatory charges are resolved via strings: `"TRANSMISSION"`, `"WHEELING"`, `"CROSS_SUBSIDY"`, `"ADDITIONAL_SURCHARGE"`, `"BANKING_CHARGES"`.
  - **Opportunity**: `RegulatoryChargeCode` enum.

---

## 4. Audit Dimension C: Type Safety and Map Usage Analysis

### 4.1 Untyped Internal Communication Across Boundaries
The legacy codebase frequently uses untyped `Map<String, Object>` structures to pass complex state across component boundaries:

1. **`shiftInsights` Pipeline**:
   - `DemandShiftOptimizer.optimizeSlots()` returns `Map<String, Object>`.
   - `SavingsCalculatorService` passes `Map<String, Object> shiftInsights` to `ForwardBankingAllocator` and `Hv2SavingsCalculator`.
   - Components unpack values with string keys:
     - `(Double) shiftInsights.get("potentialSavingsRs")`
     - `(List<Map<String, Object>>) shiftInsights.get("shiftedSlots")`
     - `(String) shiftInsights.get("recommendedWindow")`
   - *Risk*: A single key typo triggers `NullPointerException` or `ClassCastException` at runtime.
   - **Solution**: Replace with a strongly typed Java record: `DemandShiftInsights(double potentialSavingsRs, String recommendedWindow, List<ShiftedSlot> shiftedSlots)`.

2. **Monthly Market Decision Provider**:
   - `ClientOverviewAggregator` consumes a `Function<Integer, Map<String, Object>> monthlyMarketDecisionProvider`.
   - In `aggregateMarketDecisionAllMonths`, lines 95-125 unwrap 25 scalar fields by string keys:
     ```java
     Double baseBill = (Double) result.get("totalDiscomBaseBillRs");
     Double marketCost = (Double) result.get("totalMarketCostRs");
     Double netSavings = (Double) result.get("netSavingsRs");
     ```
   - **Solution**: Define `MonthlyMarketDecisionResult` typed record.

3. **`todConsumptions` JSON Serialization**:
   - In `SavingsCalculation.java`, `todConsumptions` is stored as a raw JSON string and manipulated in service layers as `Map<String, Map<String, Object>>`.
   - Requires multiple layers of unchecked casting and Jackson `readValue` fallback loops.

### 4.2 Unchecked Casts Inventory (`@SuppressWarnings("unchecked")`)
| Class | Line(s) | Cast Expression | Impact |
| :--- | :--- | :--- | :--- |
| `CalculatorUtils.java` | 184 | `(Map<String, Object>) obj` | Deserialization of ToD maps |
| `DemandShiftOptimizer.java` | 74 | `(List<Map<String, Object>>) shiftInsights.get(...)` | Slot shift unwrapping |
| `DemandShiftOptimizer.java` | 138 | `(Map<String, Object>) slot` | Slot parameter extraction |
| `ForwardBankingAllocator.java` | 47 | `(List<Map<String, Object>>) ...` | Slot grouping iterations |
| `Hv2SavingsCalculator.java` | 289 | `(Map<String, Object>) shiftInsights` | Shift savings resolution |
| `FinancialLedgerReconciler.java`| 88 | `(Map<String, Object>) rawEntry` | Financial ledger payload parsing |

---

## 5. Audit Dimension D: Method Complexity and Separation of Responsibilities

### 5.1 Oversized Methods and High Cyclomatic Complexity
Three methods account for excessive complexity and violate the Single Responsibility Principle:

1. **`Hv2SavingsCalculator.calculateSavingsHV2`**:
   - **Metrics**: 318 LOC ([Hv2SavingsCalculator.java:98-415](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/Hv2SavingsCalculator.java#L98-L415)), Cyclomatic Complexity = 28, Maximum Nesting Depth = 5.
   - **Responsibilities Mixed**:
     1. Fetches and initializes state tariffs, ISTS charges, and banking parameters.
     2. Performs linear ISTS date search per slot.
     3. Executes slot-level landed cost arbitration.
     4. Aggregates monthly ToD slab consumptions with power factor conversions.
     5. Calculates Discom base bill vs market purchase bill.
     6. Builds and returns the monolithic result map.
   - **Issue**: Any bug fix to tariff matching requires touching this 318-line block.

2. **`SavingsCalculatorService.calculateSlotLandingCosts`**:
   - **Metrics**: 168 LOC ([SavingsCalculatorService.java:719-887](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/SavingsCalculatorService.java#L719-L887)), Cyclomatic Complexity = 19.
   - **Responsibilities Mixed**:
     1. Iterates over 96 to 2,880 slots.
     2. Inlined linear search for ISTS transmission charges.
     3. Inlined filtering of `StateTariff` by slot month and category.
     4. Inlined ToD hour-range evaluation loop.
     5. Evaluates DISCOM landed cost vs DAM/RTM/GDAM options.

3. **`ClientOverviewAggregator.aggregateMarketDecisionAllMonths`**:
   - **Metrics**: 133 LOC ([ClientOverviewAggregator.java:68-201](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/ClientOverviewAggregator.java#L68-L201)), Cyclomatic Complexity = 14.
   - **Responsibilities Mixed**:
     1. Multi-month dispatch execution (12 iterations).
     2. String-based map unpacking of 25 financial figures per month.
     3. Inline computation of weighted averages and bus-loss ratios.
     4. Building client overview summaries.

---

## 6. Audit Dimension E: Duplication and Reusability Analysis

### 6.1 Identical ISTS Linear Search Logic
Both `SavingsCalculatorService` and `Hv2SavingsCalculator` duplicate the exact same loop to find applicable ISTS charges:
```java
// SavingsCalculatorService.java:748-755
IstsCharges istsCharge = null;
if (ctx.istsChargesList() != null) {
    for (IstsCharges c : ctx.istsChargesList()) {
        if (!slotDate.isBefore(c.getEffectiveFrom()) &&
            (c.getEffectiveTo() == null || !slotDate.isAfter(c.getEffectiveTo()))) {
            istsCharge = c;
            break;
        }
    }
}
```
*And identically in:*
```java
// Hv2SavingsCalculator.java:169-176
IstsCharges istsCharge = null;
if (istsChargesList != null) {
    for (IstsCharges c : istsChargesList) {
        if (!slotDate.isBefore(c.getEffectiveFrom()) &&
            (c.getEffectiveTo() == null || !slotDate.isAfter(c.getEffectiveTo()))) {
            istsCharge = c;
            break;
        }
    }
}
```

### 6.2 Identical StateTariff Sifting and ToD Matching
Both classes duplicate the logic to resolve the DISCOM energy rate for a given time slot:
- `SavingsCalculatorService.java:801-835`
- `Hv2SavingsCalculator.java:197-232`
Both loop through `ctx.tariffs()` / `tariffs`, filter by month, match category, and inspect ToD start/end hour bands.

### 6.3 Divergent Slab Consumption Extraction
In `CalculatorUtils.getSlabConsumption`, consumption extraction includes power factor adjustment (`kwh / pf` when billing demand is kVAh).
However, `Hv2SavingsCalculator:305-352` implements an independent slab consumption parsing loop that fails to consistently apply the same power factor handling, creating potential divergence across calculation modes.

### 6.4 Duplicated Import Statements
In `SavingsCalculatorService.java`:
- Lines 41-49 and lines 51-59 contain duplicated import statements (`java.util.*`, `java.time.*`). While harmless to bytecode, it signals code hygiene neglect.

---

## 7. Audit Dimension F: Performance, Complexity, and Allocation Profile

### 7.1 Algorithmic Inefficiencies in Slot Processing Loops
Calculations routinely process up to 30 days $\times$ 96 slots = 2,880 slots per execution:
1. **$O(N \times M)$ ISTS Date Range Scans**:
   - For every slot ($N \le 2,880$), the engine linearly iterates through all $M$ ISTS charge records.
   - Because ISTS records rarely exceed 10-20 entries, this doesn't cause OOM, but it wastes substantial CPU cycles.
   - **Optimization**: Pre-resolve or index ISTS charges by Date or use a `NavigableMap<LocalDate, IstsCharges>`.
2. **$O(N \times K)$ Tariff Month Stream Filtering**:
   - Inside `calculateSlotLandingCosts`, for each slot:
     ```java
     ctx.tariffs().stream().filter(t -> t.getMonth() == slotMonth)...
     ```
   - Filtering a collection of tariffs 2,880 times per month is an unnecessary $O(N)$ allocation overhead.
   - **Optimization**: Group tariffs by month (`Map<Integer, List<StateTariff>>`) once prior to entering the slot loop.

### 7.2 Allocation Churn in Inner Loops
- Inside the slot iteration loop, new instances of `MarketOption`, `ArrayList`, and `Map<String, Object>` are instantiated repeatedly.
- In multi-client batch runs or multi-month scenarios (12 months $\times$ 2,880 slots = 34,560 slot iterations), this triggers substantial GC young generation pressure.

---

## 8. Audit Dimension G: Exception Handling and Null Safety

### 8.1 Swallowed Exceptions in Multi-Month Loops
In `ClientOverviewAggregator.java`:
```java
// ClientOverviewAggregator.java:127-130
} catch (Exception e) {
    log.error("Failed to calculate market decision for month: {}", month, e);
    // Loop continues without recording failure or populating fallback
}
```
- If month 7 fails (e.g. missing exchange rates or tariff records), the exception is logged, but the loop continues.
- The annual totals aggregate months 1..6 and 8..12 as if month 7 had zero cost and zero consumption, silently corrupting annual savings projections for client proposals.
- **Remediation**: Fail fast, or propagate a structured `PartialResultException` / status flag indicating incomplete aggregation.

### 8.2 Silent Fallback in `CalculatorUtils.toInternalSlots`
```java
// CalculatorUtils.java:232-249
if (projections == null || projections.isEmpty()) {
    log.warn("No exchange rate projections found; generating 96 default mock slots");
    // Synthesizes 96 slots at 4.50, 4.35, 4.20 Rs/kWh
}
```
- In production, if exchange rate projection ingestion fails, the calculator silently switches to synthetic mock pricing without throwing an exception or flagging the result payload as `syntheticDataUsed: true`.
- **Remediation**: Ensure calculation metadata flags `syntheticFallbackUsed = true` when mock rates are substituted.

---

## 9. Audit Dimension H: Financial Precision, Rounding, and Unit Consistency

### 9.1 Double Arithmetic vs `BigDecimal`
- All monetary rates and consumption figures use IEEE 754 `double`.
- While this accurately matches the Node.js implementation (`number` type) and preserves parity, floating-point subtraction (e.g. `discomCost - marketCost`) produces rounding artifacts (e.g., `12.300000000000002`).
- The codebase uses `1e-9` epsilon comparison in `SavingsCalculatorService:870`, which is sound.
- Platform fee and prolt fee use `Math.round(x * 100.0) / 100.0`, correctly rounding to paise.

### 9.2 Magic Unit Conversion Multipliers
While `UnitConstants` was introduced in V18E (`KW_PER_MW = 1000.0`, `KWH_PER_MW_SLOT = 250.0`), several inline conversions remain:
- `DemandShiftOptimizer.java:112`: `slotDemandKw * 0.25` instead of `slotDemandKw / UnitConstants.SLOTS_PER_HOUR` or `* UnitConstants.HOURS_PER_SLOT`.
- `ClientOverviewAggregator.java:105`: Hardcoded magic number `0.1211` for loss factor (`1 - 0.1211`) when consumer bus units are absent.

---

## 10. Audit Dimension I: Testability and Regression Coverage Assessment

### 10.1 Coverage Status
- **Current Calculator Tests**: 120 tests in `src/test/java/com/probus/prolt/service/calculator/`.
- Full project test suite: **417 tests run, 0 failures, 0 errors, 1 skipped by design (`ScraperLiveManualTest`)**.
- Regression suites established in V18E through V18I.1:
  - `SavingsCalculatorServiceNodeParityTest`
  - `CalculatorPropertiesPrecedenceTest`
  - `Hv2SavingsCalculatorTest`
  - `DemandShiftOptimizerTest`

### 10.2 Identified Coverage Gaps
1. **`calculateDirect` Edge Cases**:
   - Only 1 test scenario (`testCalculateDirect_Preserved`) covers `calculateDirect`.
   - Edge cases with zero consumption, missing slabs, or high power factor values are untested.
2. **Synthetic Fallback Mode**:
   - No dedicated test validates that `CalculatorUtils.toInternalSlots` correctly triggers synthetic slots when database projections are empty, nor verifies slot price profiles.
3. **Multi-Month Incomplete Aggregation**:
   - No test verifies what happens when 1 out of 12 months throws an exception during `ClientOverviewAggregator.aggregateMarketDecisionAllMonths`.

---

## 11. Audit Dimension J: Architecture, DI, and Dependency Quality

### 11.1 Constructor Overload Bloat in `SavingsCalculatorService`
`SavingsCalculatorService` maintains 5 constructors:
1. `SavingsCalculatorService(DataAccessService, Properties, 9 components)` — 11 args (Primary Spring constructor).
2. `SavingsCalculatorService(DataAccessService, Properties, 8 components)` — 10 args.
3. `SavingsCalculatorService(DataAccessService, 8 components)` — 9 args (Instantiates default `CalculatorProperties`).
4. `SavingsCalculatorService(DataAccessService)` — 1 arg (Instantiates all 9 components manually).
5. `SavingsCalculatorService()` — 0 args (Default no-arg constructor).

**Risks**:
- Maintaining 5 constructors creates a testing hazard where tests use a legacy constructor that instantiates `new CalculatorProperties()` with default values, bypassing Spring Boot profile-injected properties!

### 11.2 Inconsistent Property Propagation
In `Hv2SavingsCalculator.java`:
```java
public Hv2SavingsCalculator(CalculatorProperties properties) {
    this.properties = properties != null ? properties : new CalculatorProperties();
}
public Hv2SavingsCalculator() {
    this(new CalculatorProperties());
}
```
If a test or service uses the no-arg constructor, configuration overrides loaded via `@ConfigurationProperties` are silently ignored.

---

## 12. Comprehensive Findings Register

| ID | Priority | Class & Line | Finding / Issue | Impact | Recommended Remediation | Effort | Risk |
| :--- | :---: | :--- | :--- | :--- | :--- | :---: | :---: |
| **F-ARCH-01** | **P1** | [SavingsCalculatorService.java:120-195](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/SavingsCalculatorService.java#L120-L195) | 5 overloaded constructors bypass Spring config | Test/runtime configuration drift | Deprecate legacy constructors; unify under a single DI constructor | M | Low |
| **F-TYP-01** | **P1** | [DemandShiftOptimizer.java:61-120](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/DemandShiftOptimizer.java#L61-L120) | `shiftInsights` passed as untyped `Map<String, Object>` | Runtime `ClassCastException` risk | Introduce typed Java record `DemandShiftInsights` | M | Low |
| **F-TYP-02** | **P1** | [ClientOverviewAggregator.java:95-125](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/ClientOverviewAggregator.java#L95-L125) | Monthly market decision returns raw Map with 25 keys | Fragile key string indexing | Introduce typed record `MonthlyMarketDecisionResult` | M | Low |
| **F-EXC-01** | **P1** | [ClientOverviewAggregator.java:127, 241](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/ClientOverviewAggregator.java#L127) | Broad `catch (Exception e)` silently swallows month failure | Corrupts annual proposal financials | Propagate error flag or structured partial result | S | Low |
| **F-EXC-02** | **P1** | [CalculatorUtils.java:232-249](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/CalculatorUtils.java#L232-L249) | Silent substitution of 96 synthetic mock slots | Hidden fallback masquerading as real data | Record `syntheticFallbackUsed = true` metadata | S | Low |
| **F-DUP-01** | **P1** | [Hv2SavingsCalculator.java:169](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/Hv2SavingsCalculator.java#L169), [SavingsCalculatorService.java:748](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/SavingsCalculatorService.java#L748) | Duplicated ISTS date-lookup linear scan | Code drift; maintenance overhead | Extract to `RegulatoryChargeResolver.findEffectiveIstsCharge` | S | Low |
| **F-DUP-02** | **P1** | [Hv2SavingsCalculator.java:197](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/Hv2SavingsCalculator.java#L197), [SavingsCalculatorService.java:801](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/SavingsCalculatorService.java#L801) | Duplicated `StateTariff` sifting and ToD matching | Divergent tariff resolution | Consolidate into `RegulatoryChargeResolver.resolveDiscomTariff` | M | Low |
| **F-CMP-01** | **P1** | [Hv2SavingsCalculator.java:98-415](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/Hv2SavingsCalculator.java#L98-L415) | 318 LOC monolithic method with CC = 28 | Unmaintainable, untestable branches | Decompose into slot simulation, tariff billing, and ledger summary | L | Med |
| **F-PERF-01** | **P2** | [SavingsCalculatorService.java:801-805](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/SavingsCalculatorService.java#L801-L805) | O(N * K) tariff stream filtering inside slot loop | High CPU and allocation churn | Pre-group tariffs by month (`Map<Integer, List<StateTariff>>`) | S | Low |
| **F-PERF-02** | **P2** | [SavingsCalculatorService.java:748-755](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/SavingsCalculatorService.java#L748-L755) | O(N * M) ISTS date range search inside slot loop | Linear scan executed 2,880 times per run | Pre-index ISTS charges into `NavigableMap<LocalDate, IstsCharges>` | S | Low |
| **F-TYP-03** | **P2** | [MarketSlotCalculation.java:35](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/model/MarketSlotCalculation.java#L35) | Market types (`DAM`, `RTM`, `GDAM`) as raw strings | Typo risk across comparisons | Introduce `EnergyMarketType` enum | S | Low |
| **F-TYP-04** | **P2** | [MarketSlotCalculation.java:36](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/model/MarketSlotCalculation.java#L36) | Energy source (`DAM`, `RTM`, `GDAM`, `DISCOM`) as raw strings | Typo risk | Introduce `EnergySource` enum | S | Low |
| **F-NAM-01** | **P3** | [DemandShiftOptimizer.java:61-90](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/DemandShiftOptimizer.java#L61-L90) | Single letter vars (`s`, `orig`, `sm`, `b`, `k`, `v`) | Lowers code readability | Rename to descriptive domain terms | S | Low |
| **F-NAM-02** | **P3** | [SavingsCalculatorService.java:204](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/SavingsCalculatorService.java#L204) | `calculateDirect` has hardcoded "Acme Steel Mills" | Confusion between demo and production code | Extract or clarify client-specific mock fixtures | S | Low |
| **F-CLG-01** | **P3** | [SavingsCalculatorService.java:51-59](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/SavingsCalculatorService.java#L51-L59) | Duplicate import block | Code hygiene defect | Remove duplicate import statements | S | Low |
| **F-MAG-01** | **P3** | [ClientOverviewAggregator.java:105](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/probus/prolt/service/calculator/ClientOverviewAggregator.java#L105) | Magic number `0.1211` for loss factor | Obscures business origin | Replace with constant or configurable property | S | Low |

# Milestone V18L.1 — Legacy Saving Calculator Domain Enums & Vocabulary Standardization

## 1. Executive Summary

Milestone **V18L.1** executes the first part of Phase 2 from the approved V18J refactoring roadmap (`docs/73-v18j-prioritized-refactoring-roadmap.md`). 

The primary objective was to replace error-prone raw business strings with strongly-typed Java enums across the Legacy Saving Calculator (`prolt-operations`), establishing a standardized domain vocabulary while preserving 100% numerical, behavioral, and API contract parity with the authoritative Node.js reference application (`IEX-Dashboard/backend`).

### Key Accomplishments
- **Standardized Domain Enums**: Created 4 dedicated, type-safe enums in `com.sense.prolt_operations.service.calculator.model`:
  1. `EnergyMarketType` (`DAM`, `RTM`, `GDAM`)
  2. `EnergySource` (`DAM`, `RTM`, `GDAM`, `DISCOM`)
  3. `RegulatoryChargeType` (`CTU`, `STU`, `WHEELING`, `CSS`, `AS`, `BANKING`)
  4. `ConsumptionType` (`FLAT`, `TOTAL`)
- **Safe Conversion & Boundary Protection**: Implemented non-throwing `fromValue(...)` / `fromCode(...)` parsers with case-insensitivity, whitespace trimming, and `Optional<T>` return semantics. Boundary maps (`Map<String, Object>`) and JSON serialization continue to emit standard uppercase string literals.
- **Calculator Engine Refactoring**: Refactored `SevenMarketArbitrator`, `RtmContiguitySolver`, `OperationalOverheadCalculator`, `ForwardBankingAllocator`, `FinancialLedgerReconciler`, `CalculatorUtils`, `Hv2SavingsCalculator`, and `MarketSlotCalculation` to leverage the new enums internally while preserving external wire formats.
- **Enterprise V2 & Node.js Isolation**: Enterprise V2 (`SavingsCalculatorNewService.java`) and the Node.js reference codebase remained 100% untouched and read-only.
- **Zero Regressions**: All 76 newly introduced enum unit tests passed; all 22 calculator subcomponent and numerical parity tests passed; and the complete test suite passed with **493 tests run, 0 failures, 0 errors, 1 skipped by design (`ScraperLiveManualTest`)**.

---

## 2. Vocabulary Classification Matrix

Before implementing changes, every candidate vocabulary across the Legacy Saving Calculator and the Node.js reference was audited and classified into three distinct categories:

| Vocabulary | Classification | Status in V18L.1 | Rationale & Node.js Alignment |
| :--- | :--- | :--- | :--- |
| **Energy Market Type** | Closed | Converted (`EnergyMarketType`) | Strictly defined by Indian electricity regulatory framework (CERC) and Indian Energy Exchange (IEX) market segments: Day-Ahead Market (`DAM`), Real-Time Market (`RTM`), and Green Day-Ahead Market (`GDAM`). Exhaustive and static across both Node.js and Java. |
| **Energy Source** | Closed | Converted (`EnergySource`) | Sourcing alternatives for any 15-minute time block: `DAM`, `RTM`, `GDAM`, and `DISCOM`. Exhaustive closed set. Dispatches can only come from power exchange products or the default distribution licensee. |
| **Regulatory Charge Type** | Closed | Converted (`RegulatoryChargeType`) | Closed statutory open access tariff components: `CTU` (ISTS), `STU`, `WHEELING`, `CSS`, `AS`, and `BANKING`. Even though database entities (`StateCharges`) store these as tabular columns rather than rows, having a domain enum standardizes code references and documentation. |
| **Consumption Fallback Key** | Semi-Dynamic | Converted (`ConsumptionType`) | Client monthly ToD consumption payloads often provide fallback summary keys when time-of-day slots are unspecified (`"FLAT"` or `"TOTAL"`). Standardized via `ConsumptionType.isFallbackTotalKey(key)`. |
| **ToD Slabs** | Dynamic / External | **Deferred (Retained as String)** | In Node.js and Java, ToD slabs are dynamic data loaded from database entities (`StateTariff`). Examples include time windows (`"22:00-06:00"`, `"06:00-18:00"`), seasonal NPCL codes (`"TOD1"` to `"TOD6"`), and colloquial descriptors (`"peak"`, `"offpeak"`, `"normal"`). Creating a closed enum would break when new state tariffs or seasonal definitions are introduced. |
| **DISCOM Code** | Dynamic / External | **Deferred (Retained as String)** | Discom identifiers (e.g., `"NPCL"`, `"PVVNL"`, `"MVVNL"`, `"DVVNL"`, `"DHBVN"`) are user- and state-configurable strings dynamically queried from PostgreSQL tables (`discom`, `state_tariff`). |
| **Consumer Category** | Open / External | **Deferred (Retained as String)** | Tariff category strings (e.g., `"HV-2"`, `"HT Industrial"`, `"11kV"`) vary per state electricity regulatory commission (SERC) order and are parsed dynamically via regex. |
| **Voltage Level** | Open / External | **Deferred (Retained as String)** | Voltage strings (e.g., `"11kV"`, `"33kV"`, `"66kV"`, `"132kV"`) represent continuous electrical ratings and are parsed numerically. |

---

## 3. Enums Introduced

### A. `EnergyMarketType`
- **Location**: `com.sense.prolt_operations.service.calculator.model.EnergyMarketType`
- **Constants**: `DAM`, `RTM`, `GDAM`
- **Fields**: `private final String value;`
- **Methods**:
  - `String getValue()`: Returns canonical uppercase string representation (`"DAM"`, `"RTM"`, `"GDAM"`).
  - `static Optional<EnergyMarketType> fromValue(String value)`: Safe parser that trims whitespace and matches case-insensitively. Returns `Optional.empty()` for null or unknown strings.
  - `static boolean isValid(String value)`: Helper returning boolean validation status.
  - `String toString()`: Delegates to `value`.

### B. `EnergySource`
- **Location**: `com.sense.prolt_operations.service.calculator.model.EnergySource`
- **Constants**: `DAM`, `RTM`, `GDAM`, `DISCOM`
- **Fields**: `private final String value;`
- **Methods**:
  - `String getValue()`: Returns canonical uppercase string representation (`"DAM"`, `"RTM"`, `"GDAM"`, `"DISCOM"`).
  - `static Optional<EnergySource> fromValue(String value)`: Safe parser with trimming and case-insensitivity.
  - `static boolean isValid(String value)`: Boolean validation helper.
  - `static EnergySource fromMarketType(EnergyMarketType marketType)`: Bridges `EnergyMarketType` to `EnergySource`.
  - `Optional<EnergyMarketType> toMarketType()`: Returns `Optional.of(market)` for exchange products, and `Optional.empty()` for `DISCOM`.
  - `boolean isMarket()`: Returns `true` for `DAM`, `RTM`, `GDAM`; `false` for `DISCOM`.
  - `boolean isDiscom()`: Returns `true` for `DISCOM`; `false` for exchange products.
  - `String toString()`: Delegates to `value`.

### C. `RegulatoryChargeType`
- **Location**: `com.sense.prolt_operations.service.calculator.model.RegulatoryChargeType`
- **Constants**:
  - `CTU("CTU", "Central Transmission Utility / ISTS Charges")`
  - `STU("STU", "State Transmission Utility Charges")`
  - `WHEELING("WHEELING", "Distribution Wheeling Charges")`
  - `CSS("CSS", "Cross Subsidy Surcharge")`
  - `AS("AS", "Additional Surcharge")`
  - `BANKING("BANKING", "Forward Banking Charges")`
- **Fields**: `private final String code;`, `private final String description;`
- **Methods**:
  - `String getCode()`: Returns canonical uppercase charge code.
  - `String getDescription()`: Returns descriptive regulatory tariff name.
  - `static Optional<RegulatoryChargeType> fromCode(String code)`: Parses code with trimming, case-insensitivity, and aliases (e.g., maps `"ISTS"` to `CTU`).
  - `static boolean isValid(String code)`: Boolean validation helper.

### D. `ConsumptionType`
- **Location**: `com.sense.prolt_operations.service.calculator.model.ConsumptionType`
- **Constants**: `FLAT("FLAT")`, `TOTAL("TOTAL")`
- **Fields**: `private final String value;`
- **Methods**:
  - `String getValue()`: Returns canonical uppercase representation.
  - `static Optional<ConsumptionType> fromValue(String value)`: Parses value safely.
  - `static boolean isFallbackTotalKey(String key)`: Returns `true` if key matches `"FLAT"` or `"TOTAL"` case-insensitively after trimming.

---

## 4. Code Modifications

The newly introduced enums were integrated across the legacy calculation pipeline:

### 1. `MarketSlotCalculation.java`
- Added overloaded `public Double getMarketLanding(EnergyMarketType marketType)` enabling type-safe lookup.
- Maintained backward-compatible `public Double getMarketLanding(String market)` which safely delegates to `fromValue(market)`.
- Added typed `getEnergySource()` and `setEnergySource(EnergySource)` accessors.
- Retained public mutable `String marketSource` field and `toMap()` serialization using `EnergySource.DISCOM.getValue()`.

### 2. `SevenMarketArbitrator.java`
- Upgraded the 7 market combinations from `List<List<String>>` to strongly typed `List<List<EnergyMarketType>>`:
  ```java
  private static final List<List<EnergyMarketType>> COMBINATIONS = List.of(
          List.of(EnergyMarketType.DAM),
          List.of(EnergyMarketType.RTM),
          List.of(EnergyMarketType.GDAM),
          List.of(EnergyMarketType.DAM, EnergyMarketType.RTM),
          List.of(EnergyMarketType.DAM, EnergyMarketType.GDAM),
          List.of(EnergyMarketType.RTM, EnergyMarketType.GDAM),
          List.of(EnergyMarketType.DAM, EnergyMarketType.RTM, EnergyMarketType.GDAM)
  );
  ```
- Replaced string concatenation with typed arbitration logic:
  ```java
  for (EnergyMarketType market : combination) {
      Double landing = s.getMarketLanding(market);
      ...
  }
  ```
- Assigned slot sourcing with `bestMarket.getValue()` and fallback `EnergySource.DISCOM.getValue()`.

### 3. `RtmContiguitySolver.java`
- Replaced magic string comparisons `"RTM".equals(slot.marketSource)` with `EnergyMarketType.RTM.getValue().equals(slot.marketSource)`.
- Replaced fallback string updates `"DAM"` and `"GDAM"` with `EnergyMarketType.DAM.getValue()` and `EnergyMarketType.GDAM.getValue()`.

### 4. `OperationalOverheadCalculator.java`
- Replaced string branching with modern Java enum switch:
  ```java
  EnergyMarketType.fromValue(s.marketSource).ifPresent(market -> {
      switch (market) {
          case DAM -> s.damLanding += perUnitOverhead;
          case RTM -> s.rtmLanding += perUnitOverhead;
          case GDAM -> s.gdamLanding += perUnitOverhead;
      }
  });
  ```

### 5. `ForwardBankingAllocator.java`
- Replaced duplicated nested ternary expressions (`"DAM".equals(s.marketSource) ? ... : ("RTM".equals(s.marketSource) ? ... : ...)`) with a dedicated helper:
  ```java
  private double resolveMarketBasePrice(MarketSlotCalculation s) {
      return EnergyMarketType.fromValue(s.marketSource)
              .map(market -> switch (market) {
                  case DAM -> s.damMcp != null ? s.damMcp : 0.0;
                  case RTM -> s.rtmMcp != null ? s.rtmMcp : 0.0;
                  case GDAM -> s.gdamMcp != null ? s.gdamMcp : 0.0;
              })
              .orElse(0.0);
  }
  ```

### 6. `FinancialLedgerReconciler.java`
- Replaced raw string check `!"GDAM".equals(s.marketSource)` with `!EnergyMarketType.GDAM.getValue().equals(s.marketSource)` during non-GDAM market energy segregation for RPO calculation.

### 7. `CalculatorUtils.java`
- Replaced raw string comparison `"FLAT".equalsIgnoreCase(consumptionKey) || "TOTAL".equalsIgnoreCase(consumptionKey)` with `ConsumptionType.isFallbackTotalKey(consumptionKey)`.

### 8. `Hv2SavingsCalculator.java`
- Standardized market option generation and source tracking:
  ```java
  String selectedSource = EnergySource.DISCOM.getValue();
  if (damLandingPrice > 0)
      availableMarkets.add(new MarketOption(EnergyMarketType.DAM.getValue(), damLandingPrice));
  if (rtmLandingPrice > 0)
      availableMarkets.add(new MarketOption(EnergyMarketType.RTM.getValue(), rtmLandingPrice));
  if (gdamLandingPrice > 0)
      availableMarkets.add(new MarketOption(EnergyMarketType.GDAM.getValue(), gdamLandingPrice));
  ...
  if (!EnergySource.DISCOM.getValue().equals(item.selectedSource())) {
      totalMarketEnergyKwh += ePerSlot;
  }
  ```

---

## 5. Backward Compatibility Safeguards

To prevent regressions in upstream/downstream services and REST clients:
1. **Response Map Serialization**: All response builder methods (e.g., `MarketSlotCalculation.toMap()`, `Hv2SlotRecord.toMap()`) continue to emit standard uppercase strings (`"DAM"`, `"RTM"`, `"GDAM"`, `"DISCOM"`) identically to the Node.js API responses.
2. **DTO & Entity Compatibility**: Database entities (`SavingsCalculatorEntry`, `StateCharges`, `StateTariff`) and DTOs remain untouched. No JPA converter or column alteration was required.
3. **No Breaking API Contracts**: All public calculation entrypoints (`calculateDirect`, `calculateDemandShift`, `calculateHv2Savings`) maintain identical signatures and JSON payload structures.

---

## 6. Safe Conversion Strategy

Rather than using native Java `Enum.valueOf(String)`—which throws an unchecked `IllegalArgumentException` on unrecognized input—all domain enums implement defensive resolution:
- **Null Safety**: Passing `null` returns `Optional.empty()` or `false` without NullPointerExceptions.
- **Whitespace Tolerance**: Leading and trailing whitespaces are trimmed automatically.
- **Case Insensitivity**: Inputs like `"dam"`, `"Dam"`, or `"DAM"` map deterministically to `EnergyMarketType.DAM`.
- **Alias Resolution**: `RegulatoryChargeType.fromCode("ISTS")` transparently resolves to `RegulatoryChargeType.CTU`.

---

## 7. Deferred Vocabularies & Items

Per the strict scope boundaries of V18L.1:
1. **Dynamic Vocabularies Retained as String**:
   - `ToDSlab`: Dynamic database schedule records (`"TOD1"` to `"TOD6"`, `"22:00-06:00"`, etc.).
   - `DiscomCode`: State utility identifiers loaded from database tables (`"NPCL"`, `"PVVNL"`, etc.).
   - `ConsumerCategory` and `VoltageLevel`: Tariff classification strings.
2. **Deferred to V18L.2**:
   - Typed internal calculation records (`DemandShiftInsights`, `MonthlyMarketDecisionResult`).
   - Cleanup of `@SuppressWarnings("unchecked")` casts on legacy maps.
   - Decomposition of oversized calculation orchestration methods.

---

## 8. Test Strategy & Results

### A. New Unit Tests Introduced
Four dedicated JUnit 5 test classes were created:
1. `EnergyMarketTypeTest`: 14 tests covering canonical values, uppercase representation, case-insensitivity, whitespace trimming, and invalid value handling.
2. `EnergySourceTest`: 16 tests verifying market vs. discom classification (`isMarket()`, `isDiscom()`), conversions between `EnergyMarketType` and `EnergySource`, and parsing.
3. `RegulatoryChargeTypeTest`: 22 tests verifying charge codes, descriptions, ISTS alias resolution, and null/invalid resilience.
4. `ConsumptionTypeTest`: 24 tests validating `FLAT`, `TOTAL`, and fallback key identification.

### B. Verification Run Results
1. **Enum Unit Tests**:
   - Command: `./mvnw test -Dtest=EnergyMarketTypeTest,EnergySourceTest,RegulatoryChargeTypeTest,ConsumptionTypeTest`
   - Outcome: **76 tests run, 0 failures, 0 errors, 0 skipped**.
2. **Calculator Component & Numerical Parity Tests**:
   - Command: `./mvnw test -Dtest=SavingsCalculatorNumericalParityTest,SevenMarketArbitratorTest,RtmContiguitySolverTest,OperationalOverheadCalculatorTest,ForwardBankingAllocatorTest,Hv2SavingsCalculatorTest`
   - Outcome: **22 tests run, 0 failures, 0 errors, 0 skipped**.
3. **Full Test Suite Execution**:
   - Command: `./mvnw test`
   - Outcome: **493 tests run, 0 failures, 0 errors, 1 skipped by design (`ScraperLiveManualTest`)**.
   - Result: **BUILD SUCCESS** in 41.801s.

---

## 9. Enterprise V2 Isolation Confirmation

- **File Inspected**: `src/main/java/com/sense/prolt_operations/service/SavingsCalculatorNewService.java`
- **Git Status**: Clean (`git status -s` shows zero modifications).
- **Verification**: The Enterprise V2 service remains 100% untouched.

---

## 10. Source-of-Truth Compliance

- **Reference Repository**: `IEX-Dashboard/backend`
- **Inspection Verification**:
  - `EnergyMarketType` values (`DAM`, `RTM`, `GDAM`) exactly match the Node.js market products in `savings-calculator.service.ts`.
  - `EnergySource` values match the Node.js slot source tagging (`selectedSource: "DISCOM"` or market string).
  - `ConsumptionType` matches the Node.js flat/total fallback logic in consumption extraction.
  - Sourcing precedence, loss gross-up, and landing calculations remain completely identical.

---

## 11. Recommendations for V18L.2

With domain enums firmly established in V18L.1, Phase 2 should proceed to **Milestone V18L.2**:
1. **Typed Internal Records**: Introduce immutable Java records to replace loosely-typed `Map<String, Object>` inside the calculator pipeline:
   - `DemandShiftInsights`: Replaces raw shift results dictionary.
   - `MonthlyMarketDecisionResult`: Bundles slot allocations, ledger aggregates, and summary metrics.
2. **Unchecked Cast Cleanup**: Systematically eliminate `@SuppressWarnings("unchecked")` across `SavingsCalculatorService` and `ForwardBankingAllocator`.
3. **Boundary Serialization Layer**: Keep `Map<String, Object>` strictly at the controller / service boundary for backwards compatibility while keeping internal processing 100% strongly typed.

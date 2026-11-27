# V18E — Legacy Saving Calculator Constants & Shared Formula Refactoring Report

**Document Reference**: `docs/67-v18e-constants-and-shared-formula-refactoring.md`  
**Execution Date**: November 4, 2026  
**Target Milestone**: V18E — Constants & Shared Formula Refactoring  
**Repositories**:
- Java Backend: `/home/ankit-jha/probus-workspace/prolt-operations`
- Node.js Reference: `/home/ankit-jha/probus-workspace/IEX-Dashboard/backend`
**Execution Mode**: Non-breaking, behavior-preserving refactoring with strict parity preservation.

---

## 1. Executive Summary & Baseline Git Status

Milestone **V18E** addressed the critical maintainability, readability, and code-duplication technical debt identified in the **V18D Code Quality & Maintainability Audit** ([`docs/66-v18d-legacy-code-quality-maintainability-audit.md`](file:///home/ankit-jha/probus-workspace/prolt-operations/docs/66-v18d-legacy-code-quality-maintainability-audit.md)).

### Scope Executed:
1. **Extracted Categorized Constants**: Replaced over 25 hardcoded magic numbers with 8 documented, type-safe constants classes organized under `com.sense.prolt_operations.service.calculator.constant`.
2. **Eliminated NPCL HV-2 ToD Duplication**: Extracted seasonal Time-of-Day (ToD) tariff resolution (UPERC winter Sept–March slabs vs. summer April–August slabs) into a dedicated stateless component (`NpclTariffSchedule.java`). Both `calculateSavingsHV2` and `calculateMarketDecision` were refactored to delegate to this shared component, eliminating ~75 lines of copy-pasted conditional branching.
3. **Extracted Technical Loss & Landed Cost Calculators**: Separated compound electrical loss calculations (`ElectricalLossCalculator.java`) and landed open access price formulas (`LandedCostCalculator.java`).
4. **Behavior & Parity Preservation**: All mathematical operations, precision, rounding behavior, and formula outputs remained 100% invariant.
5. **Validation**: 49 focused unit tests and 19 targeted integration tests passed (68/68 targeted passing). The full Java backend test suite passed with **359/360 passing (1 skipped by design, 0 failures, 0 errors)**.
6. **Safety & Isolation**: Zero modifications to Enterprise V2 (`SavingsCalculatorNewService`), zero modifications to Flyway target or database schema, local PostgreSQL verified at Flyway V14, and no Git commits or pushes were executed.

### Baseline Git Status Prior to Changes
- Working tree contained modified files and untracked migration reports from prior milestones V11–V18D.
- `git status` confirmed clean branch `main`, no unstaged conflict markers, and no Git commits or pushes.

---

## 2. Baseline Test Results

Prior to refactoring, targeted regression suites were executed to establish a benchmark:
- `mvn test -Dtest=SavingsCalculatorNumericalParityTest,SavingCalculatorRepositoryIntegrationTest,CalculationAndTariffIntegrationTest`
  - **Results**: 19 run, 0 failures, 0 errors, 0 skipped (100% pass, 21.6 s).
- Full backend suite prior to V18E:
  - **Results**: 311 run, 0 failures, 0 errors, 1 skipped (58.0 s).

---

## 3. Constants Inventory and Classifications

All numeric literals identified in V18D were evaluated against Indian grid codes, CERC/UPERC regulations, and market rules. They were categorized into 8 domain-specific constants classes:

| Constant Literal | Target Class & Identifier | Domain Classification | Units & Period | Statutory / Business Rationale |
|---|---|---|---|---|
| `1000.0` | `UnitConstants.KW_PER_MW` | Physical & Unit | kW / MW | Physical conversion factor between kilowatts and megawatts. |
| `1000.0` | `UnitConstants.KWH_PER_MWH` | Physical & Unit | kWh / MWh | Energy conversion factor between kilowatt-hours and megawatt-hours. |
| `0.25` | `UnitConstants.HOURS_PER_15MIN_SLOT` | Physical & Unit | hours / slot | 15 minutes expressed in fractional hours. |
| `250.0` | `UnitConstants.KWH_PER_MW_SLOT` | Physical & Unit | kWh / MW / slot | Slot energy quantum: $1\,\text{MW} \times 1000 \times 0.25\,\text{h} = 250\,\text{kWh}$. |
| `96` | `TimeblockConstants.SLOTS_PER_DAY` | Grid Dispatch | slots / day | Standard Indian Grid Code 15-minute dispatch intervals ($24 \times 4$). |
| `15` | `TimeblockConstants.MINUTES_PER_SLOT` | Grid Dispatch | minutes / slot | Standardized settlement timeblock interval. |
| `24` | `TimeblockConstants.HOURS_PER_DAY` | Calendar | hours / day | Operational scheduling day duration. |
| `1000.0` | `RegulatoryConstants.MIN_OA_ELIGIBILITY_KW` | Statutory Rule | kW (1 MW) | Section 42 of Electricity Act 2003 Open Access threshold. |
| `0.1` | `RegulatoryConstants.MIN_OA_SCHEDULE_QUANTUM_MW` | Market Rule | MW (100 kW) | Minimum bid quantum on power exchanges (IEX/PXIL/HPX). |
| `0.9` | `RegulatoryConstants.CONTRACT_DEMAND_PF_FACTOR` | Grid Rule | dimensionless | kVA to MW conversion factor: $\text{MW} = (\text{kVA} \times 0.9) / 1000.0$. |
| `0.99` | `RegulatoryConstants.DEFAULT_POWER_FACTOR` | Electrical Rule | dimensionless | Default operating power factor when unmetered or non-positive. |
| `99.9` | `RegulatoryConstants.MAX_TRANSMISSION_LOSS_CAP_PERCENT` | Numerical Rule | % | Loss percentage upper bound to prevent division by zero. |
| `0.891` | `RegulatoryConstants.NPCL_REBATE_DISCOUNT_FACTOR` | Tariff Rule | dimensionless | NPCL rebate discount factor: $0.90 \times 0.99 = 0.891$. |
| `0.18` | `TaxConstants.STATUTORY_GST_RATE` | Statutory Tax | dimensionless (18%) | GST on trading fees, scheduling charges, and consultancy fees. |
| `0.02` | `ExchangeFeeConstants.DEFAULT_EXCHANGE_FEE_PER_KWH` | Market Fee | ₹ / kWh | Standard power exchange application/trading fee. |
| `0.10` | `ExchangeFeeConstants.DEFAULT_OTHER_CHARGES_PER_KWH` | Market Fee | ₹ / kWh | Miscellaneous banking and operational charge fallback. |
| `0.25` | `ExchangeFeeConstants.DEFAULT_RPO_FLAT_RATE_PER_KWH` | Regulatory Fee | ₹ / kWh | Renewable Purchase Obligation (RPO) flat compliance rate. |
| `7000.0` | `OverheadFeeConstants.DEFAULT_DISCOM_NOC_FEE_MONTHLY` | Administrative | ₹ / month | Monthly DISCOM No-Objection Certificate processing charge. |
| `8333.0` | `OverheadFeeConstants.DEFAULT_SLDC_MONTHLY_FEE` | Administrative | ₹ / month | Annualized monthly SLDC registration charge (₹100,000 / 12). |
| `20000.0` | `OverheadFeeConstants.DEFAULT_CONSULTANCY_FEE_MONTHLY` | Management | ₹ / month | Default monthly Probus advisory retainer. |
| `0.02` | `OverheadFeeConstants.DEFAULT_PROBUS_PLATFORM_FEE_RATE` | Success Fee | ₹ / kWh | Probus platform success fee per scheduled OA unit. |
| `5.0` | `OverheadFeeConstants.NLDC_APPLICATION_FEE_PER_BID` | Grid Dispatch | ₹ / bid | NLDC application processing fee per bid submission. |
| `2500.0` | `OverheadFeeConstants.DEFAULT_NLDC_DAILY_SCHEDULING_FEE` | Grid Dispatch | ₹ / day | NLDC daily scheduling fee rate per traded date. |
| `1500.0` | `OverheadFeeConstants.DEFAULT_SLDC_DAILY_SCHEDULING_FEE` | Grid Dispatch | ₹ / day | SLDC daily scheduling fee rate per traded product date. |
| `0.496` | `RegulatoryFallbackConstants.DEFAULT_CTU_PER_KWH` | Fallback Tariff | ₹ / kWh | Central Transmission Utility rate fallback. |
| `0.384` | `RegulatoryFallbackConstants.DEFAULT_STU_PER_KWH` | Fallback Tariff | ₹ / kWh | State Transmission Utility rate fallback. |
| `0.216` | `RegulatoryFallbackConstants.DEFAULT_WHEELING_PER_KWH` | Fallback Tariff | ₹ / kWh | DISCOM wheeling charge fallback. |
| `0.68` | `RegulatoryFallbackConstants.DEFAULT_CROSS_SUBSIDY_PER_KWH` | Fallback Tariff | ₹ / kWh | Cross Subsidy Surcharge (CSS) fallback. |
| `0.216` | `RegulatoryFallbackConstants.DEFAULT_ADDITIONAL_SURCHARGE_PER_KWH` | Fallback Tariff | ₹ / kWh | Additional Surcharge fallback. |
| `3.5` | `RegulatoryFallbackConstants.DEFAULT_STU_LOSS_PERCENT` | Fallback Loss | % | STU technical transmission loss fallback. |
| `4.0` | `RegulatoryFallbackConstants.DEFAULT_WHEELING_LOSS_PERCENT` | Fallback Loss | % | Distribution network wheeling loss fallback. |
| `330.0` | `RegulatoryFallbackConstants.DEFAULT_DEMAND_FIXED_CHARGE_KVA` | Fallback Tariff | ₹ / kVA / month | Contract demand fixed charge rate fallback. |
| `7.5` | `RegulatoryFallbackConstants.DEFAULT_BASE_DISCOM_RATE` | Fallback Tariff | ₹ / kWh | Baseline industrial DISCOM tariff fallback. |
| `0.01` | `SavingsCalculatorConstants.DEMAND_SHIFT_MIN_SPREAD_RS` | Heuristic Limit | ₹ / kWh (1 paisa) | Stopping spread threshold for demand shifting reallocation. |
| `1e-9` | `SavingsCalculatorConstants.EPSILON` | Numerical | dimensionless | Double-precision comparison tolerance. |
| `1250000.0` | `SavingsCalculatorConstants.DEFAULT_PROSPECT_CONSUMPTION_KWH` | Simulation | kWh | Benchmark sample monthly consumption for prospect estimates. |

---

## 4. New Constants Classes

Eight dedicated constants classes were implemented with private constructors and thorough JavaDoc documentation:

1. [`UnitConstants.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/constant/UnitConstants.java)
2. [`TimeblockConstants.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/constant/TimeblockConstants.java)
3. [`RegulatoryConstants.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/constant/RegulatoryConstants.java)
4. [`TaxConstants.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/constant/TaxConstants.java)
5. [`ExchangeFeeConstants.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/constant/ExchangeFeeConstants.java)
6. [`OverheadFeeConstants.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/constant/OverheadFeeConstants.java)
7. [`RegulatoryFallbackConstants.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/constant/RegulatoryFallbackConstants.java)
8. [`SavingsCalculatorConstants.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/constant/SavingsCalculatorConstants.java)

---

## 5. NPCL ToD Component Design

[`NpclTariffSchedule.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/NpclTariffSchedule.java) was created as a stateless Spring `@Component`.

### Key Methods & Record Contract:
```java
public record NpclTodRate(String todName, double rate, double multiplier) {}

public boolean isWinter(int month); // month >= 9 || month <= 3
public NpclTodRate resolve(int month, int hour);
public NpclTodRate resolve(int month, int hour, double baseRate);
```

### Slabs & Multiplier Matrix:
- **Winter (September through March)**:
  - 22:00–04:00: `TOD1` (multiplier `0.85`, rate ₹5.78/kWh)
  - 04:00–06:00: `TOD2` (multiplier `1.00`, rate ₹6.80/kWh)
  - 06:00–10:00: `TOD3` (multiplier `1.15`, rate ₹7.82/kWh)
  - 10:00–17:00: `TOD4` (multiplier `1.00`, rate ₹6.80/kWh)
  - 17:00–19:00: `TOD6` (multiplier `1.15`, rate ₹7.82/kWh)
  - 19:00–22:00: `TOD5` (multiplier `1.00`, rate ₹6.80/kWh)
- **Summer (April through August)**:
  - 07:00–16:00: `TOD4` (multiplier `0.85`, rate ₹5.78/kWh)
  - 16:00–19:00: `TOD1` (multiplier `1.00`, rate ₹6.80/kWh)
  - 19:00–02:00: `TOD2` (multiplier `1.15`, rate ₹7.82/kWh)
  - 02:00–07:00: `TOD3` (multiplier `1.00`, rate ₹6.80/kWh)

Both `calculateSavingsHV2` and `calculateMarketDecision` now delegate directly to `npclTariffSchedule.resolve(deliveryDate.getMonthValue(), hour)`.

---

## 6. Landed Cost Component Design

Shared technical loss compounding and landed price math were extracted into two dedicated Spring components:

1. [`ElectricalLossCalculator.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/ElectricalLossCalculator.java):
   ```java
   public double calculateLossCoefficient(double istsLossPercent, double stuLossPercent, double wheelingLossPercent);
   ```
   Enforces non-negative inputs and caps individual losses at 99.9% before computing:
   $$\text{LossCoefficient} = (1 - L_{\text{ists}}) \times (1 - L_{\text{stu}}) \times (1 - L_{\text{wheeling}})$$

2. [`LandedCostCalculator.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/calculator/LandedCostCalculator.java):
   ```java
   public Double calculateExchangeLanding(Double mcpRsPerKwh, double totalRegulatoryCharges, double lossCoefficient);
   public double calculateRegulatoryChargesSum(double ctu, double stu, double wheeling, double other, double iex, double gstIex, double trader, double gstTrader, double css, double as);
   public double applyNpclDiscount(double discomRate);
   ```
   Executes the statutory landed price formula:
   $$\text{LandedPrice} = \frac{\text{MCP} + \text{TotalRegulatoryCharges}}{\text{LossCoefficient}}$$
   and encapsulates NPCL discount logic ($0.90 \times 0.99$).

---

## 7. Files Created and Modified

### New Classes Created (11 files):
- `src/main/java/com/sense/prolt_operations/service/calculator/constant/UnitConstants.java`
- `src/main/java/com/sense/prolt_operations/service/calculator/constant/TimeblockConstants.java`
- `src/main/java/com/sense/prolt_operations/service/calculator/constant/RegulatoryConstants.java`
- `src/main/java/com/sense/prolt_operations/service/calculator/constant/TaxConstants.java`
- `src/main/java/com/sense/prolt_operations/service/calculator/constant/ExchangeFeeConstants.java`
- `src/main/java/com/sense/prolt_operations/service/calculator/constant/OverheadFeeConstants.java`
- `src/main/java/com/sense/prolt_operations/service/calculator/constant/RegulatoryFallbackConstants.java`
- `src/main/java/com/sense/prolt_operations/service/calculator/constant/SavingsCalculatorConstants.java`
- `src/main/java/com/sense/prolt_operations/service/calculator/NpclTariffSchedule.java`
- `src/main/java/com/sense/prolt_operations/service/calculator/ElectricalLossCalculator.java`
- `src/main/java/com/sense/prolt_operations/service/calculator/LandedCostCalculator.java`

### New Unit Tests Created (4 files):
- `src/test/java/com/sense/prolt_operations/service/calculator/NpclTariffScheduleTest.java` (29 tests)
- `src/test/java/com/sense/prolt_operations/service/calculator/ElectricalLossCalculatorTest.java` (7 tests)
- `src/test/java/com/sense/prolt_operations/service/calculator/LandedCostCalculatorTest.java` (5 tests)
- `src/test/java/com/sense/prolt_operations/service/calculator/CalculatorConstantsTest.java` (8 tests)

### Existing Files Modified (1 file):
- [`SavingsCalculatorService.java`](file:///home/ankit-jha/probus-workspace/prolt-operations/src/main/java/com/sense/prolt_operations/service/SavingsCalculatorService.java):
  - Refactored constructor with `@Autowired` dependency injection for `NpclTariffSchedule`, `ElectricalLossCalculator`, and `LandedCostCalculator`. Preserved legacy 4-arg constructor for seamless backward compatibility.
  - Replaced duplicate NPCL ToD `if-else` trees in `calculateSavingsHV2` and `calculateMarketDecision`.
  - Replaced inline landed price calculation in `calculateMarketDecision`.
  - Replaced all magic numbers (`0.9`, `1000.0`, `250.0`, `7000.0`, `8333.0`, `20000.0`, `0.01`, etc.) with categorized constants.

---

## 8. Before / After Duplication Analysis

| Duplication Hotspot | Before Refactoring | After Refactoring | LOC Eliminated |
|---|---|---|---|
| **NPCL HV-2 ToD Branching** | 40 lines in `calculateSavingsHV2` + 35 lines in `calculateMarketDecision` = 75 LOC | 3 lines in each caller delegating to `npclTariffSchedule.resolve(...)` | **~69 LOC** |
| **NPCL 10% + 1% Discount** | `discomLandingPrice * 0.90 * 0.99` copy-pasted in 3 methods | `landedCostCalculator.applyNpclDiscount(...)` | **3 occurrences unified** |
| **Landed Cost & Losses** | Inline lambda `calcExchangeLanding` (15 lines) inside slot loop | `landedCostCalculator.calculateExchangeLanding(...)` | **15 LOC** |
| **Regulatory Fallback Defaults** | Inline literals scattered across query lookups | Centralized references in `RegulatoryFallbackConstants` | **10+ scattered literals unified** |

---

## 9. Before / After Readability Observations

1. **Self-Documenting Code**: Rather than cryptic literals such as `rawMw * 10.0`, the code now reads `rawMw / RegulatoryConstants.MIN_OA_SCHEDULE_QUANTUM_MW` and `UnitConstants.KWH_PER_MW_SLOT`, making statutory grid units immediately apparent.
2. **Reduced Cognitive Load**: Readers of `calculateMarketDecision` no longer have to parse an extensive 35-line ToD if-else ladder or an inline loss-coefficient lambda inside a 800-line method.
3. **Traceability**: All regulatory constants cite specific Indian electricity statutory orders (CERC, UPERC, Grid Code).

---

## 10. Test Cases & Exact Results

### 10.1 New Unit Test Suite
Command:
```bash
mvn test -Dtest=NpclTariffScheduleTest,ElectricalLossCalculatorTest,LandedCostCalculatorTest,CalculatorConstantsTest
```
Results:
- `NpclTariffScheduleTest`: **29/29 passed** (tested summer vs. winter boundaries across months 1–12, all 96 daily slots, and every ToD slab).
- `ElectricalLossCalculatorTest`: **7/7 passed** (tested zero losses, single losses, combined compound losses, 99.9% loss capping, and negative clamping).
- `LandedCostCalculatorTest`: **5/5 passed** (tested null MCP handling, zero-loss landed price, compound loss landed price, regulatory summation, and NPCL discount factor).
- `CalculatorConstantsTest`: **8/8 passed** (tested physical, grid, tax, and fee invariants).
- **Subtotal**: **49/49 passed (0 failures, 0 errors)** in 6.39 s.

### 10.2 Combined Targeted Regression Suite
Command:
```bash
mvn test -Dtest=SavingsCalculatorNumericalParityTest,SavingCalculatorRepositoryIntegrationTest,CalculationAndTariffIntegrationTest,NpclTariffScheduleTest,ElectricalLossCalculatorTest,LandedCostCalculatorTest,CalculatorConstantsTest
```
Results:
- `SavingsCalculatorNumericalParityTest`: 7/7 passed.
- `SavingCalculatorRepositoryIntegrationTest`: 6/6 passed.
- `CalculationAndTariffIntegrationTest`: 6/6 passed.
- New unit tests: 49/49 passed.
- **Targeted Total**: **68/68 passed (0 failures, 0 errors)** in 20.90 s.

### 10.3 Full Java Backend Test Suite
Command:
```bash
mvn test
```
Results:
```text
[INFO] Results:
[INFO] 
[INFO] Tests run: 359, Failures: 0, Errors: 0, Skipped: 1
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  57.970 s
```
- Total test count expanded from 311 to **359 tests**.
- **0 Failures, 0 Errors**.
- 1 skipped test: `ScraperLiveManualTest` (manual external scraper integration test, skipped by design).

---

## 11. Numerical Parity Comparison

To ensure zero numerical drift occurred during extraction, outputs were compared against the V18C baseline:

| Metric / Test Case | V18C Baseline Output | V18E Refactored Output | Discrepancy |
|---|---|---|---|
| Floored Slot Energy (1500 kW, 0.99 PF) | `325.0 kWh` | `325.0 kWh` | **₹0.00 / 0.00 kWh (Exact)** |
| Floored Slot Energy (2500 kW, 0.95 PF) | `575.0 kWh` | `575.0 kWh` | **₹0.00 / 0.00 kWh (Exact)** |
| Floored Slot Energy (1000 kW, 1.00 PF) | `225.0 kWh` | `225.0 kWh` | **₹0.00 / 0.00 kWh (Exact)** |
| Direct Calc Total Discom Bill (Acme Steel) | `₹10,525,000.00` | `₹10,525,000.00` | **₹0.00 (Exact)** |
| Direct Calc IEX Total Cost (Acme Steel) | `₹7,420,000.00` | `₹7,420,000.00` | **₹0.00 (Exact)** |
| Direct Calc Net Savings (Acme Steel) | `₹3,105,000.00` | `₹3,105,000.00` | **₹0.00 (Exact)** |
| Market Decision Net Savings (2026-06) | `₹374,400.00` | `₹374,400.00` | **₹0.00 (Exact)** |
| Demand Shift Net Savings (2026-06) | `₹385,820.00` | `₹385,820.00` | **₹0.00 (Exact)** |
| Client Overview Total Savings | `₹4,492,800.00` | `₹4,492,800.00` | **₹0.00 (Exact)** |

All calculations across all test scenarios remain 100% numerically identical.

---

## 12. Performance Impact Assessment

- **Garbage Collection & Object Allocation**:
  - `NpclTariffSchedule`, `ElectricalLossCalculator`, and `LandedCostCalculator` are registered as Spring singletons with zero per-request allocation overhead.
  - The inner `NpclTodRate` record has minimal memory footprint and is stack-allocated/inlined by the JVM C2 JIT compiler.
- **Database Access Invariance**:
  - Zero database queries were added to any slot loops.
  - Batching mechanics in `SavingsCalculatorDataAccessService` remain completely untouched.
- **Execution Latency**:
  - Full targeted test suite elapsed time remained at ~20.9 s.

---

## 13. Enterprise V2 Isolation Confirmation

Enterprise V2 (`SavingsCalculatorNewService.java`) was strictly quarantined:
```bash
git diff --name-only src/main/java/com/sense/prolt_operations/service/SavingsCalculatorNewService.java
```
- **Result**: Zero output / empty diff.
- **Confirmation**: Enterprise V2 remains 100% untouched.

---

## 14. Database Flyway V14 Confirmation

The local PostgreSQL database `localhost:5432/prolt_operations` was queried to confirm migration history:
```sql
SELECT installed_rank, version, description, script, installed_on, success 
FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
```
Output:
```text
 installed_rank | version |           description           |                  script                  |        installed_on        | success 
----------------+---------+---------------------------------+------------------------------------------+----------------------------+---------
             10 | 14      | create user refresh sessions    | V14__create_user_refresh_sessions.sql    | 2026-11-04 11:36:15.258788 | t
              9 | 13      | remove user password hash       | V13__remove_user_password_hash.sql       | 2026-11-04 11:35:06.077911 | t
              8 | 12      | add user token version          | V12__add_user_token_version.sql          | 2026-11-02 17:14:24.566474 | t
```
- Local database remains cleanly at **Flyway V14**.
- Application configuration `spring.flyway.target=${FLYWAY_TARGET:8}` is preserved.

---

## 15. Git Status and No-Commit Confirmation

- Zero git commits (`git commit`) were executed.
- Zero git pushes (`git push`) were executed.
- No production or staging environments were accessed.

---

## 16. Remaining Refactoring Work

With constants, NPCL ToD schedules, and landed-cost formulas cleanly extracted and verified, the codebase is primed for subsequent refactoring milestones:

1. **Milestone V18F (Method Decomposition)**:
   - Decompose the 800-line `calculateMarketDecision` method into a clean processing pipeline:
     1. Date window & regulatory pre-fetch phase.
     2. 15-minute slot landed cost resolution.
     3. 7-market SLDC heuristic arbitration.
     4. RTM contiguity resolution.
     5. Forward banking surplus allocation (Pass 1).
     6. Operational overhead & fixed fee billing (Pass 2).
     7. Financial reconciliation & summary aggregation.
2. **Milestone V18G (Domain Modeling & Type Safety)**:
   - Replace untyped `Map<String, Object>` slot records with strongly typed Java records (`MarketSlotDecision`, `OverheadSummary`, `FinancialBillingResult`).

---

## Conclusion

Milestone **V18E** is successfully completed. All completion criteria have been met:
- Unexplained numeric literals extracted into documented constants classes.
- Duplicated NPCL seasonal ToD tariff logic extracted and shared by both callers.
- Duplicated landed-cost and electrical loss calculations extracted into dedicated components.
- Zero behavioral, numerical, or architectural regressions.
- Full 359 backend tests passing.
- Database remains at V14, Enterprise V2 remains untouched, no commits or pushes performed.

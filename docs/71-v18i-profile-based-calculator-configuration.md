# Milestone V18I: Legacy Saving Calculator Profile-Based Configuration

## 1. Executive Summary

Milestone **V18I** transitions configurable business parameters, regulatory fallback charges, power exchange transaction fees, statutory tax rates, and operational dispatch overheads in the Java Legacy Saving Calculator from hardcoded static Java constants into a type-safe, validated Spring Boot profile-based configuration architecture (`CalculatorProperties`).

### Key Achievements
1. **Centralized Configuration Properties**: Created `CalculatorProperties` (`com.sense.prolt_operations.config`) with 6 strongly typed nested configuration classes (`Regulatory`, `Exchange`, `Tax`, `Fallback`, `Fees`, `Business`) bound under the prefix `calculator`.
2. **Fail-Fast Jakarta Bean Validation**: Decorated all 27 configurable properties with Jakarta validation constraints (`@DecimalMin`, `@DecimalMax`, `@Positive`, `@NotNull`, `@Valid`) preventing invalid startup states.
3. **Strict Precedence Model**: Formally established and verified the configuration precedence order:
   $$\text{Database Record Value} > \text{Configured Profile Fallback} > \text{Canonical Java Constant}$$
4. **Physical Invariants Preservation**: Pure structural, astronomical, and conversion invariants (e.g. 1000 kW/MW, 1000 kWh/MWh, 250 kWh/MW-slot, 96 slots/day, 15 min/slot, 24 hr/day, epsilon $10^{-9}$, and numerical transmission loss cap $99.9\%$) remain strictly locked in static constant classes.
5. **Multi-Environment Support**: Populated default values across `application.properties`, documented dev environment overrides in `application-dev.properties`, and established `application-test.properties` for testing.
6. **100% Backward Compatibility & Parity**: Retained non-breaking constructors across all 11 refactored calculator components/services, ensuring 100% test pass rate across the full 417-test suite with zero regressions.

---

## 2. Configuration Architecture

### Component Hierarchy
The configuration is orchestrated through `@Component @Validated @ConfigurationProperties(prefix = "calculator") class CalculatorProperties`:

```
calculator/
├── regulatory/
│   ├── min-oa-eligibility-kw (default: 1000.0)
│   ├── min-oa-schedule-quantum-mw (default: 0.1)
│   ├── contract-demand-pf-factor (default: 0.9)
│   ├── default-power-factor (default: 0.99)
│   └── npcl-rebate-discount-factor (default: 0.891)
├── exchange/
│   ├── default-exchange-fee-per-kwh (default: 0.02)
│   └── default-other-charges-per-kwh (default: 0.10)
├── tax/
│   └── gst (default: 0.18)
├── fallback/
│   ├── ctu-per-kwh (default: 0.45)
│   ├── stu-per-kwh (default: 0.25)
│   ├── wheeling-per-kwh (default: 0.20)
│   ├── cross-subsidy-per-kwh (default: 1.50)
│   ├── additional-surcharge-per-kwh (default: 0.50)
│   ├── stu-loss-percent (default: 3.5)
│   ├── wheeling-loss-percent (default: 4.0)
│   ├── demand-fixed-charge-kva (default: 350.0)
│   └── base-discom-rate (default: 7.50)
├── fees/
│   ├── discom-noc-monthly (default: 7000.0)
│   ├── sldc-monthly (default: 8333.0)
│   ├── consultancy-monthly (default: 20000.0)
│   ├── platform-fee-rate (default: 0.02)
│   ├── nldc-application-fee-per-bid (default: 5.0)
│   ├── nldc-daily-scheduling-fee (default: 2500.0)
│   └── sldc-daily-scheduling-fee (default: 1500.0)
└── business/
    ├── demand-shift-min-spread-rs (default: 0.50)
    └── default-prospect-consumption-kwh (default: 10000.0)
```

---

## 3. Configurable Properties Matrix

| Group | Property Path | Type | Canonical Default | Validation Constraint | Source Constant |
|---|---|---|---|---|---|
| **Regulatory** | `calculator.regulatory.min-oa-eligibility-kw` | `double` | `1000.0` | `@DecimalMin("0.0")` | `RegulatoryConstants.MIN_OA_ELIGIBILITY_KW` |
| **Regulatory** | `calculator.regulatory.min-oa-schedule-quantum-mw` | `double` | `0.1` | `@Positive` | `RegulatoryConstants.MIN_OA_SCHEDULE_QUANTUM_MW` |
| **Regulatory** | `calculator.regulatory.contract-demand-pf-factor` | `double` | `0.9` | `@DecimalMin("0.5") @DecimalMax("1.0")` | `RegulatoryConstants.CONTRACT_DEMAND_PF_FACTOR` |
| **Regulatory** | `calculator.regulatory.default-power-factor` | `double` | `0.99` | `@DecimalMin("0.5") @DecimalMax("1.0")` | `RegulatoryConstants.DEFAULT_POWER_FACTOR` |
| **Regulatory** | `calculator.regulatory.npcl-rebate-discount-factor` | `double` | `0.891` | `@DecimalMin("0.0") @DecimalMax("1.0")` | `RegulatoryConstants.NPCL_REBATE_DISCOUNT_FACTOR` |
| **Exchange** | `calculator.exchange.default-exchange-fee-per-kwh` | `double` | `0.02` | `@DecimalMin("0.0")` | `ExchangeFeeConstants.DEFAULT_EXCHANGE_FEE_PER_KWH` |
| **Exchange** | `calculator.exchange.default-other-charges-per-kwh` | `double` | `0.10` | `@DecimalMin("0.0")` | `ExchangeFeeConstants.DEFAULT_OTHER_CHARGES_PER_KWH` |
| **Tax** | `calculator.tax.gst` | `double` | `0.18` | `@DecimalMin("0.0") @DecimalMax("1.0")` | `TaxConstants.STATUTORY_GST_RATE` |
| **Fallback** | `calculator.fallback.ctu-per-kwh` | `double` | `0.45` | `@DecimalMin("0.0")` | `RegulatoryFallbackConstants.DEFAULT_CTU_PER_KWH` |
| **Fallback** | `calculator.fallback.stu-per-kwh` | `double` | `0.25` | `@DecimalMin("0.0")` | `RegulatoryFallbackConstants.DEFAULT_STU_PER_KWH` |
| **Fallback** | `calculator.fallback.wheeling-per-kwh` | `double` | `0.20` | `@DecimalMin("0.0")` | `RegulatoryFallbackConstants.DEFAULT_WHEELING_PER_KWH` |
| **Fallback** | `calculator.fallback.cross-subsidy-per-kwh` | `double` | `1.50` | `@DecimalMin("0.0")` | `RegulatoryFallbackConstants.DEFAULT_CROSS_SUBSIDY_PER_KWH` |
| **Fallback** | `calculator.fallback.additional-surcharge-per-kwh` | `double` | `0.50` | `@DecimalMin("0.0")` | `RegulatoryFallbackConstants.DEFAULT_ADDITIONAL_SURCHARGE_PER_KWH` |
| **Fallback** | `calculator.fallback.stu-loss-percent` | `double` | `3.5` | `@DecimalMin("0.0") @DecimalMax("99.9")` | `RegulatoryFallbackConstants.DEFAULT_STU_LOSS_PERCENT` |
| **Fallback** | `calculator.fallback.wheeling-loss-percent` | `double` | `4.0` | `@DecimalMin("0.0") @DecimalMax("99.9")` | `RegulatoryFallbackConstants.DEFAULT_WHEELING_LOSS_PERCENT` |
| **Fallback** | `calculator.fallback.demand-fixed-charge-kva` | `double` | `350.0` | `@DecimalMin("0.0")` | `RegulatoryFallbackConstants.DEFAULT_DEMAND_FIXED_CHARGE_KVA` |
| **Fallback** | `calculator.fallback.base-discom-rate` | `double` | `7.50` | `@DecimalMin("0.0")` | `RegulatoryFallbackConstants.DEFAULT_BASE_DISCOM_RATE` |
| **Fees** | `calculator.fees.discom-noc-monthly` | `double` | `7000.0` | `@DecimalMin("0.0")` | `OverheadFeeConstants.DEFAULT_DISCOM_NOC_FEE_MONTHLY` |
| **Fees** | `calculator.fees.sldc-monthly` | `double` | `8333.0` | `@DecimalMin("0.0")` | `OverheadFeeConstants.DEFAULT_SLDC_MONTHLY_FEE` |
| **Fees** | `calculator.fees.consultancy-monthly` | `double` | `20000.0` | `@DecimalMin("0.0")` | `OverheadFeeConstants.DEFAULT_CONSULTANCY_FEE_MONTHLY` |
| **Fees** | `calculator.fees.platform-fee-rate` | `double` | `0.02` | `@DecimalMin("0.0")` | `OverheadFeeConstants.DEFAULT_PROBUS_PLATFORM_FEE_RATE` |
| **Fees** | `calculator.fees.nldc-application-fee-per-bid` | `double` | `5.0` | `@DecimalMin("0.0")` | `OverheadFeeConstants.NLDC_APPLICATION_FEE_PER_BID` |
| **Fees** | `calculator.fees.nldc-daily-scheduling-fee` | `double` | `2500.0` | `@DecimalMin("0.0")` | `OverheadFeeConstants.DEFAULT_NLDC_DAILY_SCHEDULING_FEE` |
| **Fees** | `calculator.fees.sldc-daily-scheduling-fee` | `double` | `1500.0` | `@DecimalMin("0.0")` | `OverheadFeeConstants.DEFAULT_SLDC_DAILY_SCHEDULING_FEE` |
| **Business** | `calculator.business.demand-shift-min-spread-rs` | `double` | `0.50` | `@DecimalMin("0.0")` | `SavingsCalculatorConstants.DEMAND_SHIFT_MIN_SPREAD_RS` |
| **Business** | `calculator.business.default-prospect-consumption-kwh` | `double` | `10000.0` | `@Positive` | `SavingsCalculatorConstants.DEFAULT_PROSPECT_CONSUMPTION_KWH` |

---

## 4. Components Refactored with Property Injection

All components receive `CalculatorProperties` via Spring `@Autowired` constructors, while offering non-Spring default constructors chaining to `new CalculatorProperties()` for backward compatibility:

1. **`SavingsCalculatorService`**:
   - Injected `CalculatorProperties properties`. Added 16-parameter `@Autowired` constructor.
   - Updated `prepareMarketDecisionContext`: uses configured fallbacks for CTU, STU, Wheeling, CSS, AS, STU Loss, Wheeling Loss, Demand Fixed Charge, NLDC & SLDC daily scheduling fees, and GST tax rate.
   - Updated `calculateSlotLandingCosts`: uses configured exchange fees, other charges, statutory GST, minimum OA eligibility threshold, fallback DISCOM base rate, and power factor.
   - Overloaded `getFlooredMaxEnergyPerSlot(sanctionedLoadKw, powerFactor, properties)`.
2. **`LandedCostCalculator`**:
   - Injected `CalculatorProperties`. Added 2-arg constructor `(ElectricalLossCalculator, CalculatorProperties)`.
   - Updated `applyNpclDiscount`: dynamically scales base tariff by `properties.getRegulatory().getNpclRebateDiscountFactor()`.
3. **`OperationalOverheadCalculator`**:
   - Injected `CalculatorProperties`. Added 1-arg constructor.
   - Updated `calculate`: pulls `nldcApplicationFeePerBid`, `nldcDailySchedulingFee`, and `sldcDailySchedulingFee` from configured properties when database values are missing or zero.
4. **`FinancialLedgerReconciler`**:
   - Injected `CalculatorProperties`. Added 1-arg constructor.
   - Updated `reconcile`: pulls `discomNocMonthly`, `sldcMonthly`, `consultancyMonthly`, `platformFeeRate`, `defaultOtherChargesPerKwh`, `defaultExchangeFeePerKwh`, and `gst` rate from configured properties.
5. **`DemandShiftOptimizer`**:
   - Injected `CalculatorProperties`. Added 1-arg constructor.
   - Updated `calculateDemandShiftInsights`: pulls `minOaEligibilityKw`, `demandShiftMinSpreadRs`, and passes `properties` to `getFlooredMaxEnergyPerSlot`.
6. **`Hv2SavingsCalculator`**:
   - Injected `CalculatorProperties`. Added 5-arg constructor.
   - Updated 96-slot dispatch calculations to use configured fallback base DISCOM rate and minimum OA eligibility threshold.
7. **`ClientOverviewAggregator`**:
   - Injected `CalculatorProperties`. Added 1-arg constructor.
   - Updated monthly aggregation fixed fee deductions (`discomNocMonthly`, `sldcMonthly`) and fallback power factor.

---

## 5. Verification & Test Evidence

### 5.1 Unit Tests (`CalculatorPropertiesTest`)
- Location: `src/test/java/com/sense/prolt_operations/config/CalculatorPropertiesTest.java`
- Tests run: **15**, Failures: **0**, Errors: **0**, Skipped: **0**
- Test Coverage:
  - Canonical Defaults Parity across all 6 groups.
  - Jakarta validation constraint enforcement (negative fees, out-of-bounds power factor, negative/excessive GST, negative consumption).
  - Spring Boot profile property binding and override verification via `ApplicationContextRunner`.

### 5.2 Integration Tests (`CalculatorConfigurationPrecedenceIntegrationTest`)
- Location: `src/test/java/com/sense/prolt_operations/service/calculator/CalculatorConfigurationPrecedenceIntegrationTest.java`
- Tests run: **7**, Failures: **0**, Errors: **0**, Skipped: **0**
- Test Coverage:
  - Database values override configured properties when database records exist (`CtuCharges`, `StateCharges`, `IexFees`).
  - Configured property overrides are strictly used when database records are absent.
  - Component sensitivity verification (`LandedCostCalculator`, `OperationalOverheadCalculator`, `FinancialLedgerReconciler`, `DemandShiftOptimizer`).

### 5.3 Targeted Test Suite
- Test Classes: `CalculatorPropertiesTest`, `CalculatorConfigurationPrecedenceIntegrationTest`, `SavingsCalculatorNumericalParityTest`, `SavingCalculatorRepositoryIntegrationTest`, `CalculationAndTariffIntegrationTest`, `NpclTariffScheduleTest`, `ElectricalLossCalculatorTest`, `LandedCostCalculatorTest`, `CalculatorConstantsTest`, `CalculatorModelsTest`, `SevenMarketArbitratorTest`, `RtmContiguitySolverTest`, `ForwardBankingAllocatorTest`, `OperationalOverheadCalculatorTest`, `CalculatorUtilsTest`, `DemandShiftOptimizerTest`, `Hv2SavingsCalculatorTest`, `ClientOverviewAggregatorTest`.
- Tests run: **126**, Failures: **0**, Errors: **0**, Skipped: **0** (Execution time: 15.59s).

### 5.4 Full Repository Regression Suite (`mvn test`)
- Full test suite execution across all modules:
  - Tests run: **417**
  - Failures: **0**
  - Errors: **0**
  - Skipped: **1** (by design: `UploadLimitsIntegrationTest.testLargePayloadBlockedAtFilter`)
  - Execution time: 44.79s
  - Build Status: **BUILD SUCCESS**

---

## 6. Invariants & Scope Compliance

1. **Enterprise V2 Isolation**:
   - Executed `git diff src/main/java/com/sense/prolt_operations/service/SavingsCalculatorNewService.java` $\to$ Empty output (0 lines changed, 100% untouched).
2. **Node.js Reference Repository**:
   - Read-only reference inspection only; zero modifications made.
3. **Database Integrity**:
   - Inspected `flyway_schema_history` on `localhost:5432/prolt_operations` $\to$ Schema remains at Version 14 with zero new migrations.
4. **Git Discipline**:
   - `git status` verifies all changes remain in the working directory; no commits or pushes were performed.

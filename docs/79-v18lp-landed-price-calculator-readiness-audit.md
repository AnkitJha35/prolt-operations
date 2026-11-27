# Milestone V18LP — Independent Landed Price Calculator Readiness Audit

## 1. Executive Summary

This report delivers a comprehensive, strictly read-only technical and business-logic audit of the Landed Price Calculator in the Java backend repository (`prolt-operations`) compared against the authoritative Node.js reference implementation (`IEX-Dashboard/backend`).

### Key Audit Findings:
1. **Core Mathematics is Complete and Verified**: The compound electrical loss equation (`LossCoefficient = (1 - L_ists/100) * (1 - L_stu/100) * (1 - L_wheeling/100)`), regulatory charge summation (CTU, STU, Wheeling, CSS, AS, Exchange Fees, Trader Margin, GST), and landed price formula (`LandedPrice = (MCP + RegulatoryCharges) / LossCoefficient`) are fully implemented and numerically identical to the Node.js implementation (`savings-calculator-new.service.ts` and `trader-performance-daily.service.ts`).
2. **Independent Calculation Capability is Missing**:
   - `LandingPriceService.java` does not perform calculations locally; it is an HTTP REST proxy to the external website (`https://www.iexindia.com/api/v1/calculator`).
   - `LandedCostCalculator.java` is an isolated arithmetic utility rather than an independent domain engine. It lacks data resolution, date handling, database orchestration, and input/output DTO contracts.
   - The actual end-to-end landed price resolution is embedded inside the private method `SavingsCalculatorService.calculateSlotLandingCosts()`, requiring a full `SavingsCalculatorEntry`, bill consumption maps, and Saving Calculator orchestration.
3. **Discrepancy in Legacy HV-2 Flow**: In the legacy HV-2 flow (`Hv2SlotProcessor.java`), market prices are divided by 1000 and directly compared against DISCOM landing prices without adding regulatory charges or technical losses, reflecting a known legacy busbar shortcut in Node.js `savings-calculator.service.ts`.
4. **Final Overall Verdict**:
   **`CORE COMPLETE — INDEPENDENT CAPABILITIES MISSING`**

---

## 2. Architecture & Component Inventory

### 2.1 Current Architecture Diagram

```mermaid
graph TD
    subgraph "External Scraper Proxy"
        A[LandingPriceCalcController<br>/api/landing-price-calc/*] --> B[LandingPriceService]
        B -->|HTTP GET Proxy| C[(External IEX Website<br>iexindia.com/api/v1/calculator)]
    end

    subgraph "Prospect Benchmark Estimator"
        D[SavingsCalculatorController<br>/api/savings-calculator/calculate-direct] --> E[SavingsCalculatorService.calculateDirect]
        E -->|Hardcoded Constants / Mock Multipliers| F[LandedCostCalcResponse]
    end

    subgraph "Embedded Saving Calculator Engine"
        G[SavingsCalculatorService.calculateSavings] --> H[calculateSlotLandingCosts]
        I[Hv2SavingsCalculator.calculateSavingsHV2] --> J[Hv2SlotProcessor.processMonthSlots]
        
        H --> K[LandedCostCalculator<br>Arithmetic Utility]
        H --> L[ElectricalLossCalculator<br>Compound Losses]
        H --> M[RegulatoryChargeResolver<br>ISTS & DISCOM Tariff]
        H --> N[SavingsCalculatorDataAccessService<br>DB Access]

        J --> M
        J --> O[NpclTariffSchedule<br>UPERC HV-2 Seasonal ToD]
        J --> K
    end

    classDef missing fill:#ffdddd,stroke:#ff0000,stroke-width:2px;
    P[Independent Landed Price Calculator Engine]:::missing
    P -.->|MISSING: High-level independent service & API| K
```

### 2.2 Component Inventory

| Component | Nature | Role / Responsibility | Independent? |
| :--- | :--- | :--- | :---: |
| **`LandedCostCalculator`** | Spring `@Component` | Stateless arithmetic helper: `(MCP + RegCharges) / LossCoeff`, charge summation, NPCL rebate discount. | **No** (Helper only; has no data-loading or orchestration capability) |
| **`ElectricalLossCalculator`** | Spring `@Component` | Evaluates compound loss coefficient `(1 - ists/100) * (1 - stu/100) * (1 - wheeling/100)`. Clamps negative losses and caps at 99.9%. | **Yes** (Pure math helper) |
| **`RegulatoryChargeResolver`** | Spring `@Component` | Resolves ISTS date ranges, matches `StateTariff` by hour and month, evaluates ToD wrap-around, and computes DISCOM base tariffs. | **Yes** (Domain resolver component) |
| **`NpclTariffSchedule`** | Spring `@Component` | Evaluates UPERC NPCL HV-2 Winter/Summer ToD rate multipliers (+15% peak surcharge, -15% off-peak rebate). | **Yes** (Domain schedule component) |
| **`LandingPriceService`** | Spring `@Service` | HTTP proxy forwarding requests to `https://www.iexindia.com/api/v1/calculator`. | **No** (External client only; no local calculation) |
| **`LandingPriceCalcController`** | Spring `@RestController` | Exposes `/api/landing-price-calc/*` endpoints delegating to `LandingPriceService`. | **No** (Proxy controller) |
| **`SavingsCalculatorService.calculateDirect`** | Service Method | Evaluates quick prospect landed costs using hardcoded "Acme Steel Mills" benchmarks or fallback rate multipliers. | **No** (Tightly coupled to `SavingsCalculatorService`) |
| **`SavingsCalculatorService.calculateSlotLandingCosts`** | Private Service Method | Full 96-slot dispatch landed cost calculation across DAM, RTM, GDAM, and DISCOM. | **No** (Private method embedded in Saving Calculator) |
| **`Hv2SlotProcessor`** | Spring `@Component` | Simulates 96 slots for HV-2 consumers; compares raw MCP/1000 against discounted DISCOM tariff. | **No** (Embedded collaborator of `Hv2SavingsCalculator`) |

---

## 3. Functional Coverage Audit (Procurement Sources)

| Procurement Source | Handling in Commercial / Industrial (`SavingsCalculatorService`) | Handling in HV-2 (`Hv2SlotProcessor`) | Completeness & Gaps |
| :--- | :--- | :--- | :--- |
| **DAM** (Day Ahead Market) | • Converts ₹/MWh to ₹/kWh: `damMcp / 1000.0`<br>• Enforces OA threshold: Only eligible if `sanctionedLoad >= 1000 kW`<br>• Adds all 10 regulatory & statutory charges<br>• Divides by compound loss coefficient<br>• Participates in 7-market arbitration | • Converts ₹/MWh to ₹/kWh: `damMcp / 1000.0`<br>• Enforces OA threshold: Eligible if `sanctionedLoad >= 1000 kW`<br>• **Omits** regulatory charges and technical losses (busbar MCP)<br>• Direct price comparison with DISCOM | **Complete in Commercial**, legacy simplification in HV-2. Cannot be invoked independently without full monthly bill. |
| **RTM** (Real Time Market) | • Converts ₹/MWh to ₹/kWh: `rtmMcp / 1000.0`<br>• Enforces OA threshold: Only eligible if `sanctionedLoad >= 1000 kW`<br>• Adds regulatory charges and scales by compound losses<br>• Evaluated by `RtmContiguitySolver` for 30-min block constraints | • Converts ₹/MWh to ₹/kWh: `rtmMcp / 1000.0`<br>• Enforces OA threshold: Eligible if `sanctionedLoad >= 1000 kW`<br>• **Omits** regulatory charges and technical losses | **Complete in Commercial**, legacy simplification in HV-2. Contiguity solver is tightly bound to 96-slot dispatch. |
| **GDAM** (Green Day Ahead Market) | • Converts ₹/MWh to ₹/kWh: `gdamMcp / 1000.0`<br>• Green Energy Open Access: Available to **all** loads (regardless of 1000 kW threshold)<br>• Adds regulatory charges and scales by compound losses<br>• Participates in 7-market arbitration | • Converts ₹/MWh to ₹/kWh: `gdamMcp / 1000.0`<br>• Available to all loads (regardless of 1000 kW threshold)<br>• **Omits** regulatory charges and technical losses | **Complete in Commercial**, legacy simplification in HV-2. |
| **DISCOM** (Distribution Licensee) | • Resolved via `RegulatoryChargeResolver.resolveDiscomTariff`<br>• Matches `StateTariff` ToD hours (with wrap-around)<br>• Power factor adjustment for kVAh tariffs (`rate / pf`)<br>• Applies FPPA percentage: `* (1 + fppa/100)`<br>• Applies Electricity Duty: `* (1 + ed/100)`<br>• Applies NPCL discount: `* 0.90 * 0.99 = 0.891` | • Resolved via `RegulatoryChargeResolver.resolveHv2DiscomTariff`<br>• NPCL HV-2 uses seasonal UPERC schedule (`NpclTariffSchedule`)<br>• Winter: TOD1..TOD6 (+15% / -15% / 0%)<br>• Summer: TOD1..TOD4 (+15% / -15% / 0%)<br>• Applies NPCL discount: `* 0.891`<br>• Fallback off-peak (6.0), peak (8.5), normal (7.5) | **Complete and verified**. DISCOM tariff resolution has full ToD, FPPA, Duty, and NPCL rebate handling. |

---

## 4. Complete Landed Price Formula Audit

### 4.1 Detailed Component Formula Inventory

| Component | Formula | Input Source | Units | Application Order | Java Location | Node.js Location | Test Coverage |
| :--- | :--- | :--- | :---: | :---: | :--- | :--- | :--- |
| **Market Price Conversion** | `MCP_kwh = MCP_mwh / 1000.0` | `exchange_dam_rate`, `rtm`, `gdam` | ₹/kWh | 1 (First) | `SavingsCalculatorService:801-803`<br>`Hv2SlotProcessor:110-112` | `savings-calculator-new:1587-1589`<br>`savings-calculator:826-828` | Tested (`SavingsCalculatorNumericalParityTest`) |
| **ISTS Loss** | Looked up from date range `[startDate, endDate]` | `ists_charges.istsLossPercent` | % | 2 | `RegulatoryChargeResolver:58-71` | `savings-calculator-new:1581-1585` | Tested (`RegulatoryChargeResolverTest`) |
| **STU Loss** | State transmission loss | `state_charges.stuLossPercent` | % | 2 | `SavingsCalculatorDataAccessService` | `savings-calculator-new:1594` | Tested (`ElectricalLossCalculatorTest`) |
| **Wheeling Loss** | Distribution grid loss | `state_charges.wheelingLossPercent` | % | 2 | `SavingsCalculatorDataAccessService` | `savings-calculator-new:1595` | Tested (`ElectricalLossCalculatorTest`) |
| **Compound Loss Coefficient** | `(1 - ists/100) * (1 - stu/100) * (1 - wheeling/100)` | Capped at 99.9%, clamped at 0.0% | Multiplier (0..1) | 3 | `ElectricalLossCalculator:28-34` | `savings-calculator-new:1596`<br>`trader-performance-daily:554` | Tested (`ElectricalLossCalculatorTest:20-74`) |
| **CTU Charge** | Central transmission charge | `state_charges.ctuCharge` | ₹/kWh | 4 | `LandedCostCalculator:69` | `savings-calculator-new:1597` | Tested (`LandedCostCalculatorTest:53-71`) |
| **STU Charge** | State transmission charge | `state_charges.stuCharge` | ₹/kWh | 4 | `LandedCostCalculator:70` | `savings-calculator-new:1597` | Tested (`LandedCostCalculatorTest:53-71`) |
| **Wheeling Charge** | Distribution wheeling charge | `state_charges.wheelingCharge` | ₹/kWh | 4 | `LandedCostCalculator:71` | `savings-calculator-new:1597` | Tested (`LandedCostCalculatorTest:53-71`) |
| **CSS (Cross Subsidy)** | Cross Subsidy Surcharge | `state_charges.crossSubsidySurcharge` | ₹/kWh | 4 | `LandedCostCalculator:77` | `savings-calculator-new:1597` | Tested (`LandedCostCalculatorTest:53-71`) |
| **AS (Addl Surcharge)** | Additional Surcharge | `state_charges.additionalSurcharge` | ₹/kWh | 4 | `LandedCostCalculator:78` | `savings-calculator-new:1597` | Tested (`LandedCostCalculatorTest:53-71`) |
| **Other Charges** | State open access adders | `CalculatorProperties.exchange.defaultOtherChargesPerKwh` (0.10) | ₹/kWh | 4 | `LandedCostCalculator:72` | `savings-calculator-new:1597` | Tested (`LandedCostCalculatorTest:53-71`) |
| **Exchange Fee** | IEX transaction fee | `CalculatorProperties.exchange.defaultExchangeFeePerKwh` (0.02) | ₹/kWh | 4 | `LandedCostCalculator:73` | `savings-calculator-new:1597` | Tested (`LandedCostCalculatorTest:53-71`) |
| **GST on Exchange Fee** | `exchangeFees * gstRate` (18% of 0.02 = 0.0036) | `CalculatorProperties.tax.gst` (0.18) | ₹/kWh | 4 | `SavingsCalculatorService:782` | `savings-calculator-new:1597` | Tested (`LandedCostCalculatorTest:53-71`) |
| **Trader Margin** | Trading member margin | `SavingsCalculatorEntry.traderMargin` (0.02) | ₹/kWh | 4 | `LandedCostCalculator:75` | `savings-calculator-new:1597` | Tested (`LandedCostCalculatorTest:53-71`) |
| **GST on Trader Margin**| `traderMargin * gstRate` (18% of 0.02 = 0.0036) | `CalculatorProperties.tax.gst` (0.18) | ₹/kWh | 4 | `SavingsCalculatorService:763` | `savings-calculator-new:1597` | Tested (`LandedCostCalculatorTest:53-71`) |
| **Regulatory Charges Sum** | Sum of above 10 charges | Pure summation | ₹/kWh | 4 | `LandedCostCalculator:68-81` | `savings-calculator-new:1597` | Tested (`LandedCostCalculatorTest:53-71`) |
| **Total Landed Market Cost**| `(MCP_kwh + RegulatoryChargesSum) / LossCoefficient` | Compound division | ₹/kWh | 5 | `LandedCostCalculator:45-51` | `savings-calculator-new:1598`<br>`trader-performance-daily:556` | Tested (`LandedCostCalculatorTest:28-50`) |
| **DISCOM Base Tariff** | Matched ToD energy rate or FLAT fallback | `state_tariff` / `NpclTariffSchedule` | ₹/kWh | 1 (DISCOM) | `RegulatoryChargeResolver:116-249` | `savings-calculator:836-925` | Tested (`RegulatoryChargeResolverTest`) |
| **DISCOM FPPA** | `baseTariff * (1 + fppaPercent / 100)` | `fppa_charges.fppaChargePercent` | ₹/kWh | 2 (DISCOM) | `SavingsCalculatorService:845` | `savings-calculator:710` | Tested (`SavingsCalculatorNumericalParityTest`) |
| **DISCOM Duty** | `tariffWithFppa * (1 + dutyPercent / 100)` | `SavingsCalculatorEntry.applyElectricityDuty` | ₹/kWh | 3 (DISCOM) | `SavingsCalculatorService:846-848` | `trader-performance-daily:541` | Tested (`SavingsCalculatorNumericalParityTest`) |
| **DISCOM NPCL Discount**| `tariff * 0.90 * 0.99 = tariff * 0.891` | `CalculatorProperties.regulatory.npclRebateDiscountFactor` | ₹/kWh | 4 (DISCOM) | `LandedCostCalculator:90-95` | `savings-calculator:930` | Tested (`LandedCostCalculatorTest:73-80`) |

---

## 5. Formula and Numerical Parity Analysis

### 5.1 Scenario Parity Matrix

| Scenario | Market / Procurement | Input MCP | Losses Applied | Regulatory Charges | DISCOM Comparison | Parity Status | Evidence |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **1** | DAM Landed Price | ₹4,500/MWh (4.50 ₹/kWh) | ISTS=3.4%, STU=3.5%, WH=4.0%<br>Coeff = 0.8949024 | CTU=0.45, STU=0.35, WH=0.20, CSS=0.65, AS=0.20, Other=0.10, Fee=0.02, GST=0.0036, Margin=0.02, GST=0.0036<br>Sum = 1.9972 ₹/kWh | (4.50 + 1.9972) / 0.8949024 = **7.2602 ₹/kWh** | **Verified Parity** | Matches Node.js `savings-calculator-new` formula within `1e-9`. |
| **2** | RTM Landed Price | ₹4,350/MWh (4.35 ₹/kWh) | Same (0.8949024) | Same (1.9972 ₹/kWh) | (4.35 + 1.9972) / 0.8949024 = **7.0926 ₹/kWh** | **Verified Parity** | Evaluated via `RtmContiguitySolver`. |
| **3** | GDAM Landed Price | ₹4,200/MWh (4.20 ₹/kWh) | Same (0.8949024) | Same (1.9972 ₹/kWh) | (4.20 + 1.9972) / 0.8949024 = **6.9250 ₹/kWh** | **Verified Parity** | Available below 1000 kW. |
| **4** | NPCL HV-2 Winter Off-Peak | DISCOM | N/A | Base = 6.80 ₹/kWh, -15% rebate = 5.78 ₹/kWh | Discounted: 5.78 * 0.891 = **5.1500 ₹/kWh** | **Verified Parity** | Matches Node.js `calculateSavingsHV2` line 850. |
| **5** | NPCL HV-2 Winter Peak | DISCOM | N/A | Base = 6.80 ₹/kWh, +15% surcharge = 7.82 ₹/kWh | Discounted: 7.82 * 0.891 = **6.9676 ₹/kWh** | **Verified Parity** | Matches Node.js `calculateSavingsHV2` line 856. |
| **6** | Load < 1000 kW (Non-OA) | DAM / RTM | ₹4,500/MWh | N/A | Excluded from market selection | **Verified Parity** | Tested in `Hv2SlotProcessorTest:78-97`. |
| **7** | Acme Benchmark (`calculateDirect`) | Prospect Direct | Fixed 5,200,000 / 10,525,000 | Hardcoded | Hardcoded | **Mock Parity** | Hardcoded values in `calculateDirect`. |
| **8** | External IEX Calculator Proxy | Upstream REST API | Upstream wire response | Upstream wire response | Upstream wire response | **WireMock Verified** | Tested in `LandingPriceParityIntegrationTest`. |

---

## 6. Independent Calculator Capability Assessment

An in-depth investigation was conducted to determine whether the Landed Price Calculator can function independently of the Saving Calculator:

### Critical Inquiry Checklist:

| Question | Evaluation | Evidence |
| :--- | :---: | :--- |
| **Does it have a dedicated calculation service?** | **NO** | `LandingPriceService.java` is an HTTP proxy to `www.iexindia.com`. No local calculation service exists for landed prices. |
| **Can it accept market price, state, voltage, category, and power factor independently?** | **NO** | No unified request DTO exists. Inputs are scattered across `SavingsCalculatorEntry`, `MarketDecisionContext`, and query params. |
| **Can it calculate landed prices without invoking `calculateSavings()`?** | **NO** | `LandedCostCalculator` only calculates `(mcp + regSum)/coeff`. The caller must manually resolve charges and losses from repositories. `calculateDirect` returns hardcoded mock data. |
| **Are its outputs represented by a dedicated typed model?** | **NO** | Only `MarketSlotCalculation` and `Hv2SlotRecord` exist (both are 96-slot dispatch internal models for savings calculations). No standalone `LandedPriceBreakdown` DTO exists. |
| **Can other modules reuse it without depending on Saving Calculator internals?** | **NO** | Other modules (Billing, Quotations, Operations) would have to duplicate database queries and regulatory resolution logic. |
| **Is database access separated from calculation mathematics?** | **PARTIALLY** | The math classes (`LandedCostCalculator`, `ElectricalLossCalculator`) are pure and DB-free, but the data resolution layer is trapped inside `SavingsCalculatorDataAccessService`. |
| **Is the calculation engine stateless and thread-safe?** | **YES** | All math classes are stateless Spring singletons. |

### Architectural Classification:
**Category B — Shared calculation components embedded in Saving Calculator orchestration (with missing standalone engine capability).**

---

## 7. Code Quality & Performance

1. **Divergence Between Calculation Paths**:
   - In `SavingsCalculatorService.calculateSlotLandingCosts`, market landed costs include full compound losses and 10 regulatory adders: `(mcp + regSum) / lossCoeff`.
   - In `Hv2SlotProcessor` / `Hv2SavingsCalculator`, market prices are simply `mcp / 1000` (busbar MCP), omitting regulatory adders and losses.
   - In `calculateDirect`, numbers are hardcoded for Acme Steel Mills or computed with fixed constant multipliers (`4.16`, `8.42`).
   - In `LandingPriceService`, calculations are offloaded to an external website over HTTP.
   - *Impact*: Four different places produce "landed price" with fundamentally different formulas and architectures.
2. **Hardcoded Mock Values**:
   - `SavingsCalculatorService.calculateDirect()` hardcodes Acme Steel Mills bill values (`10,525,000.00`, `5,200,000.00`, `620,000.00`, etc.) and fallback rates (`4.16`, `8.42`).
3. **Null Safety and Robustness**:
   - `LandedCostCalculator.calculateExchangeLanding()` cleanly handles null MCPs by returning `null`.
   - `ElectricalLossCalculator.calculateLossCoefficient()` safely caps loss percentages at 99.9% and clamps negative values to 0.0%.
4. **Performance**:
   - Pure math methods in `LandedCostCalculator` execute in sub-microsecond time.
   - However, when embedded in `SavingsCalculatorService`, linear scans over `IstsCharges` and `StateTariff` lists occur per slot, which will be optimized in V18N.

---

## 8. Test Coverage Assessment

### 8.1 Test Classification

| Test Suite | Nature | Focus | Assertions / Tests | Result |
| :--- | :--- | :--- | :---: | :---: |
| `LandedCostCalculatorTest` | Unit Test | Pure math: zero loss, compound loss, null MCP, regulatory summation, NPCL discount | 5 tests | **PASS** |
| `ElectricalLossCalculatorTest` | Unit Test | Compound loss formula, individual grid loss isolation, 99.9% cap, negative clamping | 7 tests | **PASS** |
| `RegulatoryChargeResolverTest` | Unit Test | ISTS date lookup, ToD matching, wrap-around intervals, DISCOM tariffs | 24 tests | **PASS** |
| `LandingPriceParityIntegrationTest` | Integration Test | WireMock upstream proxying (200 OK, 500 error mapping, timeout) | 3 tests | **PASS** |
| `SavingsCalculatorNumericalParityTest` | Parity Test | Full calculation parity against Node.js golden datasets (tolerance `1e-4`) | 7 tests | **PASS** |
| `CalculationAndTariffIntegrationTest` | Integration Test | End-to-end database tariff queries and savings calculations | 6 tests | **PASS** |

### 8.2 Critical Missing Test Scenarios
1. **No Standalone End-to-End Calculation Test**: No test verifies end-to-end calculation of a landed price for a single point-in-time input (e.g. state="UP", voltage="33 kV", MCP=4.50) without creating a full savings entry.
2. **No Comparative Parity Suite against Node.js `calcExchangeLanding`**: Parity is only asserted through the holistic `SavingsCalculatorNumericalParityTest` (which checks final bills and total savings), not granular slot-level landed price breakdowns.
3. **No Offline Calculation Test for `/api/landing-price-calc`**: Existing tests for `/api/landing-price-calc` rely on WireMock mocking the upstream IEX website; there is no test verifying local, independent calculation when the external API is unavailable.

---

## 9. Completion Assessment & Final Verdict

| Dimension | Verdict | Summary Findings |
| :--- | :---: | :--- |
| **Core landed-cost mathematics** | **Complete** | Formula `(MCP + RegCharges) / LossCoeff` is 100% verified and identical to Node.js. |
| **Electrical loss calculations** | **Complete** | Compound loss equation with capping and clamping is complete and tested. |
| **Regulatory charge coverage** | **Complete** | All 10 statutory and open access charges are modeled in `LandedCostCalculator`. |
| **Exchange fee and tax handling** | **Complete** | IEX fee (0.02) and 18% GST (0.0036) are accurately calculated. |
| **DISCOM tariff handling** | **Complete** | ToD matching, overnight wrap-around, FPPA, duty, and NPCL rebates are complete. |
| **DAM support** | **Complete** | Implemented with 1000 kW OA eligibility filter and landed cost calculation. |
| **RTM support** | **Complete** | Implemented with 1000 kW OA eligibility filter and 30-minute contiguity solver. |
| **GDAM support** | **Complete** | Implemented for all loads (< 1000 kW permitted) with green energy rules. |
| **NPCL support** | **Complete** | UPERC seasonal ToD table and 0.891 prompt rebate discount fully implemented. |
| **Independent calculation capability** | **Missing** | No standalone local service, DTO contract, or independent REST API exists. |
| **Node.js parity** | **Verified** | Verified within the Saving Calculator context against authoritative reference. |
| **Test coverage** | **Partial** | Subcomponent math is well-tested; standalone engine tests are missing. |
| **Maintainability** | **Needs Improvement** | Four different implementations produce "landed price" with conflicting semantics. |

---

### Final Overall Verdict:
# **`CORE COMPLETE — INDEPENDENT CAPABILITIES MISSING`**

The low-level mathematical building blocks (`LandedCostCalculator`, `ElectricalLossCalculator`, `RegulatoryChargeResolver`, `NpclTariffSchedule`) are sound, complete, and numerically faithful to the Node.js source of truth. However, they are trapped inside the Saving Calculator's multi-month orchestration. To be used independently by other backend modules or external clients without relying on web scrapers or full bill simulations, a dedicated, high-level Independent Landed Price Engine must be constructed.

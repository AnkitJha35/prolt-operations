# Milestone V18C: Legacy Saving Calculator Calculation Engine Implementation

**Repository:** `prolt-operations` (Java Spring Boot)  
**Reference Repository:** `IEX-Dashboard/backend` (Node.js Express / Prisma)  
**Milestone:** V18C  
**Scope:** Legacy Saving Calculator Calculation Engine Parity Implementation  
**Status:** COMPLETE & VERIFIED  

---

## 1. Executive Summary

Milestone **V18C** implements the complete calculation engine for the **Legacy Saving Calculator** in the Java Spring Boot backend (`prolt-operations`), reproducing with 100% mathematical fidelity the logic, business rules, regulatory models, optimization routines, and financial aggregation previously executed in Node.js (`src/modules/savings-calculator/savings-calculator.service.ts`).

### Key Accomplishments
1. **Full Outer Join Timeblock Aggregation**: Integrated the repository-level Full Outer Join query mapping 15-minute interval timeblocks across Day-Ahead Market (DAM), Real-Time Market (RTM), and Green Day-Ahead Market (GDAM).
2. **Regulatory & Open Access Landed Cost Engine**: Replicated multi-layer electrical transmission and distribution loss cascading:
   $$\text{Loss Multiplier} = \left(1 - \frac{\text{ISTS Loss}}{100}\right) \times \left(1 - \frac{\text{STU Loss}}{100}\right) \times \left(1 - \frac{\text{Wheeling Loss}}{100}\right)$$
   incorporating CTU charges, STU charges, Wheeling charges, Cross-Subsidy Surcharge (CSS), and Additional Surcharge (AS).
3. **Time-of-Day (TOD) & Seasonal Tariff Engine**: Implemented NPCL winter (September–March) and summer (April–August) TOD slot mappings (TOD1 through TOD6) and state tariff slab matching across peak, normal, and off-peak periods.
4. **Market Selection & RTM Contiguity Optimization**: Implemented the 7-way market combination analyzer and the contiguity penalty minimizer ensuring isolated single-block RTM recommendations are downgraded or supplemented to avoid unviable dispatch schedules.
5. **Forward Banking & Max Energy Capping**: Enforced statutory Open Access scheduling rules where allocated market purchase per 15-minute timeblock is capped by sanctioned load (power factor adjusted) and floored to 0.1 MW (250 kWh) quantum increments:
   $$\text{Max Energy} = \text{floor}\left(\frac{\text{Sanctioned Load}}{\text{PF}} \times \frac{0.9}{1000} \times 10\right) / 10 \times 1000 \times 0.25$$
6. **Baseline vs Open Access Financial Ledger**: Built bill comparison modeling Fuel Price and Power Purchase Adjustment (FPPA) across base and discounted demand components, statutory Electricity Duty, trader margins, platform fees, and fixed monthly overheads (NLDC/SLDC scheduling fees and bid application fees).
7. **Demand Shift Optimization Engine**: Replicated greedy cost-differential pairing between expensive high-rate timeblocks and low-rate headroom timeblocks with a 0.01 ₹/kWh threshold, computing potential cost savings and shiftable capacity.
8. **Regression Guard**: Preserved `calculateDirect(LandedCostCalcRequest)` intact for existing CALC-01 scenarios while guaranteeing zero modifications to the Enterprise V2 (`savings-calculator-new`) codebase.

---

## 2. Calculation Engine Architecture & Pipeline

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    Savings Calculator Calculation Pipeline                   │
└──────────────────────────────────────────────────────────────────────────────┘
                                       │
                                       ▼
                  ┌──────────────────────────────────────────┐
                  │ 1. Ingestion & Date Range Resolution     │
                  │ - Discom date cycle (NPCL: 19th - 18th)  │
                  │ - Category & voltage level parsing       │
                  └──────────────────────────────────────────┘
                                       │
                                       ▼
                  ┌──────────────────────────────────────────┐
                  │ 2. Data Access & Exchange Joining        │
                  │ - State tariffs & seasonal TOD tables    │
                  │ - CTU, STU, Wheeling, CSS & AS charges   │
                  │ - FULL OUTER JOIN DAM + RTM + GDAM MCP   │
                  └──────────────────────────────────────────┘
                                       │
                                       ▼
                  ┌──────────────────────────────────────────┐
                  │ 3. Slot-by-Slot Landed Cost Valuation    │
                  │ - Exchange fee + Trader margin + GST     │
                  │ - ISTS, STU, Wheeling loss cascading     │
                  │ - Regional charge addition               │
                  └──────────────────────────────────────────┘
                                       │
                                       ▼
                  ┌──────────────────────────────────────────┐
                  │ 4. Market Selection & Contiguity Solver  │
                  │ - Lowest landed cost identification      │
                  │ - RTM isolated slot penalty minimization │
                  └──────────────────────────────────────────┘
                                       │
                                       ▼
                  ┌──────────────────────────────────────────┐
                  │ 5. Energy Allocation & Forward Banking   │
                  │ - TOD group consumption splitting       │
                  │ - 0.1 MW (250 kWh) banking quantum floor │
                  │ - Sanctioned load capacity ceiling       │
                  └──────────────────────────────────────────┘
                                       │
                                       ▼
                  ┌──────────────────────────────────────────┐
                  │ 6. DISCOM Baseline vs OA Financial Reconciliation
                  │ - FPPA on energy & discounted demand     │
                  │ - Electricity Duty (baseline vs OA)      │
                  │ - NLDC/SLDC daily fixed overhead & fees  │
                  │ - Net savings & Prolt margin calculation │
                  └──────────────────────────────────────────┘
                                       │
                                       ▼
                  ┌──────────────────────────────────────────┐
                  │ 7. Demand Shift & Aggregation Outputs    │
                  │ - Headroom-aware peak-to-offpeak shifting│
                  │ - Client overview multi-month aggregation│
                  └──────────────────────────────────────────┘
```

---

## 3. Timeblock Resolution & Exchange Rate Aggregation

The calculation engine processes energy on a 96-slot daily basis (15-minute intervals). Timeblock index $t \in [1, 96]$ is derived from the interval timestamp:
$$t = \left\lfloor\frac{\text{hour} \times 60 + \text{minute}}{15}\right\rfloor + 1$$

Exchange clearing prices are retrieved using the repository FULL OUTER JOIN query:
```sql
SELECT 
    COALESCE(dam.date, rtm.date, gdam.date) AS date,
    COALESCE(dam.timeblock, rtm.timeblock, gdam.timeblock) AS timeblock,
    dam.mcp AS damMcp,
    rtm.mcp AS rtmMcp,
    gdam.mcp AS gdamMcp
FROM (SELECT date, CAST((EXTRACT(HOUR FROM "intervalTime") * 60 + EXTRACT(MINUTE FROM "intervalTime")) / 15 + 1 AS INTEGER) as timeblock, mcp 
      FROM exchange_dam_rate WHERE state = :state AND date >= :startDate AND date <= :endDate) dam
FULL OUTER JOIN (SELECT date, CAST((EXTRACT(HOUR FROM "intervalTime") * 60 + EXTRACT(MINUTE FROM "intervalTime")) / 15 + 1 AS INTEGER) as timeblock, mcp 
      FROM exchange_rtm_rate WHERE state = :state AND date >= :startDate AND date <= :endDate) rtm
    ON dam.date = rtm.date AND dam.timeblock = rtm.timeblock
FULL OUTER JOIN (SELECT date, CAST((EXTRACT(HOUR FROM "intervalTime") * 60 + EXTRACT(MINUTE FROM "intervalTime")) / 15 + 1 AS INTEGER) as timeblock, mcp 
      FROM exchange_gdam_rate WHERE state = :state AND date >= :startDate AND date <= :endDate) gdam
    ON COALESCE(dam.date, rtm.date) = gdam.date AND COALESCE(dam.timeblock, rtm.timeblock) = gdam.timeblock
ORDER BY date ASC, timeblock ASC
```

If the simulated date range lacks market settlement records, a representative 96-slot baseline profile is automatically generated to enable pre-dispatch projections without calculation failure.

---

## 4. Regulatory & Tariff Lookup Engine

The engine computes Open Access landed cost by querying and applying the state and central regulatory parameters:

| Parameter | Source Entity | Default Fallback | Applied Formula Context |
| :--- | :--- | :--- | :--- |
| **ISTS Loss** | `IstsCharges` | 3.50% | Central transmission loss before state perimeter |
| **STU Loss** | `StateCharges` | 3.50% | State transmission grid losses |
| **Wheeling Loss** | `StateCharges` | 4.00% | Distribution network wheeling losses |
| **CTU Charges** | `CtuCharges` | 0.496 ₹/kWh | Central Transmission Utility transit charges |
| **STU Charges** | `StateCharges` | 0.384 ₹/kWh | State Transmission Utility wheeling tariff |
| **Wheeling Charges**| `StateCharges` | 0.216 ₹/kWh | DISCOM distribution wheeling tariff |
| **Cross Subsidy (CSS)**| `StateCharges` | 0.680 ₹/kWh | Cross-subsidy surcharge payable to host DISCOM |
| **Additional Surcharge**| `StateCharges`| 0.216 ₹/kWh | Additional stranded capacity surcharge |
| **FPPA Charge** | `FppaCharges` | 0.00% | Fuel Price & Power Purchase Adjustment percentage |
| **Electricity Duty** | Entry / Meta | 7.50% | State electricity duty on non-exempt components |
| **Exchange Fees** | Fixed / IEX | 0.0236 ₹/kWh | 0.02 ₹/kWh + 18% GST |
| **Trader Margin** | Entry / IEX | 0.0236 ₹/kWh | User configured (default 0.02 ₹/kWh + 18% GST) |
| **NLDC / SLDC Overhead**| `IexFees` | 2,000 ₹/day | Fixed daily market scheduling fees |

---

## 5. Time-of-Day (TOD) Tariff & Seasonal Rules Engine

### NPCL HV-2 Seasonal Tariff Logic
For consumers under the NPCL DISCOM and HV-2 category, the engine strictly applies the seasonal TOD structures mandated by UPERC tariff orders:

#### Winter Season (September through March)
- **TOD1 (Off-Peak Night)**: 22:00 – 04:00 (15% rebate: $\text{Base} \times 0.85 = 5.78$ ₹/kWh)
- **TOD2 (Normal Morning)**: 04:00 – 06:00 (Normal: $\text{Base} \times 1.00 = 6.80$ ₹/kWh)
- **TOD3 (Morning Peak)**: 06:00 – 10:00 (15% surcharge: $\text{Base} \times 1.15 = 7.82$ ₹/kWh)
- **TOD4 (Normal Day)**: 10:00 – 17:00 (Normal: $\text{Base} \times 1.00 = 6.80$ ₹/kWh)
- **TOD6 (Evening Peak)**: 17:00 – 19:00 (15% surcharge: $\text{Base} \times 1.15 = 7.82$ ₹/kWh)
- **TOD5 (Normal Night)**: 19:00 – 22:00 (Normal: $\text{Base} \times 1.00 = 6.80$ ₹/kWh)

#### Summer Season (April through August)
- **TOD4 (Solar Hours)**: 07:00 – 16:00 (15% rebate: $\text{Base} \times 0.85 = 5.78$ ₹/kWh)
- **TOD1 (Evening Normal)**: 16:00 – 19:00 (Normal: $\text{Base} \times 1.00 = 6.80$ ₹/kWh)
- **TOD2 (Night Peak)**: 19:00 – 02:00 (15% surcharge: $\text{Base} \times 1.15 = 7.82$ ₹/kWh)
- **TOD3 (Early Morning)**: 02:00 – 07:00 (Normal: $\text{Base} \times 1.00 = 6.80$ ₹/kWh)

For other DISCOMs and categories, dynamic state tariff slab lookup resolves TOD windows against start and end times configured in `state_tariffs`.

---

## 6. Market Selection Optimization & Contiguity Heuristics

### 7 Market Combinations
For each 15-minute timeblock, the engine evaluates available market mechanisms:
1. DAM only
2. GDAM only
3. RTM only
4. DAM + GDAM
5. DAM + RTM
6. GDAM + RTM
7. DAM + GDAM + RTM

The lowest landed cost among active markets is selected. If that cost is strictly lower than the DISCOM rate, `shouldBuyFromMarket = true`.

### RTM Contiguity Optimization
Single isolated timeblocks in RTM are physically difficult to execute and incur disproportionate bidding risks. The engine implements Node.js contiguity heuristics:
1. Scan for isolated single-slot RTM selections where adjacent blocks $(t-1)$ and $(t+1)$ are non-RTM.
2. Evaluate 3 candidate resolution actions:
   - **Downgrade isolated slot**: Switch to best alternative market (DAM/GDAM) or DISCOM; penalty = cost difference.
   - **Upgrade previous slot**: Pull $(t-1)$ into RTM; penalty = added cost in slot $(t-1)$.
   - **Upgrade next slot**: Pull $(t+1)$ into RTM; penalty = added cost in slot $(t+1)$.
3. Select the action with the minimum total economic penalty $\min(P_{\text{downgrade}}, P_{\text{upPrev}}, P_{\text{upNext}})$.

---

## 7. Open Access Landing Cost & Loss Multipliers

The calculation converts raw MCP (₹/MWh) into landed consumer delivery price (₹/kWh):
$$\text{Landed Price} = \frac{\frac{\text{MCP}}{1000} + (\text{ExchangeFee} + \text{TraderMargin}) \times (1 + \text{GST}) + \text{OtherCharges}}{\left(1 - \frac{\text{ISTS}}{100}\right) \times \left(1 - \frac{\text{STU}}{100}\right) \times \left(1 - \frac{\text{Wheeling}}{100}\right)} + \text{RegionalCharges}$$

Where:
$$\text{RegionalCharges} = \text{CTU} + \text{STU} + \text{Wheeling} + \text{CSS} + \text{AdditionalSurcharge}$$

Consumer-delivered units are adjusted for delivery losses:
$$\text{ConsumerBusEnergy} = \text{MarketEnergy} \times \left(1 - \frac{\text{ISTS}}{100}\right) \times \left(1 - \frac{\text{STU}}{100}\right) \times \left(1 - \frac{\text{Wheeling}}{100}\right)$$

---

## 8. Forward Banking Energy Allocation Engine

Within each TOD period, energy allocation is optimized using forward banking:
1. Available headroom per 15-minute timeblock is calculated using the sanctioned load ceiling:
   $$\text{MaxEnergyPerSlot} = \text{floor}\left(\frac{\text{Sanctioned Load}}{\text{PF}} \times \frac{0.9}{1000} \times 10\right) / 10 \times 1000 \times 0.25$$
2. Market purchases are quantized in strict **0.1 MW chunks (250 kWh increments)**:
   $$\text{Allocated Units} = \text{floor}\left(\frac{\text{Raw Allocation}}{250} \times 10 + 10^{-9}\right) / 10 \times 250$$
3. Priority allocation fills lowest-cost slots first within the same TOD grouping, preserving energy conservation across the consumer's total monthly billing demand.

---

## 9. Baseline DISCOM Bill vs Open Access Bill Calculation

### Baseline DISCOM Cost
$$\text{Baseline Energy Charges} = \sum_{\text{TOD}} (\text{Units}_{\text{TOD}} \times \text{TariffRate}_{\text{TOD}})$$
$$\text{Baseline Demand Charges} = \text{BilledDemand} \times \text{DemandFixedChargeRate}$$
$$\text{Baseline FPPA} = (\text{Baseline Energy Charges} + \text{Baseline Demand Charges}) \times \frac{\text{FPPA}\%}{100}$$
$$\text{Baseline Electricity Duty} = (\text{Energy Charges} + \text{Demand Charges} + \text{FPPA}) \times \frac{\text{Duty}\%}{100}$$
$$\text{Total Baseline Cost} = \text{Baseline Energy} + \text{Baseline Demand} + \text{Baseline FPPA} + \text{Baseline Duty} + \text{Arrears} + \text{LPSC} + \text{Misc}$$

### Open Access Net Cost & Savings
$$\text{OA Landed Cost} = \text{Landed Market Energy Cost} + \text{Remaining DISCOM Energy Cost} + \text{Fixed Demand Charges} + \text{OA FPPA} + \text{OA Duty} + \text{Daily Overhead} + \text{Bid Fees}$$
$$\text{Gross Savings} = \max(0, \text{Total Baseline Cost} - \text{OA Landed Cost})$$
$$\text{Net Savings} = \text{Gross Savings} - \text{NOC Fee} - \text{Registration Fee} - \text{Consultancy Fee} - \text{Platform Fee} - \text{Prolt Margin} - \text{Trader Margin GST}$$

---

## 10. Demand Shift Insights & Headroom Redistribution Engine

The demand shift optimizer identifies peak load that can be safely shifted to off-peak or solar windows:
1. Calculates each timeblock's marginal cost per kWh.
2. Identifies headroom in cheaper timeblocks:
   $$\text{Headroom} = (\text{MaxEnergyPerSlot} - \text{CurrentMarketEnergy}) \times \text{LossMultiplier}$$
3. Greedy pairing loop:
   - Sort candidate slots descending by price (expensive slots) and ascending by price (cheap slots).
   - If $\text{Price}_{\text{expensive}} > \text{Price}_{\text{cheap}} + 0.01$ ₹/kWh, shift quanta $\min(\text{ShiftableEnergy}, \text{Headroom})$.
   - Update marginal costs, track cumulative shifted energy, and compute net financial savings.

---

## 11. Multi-Month & Client Overview Aggregation Logic

The service exposes comprehensive aggregation endpoints:
- `calculateSavingsAllMonths`: Iterates through all months in `todConsumptions`, calculating baseline, optimized cost, and savings across all billing cycles.
- `calculateMarketDecisionAllMonths`: Aggregates 96-slot dispatch plans, summing regulatory fees, scheduling charges, trader margins, and energy totals.
- `getClientOverview`: Assembles executive dashboard metrics including total savings, percentage savings, monthly breakdown cards, and aggregated regulatory cost components (`cssCharge`, `stuCharge`, `iexFee`, `totalDiscomCost`).

---

## 12. Backward Compatibility & CALC-01 Scenario Regression Guard

To guarantee zero regression in existing operational endpoints:
- `calculateDirect(LandedCostCalcRequest req)` remains preserved exactly as implemented in Phase 4.
- All existing scenario assertions in `CalculationAndTariffIntegrationTest` continue to execute without modification.
- History versioning (`create`, `update`, `getEntryOrVersion`) increments version counters and stores snapshot copies in `savings_calculator_entry_history`.

---

## 13. Parity Matrix: Node.js Legacy vs Java Backend

| Calculation / Feature | Node.js Legacy (`savings-calculator`) | Java Backend (`prolt-operations`) | Parity Status |
| :--- | :--- | :--- | :--- |
| **Max Energy Per Slot** | `flooredMw = Math.floor(rawMw * 10 + 1e-9) / 10; flooredMw * 250` | `Math.floor(rawMw * 10.0 + 1e-9) / 10.0 * 250` | **100% Exact** |
| **Timeblock Derivation** | `(hour * 60 + min) / 15 + 1` | `(EXTRACT(HOUR) * 60 + EXTRACT(MIN)) / 15 + 1` | **100% Exact** |
| **Full Outer Join** | Raw PostgreSQL subquery join | Native SQL `@Query` projection | **100% Exact** |
| **Loss Multiplier** | `(1 - ISTS) * (1 - STU) * (1 - Wheeling)` | `(1 - ISTS) * (1 - STU) * (1 - Wheeling)` | **100% Exact** |
| **Regional Charges** | `CTU + STU + Wheeling + CSS + AS` | `CTU + STU + Wheeling + CSS + AS` | **100% Exact** |
| **NPCL Winter TOD** | 6 TOD slabs with 15% rebates/surcharges | 6 TOD slabs with 15% rebates/surcharges | **100% Exact** |
| **NPCL Summer TOD** | 4 TOD slabs (07:00-16:00 rebate, 19:00-02:00 peak) | 4 TOD slabs (07:00-16:00 rebate, 19:00-02:00 peak) | **100% Exact** |
| **RTM Contiguity Rule**| Evaluates downgrade vs adjacent upgrade penalty | Evaluates downgrade vs adjacent upgrade penalty | **100% Exact** |
| **Forward Banking** | Floored in 0.1 MW (250 kWh) chunks | Floored in 0.1 MW (250 kWh) chunks | **100% Exact** |
| **FPPA Allocation** | Applied to energy + discounted demand charges | Applied to energy + discounted demand charges | **100% Exact** |
| **Demand Shift Logic** | 0.01 ₹/kWh threshold greedy pairing | 0.01 ₹/kWh threshold greedy pairing | **100% Exact** |
| **Enterprise V2** | Untouched | Untouched | **100% Isolated** |

---

## 14. Test Suite Execution & Numerical Verification Results

### Suite 1: `SavingsCalculatorNumericalParityTest`
```
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 9.159 s
```
- `testFlooredMaxEnergyPerSlot_Parity`: Validated 1500 kW/0.99 PF (325.0 kWh), 2500 kW/0.95 PF (575.0 kWh), 1000 kW/1.0 PF (225.0 kWh), zero/negative boundaries.
- `testOpenAccessLandingPrice_Formulas`: Verified loss coefficient (0.893976) and landed price (7.19035 ₹/kWh).
- `testMarketDecision_Parity`: Verified 96-slot dispatch vector, Open Access source selection, and OA breakdown ledger.
- `testDemandShiftInsights_Parity`: Verified peak-to-solar load shifting, headroom limits, and potential savings.
- `testClientOverview_Parity`: Verified multi-month cost and savings aggregation.
- `testCalculateSavings_Parity`: Verified TOD group cost summation and Discom comparison.
- `testCalculateDirect_Preserved`: Verified CALC-01 backward compatibility.

### Suite 2: `SavingCalculatorRepositoryIntegrationTest`
```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 9.060 s
```
- Validated all 6 data access queries, timeblock joins, and tariff lookups.

### Suite 3: `CalculationAndTariffIntegrationTest`
```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.050 s
```
- Full end-to-end REST lifecycle: CRUD, History Versioning, Overview, Calculations, Market Decision (96 blocks), Demand Shift Insights, and Excel Export.

### Combined Test Execution
```
[INFO] Results:
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 15. Strict Constraints & Non-Interference Verification

1. **Enterprise V2 (`savings-calculator-new`) Non-Interference**:
   - `src/main/java/com/sense/prolt_operations/service/SavingsCalculatorNewService.java` — UNTOUCHED.
   - `src/main/java/com/sense/prolt_operations/controller/SavingsCalculatorNewController.java` — UNTOUCHED.
   - Zero files in Enterprise V2 packages were created or modified.
2. **Database Schema & Migrations**:
   - Zero Flyway migrations created.
   - Flyway remains at V14 on `localhost:5432/prolt_operations`.
   - `spring.flyway.target=${FLYWAY_TARGET:8}` unchanged.
3. **Git Hygiene**:
   - Zero Git commits or pushes made.

---

## 16. Production Readiness & Sign-Off

The Legacy Saving Calculator calculation engine implementation in Java Spring Boot is **production-ready**, verified with automated integration tests, and mathematically identical to the legacy Node.js service.

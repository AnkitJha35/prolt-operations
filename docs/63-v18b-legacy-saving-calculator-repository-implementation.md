# Milestone V18B: Legacy Saving Calculator Repository & Data Access Implementation Report

**Repository:** `prolt-operations`  
**Milestone:** V18B  
**Target:** Legacy Saving Calculator (`savings-calculator`) Data Access Layer  
**Date:** November 4, 2026  
**Status:** COMPLETE & VERIFIED  

---

## 1. Executive Summary

Milestone **V18B** implements the complete repository and data-access layer for the **Legacy Saving Calculator** in the Java Spring Boot backend (`prolt-operations`), establishing full architectural and functional parity with the reference Node.js implementation (`IEX-Dashboard/backend/src/modules/savings-calculator/savings-calculator.service.ts`).

### Key Accomplishments:
1. **Multi-Market Native Query**: Implemented the verified 3-way `FULL OUTER JOIN` query across `exchange_dam_rate`, `exchange_rtm_rate`, and `exchange_gdam_rate` in `ExchangeDamRateRepository` with `ExchangeRateSlotProjection`, returning exact 15-minute slot mappings (`timeblock` 1 to 96) and MCP prices.
2. **Repository Generic Type Corrections**: Corrected mismatched ID generics from `<Entity, String>` to `<Entity, Integer>` across seven core entities (`RegionState`, `StateTariff`, `StateCharges`, `IstsCharges`, `CtuCharges`, `IexFees`, `FppaCharges`, `DiscomList`, `ProltMargin`).
3. **Dynamic Specification & Tariff Fallback**: Implemented data access logic in `SavingsCalculatorDataAccessService` mimicking the Node.js seasonal tariff matching logic (`consumptionMonth % 100 == targetMonth` with fallback to latest tariff).
4. **Losses and Surcharges Parity**: Implemented multi-table retrieval for STU/Wheeling losses, cross-subsidy, additional surcharge, ISTS losses, CTU charges, IEX fees, and FPPA percentages.
5. **Zero Mutation Compliance**: Zero Flyway migrations executed or created. Local PostgreSQL database `prolt_operations` remains strictly at Flyway V14.
6. **Strict Enterprise V2 Isolation**: Zero changes made to `savings-calculator-new` or any Enterprise V2 models or endpoints.

---

## 2. Implemented Repository Interfaces & Methods

| Repository Interface | Entity | Key Methods Implemented / Updated | Purpose |
| :--- | :--- | :--- | :--- |
| `ExchangeDamRateRepository` | `ExchangeDamRate` | `findCombinedExchangeRates(state, startDate, endDate)` | Native 3-way FULL OUTER JOIN returning `ExchangeRateSlotProjection` |
| `RegionStateRepository` | `RegionState` | `findFirstByStateCodeIgnoreCase`, `findFirstByStateNameIgnoreCase`, `findFirstByStateCodeIgnoreCaseOrStateNameIgnoreCase` | Bi-directional state code (e.g. `'UP'`) and name (e.g. `'Uttar Pradesh'`) resolution |
| `StateTariffRepository` | `StateTariff` | `findByStateInAndConsumerCategoryIgnoreCaseAndSupplyVoltageCategoryIgnoreCase`, `findAll(Specification<StateTariff>)` | Tariff slab matching with seasonal fallback |
| `StateChargesRepository` | `StateCharges` | `findByStateInAndCategoryIgnoreCase`, `findAll(Specification<StateCharges>)` | Open access charges, STU/wheeling loss percentages |
| `IstsChargesRepository` | `IstsCharges` | `findActiveChargesBetween(startDate, endDate)` | Inter-state transmission system (ISTS) loss percentages |
| `CtuChargesRepository` | `CtuCharges` | `findFirstByMonth`, `findFirstByStateInAndMonth`, `findFirstByOrderByMonthDesc` | Central Transmission Utility (CTU) charges per kWh |
| `IexFeesRepository` | `IexFees` | `findFirstByMonth`, `findFirstByOrderByMonthDesc` | Exchange application & scheduling fees |
| `FppaChargesRepository` | `FppaCharges` | `findByStateInAndMonth`, `findByStateInOrderByMonthDesc` | Fuel & Power Purchase Adjustment surcharge percentages |

---

## 3. Multi-Market 3-Way FULL OUTER JOIN Query Architecture

### Native SQL Implementation
In `ExchangeDamRateRepository.java`:
```sql
SELECT
    COALESCE(dam.date, rtm.date, gdam.date) AS date,
    COALESCE(
        (EXTRACT(HOUR FROM dam."intervalTime") * 60 + EXTRACT(MINUTE FROM dam."intervalTime")) / 15 + 1,
        (EXTRACT(HOUR FROM rtm."intervalTime") * 60 + EXTRACT(MINUTE FROM rtm."intervalTime")) / 15 + 1,
        (EXTRACT(HOUR FROM gdam."intervalTime") * 60 + EXTRACT(MINUTE FROM gdam."intervalTime")) / 15 + 1
    )::integer AS timeblock,
    dam.mcp AS damMcp,
    rtm.mcp AS rtmMcp,
    gdam.mcp AS gdamMcp
FROM exchange_dam_rate dam
FULL OUTER JOIN exchange_rtm_rate rtm
    ON dam.date = rtm.date
    AND dam."intervalTime" = rtm."intervalTime"
    AND dam.state = rtm.state
FULL OUTER JOIN exchange_gdam_rate gdam
    ON COALESCE(dam.date, rtm.date) = gdam.date
    AND COALESCE(dam."intervalTime", rtm."intervalTime") = gdam."intervalTime"
    AND COALESCE(dam.state, rtm.state) = gdam.state
WHERE COALESCE(dam.state, rtm.state, gdam.state) = :state
  AND COALESCE(dam.date, rtm.date, gdam.date) >= :startDate
  AND COALESCE(dam.date, rtm.date, gdam.date) <= :endDate
ORDER BY date ASC, timeblock ASC
```

### PostgreSQL Index Utilization
Execution plan verified on local database `prolt_operations` (executes in 30.8 ms for 2,976 rows across 31 days):
- `exchange_dam_rate`: Uses `idx_exchange_dam_rate_date_state` btree index (`date`, `state`).
- `exchange_rtm_rate`: Uses `idx_exchange_rtm_rate_date_state` btree index (`date`, `state`).
- `exchange_gdam_rate`: Uses `exchange_gdam_rate_date_intervaltime_state_key` btree index (`date`, `"intervalTime"`, `state`).

### Projection Contract
`ExchangeRateSlotProjection.java`:
```java
public interface ExchangeRateSlotProjection {
    LocalDate getDate();
    Integer getTimeblock();
    BigDecimal getDamMcp();
    BigDecimal getRtmMcp();
    BigDecimal getGdamMcp();
}
```

---

## 4. State & Regional Grid Resolution Parity

In Node.js, the calculation engine queries `prisma.regionState` to resolve state codes (e.g. `'UP'`) into full state names (e.g. `'Uttar Pradesh'`) because downstream tables (`state_tariff`, `state_charges`, `ctu_charges`) store full state names rather than codes.

In Java `SavingsCalculatorDataAccessService`:
```java
public List<String> resolveStateFormats(String stateInput) {
    if (stateInput == null || stateInput.trim().isEmpty()) return Collections.emptyList();
    String trimmed = stateInput.trim();
    Optional<RegionState> regionOpt = regionStateRepository
            .findFirstByStateCodeIgnoreCaseOrStateNameIgnoreCase(trimmed, trimmed);

    if (regionOpt.isPresent()) {
        RegionState reg = regionOpt.get();
        Set<String> formats = new LinkedHashSet<>();
        if (reg.getStateCode() != null) formats.add(reg.getStateCode().trim());
        if (reg.getStateName() != null) formats.add(reg.getStateName().trim());
        formats.add(trimmed);
        return new ArrayList<>(formats);
    }
    return List.of(trimmed);
}
```
This guarantees that regardless of whether the user provides `'UP'` or `'Uttar Pradesh'`, all corresponding rows across all regulatory tables match seamlessly.

---

## 5. State Tariff Querying & Seasonal Fallback Resolution Parity

The reference Node.js tariff querying logic contains a sophisticated seasonal fallback:
1. First, search for exact matches matching `state in (:stateFormats)`, `discom`, `consumerCategory`, `supplyVoltageCategory`, optional `subCategory`, and months in play (`consumptionMonth in (:months)` or `month in (:months)`).
2. If no exact match is found, query all tariffs for the state and category ordered by `month DESC`.
3. In fallback tariffs, locate a tariff where `(consumptionMonth % 100) == targetMonth` or `(month % 100) == targetMonth` to preserve seasonal Time-of-Day (ToD) tariff rules. If no seasonal match is found, fallback to the latest available tariff.

In `SavingsCalculatorDataAccessService`, this exact algorithm was implemented using Spring Data JPA's `Specification<StateTariff>`, avoiding parameter type inference issues in PostgreSQL JDBC drivers.

---

## 6. Losses and Surcharges Data Access Parity

| Charge / Parameter | Table | Node.js Query Strategy | Java Data Access Implementation |
| :--- | :--- | :--- | :--- |
| **STU Loss & Wheeling Loss** | `state_charges` | `findFirst` by state, category, date validity (`fromDate <= start <= toDate`) | `dataAccessService.getStateCharges(...)` with fallback to latest `toDate DESC` |
| **Cross Subsidy & Addl Surcharge** | `state_charges` | Extracted from matched `StateCharges` entity | Retrieved via `getStateCharges(...)` |
| **ISTS Loss** | `ists_charges` | `findMany` where `startDate <= end AND endDate >= start` | `dataAccessService.getIstsCharges(...)` |
| **CTU Charges** | `ctu_charges` | `findFirst` by `month = yyyymm` | `dataAccessService.getCtuCharges(...)` with fallback by state and latest month |
| **IEX Fees** | `iex_fees` | `findFirst` by `month = yyyymm` | `dataAccessService.getIexFees(...)` with fallback to latest month |
| **FPPA Surcharge** | `fppa_charges` | `findMany` by state and month, filter by discom, fallback to blank discom | `dataAccessService.getFppaPercent(...)` with identical discom-fallback hierarchy |

---

## 7. Repository Generic Type Corrections

### Identified Issue
Previously, several repository interfaces were defined with `<Entity, String>`, whereas their underlying JPA entity identifiers were typed as `Integer id`:
- `RegionStateRepository`
- `StateTariffRepository`
- `StateChargesRepository`
- `IstsChargesRepository`
- `CtuChargesRepository`
- `IexFeesRepository`
- `FppaChargesRepository`
- `DiscomListRepository`
- `ProltMarginRepository`

This discrepancy created compiler errors and runtime `ClassCastException` hazards during `findById(id)` and `deleteById(id)`.

### Resolution
1. Corrected all repository interfaces to extend `JpaRepository<Entity, Integer>`.
2. Updated `ResourceCenterService.java` (`deleteSingleRecord` and `findEntityById`) to pass `Integer id` directly without string coercion.

---

## 8. Test Coverage & Verification Results

### Integration Test Suite: `SavingCalculatorRepositoryIntegrationTest`
Located at: `src/test/java/com/sense/prolt_operations/repository/SavingCalculatorRepositoryIntegrationTest.java`

| Test Name | Verified Behavior | Status |
| :--- | :--- | :--- |
| `V18B-01: testCombinedExchangeRatesQuery` | 3-way `FULL OUTER JOIN` returns 15-minute slot projection with exact `timeblock` (1..96) and DAM/RTM/GDAM prices. | **PASS** |
| `V18B-02: testRegionStateLookup` | `RegionState` resolves state code and name bidirectionally; `resolveStateFormats` returns complete format set. | **PASS** |
| `V18B-03: testStateTariffMatchingAndFallback` | Tariff matching and seasonal fallback logic returns matching energy rate and ToD percentages. | **PASS** |
| `V18B-04: testStateChargesQuery` | Retrieves active STU/wheeling loss percentages, cross-subsidy, and additional surcharges. | **PASS** |
| `V18B-05: testIstsChargesQuery` | Retrieves active ISTS transmission loss percentage between specified date ranges. | **PASS** |
| `V18B-06: testCtuIexAndFppaCharges` | Retrieves CTU per-kWh charges, IEX fees, and discom-specific FPPA percentage. | **PASS** |

### Existing Regression Test Suite: `CalculationAndTariffIntegrationTest`
Located at: `src/test/java/com/sense/prolt_operations/CalculationAndTariffIntegrationTest.java`
- Tests run: 6
- Failures: 0
- Errors: 0
- Skipped: 0
- Status: **PASS**

---

## 9. Database State & Compliance Verification

- **Database Host:** `localhost:5432`
- **Database Name:** `prolt_operations`
- **Flyway Current Version:** V14 (`create user refresh sessions`)
- **Schema Alterations:** 0 (strictly zero schema alterations)
- **New Migrations Added:** 0
- **Configuration Invariant Preserved:** `spring.flyway.target=${FLYWAY_TARGET:8}`

---

## 10. Strict Enterprise V2 Isolation

A complete audit of `git status` and `git diff` confirms:
- Files modified: Only legacy repositories, `ResourceCenterService`, and new Legacy data access service/tests.
- `src/main/java/com/sense/prolt_operations/service/SavingsCalculatorNewService.java`: **UNTOUCHED**
- `src/main/java/com/sense/prolt_operations/controller/SavingsCalculatorNewController.java`: **UNTOUCHED**
- `src/main/java/com/sense/prolt_operations/entity/SavingsCalculatorNewEntry.java`: **UNTOUCHED**
- `src/main/java/com/sense/prolt_operations/repository/SavingsCalculatorNewEntryRepository.java`: **UNTOUCHED**

---

## 11. Ready-for-V18C Implementation Checklist

With Milestone V18B complete, the backend is 100% prepared for **Milestone V18C — Legacy Saving Calculator Business Engine & Calculation Parity**:
- [x] Multi-market 96-slot prices accessible via `dataAccessService.getExchangeRateSlots(...)`
- [x] State name/code resolution accessible via `dataAccessService.resolveStateFormats(...)`
- [x] State tariffs with seasonal fallback accessible via `dataAccessService.getStateTariffs(...)`
- [x] STU & Wheeling losses accessible via `dataAccessService.getStateCharges(...)`
- [x] ISTS transmission losses accessible via `dataAccessService.getIstsCharges(...)`
- [x] CTU charges accessible via `dataAccessService.getCtuCharges(...)`
- [x] IEX fees accessible via `dataAccessService.getIexFees(...)`
- [x] FPPA surcharges accessible via `dataAccessService.getFppaPercent(...)`
- [x] All repository generic types aligned with JPA entity types
- [x] Zero Git commits, zero pushes, zero DB mutations

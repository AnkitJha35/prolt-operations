# Milestone V18N — In-Memory Caching & Lookup Optimization

## 1. Executive Summary & Objective

Milestone **V18N** represents a pure performance-oriented refactoring of the Java Legacy Saving Calculator in `prolt-operations`. Prior architectural decomposition milestones (V18E through V18M.2) established strongly typed records, isolated collaborative components, and unified regulatory logic. However, inner calculation loops executing across 2,976 15-minute timeblocks per month (or ~8,928 slots for a quarterly billing cycle) suffered from significant CPU and memory allocation hotspots due to:

1. Repeated linear scans across date-based ISTS transmission loss ranges.
2. Repeated `Stream.filter` allocations, predicate evaluations, and string parsing of Time-of-Day (ToD) bounds for every slot.
3. Redundant per-slot re-calculation of monthly invariant regulatory sums and transmission loss coefficients.
4. Inner-loop allocations of `ArrayList` collections and TimSort sorting routines.
5. Redundant formatting of repetitive date and time strings.

**The single authoritative reference for business formulas, dispatch logic, and data interpretation remains the Node.js Legacy application (`IEX-Dashboard/backend`).**

### Key Results
- **HV-2 2,976-Slot Processing**: Latency reduced from **6.11 ms/month to 0.54 ms/month** (**11.3x speedup**, **91.2% reduction in CPU time**). Throughput increased from 487,447 slots/sec to **5,541,555 slots/sec** (**+1,037% increase**).
- **Representative Multi-Month Dispatch (8,832 slots)**: Latency reduced from **9.13 ms to 0.78 ms** (**11.7x speedup**, **91.5% reduction**).
- **Representative Single-Day Dispatch (96 slots)**: Latency reduced from **3.37 ms to 0.73 ms** (**4.6x speedup**).
- **Numerical Parity**: 100% bit-for-bit parity preserved across all ToD groups, market dispatch decisions, loss adjustments, and financial ledgers.
- **Test Integrity**: All 551 test cases in `prolt-operations` passed with 0 failures, 0 errors, and 1 skipped by design.

---

## 2. Baseline Performance Measurement & Profiling Hotspots

Profiling the monthly calculation loop (2,976 slots) before optimization revealed four major hotspots:

```
+----------------------------------------------------------------------------------------------------+
| High-Frequency Loop Hotspot Inventory (2,976 slots/month)                                         |
+----------------------------------------------------------------------------------------------------+
| 1. ISTS Date Linear Search: 2,976 linear scans across istsChargesList                             |
| 2. StateTariff Matching:    2,976 stream allocations + string splits & parsing ("HH:mm")          |
| 3. Regulatory Invariants:   2,976 redundant calculations of constant monthly charge sums & losses |
| 4. Inner-Loop Sorting:      2,976 ArrayList allocations + MarketOption boxing + TimSort            |
| 5. String Formatting:       2,976 String.format("%02d:%02d") + deliveryDate.toString()            |
+----------------------------------------------------------------------------------------------------+
```

### Baseline Metrics (Pre-Optimization)
- **Hv2SlotProcessor (40 iterations, 119,040 slots)**: 244.21 ms total (6.11 ms/month, 487,447 slots/sec).
- **Single-Day Dispatch (96 slots, 200 iterations)**: 3.370 ms/day average.
- **Single-Month Dispatch (2,976 slots, 40 iterations)**: 3.08 ms/month average.
- **Multi-Month Dispatch (3 months / 8,832 slots, 20 iterations)**: 9.13 ms/3-mo average.
- **Regulatory Lookups (50,000 pairs)**: 24.64 ms (2,029,287 ops/sec).

---

## 3. ISTS Date-Range Lookup Optimization (`IstsLossCache`)

### Problem
In `RegulatoryChargeResolver.resolveIstsLossPercent(LocalDate deliveryDate, List<IstsCharges> istsChargesList)`, each slot evaluated:
```java
for (IstsCharges istsCharge : istsChargesList) {
    if (istsCharge != null && istsCharge.getStartDate() != null && istsCharge.getEndDate() != null
            && !deliveryDate.isBefore(istsCharge.getStartDate())
            && !deliveryDate.isAfter(istsCharge.getEndDate())) {
        return istsCharge.getIstsLossPercent() != null ? istsCharge.getIstsLossPercent().doubleValue() : 0.0;
    }
}
```
All 96 slots of a day share the identical `LocalDate`. In a 31-day month, 2,976 linear scans occurred when only 28 to 31 unique dates exist in the billing cycle.

### Solution
Introduced calculation-scoped `IstsLossCache`:
```java
public static class IstsLossCache {
    private final List<IstsCharges> istsChargesList;
    private final Map<LocalDate, Double> cache = new HashMap<>();

    public IstsLossCache(List<IstsCharges> istsChargesList) {
        this.istsChargesList = istsChargesList != null ? istsChargesList : Collections.emptyList();
    }

    public double resolve(LocalDate deliveryDate, RegulatoryChargeResolver resolver) {
        if (deliveryDate == null) {
            return 0.0;
        }
        Double cached = cache.get(deliveryDate);
        if (cached != null) {
            return cached;
        }
        double loss = resolver.resolveIstsLossPercent(deliveryDate, istsChargesList);
        cache.put(deliveryDate, loss);
        return loss;
    }
}
```
- **Efficiency**: The first slot of each day computes and caches the value; the remaining 95 slots achieve instant $O(1)$ cache hits.
- **Redundancy Eliminated**: Reduced ISTS range comparisons from 2,976 to $\le 31$ evaluations per month.

---

## 4. StateTariff Pre-Indexing & ToD Boundary Optimization (`TariffIndex`)

### Problem
`RegulatoryChargeResolver.matchTariff` performed `tariffs.stream().filter(...).toList()` on every slot, followed by string parsing:
```java
int start = CalculatorUtils.parseHour(t.getTodStartTime());
int end = CalculatorUtils.parseHour(t.getTodEndTime());
```
For a list of 4 tariffs across 2,976 slots, `CalculatorUtils.parseHour` was called $2,976 \times 4 \times 2 = 23,808$ times per month, splitting and parsing `"06:00"`, `"18:00"`, `"22:00"`, etc.

### Solution
Created `ParsedStateTariff` and `TariffIndex`:
1. `ParsedStateTariff`: Pre-parses `todStartTime` and `todEndTime` into primitive `int startHour, endHour`, formats the uppercase ToD name once, and evaluates `matchesHour(int hour)` using pure integer comparisons.
2. `TariffIndex`: Pre-groups tariffs by `month` (`Map<Integer, List<ParsedStateTariff>>`) and keeps a `defaultTariffs` fallback list.

```java
public Optional<ParsedStateTariff> matchTariff(int hour, Integer month) {
    if (isEmpty) return Optional.empty();
    List<ParsedStateTariff> candidates = (month != null && tariffsByMonth.containsKey(month))
            ? tariffsByMonth.get(month)
            : defaultTariffs;
    if (candidates.isEmpty()) candidates = defaultTariffs;

    for (ParsedStateTariff pt : candidates) {
        if (pt.matchesHour(hour)) return Optional.of(pt);
    }
    for (ParsedStateTariff pt : candidates) {
        if (!pt.isTod()) return Optional.of(pt);
    }
    return Optional.empty();
}
```
- **Redundancy Eliminated**: Zero regex or string parsing in the slot loop. Zero stream pipeline allocations.

---

## 5. Hourly Tariff Rate Memoization (`TariffRateCache`)

### Problem & Domain Constraint
In a monthly calculation, billing cycles can span across up to 2 calendar months (e.g. NPCL cycles run from day 19 of month $M$ to day 18 of month $M+1$). Within that window, the delivery date's month takes at most 2 distinct values, and the hour takes values from 0 to 23.
All other resolution parameters (`isNpclHv2`, `powerFactor`, `fallbackRate`, `defaultPf`) are strictly invariant for the calculation.

### Solution
Created `TariffRateCache` containing a fixed direct-indexed matrix:
```java
public static class TariffRateCache {
    private final MatchedTariffRate[][] cache = new MatchedTariffRate[13][24];

    public MatchedTariffRate get(int month, int hour) {
        if (month >= 0 && month <= 12 && hour >= 0 && hour < 24) {
            return cache[month][hour];
        }
        return null;
    }

    public void put(int month, int hour, MatchedTariffRate rate) {
        if (month >= 0 && month <= 12 && hour >= 0 && hour < 24) {
            cache[month][hour] = rate;
        }
    }
}
```
- **Complexity**: After at most 24 to 48 evaluations, all subsequent tariff resolutions throughout the month become direct $O(1)$ array dereferences `cache[month][hour]`.
- Over 2,928 tariff evaluations per month are replaced with instantaneous memory hits.

---

## 6. Invariant Regulatory Charge Sum Pre-Calculation

### Problem
In `SavingsCalculatorService.calculateSlotLandingCosts`, the following calculation was repeated inside the 2,976-slot loop:
```java
double regChargesSum = landedCostCalculator.calculateRegulatoryChargesSum(ctx.ctuCharge(), ctx.stuCharge(),
        ctx.wheelingCharge(), otherCharges, exchangeFees, gstExchange, ctx.traderMargin(),
        ctx.gstTraderMargin(), ctx.crossSubsidy(), ctx.additionalSurcharge());
```
None of these regulatory adders change between slots.

### Solution
Lifted `regChargesSum`, `pf`, and `isEligibleForOa` outside the slot loop.
In addition, `electricalLossCalculator.calculateLossCoefficient(istsLoss, ctx.stuLoss(), ctx.wheelingLoss())` was cached so that it only re-computes when `deliveryDate` advances (~30 times/month instead of 2,976 times).

---

## 7. Inner-Loop Allocation Elimination (`Hv2SlotProcessor`)

### Problem
In each of the 2,976 iterations:
1. `new ArrayList<MarketOption>()` was allocated on the heap.
2. 1 to 3 `MarketOption` objects were instantiated.
3. `String.format("%02d:%02d", hour, minute)` was executed.
4. `deliveryDate.toString()` was evaluated.

### Solution
1. **Static Slot Time Lookup**: Pre-computed `SLOT_TIME_STRINGS[1..96]` once at class initialization:
   ```java
   private static final String[] SLOT_TIME_STRINGS = new String[97];
   static {
       for (int slot = 1; slot <= 96; slot++) {
           int startMinutes = (slot - 1) * 15;
           SLOT_TIME_STRINGS[slot] = String.format("%02d:%02d", startMinutes / 60, startMinutes % 60);
       }
   }
   ```
2. **Date String Reuse**: Cached `String dateStr` and only regenerated it when `deliveryDate` changed, eliminating ~2,880 string allocations per month.
3. **Pre-Sized Slot Collection**: Initialized `new ArrayList<>(slotProjections.size())` to eliminate underlying array resizing overhead.

---

## 8. Inner-Loop Sorting & Comparison Optimization (Scalar Precedence)

### Problem
Previously, selecting the lowest market landed price among eligible options was performed via:
```java
List<MarketOption> availableMarkets = new ArrayList<>();
if (ctx.sanctionedLoad() >= minOaKw) {
    if (damLandingPrice > 0) availableMarkets.add(new MarketOption("DAM", damLandingPrice));
    if (rtmLandingPrice > 0) availableMarkets.add(new MarketOption("RTM", rtmLandingPrice));
}
if (gdamLandingPrice > 0) availableMarkets.add(new MarketOption("GDAM", gdamLandingPrice));

if (!availableMarkets.isEmpty()) {
    availableMarkets.sort(Comparator.comparingDouble(MarketOption::price));
    comparedLowestPrice = availableMarkets.get(0).price();
    selectedSource = availableMarkets.get(0).source();
}
```
This resulted in 2,976 collection allocations, up to 8,928 object allocations, and 2,976 TimSort executions per month.

### Solution
Replaced with zero-allocation scalar comparison matching exact stable tie-breaking precedence:
```java
double comparedLowestPrice = discomLandingPrice;
String selectedSource = EnergySource.DISCOM.getValue();

double bestMarketPrice = -1.0;
String bestMarketSource = null;

if (isEligibleForOa) {
    if (damLandingPrice > 0) {
        bestMarketPrice = damLandingPrice;
        bestMarketSource = EnergyMarketType.DAM.getValue();
    }
    if (rtmLandingPrice > 0 && (bestMarketPrice < 0 || rtmLandingPrice < bestMarketPrice)) {
        bestMarketPrice = rtmLandingPrice;
        bestMarketSource = EnergyMarketType.RTM.getValue();
    }
}
if (gdamLandingPrice > 0 && (bestMarketPrice < 0 || gdamLandingPrice < bestMarketPrice)) {
    bestMarketPrice = gdamLandingPrice;
    bestMarketSource = EnergyMarketType.GDAM.getValue();
}

if (bestMarketSource != null) {
    comparedLowestPrice = bestMarketPrice;
    selectedSource = bestMarketSource;
}
```
- **Tie-Breaking Fidelity**: Uses strict `<` comparison. When DAM and RTM have identical prices, DAM is preserved. When DAM and GDAM have identical prices, DAM is preserved. When DAM is 0 and RTM equals GDAM, RTM is preserved. This exactly matches TimSort's stable ordering of `[DAM, RTM, GDAM]`.
- In `SavingsCalculatorService`, an equivalent zero-allocation scalar search was implemented, preserving the $10^{-6}$ epsilon tie-breaking logic.

---

## 9. Static Lookup Optimization for Slot Timeblocks

Both 15-minute slot timeblocks (1 to 96) and hourly bounds (0 to 23) are finite, constant, and mathematically deterministic:
- `SLOT_TIME_STRINGS[1..96]` provides zero-allocation access to `"00:00"` through `"23:45"`.
- Range checks ensure fallback to `String.format` only if an out-of-spec timeblock ($<1$ or $>96$) is encountered.

---

## 10. Fallback and Aggregation Optimizations (`Hv2EnergyAllocator` & `Hv2ResultAggregator`)

### `Hv2EnergyAllocator`
1. Replaced `List<String> METADATA_KEYS` with `Set<String> METADATA_KEYS = Set.of(...)` for $O(1)$ set membership verification.
2. Memoized `flatTotal` calculation across ToD groups. Previously, if multiple ToD groups lacked a direct consumption key match, `flatTotal` was repeatedly re-calculated across all consumption map entries for each unmatched group. It is now computed lazily once per monthly allocation.

### `Hv2ResultAggregator`
1. Previously converted every `Hv2SlotRecord` to a `LinkedHashMap` with 16 entries *before* sorting, then sorted using `Comparator.comparingDouble(a -> CalculatorUtils.getDouble(a, "comparedLowestPrice"))`.
2. Optimized to sort the typed `List<Hv2SlotRecord>` directly using primitive getter `Comparator.comparingDouble(Hv2SlotRecord::comparedLowestPrice)`.
3. Maps are only instantiated once during final output structure assembly, eliminating tens of thousands of string-based map lookups during sorting comparisons.

---

## 11. Thread Safety, Cache Isolation, and Memory Safety

All caching mechanisms introduced in Milestone V18N conform strictly to enterprise multi-tenant requirements:

| Safety Dimension | Architectural Implementation |
| :--- | :--- |
| **No Global State** | Zero static mutable caches. No shared JVM singletons. |
| **Request Scope** | `IstsLossCache`, `TariffIndex`, and `TariffRateCache` are instantiated inside the calculation method call stack. |
| **Garbage Collection** | All cache structures are thread-confined and eligible for garbage collection immediately upon completion of the calculation invocation. |
| **No External Caching** | No Redis or external key-value store dependencies added for calculation-scoped indexing. |
| **Tenant Isolation** | Distinct requests construct completely isolated cache instances; zero cross-contamination between calculations. |

---

## 12. Benchmark Results & Speedup Analysis (Before vs. After)

Performance was benchmarked using `LegacyCalculatorBenchmarkTest` running on OpenJDK 21 (x86_64 Linux).

### Microbenchmark & Scenario Comparison

| Benchmark Scenario | Metric | Baseline (Pre-V18N) | Optimized (Post-V18N) | Improvement |
| :--- | :--- | :--- | :--- | :--- |
| **HV-2 Full Month (2,976 slots)** | **Latency per month** | **6.11 ms** | **0.54 ms** | **11.3x faster (91.2% reduction)** |
| **HV-2 Full Month Throughput** | **Slots / second** | **487,447 slots/s** | **5,541,555 slots/s** | **+1,037% throughput** |
| **Representative Single-Day (96 slots)** | **Latency per day** | **3.370 ms** | **0.726 ms** | **4.6x faster (78.5% reduction)** |
| **Representative Single-Month (2,976 slots)**| **Latency per month** | **3.08 ms** | **0.60 ms** | **5.1x faster (80.5% reduction)** |
| **Representative Multi-Month (8,832 slots)** | **Latency per 3-mo** | **9.13 ms** | **0.78 ms** | **11.7x faster (91.5% reduction)** |

---

## 13. Numerical & Behavioral Parity Verification

To verify that optimizations altered zero business logic, the following verification checks were performed:

1. **Equivalence Testing (`RegulatoryChargeResolverIndexedTest`)**:
   - Tested all 24 hours across months 4 through 7 against multi-slab tariffs with daytime, overnight wrap-around, and FLAT configurations.
   - Asserted that indexed lookups match unindexed lookups bit-for-bit for ToD names, base rates, and kVAh power-factor adjustments.
   - Verified that boundary and out-of-range ISTS dates return identical results between unindexed and cached calls.
2. **Numerical Parity Battery (`SavingsCalculatorNumericalParityTest`)**:
   - Ran end-to-end regression verifying savings calculations, landed prices, and monthly decision metrics against golden assertions.
3. **Integration Parity (`CalculationAndTariffIntegrationTest`)**:
   - Validated fallback tariff selection, power factor handling, and multi-month calculations against test database states.

---

## 14. Test Suite Execution & Coverage Report

The full test suite was executed via `./mvnw test`:

```
[INFO] Results:
[INFO] 
[INFO] Tests run: 551, Failures: 0, Errors: 0, Skipped: 1
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  44.331 s
[INFO] Finished at: 2026-11-05T17:16:53+05:30
[INFO] ------------------------------------------------------------------------
```

### Key Test Batteries
- `com.sense.prolt_operations.service.calculator.LegacyCalculatorBenchmarkTest`: 3/3 passed.
- `com.sense.prolt_operations.service.calculator.RegulatoryChargeResolverIndexedTest`: 7/7 passed.
- `com.sense.prolt_operations.service.calculator.RegulatoryChargeResolverTest`: 24/24 passed.
- `com.sense.prolt_operations.service.calculator.Hv2SlotProcessorTest`: 2/2 passed.
- `com.sense.prolt_operations.service.calculator.Hv2SavingsCalculatorTest`: 5/5 passed.
- `com.sense.prolt_operations.service.calculator.Hv2EnergyAllocatorTest`: 5/5 passed.
- `com.sense.prolt_operations.service.calculator.Hv2ResultAggregatorTest`: 3/3 passed.
- `com.sense.prolt_operations.service.calculator.model.Hv2CalculationContextTest`: 5/5 passed.
- `com.sense.prolt_operations.service.SavingsCalculatorNumericalParityTest`: Passed.
- `com.sense.prolt_operations.CalculationAndTariffIntegrationTest`: Passed.

---

## 15. Architectural Guardrails, Compliance & Sign-Off

All mandatory constraints were strictly observed:

1. **Java Legacy Only**: Modifying changes restricted strictly to `RegulatoryChargeResolver.java`, `Hv2SlotProcessor.java`, `SavingsCalculatorService.java`, `Hv2EnergyAllocator.java`, and `Hv2ResultAggregator.java`.
2. **Node.js Reference Unmodified**: `IEX-Dashboard/backend` remained 100% read-only.
3. **Enterprise V2 Isolation**: `SavingsCalculatorNewService.java` was untouched.
4. **Database & Schema Integrity**: Zero Flyway migration scripts added or modified. `spring.flyway.target=${FLYWAY_TARGET:8}` remains active. Database version remains V14.
5. **No Global State**: All caching is strictly calculation-scoped and thread-confined.
6. **Git Discipline**: Zero git commits or pushes performed.

### Milestone Status
**Milestone V18N is COMPLETE and FULLY VERIFIED.**
Halting execution to await explicit user approval before proceeding to Milestone V18O.

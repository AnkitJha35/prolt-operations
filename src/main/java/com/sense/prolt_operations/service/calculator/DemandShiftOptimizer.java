package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.service.SavingsCalculatorService;
import com.sense.prolt_operations.service.calculator.constant.SavingsCalculatorConstants;
import com.sense.prolt_operations.service.calculator.exception.CalculatorExecutionException;
import com.sense.prolt_operations.service.calculator.model.DemandShiftInsights;
import com.sense.prolt_operations.service.calculator.model.MarketSlotCalculation;
import com.sense.prolt_operations.service.calculator.model.MonthlyMarketDecisionResult;
import com.sense.prolt_operations.service.calculator.model.ShiftableSlot;
import com.sense.prolt_operations.service.calculator.model.ShiftedSlotItem;
import com.sense.prolt_operations.service.calculator.model.TodShiftSummaryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * Spring component encapsulating 15-minute slot demand shifting optimization
 * and multi-month demand shift insights aggregation.
 *
 * <p>Identifies expensive DISCOM/market slots and shifts flexible energy to cheaper
 * solar or off-peak market windows within available contract demand headroom and technical constraints.
 */
@Component
public class DemandShiftOptimizer {

    private static final Logger log = LoggerFactory.getLogger(DemandShiftOptimizer.class);

    private final CalculatorProperties properties;

    public DemandShiftOptimizer() {
        this(new CalculatorProperties());
    }

    @Autowired
    public DemandShiftOptimizer(CalculatorProperties properties) {
        this.properties = properties != null ? properties : new CalculatorProperties();
    }

    /**
     * Executes demand shift optimization for a single month based on the baseline market decision dispatch.
     *
     * @param entry the client savings calculator entry
     * @param id the entity identifier
     * @param marketResult the market decision output map containing slot-level dispatch data
     * @return map containing detailed slot shifts, ToD shift summaries, and financial savings
     */
    public Map<String, Object> calculateDemandShiftInsights(SavingsCalculatorEntry entry, String id, Map<String, Object> marketResult) {
        return calculateDemandShift(entry, id, marketResult).toMap();
    }

    public Map<String, Object> calculateDemandShiftInsights(SavingsCalculatorEntry entry, String id, MonthlyMarketDecisionResult marketResult) {
        return calculateDemandShift(entry, id, marketResult).toMap();
    }

    /**
     * Strongly typed demand shift calculation accepting a typed {@link MonthlyMarketDecisionResult}.
     */
    public DemandShiftInsights calculateDemandShift(SavingsCalculatorEntry entry, String id, MonthlyMarketDecisionResult marketResult) {
        List<MarketSlotCalculation> slots = marketResult != null ? marketResult.slotsData() : Collections.emptyList();
        return calculateDemandShiftFromSlots(entry, id, slots);
    }

    /**
     * Strongly typed demand shift calculation accepting a legacy or external {@link Map}.
     */
    public DemandShiftInsights calculateDemandShift(SavingsCalculatorEntry entry, String id, Map<String, Object> marketResult) {
        if (marketResult == null || marketResult.isEmpty()) {
            return calculateDemandShiftFromSlots(entry, id, Collections.emptyList());
        }
        List<?> rawSlots = (List<?>) marketResult.get("slotsData");
        List<MarketSlotCalculation> slots = new ArrayList<>();
        if (rawSlots != null) {
            for (Object obj : rawSlots) {
                if (obj instanceof MarketSlotCalculation msc) {
                    slots.add(msc);
                } else if (obj instanceof Map<?, ?> m) {
                    slots.add(MarketSlotCalculation.fromMap(m));
                }
            }
        }
        return calculateDemandShiftFromSlots(entry, id, slots);
    }

    public DemandShiftInsights calculateDemandShiftFromSlots(SavingsCalculatorEntry entry, String id, List<MarketSlotCalculation> slots) {
        double defaultSanctionedLoad = properties != null && properties.getRegulatory() != null
                ? properties.getRegulatory().getMinOaEligibilityKw() : 100.0;
        double sanctionedLoadKw = entry.getSanctionedLoadKw() != null ? entry.getSanctionedLoadKw().doubleValue() : defaultSanctionedLoad;
        double maxEnergyPerSlot = SavingsCalculatorService.getFlooredMaxEnergyPerSlot(entry.getSanctionedLoadKw(), entry.getPowerFactor(), this.properties);

        double originalTotalCost = 0.0;
        List<ShiftableSlot> shiftableSlots = new ArrayList<>();

        if (slots != null) {
            for (int i = 0; i < slots.size(); i++) {
                MarketSlotCalculation rawSlot = slots.get(i);
                boolean shouldBuy = rawSlot.shouldBuyFromMarket;
                double costPerKwh = shouldBuy ? rawSlot.bestMarketLanding : rawSlot.discomLanding;
                double origMarketEnergy = rawSlot.marketEnergy;
                double origConsumedMarket = rawSlot.consumedMarketEnergy;
                double origDiscomEnergy = rawSlot.discomEnergy;
                double currentEnergy = origConsumedMarket + origDiscomEnergy;

                double lossMultiplier = origMarketEnergy > 0 ? (origConsumedMarket / origMarketEnergy) : 0.90;
                double regionalHeadroom = Math.max(0.0, maxEnergyPerSlot - origMarketEnergy);
                double headroom = regionalHeadroom * lossMultiplier;
                originalTotalCost += (currentEnergy * costPerKwh);

                ShiftableSlot slot = new ShiftableSlot();
                slot.originalIndex = i;
                slot.costPerKwh = costPerKwh;
                slot.currentEnergy = currentEnergy;
                slot.originalEnergy = currentEnergy;
                slot.currentMarketEnergy = origMarketEnergy;
                slot.originalMarketEnergy = origMarketEnergy;
                slot.currentConsumedMarketEnergy = origConsumedMarket;
                slot.originalConsumedMarketEnergy = origConsumedMarket;
                slot.currentDiscomEnergy = origDiscomEnergy;
                slot.shouldBuyFromMarket = shouldBuy;
                slot.headroom = headroom;
                slot.lossMultiplier = lossMultiplier;
                slot.date = rawSlot.date != null ? rawSlot.date : "";
                slot.timeblock = rawSlot.timeblock;
                slot.tod = rawSlot.tod != null ? rawSlot.tod : "";

                shiftableSlots.add(slot);
            }
        }

        List<ShiftableSlot> expensiveSlots = new ArrayList<>(shiftableSlots);
        expensiveSlots.sort((a, b) -> Double.compare(b.costPerKwh, a.costPerKwh));

        List<ShiftableSlot> cheapSlots = new ArrayList<>(shiftableSlots);
        cheapSlots.sort(Comparator.comparingDouble(a -> a.costPerKwh));

        double shiftedEnergy = 0.0;
        double savingsAchieved = 0.0;
        int expensiveIdx = 0;
        int cheapIdx = 0;

        while (expensiveIdx < expensiveSlots.size() && cheapIdx < cheapSlots.size()) {
            ShiftableSlot expSlot = expensiveSlots.get(expensiveIdx);
            ShiftableSlot cheapSlot = cheapSlots.get(cheapIdx);

            double minSpread = properties != null && properties.getBusiness() != null
                    ? properties.getBusiness().getDemandShiftMinSpreadRs()
                    : SavingsCalculatorConstants.DEMAND_SHIFT_MIN_SPREAD_RS;
            if (expSlot.costPerKwh <= cheapSlot.costPerKwh + minSpread) {
                break;
            }

            if (expSlot.currentEnergy <= 0) {
                expensiveIdx++;
                continue;
            }

            if (cheapSlot.headroom <= 0) {
                cheapIdx++;
                continue;
            }

            double amountToShift = Math.min(expSlot.currentEnergy, cheapSlot.headroom);
            expSlot.currentEnergy -= amountToShift;

            if (expSlot.currentDiscomEnergy >= amountToShift) {
                expSlot.currentDiscomEnergy -= amountToShift;
            } else {
                double remainingToRemove = amountToShift - expSlot.currentDiscomEnergy;
                expSlot.currentDiscomEnergy = 0.0;
                expSlot.currentConsumedMarketEnergy -= remainingToRemove;
                expSlot.currentMarketEnergy -= (remainingToRemove / expSlot.lossMultiplier);
            }

            cheapSlot.headroom -= amountToShift;
            cheapSlot.currentEnergy += amountToShift;

            if (cheapSlot.shouldBuyFromMarket) {
                cheapSlot.currentConsumedMarketEnergy += amountToShift;
                cheapSlot.currentMarketEnergy += (amountToShift / cheapSlot.lossMultiplier);
            } else {
                cheapSlot.currentDiscomEnergy += amountToShift;
            }

            shiftedEnergy += amountToShift;
            savingsAchieved += amountToShift * (expSlot.costPerKwh - cheapSlot.costPerKwh);
        }

        double newTotalCost = 0.0;
        for (ShiftableSlot s : shiftableSlots) {
            newTotalCost += (s.currentEnergy * s.costPerKwh);
        }

        Map<String, Map<String, Double>> todShiftSummary = new LinkedHashMap<>();
        for (ShiftableSlot s : shiftableSlots) {
            Map<String, Double> d = todShiftSummary.computeIfAbsent(s.tod, k -> {
                Map<String, Double> init = new LinkedHashMap<>();
                init.put("originalEnergy", 0.0);
                init.put("newEnergy", 0.0);
                init.put("diff", 0.0);
                init.put("originalMarketEnergy", 0.0);
                init.put("newMarketEnergy", 0.0);
                return init;
            });
            d.put("originalEnergy", d.get("originalEnergy") + s.originalEnergy);
            d.put("newEnergy", d.get("newEnergy") + s.currentEnergy);
            d.put("originalMarketEnergy", d.get("originalMarketEnergy") + s.originalMarketEnergy);
            d.put("newMarketEnergy", d.get("newMarketEnergy") + s.currentMarketEnergy);
            d.put("diff", d.get("diff") + (s.currentEnergy - s.originalEnergy));
        }

        List<TodShiftSummaryItem> todShiftList = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> entryTod : todShiftSummary.entrySet()) {
            Map<String, Double> v = entryTod.getValue();
            todShiftList.add(new TodShiftSummaryItem(
                    entryTod.getKey(),
                    v.get("originalEnergy"),
                    v.get("newEnergy"),
                    v.get("diff"),
                    v.get("originalMarketEnergy"),
                    v.get("newMarketEnergy")
            ));
        }

        List<ShiftedSlotItem> outSlots = new ArrayList<>();
        if (slots != null) {
            for (ShiftableSlot shiftableSlot : shiftableSlots) {
                MarketSlotCalculation originalRawSlot = slots.get(shiftableSlot.originalIndex);
                outSlots.add(new ShiftedSlotItem(
                        shiftableSlot.date,
                        shiftableSlot.timeblock,
                        shiftableSlot.tod,
                        shiftableSlot.originalEnergy,
                        shiftableSlot.currentEnergy,
                        shiftableSlot.costPerKwh,
                        originalRawSlot.marketSource,
                        shiftableSlot.shouldBuyFromMarket,
                        shiftableSlot.currentMarketEnergy,
                        shiftableSlot.currentDiscomEnergy,
                        originalRawSlot.damMcp,
                        originalRawSlot.rtmMcp,
                        originalRawSlot.gdamMcp
                ));
            }
        }

        double shiftableCapacityMw = maxEnergyPerSlot > 0 ? (maxEnergyPerSlot / 250.0) * 0.5 : 0.5;

        return new DemandShiftInsights(
                id,
                entry.getClientName(),
                sanctionedLoadKw,
                maxEnergyPerSlot,
                originalTotalCost,
                newTotalCost,
                savingsAchieved,
                shiftedEnergy,
                savingsAchieved > 0 ? savingsAchieved : 250000.0,
                shiftableCapacityMw,
                "11:00 - 15:00 to 00:00 - 05:00",
                List.of("17:00 - 22:00"),
                List.of("10:00 - 16:00"),
                todShiftList,
                outSlots
        );
    }

    /**
     * Strongly typed multi-month demand shift aggregator.
     */
    public DemandShiftInsights calculateDemandShiftAllMonths(
            SavingsCalculatorEntry entry,
            String id,
            Map<String, Map<String, Object>> todConsumptions,
            Function<String, DemandShiftInsights> monthlyShiftProvider) {

        List<String> months = new ArrayList<>(todConsumptions.keySet());
        Collections.sort(months);

        double originalTotalCost = 0.0;
        double newTotalCost = 0.0;
        double savingsAchieved = 0.0;
        double shiftedEnergy = 0.0;
        Map<String, Map<String, Double>> aggregatedTodSummary = new LinkedHashMap<>();

        int successfulMonths = 0;
        List<String> failedMonths = new ArrayList<>();
        Throwable firstFailure = null;

        for (String month : months) {
            try {
                DemandShiftInsights res = monthlyShiftProvider.apply(month);
                if (res != null) {
                    successfulMonths++;
                    originalTotalCost += res.originalTotalCost();
                    newTotalCost += res.newTotalCost();
                    savingsAchieved += res.savingsAchieved();
                    shiftedEnergy += res.shiftedEnergy();

                    for (TodShiftSummaryItem item : res.todShiftSummary()) {
                        String tod = item.tod();
                        Map<String, Double> agg = aggregatedTodSummary.computeIfAbsent(tod, k -> {
                            Map<String, Double> init = new LinkedHashMap<>();
                            init.put("originalEnergy", 0.0);
                            init.put("newEnergy", 0.0);
                            init.put("diff", 0.0);
                            init.put("originalMarketEnergy", 0.0);
                            init.put("newMarketEnergy", 0.0);
                            return init;
                        });
                        agg.put("originalEnergy", agg.get("originalEnergy") + item.originalEnergy());
                        agg.put("newEnergy", agg.get("newEnergy") + item.newEnergy());
                        agg.put("diff", agg.get("diff") + item.diff());
                        agg.put("originalMarketEnergy", agg.get("originalMarketEnergy") + item.originalMarketEnergy());
                        agg.put("newMarketEnergy", agg.get("newMarketEnergy") + item.newMarketEnergy());
                    }
                }
            } catch (Exception e) {
                failedMonths.add(month);
                if (firstFailure == null) {
                    firstFailure = e;
                }
                log.error("[SavingsCalculator] Error calculating demand shift for month '{}' for client '{}' (id: {}) in calculateDemandShiftAllMonths: {}",
                        month, entry.getClientName(), id, e.getMessage(), e);
            }
        }

        if (successfulMonths == 0 && !months.isEmpty()) {
            throw new CalculatorExecutionException(
                "Demand Shift Insights",
                String.join(", ", failedMonths),
                id,
                "All " + months.size() + " monthly demand shift calculations failed" + (firstFailure != null ? ": " + firstFailure.getMessage() : ""),
                firstFailure
            );
        }

        if (!failedMonths.isEmpty()) {
            log.warn("[SavingsCalculator] Multi-month demand shift completed with partial failures for client '{}' (id: {}). Succeeded: {}/{}, Failed months: {}",
                    entry.getClientName(), id, successfulMonths, months.size(), failedMonths);
        }

        List<TodShiftSummaryItem> todList = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> e : aggregatedTodSummary.entrySet()) {
            Map<String, Double> v = e.getValue();
            todList.add(new TodShiftSummaryItem(
                    e.getKey(),
                    v.get("originalEnergy"),
                    v.get("newEnergy"),
                    v.get("diff"),
                    v.get("originalMarketEnergy"),
                    v.get("newMarketEnergy")
            ));
        }

        double defaultSanctionedLoad = properties != null && properties.getRegulatory() != null
                ? properties.getRegulatory().getMinOaEligibilityKw() : 100.0;
        double sanctionedLoadKw = entry.getSanctionedLoadKw() != null ? entry.getSanctionedLoadKw().doubleValue() : defaultSanctionedLoad;
        double maxEnergyPerSlot = SavingsCalculatorService.getFlooredMaxEnergyPerSlot(entry.getSanctionedLoadKw(), entry.getPowerFactor(), this.properties);
        double shiftableCapacityMw = maxEnergyPerSlot > 0 ? (maxEnergyPerSlot / 250.0) * 0.5 : 0.5;

        return new DemandShiftInsights(
                id,
                entry.getClientName(),
                sanctionedLoadKw,
                maxEnergyPerSlot,
                originalTotalCost,
                newTotalCost,
                savingsAchieved,
                shiftedEnergy,
                savingsAchieved > 0 ? savingsAchieved : 250000.0,
                shiftableCapacityMw,
                "11:00 - 15:00 to 00:00 - 05:00",
                List.of("17:00 - 22:00"),
                List.of("10:00 - 16:00"),
                todList,
                Collections.emptyList()
        );
    }

    /**
     * Iterates over all months in the client's ToD consumption history, invokes the single-month
     * demand shift calculator for each, and aggregates total savings, energy, and ToD distributions.
     */
    public Map<String, Object> calculateDemandShiftInsightsAllMonths(
            SavingsCalculatorEntry entry,
            String id,
            Map<String, Map<String, Object>> todConsumptions,
            Function<String, Map<String, Object>> monthlyShiftProvider) {

        return calculateDemandShiftAllMonths(
                entry,
                id,
                todConsumptions,
                month -> {
                    Map<String, Object> map = monthlyShiftProvider.apply(month);
                    return map != null ? DemandShiftInsights.fromMap(map) : null;
                }
        ).toMap();
    }
}

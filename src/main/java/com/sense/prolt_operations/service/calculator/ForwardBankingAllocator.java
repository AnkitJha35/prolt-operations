package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.entity.StateTariff;
import com.sense.prolt_operations.service.calculator.model.DemandShiftInsights;
import com.sense.prolt_operations.service.calculator.model.EnergyMarketType;
import com.sense.prolt_operations.service.calculator.model.MarketDecisionContext;
import com.sense.prolt_operations.service.calculator.model.MarketSlotCalculation;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ForwardBankingAllocator {

    public void allocate(List<MarketSlotCalculation> slotsData, MarketDecisionContext context) {
        if (slotsData == null || slotsData.isEmpty()) {
            return;
        }

        Map<String, List<MarketSlotCalculation>> slotsByTod = new LinkedHashMap<>();
        for (MarketSlotCalculation s : slotsData) {
            String key = s.tod.toUpperCase();
            slotsByTod.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        StateTariff baseTariff = !context.tariffs().isEmpty() ? context.tariffs().get(0) : null;
        boolean isKvahBilling = baseTariff != null && "kvah".equalsIgnoreCase(baseTariff.getBaseEnergyUnit());
        if (context.monthConsumptions().containsKey("Billing Unit")) {
            isKvahBilling = "kvah".equalsIgnoreCase(String.valueOf(context.monthConsumptions().get("Billing Unit")));
        }
        boolean consumptionValuesAreKwh = context.monthConsumptions().get("slots") instanceof List;
        double globalPf = context.entry().getPowerFactor() != null ? context.entry().getPowerFactor().doubleValue() : 0.99;
        if (globalPf <= 0) globalPf = 0.99;

        for (String groupKey : slotsByTod.keySet()) {
            List<MarketSlotCalculation> slotsInGroup = slotsByTod.get(groupKey);
            double slabConsumption = CalculatorUtils.getSlabConsumption(
                    groupKey,
                    context.monthConsumptions(),
                    slotsInGroup.size(),
                    slotsData.size(),
                    isKvahBilling,
                    consumptionValuesAreKwh,
                    globalPf,
                    context.shiftInsights()
            );
            if (slabConsumption <= 0) continue;

            if (context.shiftInsights() != null && context.shiftInsights().hasSlotsData()) {
                DemandShiftInsights shiftInsights = context.shiftInsights();
                for (MarketSlotCalculation s : slotsInGroup) {
                    shiftInsights.findSlot(s.date, s.timeblock).ifPresent(sm -> {
                        s.marketEnergy = sm.marketEnergy();
                        s.consumedMarketEnergy = 0.0;
                        s.discomEnergy = sm.discomEnergy();
                        double basePrice = resolveMarketBasePrice(s);
                        s.exactMarketEnergyCost = s.marketEnergy * basePrice;
                    });
                }
            } else {
                int numSlots = slotsInGroup.size();
                double requiredEnergyPerSlot = slabConsumption / numSlots;
                double maxPerSlot = context.maxEnergyPerSlot();

                MarketSlotCalculation sampleSlot = slotsInGroup.get(0);
                double istsLossRate = sampleSlot.istsLoss / 100.0;
                double stuLossRate = context.stuLoss() / 100.0;
                double wheelingLossRate = context.wheelingLoss() / 100.0;
                double groupLossMult = (1.0 - istsLossRate) * (1.0 - stuLossRate) * (1.0 - wheelingLossRate);
                double safeLossMultiplier = groupLossMult > 0.5 ? groupLossMult : 1.0;

                for (int idx = 0; idx < slotsInGroup.size(); idx++) {
                    MarketSlotCalculation s = slotsInGroup.get(idx);
                    s.idx = idx;
                    s.unfulfilledEnergy = requiredEnergyPerSlot;
                    s.marketEnergy = 0.0;
                    s.consumedMarketEnergy = 0.0;
                    s.exactMarketEnergyCost = 0.0;

                    double basePrice = resolveMarketBasePrice(s);
                    s.tempBasePrice = basePrice;
                }

                List<MarketSlotCalculation> marketSlots = new ArrayList<>(slotsInGroup.stream()
                        .filter(s -> s.shouldBuyFromMarket && s.bestMarketLanding > 0)
                        .toList());
                marketSlots.sort(Comparator.comparingDouble(a -> a.tempBasePrice));

                for (MarketSlotCalculation buyingSlot : marketSlots) {
                    double availableToBuy = maxPerSlot - buyingSlot.marketEnergy;
                    if (availableToBuy <= 0) continue;

                    double boughtInThisSlot = 0.0;

                    for (int i = buyingSlot.idx; i < numSlots; i++) {
                        MarketSlotCalculation targetSlot = slotsInGroup.get(i);
                        if (targetSlot.unfulfilledEnergy > 0) {
                            double neededAtConsumerBus = targetSlot.unfulfilledEnergy;
                            double grossedUpNeeded = neededAtConsumerBus / safeLossMultiplier;
                            double rawAllocation = Math.min(availableToBuy, grossedUpNeeded);
                            double allocationMw = Math.floor((rawAllocation / 250.0) * 10.0 + 1e-9) / 10.0;
                            double allocation = allocationMw * 250.0;

                            if (allocation <= 0) continue;

                            double deliveredAtConsumerBus = allocation * safeLossMultiplier;
                            targetSlot.unfulfilledEnergy = Math.max(0.0, targetSlot.unfulfilledEnergy - deliveredAtConsumerBus);
                            targetSlot.consumedMarketEnergy += deliveredAtConsumerBus;
                            targetSlot.exactMarketEnergyCost += allocation * buyingSlot.tempBasePrice;

                            boughtInThisSlot += allocation;
                            availableToBuy -= allocation;

                            if (availableToBuy <= 0) break;
                        }
                    }

                    buyingSlot.marketEnergy += boughtInThisSlot;
                }

                for (MarketSlotCalculation targetSlot : slotsInGroup) {
                    targetSlot.discomEnergy = targetSlot.unfulfilledEnergy;
                }
            }
        }
    }

    private double resolveMarketBasePrice(MarketSlotCalculation s) {
        return EnergyMarketType.fromValue(s.marketSource)
                .map(market -> switch (market) {
                    case DAM -> s.damMcp != null ? s.damMcp : 0.0;
                    case RTM -> s.rtmMcp != null ? s.rtmMcp : 0.0;
                    case GDAM -> s.gdamMcp != null ? s.gdamMcp : 0.0;
                })
                .orElse(0.0);
    }
}

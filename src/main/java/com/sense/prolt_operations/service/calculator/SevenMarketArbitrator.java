package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.service.calculator.model.EnergyMarketType;
import com.sense.prolt_operations.service.calculator.model.EnergySource;
import com.sense.prolt_operations.service.calculator.model.MarketSlotCalculation;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SevenMarketArbitrator {

    private static final List<List<EnergyMarketType>> COMBINATIONS = List.of(
            List.of(EnergyMarketType.DAM),
            List.of(EnergyMarketType.RTM),
            List.of(EnergyMarketType.GDAM),
            List.of(EnergyMarketType.DAM, EnergyMarketType.RTM),
            List.of(EnergyMarketType.DAM, EnergyMarketType.GDAM),
            List.of(EnergyMarketType.RTM, EnergyMarketType.GDAM),
            List.of(EnergyMarketType.DAM, EnergyMarketType.RTM, EnergyMarketType.GDAM)
    );

    public void arbitrate(List<MarketSlotCalculation> slotsData, double sldcSchedulingFees, double maxEnergyPerSlot) {
        if (slotsData == null || slotsData.isEmpty()) {
            return;
        }

        Map<String, List<MarketSlotCalculation>> slotsByDate = new LinkedHashMap<>();
        for (MarketSlotCalculation s : slotsData) {
            slotsByDate.computeIfAbsent(s.date, k -> new ArrayList<>()).add(s);
        }

        for (Map.Entry<String, List<MarketSlotCalculation>> dateEntry : slotsByDate.entrySet()) {
            List<MarketSlotCalculation> dateSlots = dateEntry.getValue();
            List<MarketSlotCalculation> marketableSlots = dateSlots.stream().filter(s -> s.shouldBuyFromMarket).toList();
            if (marketableSlots.isEmpty()) continue;

            List<EnergyMarketType> bestCombination = List.of(EnergyMarketType.DAM);
            double lowestTotalCost = Double.POSITIVE_INFINITY;

            for (List<EnergyMarketType> combo : COMBINATIONS) {
                double energyCost = 0.0;
                double sldcCost = combo.size() * sldcSchedulingFees;

                for (MarketSlotCalculation s : marketableSlots) {
                    double bestCost = s.discomLanding * maxEnergyPerSlot;
                    for (EnergyMarketType market : combo) {
                        Double landing = s.getMarketLanding(market);
                        if (landing != null && landing < bestCost) {
                            bestCost = landing * maxEnergyPerSlot;
                        }
                    }
                    energyCost += bestCost;
                }

                double totalCost = energyCost + sldcCost;
                if (totalCost < lowestTotalCost) {
                    lowestTotalCost = totalCost;
                    bestCombination = combo;
                }
            }

            for (MarketSlotCalculation s : marketableSlots) {
                double bestCost = s.discomLanding * maxEnergyPerSlot;
                EnergyMarketType bestMarket = null;
                for (EnergyMarketType market : bestCombination) {
                    Double landing = s.getMarketLanding(market);
                    if (landing != null && landing < bestCost) {
                        bestCost = landing * maxEnergyPerSlot;
                        bestMarket = market;
                    }
                }

                if (bestMarket != null) {
                    s.marketSource = bestMarket.getValue();
                    Double landing = s.getMarketLanding(bestMarket);
                    s.bestMarketLanding = landing != null ? landing : s.discomLanding;
                } else {
                    s.shouldBuyFromMarket = false;
                    s.marketSource = EnergySource.DISCOM.getValue();
                }
            }
        }
    }
}

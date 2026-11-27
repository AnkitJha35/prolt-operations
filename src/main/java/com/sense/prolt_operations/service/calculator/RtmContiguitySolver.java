package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.service.calculator.model.EnergyMarketType;
import com.sense.prolt_operations.service.calculator.model.MarketSlotCalculation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class RtmContiguitySolver {

    public void resolveContiguity(List<MarketSlotCalculation> slotsData) {
        if (slotsData == null || slotsData.isEmpty()) {
            return;
        }

        for (int i = 0; i < slotsData.size(); i++) {
            MarketSlotCalculation slot = slotsData.get(i);
            if (EnergyMarketType.RTM.getValue().equals(slot.marketSource) && slot.shouldBuyFromMarket) {
                MarketSlotCalculation prevSlot = i > 0 ? slotsData.get(i - 1) : null;
                MarketSlotCalculation nextSlot = i < slotsData.size() - 1 ? slotsData.get(i + 1) : null;

                boolean prevIsRtm = prevSlot != null && EnergyMarketType.RTM.getValue().equals(prevSlot.marketSource) && prevSlot.shouldBuyFromMarket;
                boolean nextIsRtm = nextSlot != null && EnergyMarketType.RTM.getValue().equals(nextSlot.marketSource) && nextSlot.shouldBuyFromMarket;

                if (!prevIsRtm && !nextIsRtm) {
                    List<Double> altPrices = new ArrayList<>();
                    if (slot.damLanding != null && slot.damLanding > 0) altPrices.add(slot.damLanding);
                    if (slot.gdamLanding != null && slot.gdamLanding > 0) altPrices.add(slot.gdamLanding);
                    double bestAltLanding = !altPrices.isEmpty() ? Collections.min(altPrices) : slot.discomLanding;

                    double downgradePenalty = Double.POSITIVE_INFINITY;
                    String newDowngradeSource = EnergyMarketType.DAM.getValue();
                    boolean newDowngradeShouldBuy = false;

                    if (bestAltLanding < slot.discomLanding) {
                        downgradePenalty = bestAltLanding - (slot.rtmLanding != null ? slot.rtmLanding : 0.0);
                        newDowngradeSource = (slot.damLanding != null && Math.abs(bestAltLanding - slot.damLanding) < 1e-6)
                                ? EnergyMarketType.DAM.getValue() : EnergyMarketType.GDAM.getValue();
                        newDowngradeShouldBuy = true;
                    } else {
                        downgradePenalty = slot.discomLanding - (slot.rtmLanding != null ? slot.rtmLanding : 0.0);
                        newDowngradeSource = EnergyMarketType.DAM.getValue();
                        newDowngradeShouldBuy = false;
                    }

                    double upgradePrevPenalty = Double.POSITIVE_INFINITY;
                    if (prevSlot != null && prevSlot.rtmLanding != null && prevSlot.rtmLanding > 0) {
                        double curCost = prevSlot.shouldBuyFromMarket ? prevSlot.bestMarketLanding : prevSlot.discomLanding;
                        upgradePrevPenalty = prevSlot.rtmLanding - curCost;
                    }

                    double upgradeNextPenalty = Double.POSITIVE_INFINITY;
                    if (nextSlot != null && nextSlot.rtmLanding != null && nextSlot.rtmLanding > 0) {
                        double curCost = nextSlot.shouldBuyFromMarket ? nextSlot.bestMarketLanding : nextSlot.discomLanding;
                        upgradeNextPenalty = nextSlot.rtmLanding - curCost;
                    }

                    double minPenalty = Math.min(downgradePenalty, Math.min(upgradePrevPenalty, upgradeNextPenalty));

                    if (minPenalty == downgradePenalty) {
                        slot.marketSource = newDowngradeSource;
                        slot.bestMarketLanding = newDowngradeShouldBuy ? bestAltLanding : 0.0;
                        slot.shouldBuyFromMarket = newDowngradeShouldBuy;
                        slot.savingsPerKwh = newDowngradeShouldBuy ? slot.discomLanding - bestAltLanding : 0.0;
                    } else if (minPenalty == upgradePrevPenalty && prevSlot != null) {
                        prevSlot.marketSource = EnergyMarketType.RTM.getValue();
                        prevSlot.bestMarketLanding = prevSlot.rtmLanding;
                        prevSlot.shouldBuyFromMarket = true;
                        prevSlot.savingsPerKwh = prevSlot.discomLanding - prevSlot.rtmLanding;
                    } else if (minPenalty == upgradeNextPenalty && nextSlot != null) {
                        nextSlot.marketSource = EnergyMarketType.RTM.getValue();
                        nextSlot.bestMarketLanding = nextSlot.rtmLanding;
                        nextSlot.shouldBuyFromMarket = true;
                        nextSlot.savingsPerKwh = nextSlot.discomLanding - nextSlot.rtmLanding;
                    }
                }
            }
        }
    }
}

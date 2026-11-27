package com.sense.prolt_operations.service.calculator.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strongly typed calculation model representing an individual 15-minute dispatch slot
 * during market arbitration and optimization in {@code calculateMarketDecision}.
 *
 * <p>Tracks landed market prices, technical transmission loss factors, allocated market
 * vs. DISCOM energy, exact power purchase costs, and provides serialization to the
 * legacy 31-entry JSON response map.
 */
public class MarketSlotCalculation {

    public String date;
    public int timeblock;
    public int hour;
    public String tod;
    public Double damMcp;
    public Double rtmMcp;
    public Double gdamMcp;
    public Double damLanding;
    public Double rtmLanding;
    public Double gdamLanding;
    public double bestMarketLanding;
    public String marketSource;
    public double discomLanding;
    public boolean shouldBuyFromMarket;
    public double savingsPerKwh;
    public double istsLoss;
    public double stuLoss;
    public double wheelingLoss;

    public int idx;
    public double unfulfilledEnergy;
    public double marketEnergy;
    public double consumedMarketEnergy;
    public double exactMarketEnergyCost;
    public double discomEnergy;
    public double tempBasePrice;

    public MarketSlotCalculation() {
    }

    public Double getMarketLanding(EnergyMarketType market) {
        if (market == null) return null;
        return switch (market) {
            case DAM -> damLanding;
            case RTM -> rtmLanding;
            case GDAM -> gdamLanding;
        };
    }

    public Double getMarketLanding(String market) {
        return EnergyMarketType.fromValue(market)
                .map(this::getMarketLanding)
                .orElse(null);
    }

    public EnergySource getEnergySource() {
        return EnergySource.fromValue(marketSource).orElse(null);
    }

    public void setEnergySource(EnergySource source) {
        this.marketSource = source != null ? source.getValue() : null;
    }

    /**
     * Converts this slot calculation into an untyped {@link Map} matching the legacy
     * JSON response contract (including legacy block projection support for CALC-01).
     *
     * @return Map with exact legacy keys in deterministic insertion order.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> sm = new LinkedHashMap<>();
        sm.put("date", date);
        sm.put("timeblock", timeblock);
        sm.put("hour", hour);
        sm.put("tod", tod);
        sm.put("damMcp", damMcp);
        sm.put("rtmMcp", rtmMcp);
        sm.put("gdamMcp", gdamMcp);
        sm.put("damLanding", damLanding);
        sm.put("rtmLanding", rtmLanding);
        sm.put("gdamLanding", gdamLanding);
        sm.put("bestMarketLanding", bestMarketLanding);
        sm.put("marketSource", marketSource);
        sm.put("discomLanding", discomLanding);
        sm.put("shouldBuyFromMarket", shouldBuyFromMarket);
        sm.put("savingsPerKwh", savingsPerKwh);
        sm.put("istsLoss", istsLoss);
        sm.put("stuLoss", stuLoss);
        sm.put("wheelingLoss", wheelingLoss);
        sm.put("marketEnergy", marketEnergy);
        sm.put("discomEnergy", discomEnergy);
        sm.put("consumedMarketEnergy", consumedMarketEnergy);
        sm.put("exactMarketEnergyCost", exactMarketEnergyCost);

        // Legacy block projection support for CALC-01
        sm.put("timeBlock", timeblock);
        sm.put("startTime", String.format("%02d:%02d", (timeblock - 1) * 15 / 60, ((timeblock - 1) * 15) % 60));
        sm.put("endTime", String.format("%02d:%02d", timeblock * 15 / 60, (timeblock * 15) % 60));
        sm.put("damPrice", damLanding != null ? damLanding : 4.50);
        sm.put("gdamPrice", gdamLanding != null ? gdamLanding : 4.20);
        sm.put("rtmPrice", rtmLanding != null ? rtmLanding : 4.35);
        sm.put("discomPrice", discomLanding);
        sm.put("selectedSource", shouldBuyFromMarket ? marketSource : EnergySource.DISCOM.getValue());
        sm.put("recommendedPrice", shouldBuyFromMarket ? bestMarketLanding : discomLanding);

        return sm;
    }

    public static MarketSlotCalculation fromMap(Map<?, ?> sm) {
        if (sm == null) return new MarketSlotCalculation();
        MarketSlotCalculation s = new MarketSlotCalculation();
        s.date = sm.get("date") != null ? String.valueOf(sm.get("date")) : null;
        s.timeblock = sm.get("timeblock") instanceof Number n ? n.intValue() : 0;
        s.hour = sm.get("hour") instanceof Number n ? n.intValue() : 0;
        s.tod = sm.get("tod") != null ? String.valueOf(sm.get("tod")) : null;
        s.damMcp = sm.get("damMcp") instanceof Number n ? n.doubleValue() : null;
        s.rtmMcp = sm.get("rtmMcp") instanceof Number n ? n.doubleValue() : null;
        s.gdamMcp = sm.get("gdamMcp") instanceof Number n ? n.doubleValue() : null;
        s.damLanding = sm.get("damLanding") instanceof Number n ? n.doubleValue() : null;
        s.rtmLanding = sm.get("rtmLanding") instanceof Number n ? n.doubleValue() : null;
        s.gdamLanding = sm.get("gdamLanding") instanceof Number n ? n.doubleValue() : null;
        s.bestMarketLanding = sm.get("bestMarketLanding") instanceof Number n ? n.doubleValue() : 0.0;
        s.marketSource = sm.get("marketSource") != null ? String.valueOf(sm.get("marketSource")) : null;
        s.discomLanding = sm.get("discomLanding") instanceof Number n ? n.doubleValue() : 0.0;
        s.shouldBuyFromMarket = Boolean.TRUE.equals(sm.get("shouldBuyFromMarket"));
        s.savingsPerKwh = sm.get("savingsPerKwh") instanceof Number n ? n.doubleValue() : 0.0;
        s.istsLoss = sm.get("istsLoss") instanceof Number n ? n.doubleValue() : 0.0;
        s.stuLoss = sm.get("stuLoss") instanceof Number n ? n.doubleValue() : 0.0;
        s.wheelingLoss = sm.get("wheelingLoss") instanceof Number n ? n.doubleValue() : 0.0;
        s.marketEnergy = sm.get("marketEnergy") instanceof Number n ? n.doubleValue() : 0.0;
        s.discomEnergy = sm.get("discomEnergy") instanceof Number n ? n.doubleValue() : 0.0;
        s.consumedMarketEnergy = sm.get("consumedMarketEnergy") instanceof Number n ? n.doubleValue() : 0.0;
        s.exactMarketEnergyCost = sm.get("exactMarketEnergyCost") instanceof Number n ? n.doubleValue() : 0.0;
        return s;
    }
}

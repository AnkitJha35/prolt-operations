package com.sense.prolt_operations.service.calculator.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strongly typed, immutable record representing a 15-minute timeblock dispatch slot
 * within the HV-2 savings calculation workflow ({@code calculateSavingsHV2}).
 *
 * <p>Encapsulates all landed market prices, DISCOM landing price, technical transmission losses,
 * and energy/cost allocations for the slot.
 */
public record Hv2SlotRecord(
        String date,
        int slot,
        String timeStr,
        String todSlab,
        Double damLandingPrice,
        Double rtmLandingPrice,
        Double gdamLandingPrice,
        double discomLandingPrice,
        double comparedLowestPrice,
        String selectedSource,
        double maxEnergyPerSlot,
        double optimizedCost,
        double baselineCost,
        double istsLoss,
        double stuLoss,
        double wheelingLoss
) {

    /**
     * Factory method creating a slot before energy allocation (with zero energy and zero costs).
     */
    public static Hv2SlotRecord initial(
            String date,
            int slot,
            String timeStr,
            String todSlab,
            Double damLandingPrice,
            Double rtmLandingPrice,
            Double gdamLandingPrice,
            double discomLandingPrice,
            double comparedLowestPrice,
            String selectedSource,
            double istsLoss,
            double stuLoss,
            double wheelingLoss
    ) {
        return new Hv2SlotRecord(
                date,
                slot,
                timeStr,
                todSlab,
                damLandingPrice,
                rtmLandingPrice,
                gdamLandingPrice,
                discomLandingPrice,
                comparedLowestPrice,
                selectedSource,
                0.0,
                0.0,
                0.0,
                istsLoss,
                stuLoss,
                wheelingLoss
        );
    }

    /**
     * Returns a copy of this slot with allocated energy and resulting baseline/optimized costs.
     */
    public Hv2SlotRecord withEnergyAndCosts(double maxEnergyPerSlot, double optimizedCost, double baselineCost) {
        return new Hv2SlotRecord(
                date,
                slot,
                timeStr,
                todSlab,
                damLandingPrice,
                rtmLandingPrice,
                gdamLandingPrice,
                discomLandingPrice,
                comparedLowestPrice,
                selectedSource,
                maxEnergyPerSlot,
                optimizedCost,
                baselineCost,
                istsLoss,
                stuLoss,
                wheelingLoss
        );
    }

    /**
     * Converts this strongly typed slot into an untyped {@link Map} matching the legacy
     * JSON response contract.
     *
     * @return Map with exact legacy keys in deterministic insertion order.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("date", date);
        map.put("slot", slot);
        map.put("timeStr", timeStr);
        map.put("todSlab", todSlab);
        map.put("damLandingPrice", damLandingPrice);
        map.put("rtmLandingPrice", rtmLandingPrice);
        map.put("gdamLandingPrice", gdamLandingPrice);
        map.put("discomLandingPrice", discomLandingPrice);
        map.put("comparedLowestPrice", comparedLowestPrice);
        map.put("selectedSource", selectedSource);
        map.put("maxEnergyPerSlot", maxEnergyPerSlot);
        map.put("optimizedCost", optimizedCost);
        map.put("baselineCost", baselineCost);
        map.put("istsLoss", istsLoss);
        map.put("stuLoss", stuLoss);
        map.put("wheelingLoss", wheelingLoss);
        return map;
    }
}

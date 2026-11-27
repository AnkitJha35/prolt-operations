package com.sense.prolt_operations.service.calculator.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable representation of a ToD slab demand shift summary entry.
 */
public record TodShiftSummaryItem(
        String tod,
        double originalEnergy,
        double newEnergy,
        double diff,
        double originalMarketEnergy,
        double newMarketEnergy
) {

    /**
     * Serializes this item into a Map matching the wire JSON format.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tod", tod);
        map.put("originalEnergy", originalEnergy);
        map.put("newEnergy", newEnergy);
        map.put("diff", diff);
        map.put("originalMarketEnergy", originalMarketEnergy);
        map.put("newMarketEnergy", newMarketEnergy);
        return map;
    }

    /**
     * Safely constructs an item from a loose map.
     */
    public static TodShiftSummaryItem fromMap(Map<?, ?> map) {
        if (map == null) {
            return new TodShiftSummaryItem("", 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        return new TodShiftSummaryItem(
                map.get("tod") != null ? String.valueOf(map.get("tod")) : "",
                toDouble(map.get("originalEnergy")),
                toDouble(map.get("newEnergy")),
                toDouble(map.get("diff")),
                toDouble(map.get("originalMarketEnergy")),
                toDouble(map.get("newMarketEnergy"))
        );
    }

    private static double toDouble(Object val) {
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val != null) {
            try {
                return Double.parseDouble(val.toString().trim());
            } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }
}

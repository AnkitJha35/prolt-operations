package com.sense.prolt_operations.service.calculator.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strongly typed, immutable record representing a Time-of-Day (ToD) tariff slab summary
 * generated during the market decision calculation ({@code calculateMarketDecision}).
 *
 * <p>Tracks total energy consumed, market energy scheduled, and base market energy cost
 * for each distinct tariff slab (e.g. TOD1 through TOD6).
 */
public record TodSlabSummary(
        String slabName,
        double totalEnergyKwh,
        double marketEnergyKwh,
        double marketCostBase
) {

    /**
     * Converts this strongly typed summary to an untyped {@link Map} matching the legacy
     * JSON response contract.
     *
     * @return Map with exact legacy keys in deterministic insertion order.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slabName", slabName);
        map.put("totalEnergyKwh", totalEnergyKwh);
        map.put("marketEnergyKwh", marketEnergyKwh);
        map.put("marketCostBase", marketCostBase);
        return map;
    }

    public static TodSlabSummary fromMap(Map<?, ?> map) {
        if (map == null) return new TodSlabSummary("", 0.0, 0.0, 0.0);
        return new TodSlabSummary(
                map.get("slabName") != null ? String.valueOf(map.get("slabName")) : "",
                map.get("totalEnergyKwh") instanceof Number n ? n.doubleValue() : 0.0,
                map.get("marketEnergyKwh") instanceof Number n ? n.doubleValue() : 0.0,
                map.get("marketCostBase") instanceof Number n ? n.doubleValue() : 0.0
        );
    }
}

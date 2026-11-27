package com.sense.prolt_operations.service.calculator.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strongly typed, immutable record representing an Open Access (OA) detailed breakdown
 * entry for an individual Time-of-Day tariff slab.
 *
 * <p>Contains volumetric allocations (DISCOM vs OA vs consumer bus units) and billing figures
 * (baseline DISCOM bill, prolt DISCOM bill, and total landed OA bill).
 */
public record OaBreakdownItem(
        String slabName,
        double discomUnits,
        double oaUnits,
        double discomBill,
        double proltDiscomBill,
        double consumerBusUnits,
        double oaBill,
        double discomRate
) {

    /**
     * Converts this strongly typed breakdown item into an untyped {@link Map} matching
     * the legacy JSON response contract.
     *
     * @return Map with exact legacy keys in deterministic insertion order.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slabName", slabName);
        map.put("discomUnits", discomUnits);
        map.put("oaUnits", oaUnits);
        map.put("discomBill", discomBill);
        map.put("proltDiscomBill", proltDiscomBill);
        map.put("consumerBusUnits", consumerBusUnits);
        map.put("oaBill", oaBill);
        map.put("discomRate", discomRate);
        return map;
    }

    public static OaBreakdownItem fromMap(Map<?, ?> map) {
        if (map == null) return new OaBreakdownItem("", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        return new OaBreakdownItem(
                map.get("slabName") != null ? String.valueOf(map.get("slabName")) : "",
                toDouble(map.get("discomUnits")),
                toDouble(map.get("oaUnits")),
                toDouble(map.get("discomBill")),
                toDouble(map.get("proltDiscomBill")),
                toDouble(map.get("consumerBusUnits")),
                toDouble(map.get("oaBill")),
                toDouble(map.get("discomRate"))
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

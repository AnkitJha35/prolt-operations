package com.sense.prolt_operations.service.calculator.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable representation of a single 15-minute slot under demand-shift optimization.
 */
public record ShiftedSlotItem(
        String date,
        int timeblock,
        String tod,
        double originalEnergy,
        double newEnergy,
        double costPerKwh,
        String marketSource,
        boolean shouldBuyFromMarket,
        double marketEnergy,
        double discomEnergy,
        Double damMcp,
        Double rtmMcp,
        Double gdamMcp
) {

    /**
     * Serializes this slot item to a Map matching the wire JSON format.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("date", date);
        map.put("timeblock", timeblock);
        map.put("tod", tod);
        map.put("originalEnergy", originalEnergy);
        map.put("newEnergy", newEnergy);
        map.put("costPerKwh", costPerKwh);
        map.put("marketSource", marketSource);
        map.put("shouldBuyFromMarket", shouldBuyFromMarket);
        map.put("marketEnergy", marketEnergy);
        map.put("discomEnergy", discomEnergy);
        map.put("damMcp", damMcp);
        map.put("rtmMcp", rtmMcp);
        map.put("gdamMcp", gdamMcp);
        return map;
    }

    /**
     * Safely constructs a slot item from a loose map.
     */
    public static ShiftedSlotItem fromMap(Map<?, ?> map) {
        if (map == null) {
            return new ShiftedSlotItem("", 1, "", 0.0, 0.0, 0.0, "DISCOM", false, 0.0, 0.0, null, null, null);
        }
        return new ShiftedSlotItem(
                map.get("date") != null ? String.valueOf(map.get("date")) : "",
                map.get("timeblock") instanceof Number n ? n.intValue() : 1,
                map.get("tod") != null ? String.valueOf(map.get("tod")) : "",
                toDouble(map.get("originalEnergy")),
                toDouble(map.get("newEnergy")),
                toDouble(map.get("costPerKwh")),
                map.get("marketSource") != null ? String.valueOf(map.get("marketSource")) : "DISCOM",
                Boolean.TRUE.equals(map.get("shouldBuyFromMarket")),
                toDouble(map.get("marketEnergy")),
                toDouble(map.get("discomEnergy")),
                toDoubleOrNull(map.get("damMcp")),
                toDoubleOrNull(map.get("rtmMcp")),
                toDoubleOrNull(map.get("gdamMcp"))
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

    private static Double toDoubleOrNull(Object val) {
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val != null) {
            try {
                return Double.parseDouble(val.toString().trim());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}

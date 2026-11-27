package com.sense.prolt_operations.service.calculator.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Standardized fallback total consumption classification keys used when resolving
 * monthly utility billing data across unstructured Time-of-Day (ToD) client consumption maps.
 *
 * <p>Identifies whether client billing data provides an aggregated flat/total consumption figure
 * (e.g., bills lacking ToD interval breakdown):
 * <ul>
 *   <li>{@code FLAT} — Single flat tariff consumption across all billing slots</li>
 *   <li>{@code TOTAL} — Cumulative total monthly consumption across all slabs</li>
 * </ul>
 */
public enum ConsumptionType {

    FLAT("FLAT"),
    TOTAL("TOTAL");

    private final String value;

    ConsumptionType(String value) {
        this.value = value;
    }

    /**
     * Returns the uppercase string key matching client consumption dictionary entries.
     *
     * @return String value ("FLAT" or "TOTAL")
     */
    public String getValue() {
        return value;
    }

    /**
     * Safely converts a candidate dictionary key to the corresponding {@link ConsumptionType}.
     *
     * @param value candidate key string
     * @return Optional containing the resolved enum, or empty if null or not matching
     */
    public static Optional<ConsumptionType> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(trimmed))
                .findFirst();
    }

    /**
     * Tests whether a given consumption dictionary key represents a flat or total fallback summary key.
     *
     * @param key candidate key string from monthly consumption map
     * @return true if key equals "FLAT" or "TOTAL" (case-insensitive), false otherwise
     */
    public static boolean isFallbackTotalKey(String key) {
        return fromValue(key).isPresent();
    }

    @Override
    public String toString() {
        return value;
    }
}

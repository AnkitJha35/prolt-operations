package com.sense.prolt_operations.service.calculator.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Standardized power exchange energy market trading products regulated by CERC
 * and supported by Indian power exchanges (IEX, PXIL, HPX).
 *
 * <p>Strictly closed vocabulary matching the Node.js legacy source of truth:
 * <ul>
 *   <li>{@code DAM} — Day-Ahead Market</li>
 *   <li>{@code RTM} — Real-Time Market</li>
 *   <li>{@code GDAM} — Green Day-Ahead Market</li>
 * </ul>
 */
public enum EnergyMarketType {

    DAM("DAM"),
    RTM("RTM"),
    GDAM("GDAM");

    private final String value;

    EnergyMarketType(String value) {
        this.value = value;
    }

    /**
     * Returns the exact uppercase string literal expected by API responses and external contracts.
     *
     * @return Market type string value ("DAM", "RTM", or "GDAM")
     */
    public String getValue() {
        return value;
    }

    /**
     * Safely converts an external string representation to the corresponding {@link EnergyMarketType}.
     * Handles case insensitivity and whitespace trimming without throwing exceptions or silently
     * defaulting to an arbitrary market.
     *
     * @param value external string representation
     * @return Optional containing the resolved enum, or empty if null or unknown
     */
    public static Optional<EnergyMarketType> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(type -> type.value.equalsIgnoreCase(trimmed))
                .findFirst();
    }

    /**
     * Validates whether an external string is a valid energy market type.
     *
     * @param value external string to test
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String value) {
        return fromValue(value).isPresent();
    }

    @Override
    public String toString() {
        return value;
    }
}

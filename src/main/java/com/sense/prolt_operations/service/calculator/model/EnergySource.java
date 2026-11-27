package com.sense.prolt_operations.service.calculator.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Standardized sourcing options available for dispatching electricity during any 15-minute timeblock.
 *
 * <p>Represents the closed vocabulary of dispatch sources:
 * <ul>
 *   <li>{@code DAM} — Sourced from Day-Ahead Market</li>
 *   <li>{@code RTM} — Sourced from Real-Time Market</li>
 *   <li>{@code GDAM} — Sourced from Green Day-Ahead Market</li>
 *   <li>{@code DISCOM} — Retained on State Distribution Licensee supply</li>
 * </ul>
 */
public enum EnergySource {

    DAM("DAM"),
    RTM("RTM"),
    GDAM("GDAM"),
    DISCOM("DISCOM");

    private final String value;

    EnergySource(String value) {
        this.value = value;
    }

    /**
     * Returns the exact uppercase string literal expected by API responses and external contracts.
     *
     * @return Source string value ("DAM", "RTM", "GDAM", or "DISCOM")
     */
    public String getValue() {
        return value;
    }

    /**
     * Safely converts an external string representation to the corresponding {@link EnergySource}.
     * Handles case insensitivity and whitespace trimming without throwing exceptions or silently
     * defaulting to an arbitrary source.
     *
     * @param value external string representation
     * @return Optional containing the resolved enum, or empty if null or unknown
     */
    public static Optional<EnergySource> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return Arrays.stream(values())
                .filter(source -> source.value.equalsIgnoreCase(trimmed))
                .findFirst();
    }

    /**
     * Checks if a given string represents a valid energy source.
     *
     * @param value external string to test
     * @return true if valid energy source, false otherwise
     */
    public static boolean isValid(String value) {
        return fromValue(value).isPresent();
    }

    /**
     * Converts a traded {@link EnergyMarketType} into its corresponding {@link EnergySource}.
     *
     * @param marketType the energy market type
     * @return corresponding EnergySource, or null if marketType is null
     */
    public static EnergySource fromMarketType(EnergyMarketType marketType) {
        if (marketType == null) {
            return null;
        }
        return switch (marketType) {
            case DAM -> DAM;
            case RTM -> RTM;
            case GDAM -> GDAM;
        };
    }

    /**
     * Converts this source into the corresponding {@link EnergyMarketType} if it represents
     * an open access exchange product.
     *
     * @return Optional containing the market type, or empty if this source is DISCOM
     */
    public Optional<EnergyMarketType> toMarketType() {
        return switch (this) {
            case DAM -> Optional.of(EnergyMarketType.DAM);
            case RTM -> Optional.of(EnergyMarketType.RTM);
            case GDAM -> Optional.of(EnergyMarketType.GDAM);
            case DISCOM -> Optional.empty();
        };
    }

    /**
     * Checks if this source represents a power exchange market purchase.
     *
     * @return true for DAM, RTM, and GDAM; false for DISCOM
     */
    public boolean isMarket() {
        return this != DISCOM;
    }

    /**
     * Checks if this source represents the state distribution utility.
     *
     * @return true for DISCOM; false for exchange markets
     */
    public boolean isDiscom() {
        return this == DISCOM;
    }

    @Override
    public String toString() {
        return value;
    }
}

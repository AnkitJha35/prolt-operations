package com.sense.prolt_operations.service.calculator.model;

/**
 * Immutable record representing the resolved DISCOM tariff slab name and
 * effective energy rate (INR/kWh) for a specific timeblock or hour.
 *
 * @param todName the formatted ToD name (e.g. "17:00-22:00", "FLAT", "TOD1", "normal")
 * @param rate    the effective energy rate in INR/kWh (power-factor adjusted if applicable)
 */
public record MatchedTariffRate(
        String todName,
        double rate
) {
}

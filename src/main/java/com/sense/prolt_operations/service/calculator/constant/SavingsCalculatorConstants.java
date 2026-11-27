package com.sense.prolt_operations.service.calculator.constant;

/**
 * Heuristic parameters, tolerance limits, and simulation defaults for the Saving Calculator engine.
 */
public final class SavingsCalculatorConstants {

    private SavingsCalculatorConstants() {
        // Prevent instantiation of static constants utility class
    }

    /**
     * Minimum price differential stopping threshold (Rs. 0.01 / 1 paisa per kWh) for demand shift reallocation.
     * When the savings spread between peak and off-peak slots drops below this margin, iterative shifting stops.
     */
    public static final double DEMAND_SHIFT_MIN_SPREAD_RS = 0.01;

    /**
     * Floating-point comparison epsilon to prevent underflow and rounding jitter in threshold checks.
     */
    public static final double EPSILON = 1e-9;

    /**
     * Default monthly energy consumption benchmark (1,250,000 kWh / 1.25 MWh) used for prospect quick calculations.
     */
    public static final double DEFAULT_PROSPECT_CONSUMPTION_KWH = 1250000.0;
}

package com.sense.prolt_operations.service.calculator.constant;

/**
 * Standard physical and electrical engineering conversion factors used throughout
 * the saving calculator and power scheduling modules.
 */
public final class UnitConstants {

    private UnitConstants() {
        // Prevent instantiation of static constants utility class
    }

    /**
     * Kilowatts to Megawatts conversion factor (1 MW = 1000 kW).
     */
    public static final double KW_PER_MW = 1000.0;

    /**
     * Kilowatt-hours to Megawatt-hours conversion factor (1 MWh = 1000 kWh).
     */
    public static final double KWH_PER_MWH = 1000.0;

    /**
     * Duration of a standard Indian grid timeblock represented in hours (15 min = 0.25 hour).
     */
    public static final double HOURS_PER_15MIN_SLOT = 0.25;

    /**
     * Energy quantum generated/consumed by 1 MW sustained over a 15-minute slot:
     * 1 MW * 1000 kW/MW * 0.25 hours = 250 kWh (or 0.25 MWh).
     */
    public static final double KWH_PER_MW_SLOT = 250.0;
}

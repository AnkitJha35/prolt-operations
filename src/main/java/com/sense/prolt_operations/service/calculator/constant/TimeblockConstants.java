package com.sense.prolt_operations.service.calculator.constant;

/**
 * Timeblock and scheduling interval constants per Indian Grid Code (CERC/POSOCO/GRID-INDIA).
 */
public final class TimeblockConstants {

    private TimeblockConstants() {
        // Prevent instantiation of static constants utility class
    }

    /**
     * Total number of 15-minute operational dispatch timeblocks in a 24-hour calendar day (24 * 4 = 96).
     */
    public static final int SLOTS_PER_DAY = 96;

    /**
     * Duration of each standardized power market dispatch interval in minutes.
     */
    public static final int MINUTES_PER_SLOT = 15;

    /**
     * Total hours in a standard calendar dispatch day.
     */
    public static final int HOURS_PER_DAY = 24;
}

package com.sense.prolt_operations.service.calculator.constant;

/**
 * Standard benchmark regulatory fallback tariffs and loss defaults applied when historical
 * database lookup records are absent for a specific simulation billing window.
 */
public final class RegulatoryFallbackConstants {

    private RegulatoryFallbackConstants() {
        // Prevent instantiation of static constants utility class
    }

    /**
     * Default Central Transmission Utility (CTU / ISTS) charges fallback (Rs. 0.496 per kWh).
     */
    public static final double DEFAULT_CTU_PER_KWH = 0.496;

    /**
     * Default State Transmission Utility (STU) transmission charges fallback (Rs. 0.384 per kWh).
     */
    public static final double DEFAULT_STU_PER_KWH = 0.384;

    /**
     * Default DISCOM wheeling charge fallback (Rs. 0.216 per kWh).
     */
    public static final double DEFAULT_WHEELING_PER_KWH = 0.216;

    /**
     * Default Cross Subsidy Surcharge (CSS) fallback (Rs. 0.68 per kWh).
     */
    public static final double DEFAULT_CROSS_SUBSIDY_PER_KWH = 0.68;

    /**
     * Default Additional Surcharge fallback (Rs. 0.216 per kWh).
     */
    public static final double DEFAULT_ADDITIONAL_SURCHARGE_PER_KWH = 0.216;

    /**
     * Default State Transmission Utility (STU) technical loss percentage fallback (3.5%).
     */
    public static final double DEFAULT_STU_LOSS_PERCENT = 3.5;

    /**
     * Default DISCOM wheeling technical loss percentage fallback (4.0%).
     */
    public static final double DEFAULT_WHEELING_LOSS_PERCENT = 4.0;

    /**
     * Default contract demand fixed charge rate fallback (Rs. 330.0 per kVA per month).
     */
    public static final double DEFAULT_DEMAND_FIXED_CHARGE_KVA = 330.0;

    /**
     * Default baseline industrial DISCOM tariff rate fallback (Rs. 7.5 per kWh).
     */
    public static final double DEFAULT_BASE_DISCOM_RATE = 7.5;
}

package com.sense.prolt_operations.service.calculator.constant;

/**
 * Power exchange operational fees, trading charges, and settlement surcharges.
 */
public final class ExchangeFeeConstants {

    private ExchangeFeeConstants() {
        // Prevent instantiation of static constants utility class
    }

    /**
     * Standard power exchange trading/transaction fee (Rs. 0.02 per kWh) charged by IEX, PXIL, and HPX.
     */
    public static final double DEFAULT_EXCHANGE_FEE_PER_KWH = 0.02;

    /**
     * Fallback for miscellaneous operational clearing, banking, and exchange charges (Rs. 0.10 per kWh).
     */
    public static final double DEFAULT_OTHER_CHARGES_PER_KWH = 0.10;

    /**
     * Renewable Purchase Obligation (RPO) flat replacement/compliance charge rate (Rs. 0.25 per kWh).
     */
    public static final double DEFAULT_RPO_FLAT_RATE_PER_KWH = 0.25;
}

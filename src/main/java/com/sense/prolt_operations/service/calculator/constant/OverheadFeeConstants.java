package com.sense.prolt_operations.service.calculator.constant;

/**
 * Administrative, dispatch centre, and management overhead fees associated with Open Access transactions.
 */
public final class OverheadFeeConstants {

    private OverheadFeeConstants() {
        // Prevent instantiation of static constants utility class
    }

    /**
     * Monthly DISCOM No-Objection Certificate (NOC) application and standing clearance processing fee (Rs. 7,000 / month).
     */
    public static final double DEFAULT_DISCOM_NOC_FEE_MONTHLY = 7000.0;

    /**
     * Annualized monthly State Load Despatch Centre (SLDC) operational registration charge
     * (Rs. 100,000 per year / 12 = Rs. 8,333.33 / month, rounded to Rs. 8,333.0).
     */
    public static final double DEFAULT_SLDC_MONTHLY_FEE = 8333.0;

    /**
     * Default monthly advisory retainer and power portfolio consultancy fee (Rs. 20,000 / month).
     */
    public static final double DEFAULT_CONSULTANCY_FEE_MONTHLY = 20000.0;

    /**
     * Default Probus platform success fee rate per scheduled Open Access kWh (Rs. 0.02 / kWh).
     */
    public static final double DEFAULT_PROBUS_PLATFORM_FEE_RATE = 0.02;

    /**
     * National Load Despatch Centre (NLDC) application processing fee per bid submission (Rs. 5.0 / bid).
     */
    public static final double NLDC_APPLICATION_FEE_PER_BID = 5.0;

    /**
     * Default NLDC daily scheduling fee rate per traded date (Rs. 2,500 / day).
     */
    public static final double DEFAULT_NLDC_DAILY_SCHEDULING_FEE = 2500.0;

    /**
     * Default SLDC daily scheduling fee rate per traded market product date (Rs. 1,500 / day).
     */
    public static final double DEFAULT_SLDC_DAILY_SCHEDULING_FEE = 1500.0;
}

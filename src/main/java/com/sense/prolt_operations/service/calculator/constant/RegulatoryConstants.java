package com.sense.prolt_operations.service.calculator.constant;

/**
 * Statutory regulatory thresholds and technical grid rules defined by the
 * Electricity Act 2003, Central Electricity Regulatory Commission (CERC),
 * and State Electricity Regulatory Commissions (SERCs).
 */
public final class RegulatoryConstants {

    private RegulatoryConstants() {
        // Prevent instantiation of static constants utility class
    }

    /**
     * Statutory minimum sanctioned load / contract demand required for Open Access eligibility
     * (1 MW / 1000 kW) per Section 42 of the Electricity Act 2003.
     */
    public static final double MIN_OA_ELIGIBILITY_KW = 1000.0;

    /**
     * Minimum Open Access scheduling and bidding increment in Megawatts (0.1 MW / 100 kW)
     * mandated for collective transactions on power exchanges (IEX, PXIL, HPX).
     */
    public static final double MIN_OA_SCHEDULE_QUANTUM_MW = 0.1;

    /**
     * Standard billing conversion factor from contract demand (kVA) to sanctioned capacity (MW)
     * under statutory power factor standards: MW = (kVA * 0.9) / 1000.0.
     */
    public static final double CONTRACT_DEMAND_PF_FACTOR = 0.9;

    /**
     * Default consumer operating power factor when unspecified, unmetered, or non-positive.
     */
    public static final double DEFAULT_POWER_FACTOR = 0.99;

    /**
     * Numerical safety cap on technical transmission loss percentages (99.9%) to prevent
     * division by zero or negative loss multipliers during extreme edge-case simulation inputs.
     */
    public static final double MAX_TRANSMISSION_LOSS_CAP_PERCENT = 99.9;

    /**
     * Statutory prompt payment and special industrial consumer rebate discount factor for NPCL
     * (10% prompt payment discount * 1% load factor rebate = 0.90 * 0.99 = 0.891).
     */
    public static final double NPCL_REBATE_DISCOUNT_FACTOR = 0.90 * 0.99;
}

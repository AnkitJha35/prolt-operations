package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.service.calculator.constant.RegulatoryConstants;
import org.springframework.stereotype.Component;

/**
 * Computes compound technical electrical transmission losses across Inter-State (ISTS),
 * Intra-State (STU), and distribution (Wheeling) grids.
 *
 * <p>Standard Indian Grid Loss Compounding Equation:
 * <pre>
 *   LossCoefficient = (1 - L_ists/100) * (1 - L_stu/100) * (1 - L_wheeling/100)
 * </pre>
 */
@Component
public class ElectricalLossCalculator {

    /**
     * Calculates the net compound transmission loss coefficient across ISTS, STU, and Wheeling.
     * Each individual loss percentage is capped at {@link RegulatoryConstants#MAX_TRANSMISSION_LOSS_CAP_PERCENT} (99.9%)
     * to prevent negative multipliers or division-by-zero errors.
     *
     * @param istsLossPercent Inter-State transmission loss percentage (e.g. 3.4%)
     * @param stuLossPercent State transmission loss percentage (e.g. 3.5%)
     * @param wheelingLossPercent Distribution network wheeling loss percentage (e.g. 4.0%)
     * @return Compound loss coefficient between 0.000001 and 1.0
     */
    public double calculateLossCoefficient(double istsLossPercent, double stuLossPercent, double wheelingLossPercent) {
        double safeIsts = Math.min(Math.max(istsLossPercent, 0.0), RegulatoryConstants.MAX_TRANSMISSION_LOSS_CAP_PERCENT);
        double safeStu = Math.min(Math.max(stuLossPercent, 0.0), RegulatoryConstants.MAX_TRANSMISSION_LOSS_CAP_PERCENT);
        double safeWheeling = Math.min(Math.max(wheelingLossPercent, 0.0), RegulatoryConstants.MAX_TRANSMISSION_LOSS_CAP_PERCENT);

        return (1.0 - (safeIsts / 100.0)) * (1.0 - (safeStu / 100.0)) * (1.0 - (safeWheeling / 100.0));
    }
}

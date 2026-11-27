package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Calculates landed open access electricity prices by combining market clearing prices (MCP),
 * non-energy statutory and network charges, and technical transmission losses.
 */
@Component
public class LandedCostCalculator {

    private final ElectricalLossCalculator lossCalculator;
    private final CalculatorProperties properties;

    public LandedCostCalculator() {
        this(new ElectricalLossCalculator(), new CalculatorProperties());
    }

    public LandedCostCalculator(ElectricalLossCalculator lossCalculator) {
        this(lossCalculator, new CalculatorProperties());
    }

    @Autowired
    public LandedCostCalculator(ElectricalLossCalculator lossCalculator, CalculatorProperties properties) {
        this.lossCalculator = lossCalculator != null ? lossCalculator : new ElectricalLossCalculator();
        this.properties = properties != null ? properties : new CalculatorProperties();
    }

    /**
     * Calculates the landed exchange price for a given slot MCP (in Rs./kWh), regulatory charges,
     * and transmission loss coefficient.
     *
     * <pre>
     *   LandedPrice = (MCP + TotalRegulatoryCharges) / LossCoefficient
     * </pre>
     *
     * @param mcpRsPerKwh Market clearing price converted to Rs./kWh (or null if unavailable)
     * @param totalRegulatoryCharges Sum of CTU, STU, Wheeling, CSS, AS, Exchange Fees, Trader Margin, and Taxes
     * @param lossCoefficient Compound transmission loss coefficient
     * @return Landed price in Rs./kWh, or null if MCP is null
     */
    public Double calculateExchangeLanding(Double mcpRsPerKwh, double totalRegulatoryCharges, double lossCoefficient) {
        if (mcpRsPerKwh == null) {
            return null;
        }
        double safeCoeff = lossCoefficient > 0 ? lossCoefficient : 1.0;
        return (mcpRsPerKwh + totalRegulatoryCharges) / safeCoeff;
    }

    /**
     * Overload calculating the loss coefficient internally from component loss percentages.
     */
    public Double calculateExchangeLanding(Double mcpRsPerKwh, double totalRegulatoryCharges,
                                           double istsLossPercent, double stuLossPercent, double wheelingLossPercent) {
        if (mcpRsPerKwh == null) {
            return null;
        }
        double lossCoeff = lossCalculator.calculateLossCoefficient(istsLossPercent, stuLossPercent, wheelingLossPercent);
        return calculateExchangeLanding(mcpRsPerKwh, totalRegulatoryCharges, lossCoeff);
    }

    /**
     * Sums all regulatory Open Access adders, taxes, and operational charges.
     */
    public double calculateRegulatoryChargesSum(
            double ctuCharge,
            double stuCharge,
            double wheelingCharge,
            double otherCharges,
            double exchangeFees,
            double gstExchange,
            double traderMargin,
            double gstTraderMargin,
            double crossSubsidy,
            double additionalSurcharge) {
        return ctuCharge + stuCharge + wheelingCharge + otherCharges + exchangeFees + gstExchange
                + traderMargin + gstTraderMargin + crossSubsidy + additionalSurcharge;
    }

    /**
     * Applies standard NPCL prompt payment and load-factor rebate discount factor (0.90 * 0.99)
     * to a baseline DISCOM landed tariff.
     *
     * @param discomRate Gross DISCOM energy tariff in Rs./kWh
     * @return Net discounted tariff in Rs./kWh
     */
    public double applyNpclDiscount(double discomRate) {
        double discountFactor = properties != null && properties.getRegulatory() != null
                ? properties.getRegulatory().getNpclRebateDiscountFactor()
                : RegulatoryConstants.NPCL_REBATE_DISCOUNT_FACTOR;
        return discomRate * discountFactor;
    }
}

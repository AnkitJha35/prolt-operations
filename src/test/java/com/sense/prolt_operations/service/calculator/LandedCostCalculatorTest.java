package com.sense.prolt_operations.service.calculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LandedCostCalculatorTest {

    private LandedCostCalculator landedCostCalculator;

    @BeforeEach
    void setUp() {
        ElectricalLossCalculator lossCalculator = new ElectricalLossCalculator();
        landedCostCalculator = new LandedCostCalculator(lossCalculator);
    }

    @Test
    @DisplayName("Null MCP yields null landed price")
    void testNullMcpYieldsNull() {
        Double landed = landedCostCalculator.calculateExchangeLanding(null, 1.5, 0.95);
        assertThat(landed).isNull();
    }

    @Test
    @DisplayName("Landed price calculation with zero loss")
    void testLandedPriceZeroLoss() {
        double mcp = 3.50; // Rs. 3.50/kWh
        double regCharges = 1.25;
        double lossCoeff = 1.0;

        Double landed = landedCostCalculator.calculateExchangeLanding(mcp, regCharges, lossCoeff);
        assertThat(landed).isNotNull();
        assertThat(landed).isCloseTo(4.75, within(1e-9));
    }

    @Test
    @DisplayName("Landed price calculation with compound losses")
    void testLandedPriceWithCompoundLoss() {
        double mcp = 4.00;
        double regCharges = 2.00;
        double lossCoeff = 0.90; // 10% loss

        Double landed = landedCostCalculator.calculateExchangeLanding(mcp, regCharges, lossCoeff);
        assertThat(landed).isNotNull();
        // (4.00 + 2.00) / 0.90 = 6.666666667
        assertThat(landed).isCloseTo(6.666666666666667, within(1e-9));
    }

    @Test
    @DisplayName("Regulatory charges summation")
    void testRegulatoryChargesSum() {
        double ctu = 0.496;
        double stu = 0.384;
        double wheeling = 0.216;
        double other = 0.10;
        double iexFee = 0.02;
        double gstIex = 0.0036;
        double traderMargin = 0.02;
        double gstMargin = 0.0036;
        double css = 0.68;
        double as = 0.216;

        double expected = ctu + stu + wheeling + other + iexFee + gstIex + traderMargin + gstMargin + css + as;
        double actual = landedCostCalculator.calculateRegulatoryChargesSum(
                ctu, stu, wheeling, other, iexFee, gstIex, traderMargin, gstMargin, css, as
        );
        assertThat(actual).isCloseTo(expected, within(1e-9));
    }

    @Test
    @DisplayName("NPCL prompt rebate and load factor discount factor (0.90 * 0.99)")
    void testNpclDiscount() {
        double rawTariff = 10.0;
        double expected = 10.0 * 0.90 * 0.99; // 8.91
        double discounted = landedCostCalculator.applyNpclDiscount(rawTariff);
        assertThat(discounted).isCloseTo(expected, within(1e-9));
    }
}

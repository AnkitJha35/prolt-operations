package com.sense.prolt_operations.service.calculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ElectricalLossCalculatorTest {

    private ElectricalLossCalculator lossCalculator;

    @BeforeEach
    void setUp() {
        lossCalculator = new ElectricalLossCalculator();
    }

    @Test
    @DisplayName("Zero losses yield coefficient of 1.0")
    void testZeroLosses() {
        double coeff = lossCalculator.calculateLossCoefficient(0.0, 0.0, 0.0);
        assertThat(coeff).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("ISTS loss only")
    void testIstsLossOnly() {
        // ISTS 3.5% -> coeff = 1 - 0.035 = 0.965
        double coeff = lossCalculator.calculateLossCoefficient(3.5, 0.0, 0.0);
        assertThat(coeff).isCloseTo(0.965, within(1e-9));
    }

    @Test
    @DisplayName("STU loss only")
    void testStuLossOnly() {
        // STU 4.0% -> coeff = 1 - 0.04 = 0.96
        double coeff = lossCalculator.calculateLossCoefficient(0.0, 4.0, 0.0);
        assertThat(coeff).isCloseTo(0.96, within(1e-9));
    }

    @Test
    @DisplayName("Wheeling loss only")
    void testWheelingLossOnly() {
        // Wheeling 5.0% -> coeff = 1 - 0.05 = 0.95
        double coeff = lossCalculator.calculateLossCoefficient(0.0, 0.0, 5.0);
        assertThat(coeff).isCloseTo(0.95, within(1e-9));
    }

    @Test
    @DisplayName("Combined compound loss calculation")
    void testCombinedLosses() {
        // ISTS 3.4%, STU 3.5%, Wheeling 4.0%
        // (1 - 0.034) * (1 - 0.035) * (1 - 0.04) = 0.966 * 0.965 * 0.96 = 0.8949024
        double expected = (1.0 - 0.034) * (1.0 - 0.035) * (1.0 - 0.04);
        double coeff = lossCalculator.calculateLossCoefficient(3.4, 3.5, 4.0);
        assertThat(coeff).isCloseTo(expected, within(1e-9));
    }

    @Test
    @DisplayName("Loss percentage capping at 99.9%")
    void testLossCapping() {
        // If an extreme input like 105% is passed, it caps at 99.9%
        double coeff = lossCalculator.calculateLossCoefficient(150.0, 0.0, 0.0);
        double expected = 1.0 - (99.9 / 100.0); // 0.001
        assertThat(coeff).isCloseTo(expected, within(1e-9));
    }

    @Test
    @DisplayName("Negative loss values clamped to zero")
    void testNegativeLossClamped() {
        double coeff = lossCalculator.calculateLossCoefficient(-5.0, -2.0, 0.0);
        assertThat(coeff).isCloseTo(1.0, within(1e-9));
    }
}

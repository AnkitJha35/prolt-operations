package com.sense.prolt_operations.service.calculator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NpclTariffScheduleTest {

    private NpclTariffSchedule schedule;

    @BeforeEach
    void setUp() {
        schedule = new NpclTariffSchedule();
    }

    @Test
    @DisplayName("Winter season boundary months (Sept through March)")
    void testWinterSeasonBoundaries() {
        // Winter: 9, 10, 11, 12, 1, 2, 3
        assertThat(schedule.isWinter(9)).isTrue();  // September
        assertThat(schedule.isWinter(10)).isTrue(); // October
        assertThat(schedule.isWinter(11)).isTrue(); // November
        assertThat(schedule.isWinter(12)).isTrue(); // December
        assertThat(schedule.isWinter(1)).isTrue();  // January
        assertThat(schedule.isWinter(2)).isTrue();  // February
        assertThat(schedule.isWinter(3)).isTrue();  // March
    }

    @Test
    @DisplayName("Summer season boundary months (April through August)")
    void testSummerSeasonBoundaries() {
        // Summer: 4, 5, 6, 7, 8
        assertThat(schedule.isWinter(4)).isFalse(); // April
        assertThat(schedule.isWinter(5)).isFalse(); // May
        assertThat(schedule.isWinter(6)).isFalse(); // June
        assertThat(schedule.isWinter(7)).isFalse(); // July
        assertThat(schedule.isWinter(8)).isFalse(); // August
    }

    @ParameterizedTest(name = "Winter hour {0} -> {1} (mult: {2})")
    @CsvSource({
            "22, TOD1, 0.85",
            "23, TOD1, 0.85",
            "0,  TOD1, 0.85",
            "3,  TOD1, 0.85",
            "4,  TOD2, 1.00",
            "5,  TOD2, 1.00",
            "6,  TOD3, 1.15",
            "9,  TOD3, 1.15",
            "10, TOD4, 1.00",
            "16, TOD4, 1.00",
            "17, TOD6, 1.15",
            "18, TOD6, 1.15",
            "19, TOD5, 1.00",
            "21, TOD5, 1.00"
    })
    @DisplayName("Winter ToD slabs and multipliers across 24 hours")
    void testWinterTodSlabs(int hour, String expectedName, double expectedMult) {
        NpclTariffSchedule.NpclTodRate rate = schedule.resolve(1, hour); // January (Winter)
        assertThat(rate.todName()).isEqualTo(expectedName);
        assertThat(rate.multiplier()).isCloseTo(expectedMult, within(1e-6));
        assertThat(rate.rate()).isCloseTo(6.80 * expectedMult, within(1e-6));
    }

    @ParameterizedTest(name = "Summer hour {0} -> {1} (mult: {2})")
    @CsvSource({
            "7,  TOD4, 0.85",
            "12, TOD4, 0.85",
            "15, TOD4, 0.85",
            "16, TOD1, 1.00",
            "18, TOD1, 1.00",
            "19, TOD2, 1.15",
            "23, TOD2, 1.15",
            "0,  TOD2, 1.15",
            "1,  TOD2, 1.15",
            "2,  TOD3, 1.00",
            "6,  TOD3, 1.00"
    })
    @DisplayName("Summer ToD slabs and multipliers across 24 hours")
    void testSummerTodSlabs(int hour, String expectedName, double expectedMult) {
        NpclTariffSchedule.NpclTodRate rate = schedule.resolve(6, hour); // June (Summer)
        assertThat(rate.todName()).isEqualTo(expectedName);
        assertThat(rate.multiplier()).isCloseTo(expectedMult, within(1e-6));
        assertThat(rate.rate()).isCloseTo(6.80 * expectedMult, within(1e-6));
    }

    @Test
    @DisplayName("All 96 daily slots produce valid non-null ToD rates")
    void testAll96SlotsCoverage() {
        for (int month = 1; month <= 12; month++) {
            for (int slot = 1; slot <= 96; slot++) {
                int hour = ((slot - 1) * 15) / 60;
                NpclTariffSchedule.NpclTodRate rate = schedule.resolve(month, hour);
                assertThat(rate).isNotNull();
                assertThat(rate.todName()).isNotBlank();
                assertThat(rate.rate()).isPositive();
                assertThat(rate.multiplier()).isIn(0.85, 1.00, 1.15);
            }
        }
    }

    @Test
    @DisplayName("Custom base rate calculation")
    void testCustomBaseRate() {
        double customBase = 10.0;
        NpclTariffSchedule.NpclTodRate winterRebate = schedule.resolve(12, 23, customBase);
        assertThat(winterRebate.todName()).isEqualTo("TOD1");
        assertThat(winterRebate.rate()).isCloseTo(8.50, within(1e-6));

        NpclTariffSchedule.NpclTodRate winterSurcharge = schedule.resolve(12, 8, customBase);
        assertThat(winterSurcharge.todName()).isEqualTo("TOD3");
        assertThat(winterSurcharge.rate()).isCloseTo(11.50, within(1e-6));
    }
}

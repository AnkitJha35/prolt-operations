package com.sense.prolt_operations.service.calculator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("V18L.1 - ConsumptionType Enum Unit Tests")
class ConsumptionTypeTest {

    @Test
    @DisplayName("Verify canonical enum values and string representations")
    void testEnumValues() {
        ConsumptionType[] values = ConsumptionType.values();
        assertEquals(2, values.length);

        assertEquals("FLAT", ConsumptionType.FLAT.getValue());
        assertEquals("TOTAL", ConsumptionType.TOTAL.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FLAT", "flat", "  Flat  "})
    void testFromValueFlat(String input) {
        Optional<ConsumptionType> result = ConsumptionType.fromValue(input);
        assertTrue(result.isPresent());
        assertEquals(ConsumptionType.FLAT, result.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOTAL", "total", "  Total  "})
    void testFromValueTotal(String input) {
        Optional<ConsumptionType> result = ConsumptionType.fromValue(input);
        assertTrue(result.isPresent());
        assertEquals(ConsumptionType.TOTAL, result.get());
    }

    @Test
    @DisplayName("fromValue returns empty for null, blank, or unknown keys")
    void testInvalidFromValue() {
        assertFalse(ConsumptionType.fromValue(null).isPresent());
        assertFalse(ConsumptionType.fromValue("").isPresent());
        assertFalse(ConsumptionType.fromValue("   ").isPresent());
        assertFalse(ConsumptionType.fromValue("PEAK").isPresent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"FLAT", "flat", "Flat", "  flat  ", "TOTAL", "total", "Total", "  TOTAL  "})
    @DisplayName("isFallbackTotalKey returns true for FLAT and TOTAL variants")
    void testIsFallbackTotalKeyTrue(String input) {
        assertTrue(ConsumptionType.isFallbackTotalKey(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOD1", "PEAK", "OFFPEAK", "NORMAL", "22:00-06:00", "", "   "})
    @DisplayName("isFallbackTotalKey returns false for non-fallback keys")
    void testIsFallbackTotalKeyFalse(String input) {
        assertFalse(ConsumptionType.isFallbackTotalKey(input));
    }

    @Test
    @DisplayName("isFallbackTotalKey handles null gracefully")
    void testIsFallbackTotalKeyNull() {
        assertFalse(ConsumptionType.isFallbackTotalKey(null));
    }
}

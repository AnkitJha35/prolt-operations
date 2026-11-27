package com.sense.prolt_operations.service.calculator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("V18L.1 - EnergyMarketType Enum Unit Tests")
class EnergyMarketTypeTest {

    @Test
    @DisplayName("Verify canonical enum values and counts")
    void testEnumValues() {
        EnergyMarketType[] values = EnergyMarketType.values();
        assertEquals(3, values.length);
        assertEquals(EnergyMarketType.DAM, EnergyMarketType.valueOf("DAM"));
        assertEquals(EnergyMarketType.RTM, EnergyMarketType.valueOf("RTM"));
        assertEquals(EnergyMarketType.GDAM, EnergyMarketType.valueOf("GDAM"));

        assertEquals("DAM", EnergyMarketType.DAM.getValue());
        assertEquals("RTM", EnergyMarketType.RTM.getValue());
        assertEquals("GDAM", EnergyMarketType.GDAM.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DAM", "dam", "  DAM  ", "Dam"})
    @DisplayName("fromValue parses DAM variations case-insensitively with trimming")
    void testFromValueDam(String input) {
        Optional<EnergyMarketType> result = EnergyMarketType.fromValue(input);
        assertTrue(result.isPresent());
        assertEquals(EnergyMarketType.DAM, result.get());
        assertTrue(EnergyMarketType.isValid(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RTM", "rtm", "  Rtm  ", "RTM"})
    @DisplayName("fromValue parses RTM variations case-insensitively with trimming")
    void testFromValueRtm(String input) {
        Optional<EnergyMarketType> result = EnergyMarketType.fromValue(input);
        assertTrue(result.isPresent());
        assertEquals(EnergyMarketType.RTM, result.get());
        assertTrue(EnergyMarketType.isValid(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GDAM", "gdam", "  GDAM  ", "Gdam"})
    @DisplayName("fromValue parses GDAM variations case-insensitively with trimming")
    void testFromValueGdam(String input) {
        Optional<EnergyMarketType> result = EnergyMarketType.fromValue(input);
        assertTrue(result.isPresent());
        assertEquals(EnergyMarketType.GDAM, result.get());
        assertTrue(EnergyMarketType.isValid(input));
    }

    @Test
    @DisplayName("fromValue and isValid return empty/false for invalid, empty, or null values")
    void testInvalidValues() {
        assertFalse(EnergyMarketType.fromValue(null).isPresent());
        assertFalse(EnergyMarketType.fromValue("").isPresent());
        assertFalse(EnergyMarketType.fromValue("   ").isPresent());
        assertFalse(EnergyMarketType.fromValue("TAM").isPresent());
        assertFalse(EnergyMarketType.fromValue("DISCOM").isPresent());

        assertFalse(EnergyMarketType.isValid(null));
        assertFalse(EnergyMarketType.isValid(""));
        assertFalse(EnergyMarketType.isValid("   "));
        assertFalse(EnergyMarketType.isValid("TAM"));
        assertFalse(EnergyMarketType.isValid("DISCOM"));
    }
}

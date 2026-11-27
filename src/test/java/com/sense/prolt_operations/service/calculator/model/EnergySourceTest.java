package com.sense.prolt_operations.service.calculator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("V18L.1 - EnergySource Enum Unit Tests")
class EnergySourceTest {

    @Test
    @DisplayName("Verify canonical enum values, counts, and string representations")
    void testEnumValues() {
        EnergySource[] values = EnergySource.values();
        assertEquals(4, values.length);

        assertEquals("DAM", EnergySource.DAM.getValue());
        assertEquals("RTM", EnergySource.RTM.getValue());
        assertEquals("GDAM", EnergySource.GDAM.getValue());
        assertEquals("DISCOM", EnergySource.DISCOM.getValue());
    }

    @Test
    @DisplayName("Verify isMarket and isDiscom classification")
    void testSourceClassification() {
        assertTrue(EnergySource.DAM.isMarket());
        assertFalse(EnergySource.DAM.isDiscom());

        assertTrue(EnergySource.RTM.isMarket());
        assertFalse(EnergySource.RTM.isDiscom());

        assertTrue(EnergySource.GDAM.isMarket());
        assertFalse(EnergySource.GDAM.isDiscom());

        assertFalse(EnergySource.DISCOM.isMarket());
        assertTrue(EnergySource.DISCOM.isDiscom());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DAM", "dam", "  DAM  "})
    void testFromValueDam(String input) {
        Optional<EnergySource> result = EnergySource.fromValue(input);
        assertTrue(result.isPresent());
        assertEquals(EnergySource.DAM, result.get());
        assertTrue(EnergySource.isValid(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RTM", "rtm", "  rtm  "})
    void testFromValueRtm(String input) {
        Optional<EnergySource> result = EnergySource.fromValue(input);
        assertTrue(result.isPresent());
        assertEquals(EnergySource.RTM, result.get());
        assertTrue(EnergySource.isValid(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GDAM", "gdam", "  GDAM  "})
    void testFromValueGdam(String input) {
        Optional<EnergySource> result = EnergySource.fromValue(input);
        assertTrue(result.isPresent());
        assertEquals(EnergySource.GDAM, result.get());
        assertTrue(EnergySource.isValid(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISCOM", "discom", "  Discom  "})
    void testFromValueDiscom(String input) {
        Optional<EnergySource> result = EnergySource.fromValue(input);
        assertTrue(result.isPresent());
        assertEquals(EnergySource.DISCOM, result.get());
        assertTrue(EnergySource.isValid(input));
    }

    @Test
    @DisplayName("fromValue and isValid return empty/false for invalid, empty, or null values")
    void testInvalidValues() {
        assertFalse(EnergySource.fromValue(null).isPresent());
        assertFalse(EnergySource.fromValue("").isPresent());
        assertFalse(EnergySource.fromValue("   ").isPresent());
        assertFalse(EnergySource.fromValue("TAM").isPresent());
        assertFalse(EnergySource.fromValue("SOLAR").isPresent());

        assertFalse(EnergySource.isValid(null));
        assertFalse(EnergySource.isValid(""));
        assertFalse(EnergySource.isValid("   "));
        assertFalse(EnergySource.isValid("TAM"));
    }

    @Test
    @DisplayName("Convert between EnergyMarketType and EnergySource")
    void testMarketTypeConversions() {
        assertEquals(EnergySource.DAM, EnergySource.fromMarketType(EnergyMarketType.DAM));
        assertEquals(EnergySource.RTM, EnergySource.fromMarketType(EnergyMarketType.RTM));
        assertEquals(EnergySource.GDAM, EnergySource.fromMarketType(EnergyMarketType.GDAM));
        assertNull(EnergySource.fromMarketType(null));

        assertEquals(Optional.of(EnergyMarketType.DAM), EnergySource.DAM.toMarketType());
        assertEquals(Optional.of(EnergyMarketType.RTM), EnergySource.RTM.toMarketType());
        assertEquals(Optional.of(EnergyMarketType.GDAM), EnergySource.GDAM.toMarketType());
        assertEquals(Optional.empty(), EnergySource.DISCOM.toMarketType());
    }
}

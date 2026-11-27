package com.sense.prolt_operations.service.calculator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("V18L.1 - RegulatoryChargeType Enum Unit Tests")
class RegulatoryChargeTypeTest {

    @Test
    @DisplayName("Verify canonical charge types, codes, and descriptions")
    void testEnumValues() {
        RegulatoryChargeType[] values = RegulatoryChargeType.values();
        assertEquals(6, values.length);

        assertEquals("CTU", RegulatoryChargeType.CTU.getCode());
        assertEquals("Central Transmission Utility / ISTS Charges", RegulatoryChargeType.CTU.getDescription());

        assertEquals("STU", RegulatoryChargeType.STU.getCode());
        assertEquals("State Transmission Utility Charges", RegulatoryChargeType.STU.getDescription());

        assertEquals("WHEELING", RegulatoryChargeType.WHEELING.getCode());
        assertEquals("Distribution Wheeling Charges", RegulatoryChargeType.WHEELING.getDescription());

        assertEquals("CSS", RegulatoryChargeType.CSS.getCode());
        assertEquals("Cross Subsidy Surcharge", RegulatoryChargeType.CSS.getDescription());

        assertEquals("AS", RegulatoryChargeType.AS.getCode());
        assertEquals("Additional Surcharge", RegulatoryChargeType.AS.getDescription());

        assertEquals("BANKING", RegulatoryChargeType.BANKING.getCode());
        assertEquals("Forward Banking Charges", RegulatoryChargeType.BANKING.getDescription());
    }

    @ParameterizedTest
    @CsvSource({
            "CTU, CTU",
            "ctu, CTU",
            "ISTS, CTU",
            "ists, CTU",
            "  ists  , CTU",
            "STU, STU",
            "stu, STU",
            "WHEELING, WHEELING",
            "wheeling, WHEELING",
            "CSS, CSS",
            "css, CSS",
            "AS, AS",
            "as, AS",
            "BANKING, BANKING",
            "banking, BANKING"
    })
    @DisplayName("fromCode resolves standard codes and ISTS alias case-insensitively with trimming")
    void testFromCode(String input, RegulatoryChargeType expected) {
        Optional<RegulatoryChargeType> result = RegulatoryChargeType.fromCode(input);
        assertTrue(result.isPresent());
        assertEquals(expected, result.get());
        assertTrue(RegulatoryChargeType.isValid(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "UNKNOWN", "SLDC", "FPPCA"})
    @DisplayName("fromCode and isValid return empty/false for invalid, empty, or unknown codes")
    void testInvalidCodes(String input) {
        assertFalse(RegulatoryChargeType.fromCode(input).isPresent());
        assertFalse(RegulatoryChargeType.isValid(input));
    }

    @Test
    @DisplayName("fromCode and isValid handle null gracefully")
    void testNullCode() {
        assertFalse(RegulatoryChargeType.fromCode(null).isPresent());
        assertFalse(RegulatoryChargeType.isValid(null));
    }
}

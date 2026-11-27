package com.sense.prolt_operations.service.calculator.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Statutory regulatory open access charges levied by Central (CERC) and State (SERC)
 * electricity regulatory commissions under the Electricity Act 2003.
 *
 * <p>Represents the closed domain vocabulary of statutory open access tariff components:
 * <ul>
 *   <li>{@code CTU} — Central Transmission Utility / Inter-State Transmission System (ISTS) charges</li>
 *   <li>{@code STU} — State Transmission Utility intra-state transmission charges</li>
 *   <li>{@code WHEELING} — DISCOM distribution wheeling network charges</li>
 *   <li>{@code CSS} — Cross Subsidy Surcharge for open access consumers</li>
 *   <li>{@code AS} — Additional Surcharge for stranded DISCOM capacity recovery</li>
 *   <li>{@code BANKING} — Green energy forward banking withdrawal charges</li>
 * </ul>
 */
public enum RegulatoryChargeType {

    CTU("CTU", "Central Transmission Utility / ISTS Charges"),
    STU("STU", "State Transmission Utility Charges"),
    WHEELING("WHEELING", "Distribution Wheeling Charges"),
    CSS("CSS", "Cross Subsidy Surcharge"),
    AS("AS", "Additional Surcharge"),
    BANKING("BANKING", "Forward Banking Charges");

    private final String code;
    private final String description;

    RegulatoryChargeType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Returns the uppercase regulatory charge code.
     *
     * @return Charge code string (e.g., "CTU", "STU", "CSS")
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the human-readable statutory description of the charge.
     *
     * @return Charge description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Safely converts an external string representation to the corresponding {@link RegulatoryChargeType}.
     * Handles case insensitivity, whitespace trimming, and common alternate acronyms without throwing
     * exceptions or defaulting arbitrarily.
     *
     * @param code external charge code representation
     * @return Optional containing the resolved enum, or empty if null or unknown
     */
    public static Optional<RegulatoryChargeType> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String trimmed = code.trim().toUpperCase();
        // Support ISTS as an alias for CTU
        if ("ISTS".equals(trimmed)) {
            return Optional.of(CTU);
        }
        return Arrays.stream(values())
                .filter(charge -> charge.code.equalsIgnoreCase(trimmed))
                .findFirst();
    }

    /**
     * Validates whether an external string is a valid regulatory charge type code.
     *
     * @param code external string to test
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String code) {
        return fromCode(code).isPresent();
    }

    @Override
    public String toString() {
        return code;
    }
}

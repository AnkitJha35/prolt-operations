package com.sense.prolt_operations.dto.calculator;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotBlank;

/**
 * Strongly typed request DTO for independent landed price calculation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LandingPriceCalculationRequest(
    @NotBlank(message = "State is required")
    @JsonAlias({"stateCode"})
    String state,

    @NotBlank(message = "Consumer category is required")
    @JsonAlias({"category", "tariffCategory"})
    String consumerCategory,

    @NotBlank(message = "Voltage level is required")
    @JsonAlias({"voltage", "supplyVoltage"})
    String voltageLevel,

    String discom,

    @JsonAlias({"date"})
    LocalDate deliveryDate,

    @JsonAlias({"slot", "slotIndex"})
    Integer timeblock,

    @JsonAlias({"iexPrice", "mcp"})
    Double mcpRsPerKwh,

    @JsonAlias({"damMcp"})
    Double damMcpRsPerMwh,

    @JsonAlias({"rtmMcp"})
    Double rtmMcpRsPerMwh,

    @JsonAlias({"gdamMcp"})
    Double gdamMcpRsPerMwh,

    @JsonAlias({"sanctionedLoad", "loadKw"})
    Double sanctionedLoadKw,

    Double powerFactor,

    Boolean applyElectricityDuty,

    Double traderMarginRsPerKwh,

    CalculationMode calculationMode
) {
    public enum CalculationMode {
        OPEN_ACCESS_LANDED,
        BUSBAR_COMPARISON
    }

    /**
     * Compact constructor providing sane defaults.
     */
    public LandingPriceCalculationRequest {
        if (calculationMode == null) {
            calculationMode = CalculationMode.OPEN_ACCESS_LANDED;
        }
    }
}

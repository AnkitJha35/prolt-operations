package com.sense.prolt_operations.dto.calculator;

import java.math.BigDecimal;

public record LandedCostCalcRequest(
    String clientName,
    String state,
    String tariffCategory,
    BigDecimal contractDemandKva,
    String connectedVoltage,
    BigDecimal consumptionKwh,
    String monthYear,
    LandingPriceCalculationRequest.CalculationMode calculationMode
) {
    public LandedCostCalcRequest(
            String clientName,
            String state,
            String tariffCategory,
            BigDecimal contractDemandKva,
            String connectedVoltage,
            BigDecimal consumptionKwh,
            String monthYear) {
        this(clientName, state, tariffCategory, contractDemandKva, connectedVoltage, consumptionKwh, monthYear,
                LandingPriceCalculationRequest.CalculationMode.OPEN_ACCESS_LANDED);
    }
}

package com.sense.prolt_operations.dto.calculator;

import java.math.BigDecimal;

public record LandedCostCalcRequest(
    String clientName,
    String state,
    String tariffCategory,
    BigDecimal contractDemandKva,
    String connectedVoltage,
    BigDecimal consumptionKwh,
    String monthYear
) {}

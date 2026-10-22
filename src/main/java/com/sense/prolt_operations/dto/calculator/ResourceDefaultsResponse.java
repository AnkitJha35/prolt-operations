package com.sense.prolt_operations.dto.calculator;

import java.math.BigDecimal;

public record ResourceDefaultsResponse(
    BigDecimal fppaChargePercent,
    BigDecimal demandChargeKwRate,
    BigDecimal electricityDutyPercent
) {}

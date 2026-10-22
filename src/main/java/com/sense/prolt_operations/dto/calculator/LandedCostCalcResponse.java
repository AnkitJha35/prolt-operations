package com.sense.prolt_operations.dto.calculator;

import java.math.BigDecimal;

public record LandedCostCalcResponse(
    boolean success,
    LandedCostData data
) {
    public record LandedCostData(
        BigDecimal discomBillAmount,
        BigDecimal iexTotalCost,
        BigDecimal totalSavings,
        BigDecimal percentageSavings,
        CostBreakdown breakdown
    ) {}

    public record CostBreakdown(
        BigDecimal energyCost,
        BigDecimal ctuLossAndCharges,
        BigDecimal stLossAndCharges,
        BigDecimal crossSubsidySurcharge,
        BigDecimal additionalSurcharge
    ) {}
}

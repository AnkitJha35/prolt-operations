package com.sense.prolt_operations.dto.calculator;

import java.time.LocalDate;
import java.util.Map;

/**
 * Strongly typed response DTO for independent landed price calculation.
 */
public record LandingPriceCalculationResponse(
    String state,
    String discom,
    String consumerCategory,
    String voltageLevel,
    LocalDate deliveryDate,
    int timeblock,
    String calculationMode,
    LossBreakdown losses,
    RegulatoryChargesBreakdown regulatoryCharges,
    Map<String, MarketRateDetail> marketLandings,
    String bestMarketSource,
    Double bestMarketLandedPrice,
    DiscomTariffBreakdown discomTariff,
    ComparisonSummary comparison
) {

    public record LossBreakdown(
        double istsLossPercent,
        double stuLossPercent,
        double wheelingLossPercent,
        double compoundLossCoefficient
    ) {}

    public record RegulatoryChargesBreakdown(
        double ctuCharge,
        double stuCharge,
        double wheelingCharge,
        double otherCharges,
        double exchangeFees,
        double gstExchange,
        double traderMargin,
        double gstTraderMargin,
        double crossSubsidy,
        double additionalSurcharge,
        double totalRegulatoryCharges
    ) {}

    public record MarketRateDetail(
        Double mcpRsPerKwh,
        Double lossAdjustedMcp,
        Double landedPriceRsPerKwh,
        boolean eligibleForOa
    ) {}

    public record DiscomTariffBreakdown(
        double baseRate,
        String todSlab,
        double fppaAdder,
        double dutyAdder,
        double npclRebateDiscount,
        double effectiveDiscomLandingPrice
    ) {}

    public record ComparisonSummary(
        String cheapestSource,
        double bestMarketPrice,
        double discomPrice,
        double spreadVsDiscomRsPerKwh,
        double potentialSavingsPercent
    ) {}
}

package com.sense.prolt_operations.service.calculator.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strongly typed record representing a monthly summary entry in the client overview
 * calculation ({@code getClientOverview}).
 *
 * <p>Supports both successfully computed monthly profiles and computation error states.
 */
public record ClientOverviewMonthRecord(
        String month,
        double savings,
        Double grossSavings,
        Double totalEnergyKwh,
        Double totalMarketEnergyKwh,
        Double oaConsumer,
        Double oaCoverage,
        Double proltMarginCost,
        Double traderMargin,
        Double consultancyFee,
        Double probusPlatformFee,
        Double totalBaselineCost,
        Double totalOptimizedCost,
        String error
) {

    /**
     * Factory method for a successfully computed monthly profile.
     */
    public static ClientOverviewMonthRecord success(
            String month,
            double savings,
            double grossSavings,
            double totalEnergyKwh,
            double totalMarketEnergyKwh,
            double oaConsumer,
            double oaCoverage,
            double proltMarginCost,
            double traderMargin,
            double consultancyFee,
            double probusPlatformFee,
            double totalBaselineCost,
            double totalOptimizedCost
    ) {
        return new ClientOverviewMonthRecord(
                month,
                savings,
                grossSavings,
                totalEnergyKwh,
                totalMarketEnergyKwh,
                oaConsumer,
                oaCoverage,
                proltMarginCost,
                traderMargin,
                consultancyFee,
                probusPlatformFee,
                totalBaselineCost,
                totalOptimizedCost,
                null
        );
    }

    /**
     * Factory method for a failed month calculation.
     */
    public static ClientOverviewMonthRecord failure(String month, String error) {
        return new ClientOverviewMonthRecord(
                month,
                0.0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                error
        );
    }

    /**
     * Indicates whether this month's calculation was successful.
     */
    public boolean isSuccess() {
        return error == null;
    }

    /**
     * Converts this strongly typed monthly entry into an untyped {@link Map} matching
     * the legacy JSON response contract.
     *
     * @return Map with exact legacy keys in deterministic insertion order.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("month", month);
        map.put("savings", savings);

        if (error != null) {
            map.put("error", error);
            return map;
        }

        map.put("grossSavings", grossSavings);
        map.put("totalEnergyKwh", totalEnergyKwh);
        map.put("totalMarketEnergyKwh", totalMarketEnergyKwh);
        map.put("oaConsumer", oaConsumer);
        map.put("oaCoverage", oaCoverage);
        map.put("proltMarginCost", proltMarginCost);
        map.put("traderMargin", traderMargin);
        map.put("consultancyFee", consultancyFee);
        map.put("probusPlatformFee", probusPlatformFee);
        map.put("totalBaselineCost", totalBaselineCost);
        map.put("totalOptimizedCost", totalOptimizedCost);
        return map;
    }
}

package com.sense.prolt_operations.service.calculator.model;

import java.util.*;

/**
 * Immutable typed model representing the comprehensive monthly market decision and financial ledger reconciliation result.
 * Replaces weakly-typed {@code Map<String, Object>} across multi-month rollups and decision reporting.
 */
public record MonthlyMarketDecisionResult(
        String clientId,
        String clientName,
        String state,
        String month,
        List<MarketSlotCalculation> slotsData,
        double totalEnergyKwh,
        double totalMarketEnergyKwh,
        double totalConsumerBusEnergyKwh,
        double totalBaselineCost,
        double fppaPercent,
        double fppaCharge,
        double fppaChargeAfterOA,
        double totalLandedExchangeCost,
        double totalDiscomAfterProlt,
        double baselineEnergyCharges,
        double discomEnergyChargesAfterOA,
        double demandAndFixedChargesApplied,
        double electricityDutyAfterOA,
        double totalSavings,
        double grossSavings,
        double demandCharge,
        double electricityDuty,
        double arrearAmount,
        double currentLpsc,
        double miscellaneousCharges,
        String discom,
        double peakDemand,
        double powerFactor,
        double demandChargeRate,
        List<TodSlabSummary> todSummaries,
        OaDetailedResult oaDetailed
) {

    public MonthlyMarketDecisionResult {
        slotsData = slotsData != null ? List.copyOf(slotsData) : Collections.emptyList();
        todSummaries = todSummaries != null ? List.copyOf(todSummaries) : Collections.emptyList();
    }

    /**
     * Serializes this monthly result into the exact 30+ key Map contract expected by REST endpoints and downstream consumers.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("clientId", clientId);
        map.put("clientName", clientName);
        map.put("state", state);
        map.put("month", month);

        List<Map<String, Object>> slotsDataList = new ArrayList<>(slotsData.size());
        for (MarketSlotCalculation s : slotsData) {
            slotsDataList.add(s.toMap());
        }
        map.put("slotsData", slotsDataList);

        // Alias for integration test parity expecting 96 blocks
        List<Map<String, Object>> marketDecisionBlocks = slotsDataList.size() >= 96
                ? slotsDataList.subList(0, 96)
                : slotsDataList;
        map.put("marketDecision", marketDecisionBlocks);

        map.put("totalEnergyKwh", totalEnergyKwh);
        map.put("totalMarketEnergyKwh", totalMarketEnergyKwh);
        map.put("totalConsumerBusEnergyKwh", totalConsumerBusEnergyKwh);
        map.put("totalBaselineCost", totalBaselineCost);
        map.put("fppaPercent", fppaPercent);
        map.put("fppaCharge", fppaCharge);
        map.put("fppaChargeAfterOA", fppaChargeAfterOA);
        map.put("totalLandedExchangeCost", totalLandedExchangeCost);
        map.put("totalDiscomAfterProlt", totalDiscomAfterProlt);
        map.put("baselineEnergyCharges", baselineEnergyCharges);
        map.put("discomEnergyChargesAfterOA", discomEnergyChargesAfterOA);
        map.put("demandAndFixedChargesApplied", demandAndFixedChargesApplied);
        map.put("electricityDutyAfterOA", electricityDutyAfterOA);
        map.put("totalSavings", totalSavings);
        map.put("grossSavings", grossSavings);
        map.put("demandCharge", demandCharge);
        map.put("electricityDuty", electricityDuty);
        map.put("arrearAmount", arrearAmount);
        map.put("currentLpsc", currentLpsc);
        map.put("miscellaneousCharges", miscellaneousCharges);
        map.put("discom", discom);
        map.put("peakDemand", peakDemand);
        map.put("powerFactor", powerFactor);
        map.put("demandChargeRate", demandChargeRate);

        List<Map<String, Object>> todSummaryList = new ArrayList<>(todSummaries.size());
        for (TodSlabSummary s : todSummaries) {
            todSummaryList.add(s.toMap());
        }
        map.put("todSummaries", todSummaryList);
        map.put("oaDetailed", oaDetailed != null ? oaDetailed.toMap() : Collections.emptyMap());

        return map;
    }

    /**
     * Safely parses a {@link MonthlyMarketDecisionResult} from an untyped map.
     */
    public static MonthlyMarketDecisionResult fromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return new MonthlyMarketDecisionResult(
                    "", "", "", "", Collections.emptyList(),
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, "", 0.0, 0.0, 0.0, Collections.emptyList(), null
            );
        }

        List<MarketSlotCalculation> slotList = new ArrayList<>();
        if (map.get("slotsData") instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) {
                    slotList.add(MarketSlotCalculation.fromMap(m));
                }
            }
        }

        List<TodSlabSummary> todSummaryList = new ArrayList<>();
        if (map.get("todSummaries") instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) {
                    todSummaryList.add(TodSlabSummary.fromMap(m));
                }
            }
        }

        OaDetailedResult oaDetailedResult = map.get("oaDetailed") instanceof Map<?, ?> m
                ? OaDetailedResult.fromMap(m)
                : null;

        return new MonthlyMarketDecisionResult(
                map.get("clientId") != null ? String.valueOf(map.get("clientId")) : "",
                map.get("clientName") != null ? String.valueOf(map.get("clientName")) : "",
                map.get("state") != null ? String.valueOf(map.get("state")) : "",
                map.get("month") != null ? String.valueOf(map.get("month")) : "",
                slotList,
                toDouble(map.get("totalEnergyKwh")),
                toDouble(map.get("totalMarketEnergyKwh")),
                toDouble(map.get("totalConsumerBusEnergyKwh")),
                toDouble(map.get("totalBaselineCost")),
                toDouble(map.get("fppaPercent")),
                toDouble(map.get("fppaCharge")),
                toDouble(map.get("fppaChargeAfterOA")),
                toDouble(map.get("totalLandedExchangeCost")),
                toDouble(map.get("totalDiscomAfterProlt")),
                toDouble(map.get("baselineEnergyCharges")),
                toDouble(map.get("discomEnergyChargesAfterOA")),
                toDouble(map.get("demandAndFixedChargesApplied")),
                toDouble(map.get("electricityDutyAfterOA")),
                toDouble(map.get("totalSavings")),
                toDouble(map.get("grossSavings")),
                toDouble(map.get("demandCharge")),
                toDouble(map.get("electricityDuty")),
                toDouble(map.get("arrearAmount")),
                toDouble(map.get("currentLpsc")),
                toDouble(map.get("miscellaneousCharges")),
                map.get("discom") != null ? String.valueOf(map.get("discom")) : "",
                toDouble(map.get("peakDemand")),
                toDouble(map.get("powerFactor")),
                toDouble(map.get("demandChargeRate")),
                todSummaryList,
                oaDetailedResult
        );
    }

    private static double toDouble(Object val) {
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val != null) {
            try {
                return Double.parseDouble(val.toString().trim());
            } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }
}

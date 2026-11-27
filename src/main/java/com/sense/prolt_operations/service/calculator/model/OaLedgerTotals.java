package com.sense.prolt_operations.service.calculator.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strongly typed, immutable record representing Open Access (OA) ledger totals
 * aggregating all statutory surcharges, transmission charges, market fees, trader margins,
 * management fees, and net consumer bus units.
 */
public record OaLedgerTotals(
        double cssCharge,
        double cssRate,
        double rpoCharge,
        double pocCharge,
        double stuCharge,
        double dcCharge,
        double iexFee,
        double traderMargin,
        double traderMarginGst,
        double proltMarginCost,
        double consultancyFee,
        double probusPlatformFee,
        double nocFee,
        double regFee,
        double consumerBusUnits
) {

    /**
     * Converts this strongly typed ledger totals into an untyped {@link Map} matching
     * the legacy JSON response contract.
     *
     * @return Map with exact legacy keys in deterministic insertion order.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cssCharge", cssCharge);
        map.put("cssRate", cssRate);
        map.put("rpoCharge", rpoCharge);
        map.put("pocCharge", pocCharge);
        map.put("stuCharge", stuCharge);
        map.put("dcCharge", dcCharge);
        map.put("iexFee", iexFee);
        map.put("traderMargin", traderMargin);
        map.put("traderMarginGst", traderMarginGst);
        map.put("proltMarginCost", proltMarginCost);
        map.put("consultancyFee", consultancyFee);
        map.put("probusPlatformFee", probusPlatformFee);
        map.put("nocFee", nocFee);
        map.put("regFee", regFee);
        map.put("consumerBusUnits", consumerBusUnits);
        return map;
    }

    public static OaLedgerTotals empty() {
        return new OaLedgerTotals(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public static OaLedgerTotals fromMap(Map<?, ?> map) {
        if (map == null) return empty();
        return new OaLedgerTotals(
                toDouble(map.get("cssCharge")),
                toDouble(map.get("cssRate")),
                toDouble(map.get("rpoCharge")),
                toDouble(map.get("pocCharge")),
                toDouble(map.get("stuCharge")),
                toDouble(map.get("dcCharge")),
                toDouble(map.get("iexFee")),
                toDouble(map.get("traderMargin")),
                toDouble(map.get("traderMarginGst")),
                toDouble(map.get("proltMarginCost")),
                toDouble(map.get("consultancyFee")),
                toDouble(map.get("probusPlatformFee")),
                toDouble(map.get("nocFee")),
                toDouble(map.get("regFee")),
                toDouble(map.get("consumerBusUnits"))
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

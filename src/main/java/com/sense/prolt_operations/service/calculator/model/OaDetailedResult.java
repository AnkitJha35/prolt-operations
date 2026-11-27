package com.sense.prolt_operations.service.calculator.model;

import java.util.*;

/**
 * Immutable typed model representing the detailed Open Access operational and financial breakdown.
 */
public record OaDetailedResult(
        List<OaBreakdownItem> breakdown,
        double dailyFixedOverhead,
        double nldcSchedulingCost,
        double sldcSchedulingCost,
        double bidApplicationFees,
        int totalDaysTraded,
        OaLedgerTotals totals
) {

    public OaDetailedResult {
        breakdown = breakdown != null ? List.copyOf(breakdown) : Collections.emptyList();
        totals = totals != null ? totals : OaLedgerTotals.empty();
    }

    /**
     * Converts to Map for API boundary serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        List<Map<String, Object>> breakdownList = new ArrayList<>(breakdown.size());
        for (OaBreakdownItem b : breakdown) {
            breakdownList.add(b.toMap());
        }
        map.put("breakdown", breakdownList);
        map.put("dailyFixedOverhead", dailyFixedOverhead);
        map.put("nldcSchedulingCost", nldcSchedulingCost);
        map.put("sldcSchedulingCost", sldcSchedulingCost);
        map.put("bidApplicationFees", bidApplicationFees);
        map.put("totalDaysTraded", totalDaysTraded);
        map.put("totals", totals != null ? totals.toMap() : Collections.emptyMap());
        return map;
    }

    /**
     * Safely constructs an {@link OaDetailedResult} from an untyped map.
     */
    public static OaDetailedResult fromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return new OaDetailedResult(Collections.emptyList(), 0.0, 0.0, 0.0, 0.0, 0, OaLedgerTotals.empty());
        }

        List<OaBreakdownItem> breakdownItems = new ArrayList<>();
        if (map.get("breakdown") instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) {
                    breakdownItems.add(OaBreakdownItem.fromMap(m));
                }
            }
        }

        OaLedgerTotals totals = map.get("totals") instanceof Map<?, ?> m
                ? OaLedgerTotals.fromMap(m)
                : OaLedgerTotals.empty();

        return new OaDetailedResult(
                breakdownItems,
                toDouble(map.get("dailyFixedOverhead")),
                toDouble(map.get("nldcSchedulingCost")),
                toDouble(map.get("sldcSchedulingCost")),
                toDouble(map.get("bidApplicationFees")),
                map.get("totalDaysTraded") instanceof Number n ? n.intValue() : 0,
                totals
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

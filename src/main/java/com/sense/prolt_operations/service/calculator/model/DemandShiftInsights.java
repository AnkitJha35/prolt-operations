package com.sense.prolt_operations.service.calculator.model;

import java.util.*;

/**
 * Immutable typed representation of demand shift optimization insights.
 * Replaces weakly-typed {@code Map<String, Object>} across the calculator pipeline.
 */
public record DemandShiftInsights(
        String clientId,
        String clientName,
        double sanctionedLoadKw,
        double maxEnergyPerSlot,
        double originalTotalCost,
        double newTotalCost,
        double savingsAchieved,
        double shiftedEnergy,
        double potentialSavingsRs,
        double shiftableCapacityMw,
        String recommendedWindow,
        List<String> peakHours,
        List<String> solarHours,
        List<TodShiftSummaryItem> todShiftSummary,
        List<ShiftedSlotItem> slotsData
) {

    public DemandShiftInsights {
        peakHours = peakHours != null ? List.copyOf(peakHours) : Collections.emptyList();
        solarHours = solarHours != null ? List.copyOf(solarHours) : Collections.emptyList();
        todShiftSummary = todShiftSummary != null ? List.copyOf(todShiftSummary) : Collections.emptyList();
        slotsData = slotsData != null ? List.copyOf(slotsData) : Collections.emptyList();
    }

    /**
     * Checks if this insights model contains any slot-level data.
     */
    public boolean hasSlotsData() {
        return slotsData != null && !slotsData.isEmpty();
    }

    /**
     * Checks if this insights model contains ToD shift summary entries.
     */
    public boolean hasTodShiftSummary() {
        return todShiftSummary != null && !todShiftSummary.isEmpty();
    }

    /**
     * Finds a ToD shift summary item matching the given slab name case-insensitively.
     */
    public Optional<TodShiftSummaryItem> findTodSummary(String tod) {
        if (tod == null || todShiftSummary == null) {
            return Optional.empty();
        }
        return todShiftSummary.stream()
                .filter(item -> tod.equalsIgnoreCase(item.tod()))
                .findFirst();
    }

    /**
     * Directly retrieves new energy for a given ToD slab name if available.
     */
    public Optional<Double> getNewEnergyForTod(String tod) {
        return findTodSummary(tod).map(TodShiftSummaryItem::newEnergy);
    }

    /**
     * Finds the shifted slot matching the exact date and 15-minute timeblock.
     */
    public Optional<ShiftedSlotItem> findSlot(String date, Integer timeblock) {
        if (date == null || timeblock == null || slotsData == null) {
            return Optional.empty();
        }
        return slotsData.stream()
                .filter(s -> date.equals(s.date()) && Objects.equals(timeblock, s.timeblock()))
                .findFirst();
    }

    /**
     * Converts this typed model to a {@code Map<String, Object>} matching the exact API response contract.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("clientId", clientId);
        map.put("clientName", clientName);
        map.put("sanctionedLoadKw", sanctionedLoadKw);
        map.put("maxEnergyPerSlot", maxEnergyPerSlot);
        map.put("originalTotalCost", originalTotalCost);
        map.put("newTotalCost", newTotalCost);
        map.put("savingsAchieved", savingsAchieved);
        map.put("shiftedEnergy", shiftedEnergy);
        map.put("potentialSavingsRs", potentialSavingsRs);
        map.put("shiftableCapacityMw", shiftableCapacityMw);
        map.put("recommendedWindow", recommendedWindow != null ? recommendedWindow : "11:00 - 15:00 to 00:00 - 05:00");
        map.put("peakHours", peakHours);
        map.put("solarHours", solarHours);

        List<Map<String, Object>> todList = new ArrayList<>(todShiftSummary.size());
        for (TodShiftSummaryItem item : todShiftSummary) {
            todList.add(item.toMap());
        }
        map.put("todShiftSummary", todList);

        List<Map<String, Object>> slotList = new ArrayList<>(slotsData.size());
        for (ShiftedSlotItem slot : slotsData) {
            slotList.add(slot.toMap());
        }
        map.put("slotsData", slotList);

        return map;
    }

    /**
     * Safely creates a {@link DemandShiftInsights} from an external or legacy map.
     */
    public static DemandShiftInsights fromMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return empty("", "");
        }

        List<String> peakHoursList = map.get("peakHours") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of("17:00 - 22:00");

        List<String> solarHoursList = map.get("solarHours") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of("10:00 - 16:00");

        List<TodShiftSummaryItem> todItems = new ArrayList<>();
        if (map.get("todShiftSummary") instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) {
                    todItems.add(TodShiftSummaryItem.fromMap(m));
                }
            }
        }

        List<ShiftedSlotItem> slotItems = new ArrayList<>();
        if (map.get("slotsData") instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) {
                    slotItems.add(ShiftedSlotItem.fromMap(m));
                }
            }
        }

        double savingsAchieved = toDouble(map.get("savingsAchieved"));
        double potentialSavingsRs = map.containsKey("potentialSavingsRs")
                ? toDouble(map.get("potentialSavingsRs"))
                : (savingsAchieved > 0 ? savingsAchieved : 250000.0);

        return new DemandShiftInsights(
                map.get("clientId") != null ? String.valueOf(map.get("clientId")) : "",
                map.get("clientName") != null ? String.valueOf(map.get("clientName")) : "",
                toDouble(map.get("sanctionedLoadKw")),
                toDouble(map.get("maxEnergyPerSlot")),
                toDouble(map.get("originalTotalCost")),
                toDouble(map.get("newTotalCost")),
                savingsAchieved,
                toDouble(map.get("shiftedEnergy")),
                potentialSavingsRs,
                toDouble(map.get("shiftableCapacityMw")),
                map.get("recommendedWindow") != null ? String.valueOf(map.get("recommendedWindow")) : "11:00 - 15:00 to 00:00 - 05:00",
                peakHoursList,
                solarHoursList,
                todItems,
                slotItems
        );
    }

    /**
     * Factory method creating an empty placeholder insights model.
     */
    public static DemandShiftInsights empty(String clientId, String clientName) {
        return new DemandShiftInsights(
                clientId != null ? clientId : "",
                clientName != null ? clientName : "",
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                250000.0,
                0.5,
                "11:00 - 15:00 to 00:00 - 05:00",
                List.of("17:00 - 22:00"),
                List.of("10:00 - 16:00"),
                Collections.emptyList(),
                Collections.emptyList()
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

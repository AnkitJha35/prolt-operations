package com.sense.prolt_operations.service.calculator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.sense.prolt_operations.service.calculator.model.DemandShiftInsights;
import com.sense.prolt_operations.service.calculator.model.Hv2SlotRecord;

/**
 * Spring component responsible for ToD grouping, monthly consumption matching,
 * demand shift adjustments, and energy allocation across 15-minute simulation slots.
 */
@Component
public class Hv2EnergyAllocator {

    private static final Set<String> METADATA_KEYS = Set.of(
            "power factor", "electricity duty", "peak demand (kva)",
            "start date", "end date", "arrears", "lpsc", "miscellaneous charges", "_rawkvah"
    );

    /**
     * Allocates monthly consumption energy across slots based on ToD slabs, demand shift insights,
     * or proportional fallback distribution.
     *
     * @param slotsData         the 96 monthly slot records (modified in-place)
     * @param monthConsumptions monthly consumption entries from the bill
     * @param shiftInsights     optional demand shift insights
     */
    public void allocateEnergy(List<Hv2SlotRecord> slotsData, Map<String, Object> monthConsumptions,
            DemandShiftInsights shiftInsights) {
        if (slotsData == null || slotsData.isEmpty()) {
            return;
        }

        Map<String, List<Integer>> slotIndicesByTod = new LinkedHashMap<>();
        for (int i = 0; i < slotsData.size(); i++) {
            String groupKey = slotsData.get(i).todSlab().toUpperCase();
            slotIndicesByTod.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(i);
        }

        Double cachedFlatTotal = null;

        for (String groupKey : slotIndicesByTod.keySet()) {
            double remainingEnergy = 0.0;

            if (shiftInsights != null && shiftInsights.hasTodShiftSummary()) {
                remainingEnergy = shiftInsights.getNewEnergyForTod(groupKey).orElse(0.0);
            } else if (monthConsumptions != null) {
                String matchedKey = null;
                for (String k : monthConsumptions.keySet()) {
                    String kLower = k.toLowerCase();
                    if (kLower.contains("peak demand") || kLower.contains("sanctioned") || k.startsWith("_"))
                        continue;
                    if (k.toUpperCase().contains(groupKey) || k.equalsIgnoreCase(groupKey)) {
                        matchedKey = k;
                        break;
                    }
                }

                if (matchedKey != null && monthConsumptions.get(matchedKey) != null
                        && !String.valueOf(monthConsumptions.get(matchedKey)).isBlank()) {
                    remainingEnergy = CalculatorUtils.getDouble(monthConsumptions, matchedKey);
                } else {
                    if (cachedFlatTotal == null) {
                        cachedFlatTotal = resolveFlatTotal(monthConsumptions);
                    }
                    double flatTotal = cachedFlatTotal;

                    if (flatTotal > 0 && !slotsData.isEmpty()) {
                        remainingEnergy = flatTotal
                                * ((double) slotIndicesByTod.get(groupKey).size() / (double) slotsData.size());
                    }
                }
            }

            List<Integer> todIndices = slotIndicesByTod.get(groupKey);
            double energyPerSlot = !todIndices.isEmpty() ? remainingEnergy / todIndices.size() : 0.0;

            for (int idx : todIndices) {
                Hv2SlotRecord slot = slotsData.get(idx);
                double lowestP = slot.comparedLowestPrice();
                double discomP = slot.discomLandingPrice();
                slotsData.set(idx,
                        slot.withEnergyAndCosts(energyPerSlot, energyPerSlot * lowestP, energyPerSlot * discomP));
            }
        }
    }

    private double resolveFlatTotal(Map<String, Object> monthConsumptions) {
        if (monthConsumptions == null) {
            return 0.0;
        }

        String flatKey = null;
        for (String k : monthConsumptions.keySet()) {
            if ("FLAT".equalsIgnoreCase(k) || "TOTAL".equalsIgnoreCase(k)) {
                flatKey = k;
                break;
            }
        }

        if (flatKey != null && monthConsumptions.get(flatKey) != null
                && !String.valueOf(monthConsumptions.get(flatKey)).isBlank()) {
            return CalculatorUtils.getDouble(monthConsumptions, flatKey);
        }

        double flatTotal = 0.0;
        for (Map.Entry<String, Object> cEntry : monthConsumptions.entrySet()) {
            String k = cEntry.getKey();
            Object v = cEntry.getValue();
            if (v != null && !METADATA_KEYS.contains(k.toLowerCase()) && !k.startsWith("_")) {
                try {
                    flatTotal += Double.parseDouble(String.valueOf(v).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return flatTotal;
    }
}

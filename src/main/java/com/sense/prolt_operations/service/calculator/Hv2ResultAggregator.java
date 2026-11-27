package com.sense.prolt_operations.service.calculator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sense.prolt_operations.service.calculator.model.EnergySource;
import com.sense.prolt_operations.service.calculator.model.Hv2CalculationContext;
import com.sense.prolt_operations.service.calculator.model.Hv2SlotRecord;

/**
 * Spring component responsible for aggregating financial costs, ledger totals,
 * ToD group price sorting, and building the final response map for HV-2 calculations.
 */
@Component
public class Hv2ResultAggregator {

    /**
     * Aggregates energy totals, calculates baseline and optimized costs, sorts slots by price,
     * and constructs the external response map.
     *
     * @param ctx          the calculation context
     * @param allSlotsData all simulated slot records across all processed months
     * @return response map compatible with existing API contracts
     */
    public Map<String, Object> aggregateResults(Hv2CalculationContext ctx, List<Hv2SlotRecord> allSlotsData) {
        double totalBaselineCost = 0.0;
        double totalOptimizedCost = 0.0;
        double totalEnergyKwh = 0.0;
        double totalMarketEnergyKwh = 0.0;
        double totalConsumerBusEnergyKwh = 0.0;

        for (Hv2SlotRecord item : allSlotsData) {
            totalOptimizedCost += item.optimizedCost();
            totalBaselineCost += item.baselineCost();
            double ePerSlot = item.maxEnergyPerSlot();
            totalEnergyKwh += ePerSlot;
            if (!EnergySource.DISCOM.getValue().equals(item.selectedSource())) {
                totalMarketEnergyKwh += ePerSlot;
            }
            totalConsumerBusEnergyKwh += ePerSlot;
        }

        double totalSavings = totalBaselineCost - totalOptimizedCost;

        // Group and sort typed records directly by comparedLowestPrice (Milestone V18N)
        Map<String, List<Hv2SlotRecord>> groups = new LinkedHashMap<>();
        for (Hv2SlotRecord item : allSlotsData) {
            String groupKey = item.todSlab().toUpperCase();
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(item);
        }

        Map<String, List<Map<String, Object>>> sortedGroups = new LinkedHashMap<>();
        for (Map.Entry<String, List<Hv2SlotRecord>> gEntry : groups.entrySet()) {
            List<Hv2SlotRecord> records = new ArrayList<>(gEntry.getValue());
            records.sort(Comparator.comparingDouble(Hv2SlotRecord::comparedLowestPrice));
            List<Map<String, Object>> sortedMaps = new ArrayList<>(records.size());
            for (Hv2SlotRecord r : records) {
                sortedMaps.add(r.toMap());
            }
            sortedGroups.put(gEntry.getKey(), sortedMaps);
        }

        List<Hv2SlotRecord> sortedRecords = new ArrayList<>(allSlotsData);
        sortedRecords.sort(Comparator.comparingDouble(Hv2SlotRecord::comparedLowestPrice));
        List<Map<String, Object>> sortedMonthlyList = new ArrayList<>(sortedRecords.size());
        for (Hv2SlotRecord r : sortedRecords) {
            sortedMonthlyList.add(r.toMap());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientId", ctx.id());
        response.put("clientName", ctx.entry().getClientName());
        response.put("sanctionedLoad", ctx.sanctionedLoad());
        response.put("maxEnergyPerSlot", ctx.maxEnergyPerSlot());
        response.put("totalEnergyKwh", totalEnergyKwh);
        response.put("totalMarketEnergyKwh", totalMarketEnergyKwh);
        response.put("totalConsumerBusEnergyKwh", totalConsumerBusEnergyKwh);
        response.put("totalBaselineCost", totalBaselineCost);
        response.put("totalOptimizedCost", totalOptimizedCost);
        response.put("totalSavings", totalSavings);
        response.put("todGroups", sortedGroups);
        response.put("sortedMonthlyList", sortedMonthlyList);
        return response;
    }
}

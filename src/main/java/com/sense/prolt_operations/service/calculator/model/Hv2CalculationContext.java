package com.sense.prolt_operations.service.calculator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.service.SavingsCalculatorService;
import com.sense.prolt_operations.service.calculator.CalculatorUtils;

/**
 * Immutable calculation context capturing validated inputs, client configuration,
 * parsed categories, and target months for HV-2 savings calculations.
 */
public record Hv2CalculationContext(
        SavingsCalculatorEntry entry,
        String id,
        String stateCode,
        double sanctionedLoad,
        ParsedCategory parsedCategory,
        String parsedSupplyVoltage,
        double maxEnergyPerSlot,
        List<Map.Entry<String, Map<String, Object>>> monthsToProcess,
        DemandShiftInsights shiftInsights
) {

    /**
     * Builds and validates a new {@link Hv2CalculationContext} from the given entry, target month,
     * and optional shift insights.
     *
     * @param entry         the savings calculator entry
     * @param targetMonth   target month string (e.g. "2024-05") or null for all months
     * @param shiftInsights optional demand shift adjustments
     * @param objectMapper  Jackson object mapper for JSON deserialization
     * @return validated immutable context
     * @throws BadRequestException if consumption data is missing or target month is not present
     */
    public static Hv2CalculationContext build(SavingsCalculatorEntry entry, String targetMonth,
            DemandShiftInsights shiftInsights, ObjectMapper objectMapper) {
        String id = entry.getId();
        String stateCode = entry.getStateCode() != null ? entry.getStateCode() : "MH";
        double sanctionedLoad = entry.getSanctionedLoadKw() != null ? entry.getSanctionedLoadKw().doubleValue() : 100.0;
        String rawCategory = entry.getConsumerCategory() != null ? entry.getConsumerCategory() : "Industrial";
        String rawVoltage = entry.getVoltageLevel() != null ? entry.getVoltageLevel() : "11 kV";
        double maxEnergyPerSlot = SavingsCalculatorService.getFlooredMaxEnergyPerSlot(entry.getSanctionedLoadKw(),
                entry.getPowerFactor());

        Map<String, Map<String, Object>> todConsumptions = CalculatorUtils
                .parseTodConsumptions(entry.getTodConsumptions(), objectMapper);
        if (todConsumptions.isEmpty()) {
            throw new BadRequestException(
                    "No TOD consumption data found. Please edit the entry and provide consumption data.");
        }

        List<Map.Entry<String, Map<String, Object>>> monthsToProcess = new ArrayList<>();
        if (targetMonth != null && !targetMonth.isBlank()) {
            if (!todConsumptions.containsKey(targetMonth)) {
                throw new BadRequestException("No consumption data found for month " + targetMonth);
            }
            monthsToProcess.add(Map.entry(targetMonth, todConsumptions.get(targetMonth)));
        } else {
            monthsToProcess.addAll(todConsumptions.entrySet());
        }

        ParsedCategory parsedCat = CalculatorUtils.parseCategory(rawCategory);
        String parsedSupplyVoltage = CalculatorUtils.parseSupplyVoltageCategory(rawVoltage);

        return new Hv2CalculationContext(entry, id, stateCode, sanctionedLoad, parsedCat,
                parsedSupplyVoltage, maxEnergyPerSlot, Collections.unmodifiableList(monthsToProcess), shiftInsights);
    }
}

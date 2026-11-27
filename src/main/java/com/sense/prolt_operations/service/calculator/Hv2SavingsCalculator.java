package com.sense.prolt_operations.service.calculator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.dto.calculator.ExchangeRateSlotProjection;
import com.sense.prolt_operations.entity.IstsCharges;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.entity.StateCharges;
import com.sense.prolt_operations.entity.StateTariff;
import com.sense.prolt_operations.service.SavingsCalculatorDataAccessService;
import com.sense.prolt_operations.service.SavingsCalculatorService;
import com.sense.prolt_operations.service.calculator.exception.CalculatorExecutionException;
import com.sense.prolt_operations.service.calculator.model.DemandShiftInsights;
import com.sense.prolt_operations.service.calculator.model.Hv2CalculationContext;
import com.sense.prolt_operations.service.calculator.model.Hv2SlotRecord;

/**
 * Spring component encapsulating HV-2 seasonal savings calculations, 96-slot
 * dispatch simulations, and baseline DISCOM cost calculations.
 */
@Component
public class Hv2SavingsCalculator {

    private static final Logger log = LoggerFactory.getLogger(Hv2SavingsCalculator.class);

    private final SavingsCalculatorDataAccessService dataAccessService;
    private final NpclTariffSchedule npclTariffSchedule;
    private final LandedCostCalculator landedCostCalculator;
    private final ObjectMapper objectMapper;
    private final CalculatorProperties properties;
    private final RegulatoryChargeResolver regulatoryChargeResolver;
    private final Hv2SlotProcessor slotProcessor;
    private final Hv2EnergyAllocator energyAllocator;
    private final Hv2ResultAggregator resultAggregator;

    @Autowired
    public Hv2SavingsCalculator(SavingsCalculatorDataAccessService dataAccessService,
            NpclTariffSchedule npclTariffSchedule, LandedCostCalculator landedCostCalculator,
            ObjectMapper objectMapper, CalculatorProperties properties,
            RegulatoryChargeResolver regulatoryChargeResolver,
            Hv2SlotProcessor slotProcessor,
            Hv2EnergyAllocator energyAllocator,
            Hv2ResultAggregator resultAggregator) {
        this.dataAccessService = dataAccessService;
        this.npclTariffSchedule = npclTariffSchedule != null ? npclTariffSchedule : new NpclTariffSchedule();
        this.landedCostCalculator = landedCostCalculator != null ? landedCostCalculator : new LandedCostCalculator();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.properties = properties != null ? properties : new CalculatorProperties();
        this.regulatoryChargeResolver = regulatoryChargeResolver != null ? regulatoryChargeResolver
                : new RegulatoryChargeResolver(this.properties, this.npclTariffSchedule);
        this.slotProcessor = slotProcessor != null ? slotProcessor
                : new Hv2SlotProcessor(this.dataAccessService, this.regulatoryChargeResolver, this.landedCostCalculator, this.properties);
        this.energyAllocator = energyAllocator != null ? energyAllocator : new Hv2EnergyAllocator();
        this.resultAggregator = resultAggregator != null ? resultAggregator : new Hv2ResultAggregator();
    }

    public Hv2SavingsCalculator(SavingsCalculatorDataAccessService dataAccessService,
            NpclTariffSchedule npclTariffSchedule, LandedCostCalculator landedCostCalculator,
            ObjectMapper objectMapper, CalculatorProperties properties,
            RegulatoryChargeResolver regulatoryChargeResolver) {
        this(dataAccessService, npclTariffSchedule, landedCostCalculator, objectMapper, properties,
                regulatoryChargeResolver, null, null, null);
    }

    public Hv2SavingsCalculator(SavingsCalculatorDataAccessService dataAccessService,
            NpclTariffSchedule npclTariffSchedule, LandedCostCalculator landedCostCalculator,
            ObjectMapper objectMapper, CalculatorProperties properties) {
        this(dataAccessService, npclTariffSchedule, landedCostCalculator, objectMapper, properties, null);
    }

    public Hv2SavingsCalculator(SavingsCalculatorDataAccessService dataAccessService,
            NpclTariffSchedule npclTariffSchedule, LandedCostCalculator landedCostCalculator,
            ObjectMapper objectMapper) {
        this(dataAccessService, npclTariffSchedule, landedCostCalculator, objectMapper, new CalculatorProperties());
    }

    public Hv2SavingsCalculator(SavingsCalculatorDataAccessService dataAccessService,
            NpclTariffSchedule npclTariffSchedule, LandedCostCalculator landedCostCalculator) {
        this(dataAccessService, npclTariffSchedule, landedCostCalculator, null, new CalculatorProperties());
    }

    /**
     * Calculates baseline DISCOM energy cost for an entry and target month without
     * open access market optimization.
     *
     * @param entry       the savings calculator entry
     * @param targetMonth the target month string (e.g. "2024-05")
     * @return the total unoptimized baseline DISCOM cost in INR
     */
    public double calculateBaselineCost(SavingsCalculatorEntry entry, String targetMonth) {
        Map<String, Object> res = calculateSavingsHV2(entry, targetMonth, (DemandShiftInsights) null);
        return CalculatorUtils.getDouble(res, "totalBaselineCost");
    }

    /**
     * Executes the HV-2 96-slot dispatch simulation against NPCL seasonal ToD
     * tariffs and day-ahead / real-time exchange landed costs.
     *
     * @param entry         the client savings calculator entry
     * @param targetMonth   the target month string (null or empty processes all
     *                      months)
     * @param shiftInsights optional demand shift adjustments
     * @return map containing slot dispatches, ToD groups, and financial savings
     */
    public Map<String, Object> calculateSavingsHV2(SavingsCalculatorEntry entry, String targetMonth,
            Map<String, Object> shiftInsights) {
        return calculateSavingsHV2(entry, targetMonth,
                shiftInsights != null ? DemandShiftInsights.fromMap(shiftInsights) : null);
    }

    public Map<String, Object> calculateSavingsHV2(SavingsCalculatorEntry entry, String targetMonth,
            DemandShiftInsights shiftInsights) {
        Hv2CalculationContext ctx = Hv2CalculationContext.build(entry, targetMonth, shiftInsights, objectMapper);

        List<Hv2SlotRecord> allSlotsData = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entryMonthData : ctx.monthsToProcess()) {
            String yearMonth = entryMonthData.getKey();
            Map<String, Object> monthConsumptions = entryMonthData.getValue();

            List<Hv2SlotRecord> slotsData = slotProcessor.processMonthSlots(ctx, yearMonth, monthConsumptions);
            energyAllocator.allocateEnergy(slotsData, monthConsumptions, ctx.shiftInsights());
            allSlotsData.addAll(slotsData);
        }

        return resultAggregator.aggregateResults(ctx, allSlotsData);
    }

    /**
     * Aggregates HV-2 monthly calculation results across all months.
     */
    public Map<String, Object> calculateSavingsAllMonths(SavingsCalculatorEntry entry,
            Map<String, Map<String, Object>> todConsumptions,
            BiFunction<String, Map<String, Object>, Map<String, Object>> monthSavingsProvider) {

        List<String> months = new ArrayList<>(todConsumptions.keySet());
        Collections.sort(months);

        double totalSavings = 0.0;
        double totalOptimizedCost = 0.0;
        double totalBaselineCost = 0.0;
        double totalEnergyKwh = 0.0;
        double totalMarketEnergyKwh = 0.0;
        double totalConsumerBusEnergyKwh = 0.0;

        int successfulMonths = 0;
        List<String> failedMonths = new ArrayList<>();
        Throwable firstFailure = null;

        for (String month : months) {
            try {
                Map<String, Object> res = monthSavingsProvider.apply(month, null);
                if (res != null) {
                    successfulMonths++;
                    totalSavings += CalculatorUtils.getDouble(res, "totalSavings");
                    totalOptimizedCost += CalculatorUtils.getDouble(res, "totalOptimizedCost");
                    totalBaselineCost += CalculatorUtils.getDouble(res, "totalBaselineCost");
                    totalEnergyKwh += CalculatorUtils.getDouble(res, "totalEnergyKwh");
                    totalMarketEnergyKwh += CalculatorUtils.getDouble(res, "totalMarketEnergyKwh");
                    totalConsumerBusEnergyKwh += res.containsKey("totalConsumerBusEnergyKwh")
                            ? CalculatorUtils.getDouble(res, "totalConsumerBusEnergyKwh")
                            : CalculatorUtils.getDouble(res, "totalMarketEnergyKwh");
                }
            } catch (Exception e) {
                failedMonths.add(month);
                if (firstFailure == null) {
                    firstFailure = e;
                }
                log.error("[SavingsCalculator] Error calculating month '{}' for client '{}' (id: {}) in calculateSavingsAllMonths: {}",
                        month, entry.getClientName(), entry.getId(), e.getMessage(), e);
            }
        }

        if (successfulMonths == 0 && !months.isEmpty()) {
            throw new CalculatorExecutionException(
                "HV-2 Savings",
                String.join(", ", failedMonths),
                entry.getId(),
                "All " + months.size() + " monthly calculations failed" + (firstFailure != null ? ": " + firstFailure.getMessage() : ""),
                firstFailure
            );
        }

        if (!failedMonths.isEmpty()) {
            log.warn("[SavingsCalculator] Multi-month HV-2 calculation completed with partial failures for client '{}' (id: {}). Succeeded: {}/{}, Failed months: {}",
                    entry.getClientName(), entry.getId(), successfulMonths, months.size(), failedMonths);
        }

        double sanctionedLoad = entry.getSanctionedLoadKw() != null ? entry.getSanctionedLoadKw().doubleValue() : 0.0;
        double maxEnergyPerSlot = SavingsCalculatorService.getFlooredMaxEnergyPerSlot(entry.getSanctionedLoadKw(),
                entry.getPowerFactor());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientId", entry.getId());
        response.put("clientName", entry.getClientName());
        response.put("sanctionedLoad", sanctionedLoad);
        response.put("maxEnergyPerSlot", maxEnergyPerSlot);
        response.put("totalEnergyKwh", totalEnergyKwh);
        response.put("totalMarketEnergyKwh", totalMarketEnergyKwh);
        response.put("totalConsumerBusEnergyKwh", totalConsumerBusEnergyKwh);
        response.put("totalBaselineCost", totalBaselineCost);
        response.put("totalOptimizedCost", totalOptimizedCost);
        response.put("totalSavings", totalSavings);
        response.put("todGroups", Collections.emptyMap());
        response.put("sortedMonthlyList", Collections.emptyList());
        return response;
    }
}

package com.sense.prolt_operations.service.calculator.model;

import com.sense.prolt_operations.entity.IstsCharges;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.entity.StateTariff;

import java.util.List;
import java.util.Map;

public record MarketDecisionContext(
        SavingsCalculatorEntry entry,
        String clientId,
        String targetMonthStr,
        String monthKey,
        Map<String, Object> monthConsumptions,
        SimulationDateRange dateRange,
        String stateCode,
        ParsedCategory parsedCat,
        String parsedSupplyVoltage,
        List<String> stateFormats,
        double traderMargin,
        double gstTraderMargin,
        double sanctionedLoad,
        double maxEnergyPerSlot,
        boolean applyElectricityDuty,
        double electricityDutyPercent,
        double monthArrear,
        double monthLpsc,
        double monthMisc,
        double ctuCharge,
        double stuCharge,
        double wheelingCharge,
        double crossSubsidy,
        double additionalSurcharge,
        double stuLoss,
        double wheelingLoss,
        double demandChargeRate,
        double nldcSchedulingFees,
        double sldcSchedulingFees,
        double fppaPercent,
        List<StateTariff> tariffs,
        List<IstsCharges> istsChargesList,
        List<InternalRateSlot> slotProjections,
        boolean isNpcl,
        boolean isNpclHv2,
        DemandShiftInsights shiftInsights
) {}

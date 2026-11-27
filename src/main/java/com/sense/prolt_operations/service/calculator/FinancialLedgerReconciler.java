package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.entity.StateTariff;
import com.sense.prolt_operations.service.calculator.constant.ExchangeFeeConstants;
import com.sense.prolt_operations.service.calculator.constant.OverheadFeeConstants;
import com.sense.prolt_operations.service.calculator.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FinancialLedgerReconciler {

    private final CalculatorProperties properties;

    public FinancialLedgerReconciler() {
        this(new CalculatorProperties());
    }

    @Autowired
    public FinancialLedgerReconciler(CalculatorProperties properties) {
        this.properties = properties != null ? properties : new CalculatorProperties();
    }

    public MonthlyMarketDecisionResult reconcile(
            MarketDecisionContext context,
            List<MarketSlotCalculation> slotsData,
            OperationalOverheads overheads) {

        double exchangeFees = properties != null && properties.getExchange() != null
                ? properties.getExchange().getDefaultExchangeFeePerKwh()
                : ExchangeFeeConstants.DEFAULT_EXCHANGE_FEE_PER_KWH;
        double rpoFlatRate = properties != null && properties.getExchange() != null
                ? properties.getExchange().getDefaultRpoRatePerKwh()
                : ExchangeFeeConstants.DEFAULT_RPO_FLAT_RATE_PER_KWH;

        double peakDemand = context.entry().getBilledDemandKv() != null
                ? context.entry().getBilledDemandKv().doubleValue() : 0.0;
        if (peakDemand == 0.0) {
            for (String k : context.monthConsumptions().keySet()) {
                String kl = k.toLowerCase();
                if (kl.contains("peak demand") || kl.contains("sanctioned")) {
                    peakDemand = Math.max(peakDemand, CalculatorUtils.getDouble(context.monthConsumptions(), k));
                }
            }
        }

        double demandCharge = peakDemand * context.demandChargeRate();

        Map<String, List<MarketSlotCalculation>> slotsByTod = new LinkedHashMap<>();
        for (MarketSlotCalculation s : slotsData) {
            String key = s.tod.toUpperCase();
            slotsByTod.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        StateTariff baseTariff = !context.tariffs().isEmpty() ? context.tariffs().get(0) : null;
        boolean isKvahBilling = baseTariff != null && "kvah".equalsIgnoreCase(baseTariff.getBaseEnergyUnit());
        if (context.monthConsumptions().containsKey("Billing Unit")) {
            isKvahBilling = "kvah".equalsIgnoreCase(String.valueOf(context.monthConsumptions().get("Billing Unit")));
        }
        boolean consumptionValuesAreKwh = context.monthConsumptions().get("slots") instanceof List;
        double globalPf = context.entry().getPowerFactor() != null ? context.entry().getPowerFactor().doubleValue() : 0.99;
        if (globalPf <= 0) globalPf = 0.99;

        double preTotalEnergyKwh = 0.0;
        for (String groupKey : slotsByTod.keySet()) {
            double slabConsumption = CalculatorUtils.getSlabConsumption(
                    groupKey,
                    context.monthConsumptions(),
                    slotsByTod.get(groupKey).size(),
                    slotsData.size(),
                    isKvahBilling,
                    consumptionValuesAreKwh,
                    globalPf,
                    context.shiftInsights()
            );
            preTotalEnergyKwh += slabConsumption;
        }

        double totalBaselineCost = 0.0;
        double totalElectricityDuty = 0.0;
        double totalElectricityDutyAfterOA = 0.0;
        double totalLandedExchangeCost = 0.0;
        double totalDiscomAfterProlt = 0.0;
        double totalEnergyKwh = 0.0;
        double totalMarketEnergyKwh = 0.0;
        double totalConsumerBusEnergyKwh = 0.0;
        double totalBaselineEnergyCharges = 0.0;
        double totalDiscomEnergyChargesAfterOA = 0.0;
        double totalDemandAndFixedChargesApplied = 0.0;
        double totalFppaCharge = 0.0;
        double totalFppaChargeAfterOA = 0.0;

        double globalCssCharge = 0.0;
        double globalRpoCharge = 0.0;
        double globalPocCharge = 0.0;
        double globalStuCharge = 0.0;
        double globalDcCharge = 0.0;
        double globalIexFee = 0.0;
        double globalTraderMargin = 0.0;
        double globalTraderMarginGst = 0.0;

        List<TodSlabSummary> todSummaries = new ArrayList<>();
        List<OaBreakdownItem> oaDetailedBreakdown = new ArrayList<>();

        List<String> sortedTodKeys = new ArrayList<>(slotsByTod.keySet());
        sortedTodKeys.sort(Comparator.comparingInt(k -> {
            String digits = k.replaceAll("\\D", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }));

        boolean applyED = true;
        for (String k : context.monthConsumptions().keySet()) {
            if ("electricity duty".equalsIgnoreCase(k) && context.monthConsumptions().get(k) != null) {
                applyED = !"no".equalsIgnoreCase(String.valueOf(context.monthConsumptions().get(k)).trim());
                break;
            }
        }
        double edRate = "HV-1".equalsIgnoreCase(context.parsedCat().category()) ? 0.05 : 0.075;

        for (String groupKey : sortedTodKeys) {
            List<MarketSlotCalculation> slotsInGroup = slotsByTod.get(groupKey);
            double slabConsumption = CalculatorUtils.getSlabConsumption(
                    groupKey,
                    context.monthConsumptions(),
                    slotsInGroup.size(),
                    slotsData.size(),
                    isKvahBilling,
                    consumptionValuesAreKwh,
                    globalPf,
                    context.shiftInsights()
            );
            if (slabConsumption <= 0) continue;

            double finalMarketEnergy = 0.0;
            double exactMarketEnergyCost = 0.0;
            double discomEnergy = 0.0;
            double consumerBusUnits = 0.0;

            for (MarketSlotCalculation s : slotsInGroup) {
                finalMarketEnergy += s.marketEnergy;
                exactMarketEnergyCost += s.exactMarketEnergyCost;
                discomEnergy += s.discomEnergy;
                consumerBusUnits += s.consumedMarketEnergy;
            }

            double slabDiscomRate = !slotsInGroup.isEmpty() ? slotsInGroup.get(0).discomLanding : 0.0;
            List<MarketSlotCalculation> marketSlots = slotsInGroup.stream().filter(s -> s.marketEnergy > 0).toList();

            double slabFraction = preTotalEnergyKwh > 0 ? slabConsumption / preTotalEnergyKwh : 0.0;
            double slabDemandCharge = demandCharge * slabFraction;
            double slabEnergyBill = slabConsumption * slabDiscomRate;

            double demandChargeDiscounted = context.isNpcl() ? slabDemandCharge * 0.90 * 0.99 : slabDemandCharge;
            totalDemandAndFixedChargesApplied += demandChargeDiscounted;

            double slabFppaCharge = (slabEnergyBill + demandChargeDiscounted) * (context.fppaPercent() / 100.0);
            totalFppaCharge += slabFppaCharge;

            double discountedSlabBill = slabEnergyBill + demandChargeDiscounted + slabFppaCharge;
            totalBaselineEnergyCharges += slabEnergyBill;

            double slabED = applyED ? discountedSlabBill * edRate : 0.0;
            totalElectricityDuty += slabED;

            double slabTotalDiscomBill = discountedSlabBill + slabED;
            totalBaselineCost += slabTotalDiscomBill;

            double proltEnergyBill = discomEnergy * slabDiscomRate;
            totalDiscomEnergyChargesAfterOA += proltEnergyBill;

            double slabFppaChargeAfterOA = (proltEnergyBill + demandChargeDiscounted) * (context.fppaPercent() / 100.0);
            totalFppaChargeAfterOA += slabFppaChargeAfterOA;

            double discountedProltBill = proltEnergyBill + demandChargeDiscounted + slabFppaChargeAfterOA;
            double slabEDAfterOA = applyED ? discountedProltBill * edRate : 0.0;
            totalElectricityDutyAfterOA += slabEDAfterOA;

            double proltDiscomBillTotal = discountedProltBill + slabEDAfterOA;
            totalDiscomAfterProlt += proltDiscomBillTotal;

            double nonGdamMarketEnergy = 0.0;
            for (MarketSlotCalculation s : marketSlots) {
                if (!EnergyMarketType.GDAM.getValue().equals(s.marketSource)) {
                    nonGdamMarketEnergy += s.marketEnergy;
                }
            }
            double nonGdamFraction = finalMarketEnergy > 0 ? nonGdamMarketEnergy / finalMarketEnergy : 0.0;
            double nonGdamConsumerBusUnits = consumerBusUnits * nonGdamFraction;

            double rpoCharge = nonGdamConsumerBusUnits * rpoFlatRate;
            double cssCharge = consumerBusUnits * context.crossSubsidy();
            double pocCharge = finalMarketEnergy * context.ctuCharge();
            double stuChargeVal = finalMarketEnergy * context.stuCharge();
            double dcCharge = finalMarketEnergy * context.wheelingCharge();
            double iexFeesTotal = finalMarketEnergy * exchangeFees;
            double traderMarginTotal = finalMarketEnergy * context.traderMargin();
            double traderMarginGstTotal = finalMarketEnergy * context.gstTraderMargin();

            globalCssCharge += cssCharge;
            globalRpoCharge += rpoCharge;
            globalPocCharge += pocCharge;
            globalStuCharge += stuChargeVal;
            globalDcCharge += dcCharge;
            globalIexFee += iexFeesTotal;
            globalTraderMargin += traderMarginTotal;
            globalTraderMarginGst += traderMarginGstTotal;

            double marketEnergyCost = exactMarketEnergyCost;
            double slabOaBill = cssCharge + rpoCharge + pocCharge + stuChargeVal + dcCharge + iexFeesTotal + marketEnergyCost;

            totalLandedExchangeCost += slabOaBill + proltDiscomBillTotal;
            totalEnergyKwh += slabConsumption;
            totalMarketEnergyKwh += finalMarketEnergy;
            totalConsumerBusEnergyKwh += consumerBusUnits;

            todSummaries.add(new TodSlabSummary(groupKey, slabConsumption, finalMarketEnergy, marketEnergyCost));

            oaDetailedBreakdown.add(new OaBreakdownItem(
                    groupKey,
                    slabConsumption,
                    finalMarketEnergy,
                    slabEnergyBill,
                    proltEnergyBill,
                    consumerBusUnits,
                    slabOaBill,
                    slabDiscomRate
            ));
        }

        double nocFee = properties != null && properties.getFees() != null
                ? properties.getFees().getDiscomNocMonthly() : OverheadFeeConstants.DEFAULT_DISCOM_NOC_FEE_MONTHLY;
        double regFee = properties != null && properties.getFees() != null
                ? properties.getFees().getSldcMonthly() : OverheadFeeConstants.DEFAULT_SLDC_MONTHLY_FEE;
        double defaultConsultancy = properties != null && properties.getFees() != null
                ? properties.getFees().getConsultancyMonthly() : OverheadFeeConstants.DEFAULT_CONSULTANCY_FEE_MONTHLY;
        double consultancyFeeVal = context.entry().getConsultancyFee() != null
                ? context.entry().getConsultancyFee().doubleValue() : defaultConsultancy;
        double defaultPlatformRate = properties != null && properties.getFees() != null
                ? properties.getFees().getPlatformFeeRate() : OverheadFeeConstants.DEFAULT_PROBUS_PLATFORM_FEE_RATE;
        double platformFeeRate = context.entry().getProbusPlatformFee() != null
                ? context.entry().getProbusPlatformFee().doubleValue() : defaultPlatformRate;
        double probusPlatformFee = Math.round(totalMarketEnergyKwh * platformFeeRate);

        totalBaselineCost += context.monthMisc();
        totalDiscomAfterProlt += context.monthMisc();
        totalLandedExchangeCost += context.monthMisc();

        double netSavings = totalBaselineCost - (totalLandedExchangeCost + overheads.dailyFixedOverhead() + overheads.bidApplicationFees());
        double proltMarginInput = context.entry().getProltMargin() != null ? context.entry().getProltMargin().doubleValue() : 0.0;
        double grossSavings = Math.max(0.0, netSavings);
        double totalProltMarginCost = Math.round(grossSavings * (proltMarginInput / 100.0));

        double totalSavings = netSavings - nocFee - regFee - consultancyFeeVal - probusPlatformFee - totalProltMarginCost - globalTraderMargin - globalTraderMarginGst;
        double finalSavings = Math.max(0.0, totalSavings);

        OaLedgerTotals totals = new OaLedgerTotals(
                globalCssCharge,
                context.crossSubsidy(),
                globalRpoCharge,
                globalPocCharge,
                globalStuCharge,
                globalDcCharge,
                globalIexFee,
                globalTraderMargin,
                globalTraderMarginGst,
                totalProltMarginCost,
                consultancyFeeVal,
                probusPlatformFee,
                nocFee,
                regFee,
                totalConsumerBusEnergyKwh
        );

        OaDetailedResult oaDetailedResult = new OaDetailedResult(
                oaDetailedBreakdown,
                overheads.dailyFixedOverhead(),
                overheads.nldcSchedulingCost(),
                overheads.sldcSchedulingCost(),
                overheads.bidApplicationFees(),
                overheads.totalDaysTraded(),
                totals
        );

        return new MonthlyMarketDecisionResult(
                context.clientId(),
                context.entry().getClientName(),
                context.stateCode(),
                context.targetMonthStr() != null ? context.targetMonthStr() : context.monthKey(),
                slotsData,
                totalEnergyKwh,
                totalMarketEnergyKwh,
                totalConsumerBusEnergyKwh,
                totalBaselineCost,
                context.fppaPercent(),
                totalFppaCharge,
                totalFppaChargeAfterOA,
                totalLandedExchangeCost,
                totalDiscomAfterProlt,
                totalBaselineEnergyCharges,
                totalDiscomEnergyChargesAfterOA,
                totalDemandAndFixedChargesApplied,
                totalElectricityDutyAfterOA,
                finalSavings,
                grossSavings,
                demandCharge,
                totalElectricityDuty,
                context.monthArrear(),
                context.monthLpsc(),
                context.monthMisc(),
                context.entry().getDiscom(),
                peakDemand,
                globalPf,
                context.demandChargeRate(),
                todSummaries,
                oaDetailedResult
        );
    }

    public Map<String, Object> reconcileToMap(
            MarketDecisionContext context,
            List<MarketSlotCalculation> slotsData,
            OperationalOverheads overheads) {
        return reconcile(context, slotsData, overheads).toMap();
    }
}

package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.service.calculator.constant.OverheadFeeConstants;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryConstants;
import com.sense.prolt_operations.service.calculator.exception.CalculatorExecutionException;
import com.sense.prolt_operations.service.calculator.model.AggregatedCostsAccumulator;
import com.sense.prolt_operations.service.calculator.model.ClientOverviewMonthRecord;
import com.sense.prolt_operations.service.calculator.model.MonthlyMarketDecisionResult;
import com.sense.prolt_operations.service.calculator.model.OaDetailedResult;
import com.sense.prolt_operations.service.calculator.model.OaLedgerTotals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * Spring component encapsulating multi-month aggregation logic for:
 * 1. Client overview dashboard analytics ({@code getClientOverview}).
 * 2. Multi-month market decision ledger rollups ({@code calculateMarketDecisionAllMonths}).
 */
@Component
public class ClientOverviewAggregator {

    private static final Logger log = LoggerFactory.getLogger(ClientOverviewAggregator.class);

    /**
     * Default cumulative loss ratio fallback (12.11%) applied when consumer bus energy is absent.
     */
    private static final double DEFAULT_CONSUMER_BUS_LOSS_RATIO = 0.1211;

    private final CalculatorProperties properties;

    public ClientOverviewAggregator() {
        this(new CalculatorProperties());
    }

    @Autowired
    public ClientOverviewAggregator(CalculatorProperties properties) {
        this.properties = properties != null ? properties : new CalculatorProperties();
    }

    /**
     * Aggregates multi-month market decision results into a high-level client overview
     * financial summary suitable for executive reporting.
     */
    public Map<String, Object> aggregateClientOverview(
            SavingsCalculatorEntry entry,
            Map<String, Map<String, Object>> todConsumptions,
            Function<String, Map<String, Object>> monthlyMarketDecisionProvider) {
        return aggregateClientOverviewTyped(entry, todConsumptions,
                month -> {
                    Map<String, Object> m = monthlyMarketDecisionProvider.apply(month);
                    return m != null ? MonthlyMarketDecisionResult.fromMap(m) : null;
                });
    }

    /**
     * Strongly typed client overview aggregator accepting a typed {@link MonthlyMarketDecisionResult} provider.
     */
    public Map<String, Object> aggregateClientOverviewTyped(
            SavingsCalculatorEntry entry,
            Map<String, Map<String, Object>> todConsumptions,
            Function<String, MonthlyMarketDecisionResult> monthlyMarketDecisionProvider) {

        if (todConsumptions == null || todConsumptions.isEmpty()) {
            Map<String, Object> emptyRes = new LinkedHashMap<>();
            emptyRes.put("clientName", entry.getClientName());
            emptyRes.put("industryName", entry.getIndustryName());
            emptyRes.put("months", Collections.emptyList());
            emptyRes.put("totalSavings", 0.0);
            return emptyRes;
        }

        List<String> months = new ArrayList<>(todConsumptions.keySet());
        Collections.sort(months);

        double totalSavings = 0.0;
        List<ClientOverviewMonthRecord> monthsData = new ArrayList<>();
        AggregatedCostsAccumulator aggregatedCosts = new AggregatedCostsAccumulator();

        for (String month : months) {
            try {
                MonthlyMarketDecisionResult result = monthlyMarketDecisionProvider.apply(month);
                if (result == null) continue;
                double netSavings = result.totalSavings();
                OaDetailedResult oaDetailed = result.oaDetailed();
                OaLedgerTotals totals = oaDetailed != null ? oaDetailed.totals() : OaLedgerTotals.empty();
                double proltMarginCost = totals.proltMarginCost();
                double grossSavings = netSavings + proltMarginCost;

                aggregatedCosts.addCssCharge(totals.cssCharge());
                aggregatedCosts.addRpoCharge(totals.rpoCharge());
                aggregatedCosts.addPocCharge(totals.pocCharge());
                aggregatedCosts.addStuCharge(totals.stuCharge());
                aggregatedCosts.addDcCharge(totals.dcCharge());
                aggregatedCosts.addIexFee(totals.iexFee());
                aggregatedCosts.addTraderMarginTotal(totals.traderMargin() + totals.traderMarginGst());
                aggregatedCosts.addDailyFixedOverhead(oaDetailed != null ? oaDetailed.dailyFixedOverhead() : 0.0);
                aggregatedCosts.addBidApplicationFees(oaDetailed != null ? oaDetailed.bidApplicationFees() : 0.0);
                aggregatedCosts.addProltMarginCost(proltMarginCost);
                aggregatedCosts.addConsultancyFee(totals.consultancyFee());
                aggregatedCosts.addProbusPlatformFee(totals.probusPlatformFee());

                double totalBaselineCost = result.totalBaselineCost();
                double demandCharge = result.demandCharge();
                double electricityDuty = result.electricityDuty();
                double miscCharges = result.miscellaneousCharges();

                aggregatedCosts.addTotalDiscomCost(totalBaselineCost);
                aggregatedCosts.addDemandAndFixedCharges(demandCharge);
                aggregatedCosts.addMiscellaneousCharges(electricityDuty + miscCharges);
                aggregatedCosts.addEnergyCharges(totalBaselineCost - demandCharge - electricityDuty - miscCharges);

                double peakDemand = result.peakDemand();
                aggregatedCosts.updatePeakDemand(peakDemand);
                aggregatedCosts.setDemandChargeRate(result.demandChargeRate());

                totalSavings += netSavings;

                double totalEnergyKwh = result.totalEnergyKwh();
                double totalMarketEnergyKwh = result.totalMarketEnergyKwh();
                double consumerBusUnits = totals.consumerBusUnits() > 0
                        ? totals.consumerBusUnits()
                        : (totalMarketEnergyKwh * (1.0 - DEFAULT_CONSUMER_BUS_LOSS_RATIO));

                double traderMarginTotal = totals.traderMargin() + totals.traderMarginGst();
                double consultancyFee = totals.consultancyFee();
                double probusPlatformFee = totals.probusPlatformFee();
                double oaCoverage = totalEnergyKwh > 0 ? (consumerBusUnits / totalEnergyKwh) * 100.0 : 0.0;

                monthsData.add(ClientOverviewMonthRecord.success(
                        month,
                        netSavings,
                        grossSavings,
                        totalEnergyKwh,
                        totalMarketEnergyKwh,
                        consumerBusUnits,
                        oaCoverage,
                        proltMarginCost,
                        traderMarginTotal,
                        consultancyFee,
                        probusPlatformFee,
                        totalBaselineCost,
                        totalBaselineCost - netSavings
                ));
            } catch (Exception ex) {
                log.error("[SavingsCalculator] Error calculating market decision for month '{}' for client '{}' (id: {}): {}",
                        month, entry.getClientName(), entry.getId(), ex.getMessage(), ex);
                monthsData.add(ClientOverviewMonthRecord.failure(month, "Calculation failed"));
            }
        }

        List<Map<String, Object>> monthsDataMapList = new ArrayList<>(monthsData.size());
        for (ClientOverviewMonthRecord monthRecord : monthsData) {
            monthsDataMapList.add(monthRecord.toMap());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientName", entry.getClientName());
        response.put("industryName", entry.getIndustryName());
        response.put("months", monthsDataMapList);
        response.put("totalSavings", totalSavings);
        response.put("aggregatedCosts", aggregatedCosts.toMap());
        return response;
    }

    /**
     * Aggregates multi-month market decision results into a complete all-months
     * ledger response matching the standard calculateMarketDecision API schema.
     */
    public Map<String, Object> aggregateMarketDecisionAllMonths(
            SavingsCalculatorEntry entry,
            Map<String, Map<String, Object>> todConsumptions,
            Function<String, Map<String, Object>> monthlyMarketDecisionProvider) {
        return aggregateMarketDecisionAllMonthsTyped(entry, todConsumptions,
                month -> {
                    Map<String, Object> m = monthlyMarketDecisionProvider.apply(month);
                    return m != null ? MonthlyMarketDecisionResult.fromMap(m) : null;
                });
    }

    /**
     * Strongly typed all-months market decision ledger aggregator accepting a typed {@link MonthlyMarketDecisionResult} provider.
     */
    public Map<String, Object> aggregateMarketDecisionAllMonthsTyped(
            SavingsCalculatorEntry entry,
            Map<String, Map<String, Object>> todConsumptions,
            Function<String, MonthlyMarketDecisionResult> monthlyMarketDecisionProvider) {

        List<String> months = new ArrayList<>(todConsumptions.keySet());
        Collections.sort(months);

        double totalSavings = 0.0;
        double totalBaselineCost = 0.0;
        double totalEnergyKwh = 0.0;
        double totalMarketEnergyKwh = 0.0;
        double totalLandedExchangeCost = 0.0;
        double totalDiscomAfterProlt = 0.0;
        double demandCharge = 0.0;
        double electricityDuty = 0.0;
        double peakDemand = 0.0;
        double totalConsumerBusEnergyKwh = 0.0;
        double demandChargeRate = 0.0;

        Map<String, Object> aggregatedTotals = new LinkedHashMap<>();
        aggregatedTotals.put("cssCharge", 0.0);
        aggregatedTotals.put("cssRate", 0.0);
        aggregatedTotals.put("rpoCharge", 0.0);
        aggregatedTotals.put("pocCharge", 0.0);
        aggregatedTotals.put("stuCharge", 0.0);
        aggregatedTotals.put("dcCharge", 0.0);
        aggregatedTotals.put("iexFee", 0.0);
        aggregatedTotals.put("traderMargin", 0.0);
        aggregatedTotals.put("traderMarginGst", 0.0);
        aggregatedTotals.put("proltMarginCost", 0.0);
        aggregatedTotals.put("consultancyFee", 0.0);
        aggregatedTotals.put("probusPlatformFee", 0.0);

        double aggregatedDailyOverhead = 0.0;
        double aggregatedNldc = 0.0;
        double aggregatedSldc = 0.0;
        double aggregatedBidFees = 0.0;
        int aggregatedTotalDaysTraded = 0;

        int successfulMonths = 0;
        List<String> failedMonths = new ArrayList<>();
        Throwable firstFailure = null;

        for (String month : months) {
            try {
                MonthlyMarketDecisionResult monthResult = monthlyMarketDecisionProvider.apply(month);
                if (monthResult != null) {
                    successfulMonths++;
                    totalSavings += monthResult.totalSavings();
                    totalBaselineCost += monthResult.totalBaselineCost();
                    totalEnergyKwh += monthResult.totalEnergyKwh();
                    totalMarketEnergyKwh += monthResult.totalMarketEnergyKwh();
                    totalLandedExchangeCost += monthResult.totalLandedExchangeCost();
                    totalDiscomAfterProlt += monthResult.totalDiscomAfterProlt();
                    demandCharge += monthResult.demandCharge();
                    electricityDuty += monthResult.electricityDuty();
                    peakDemand = Math.max(peakDemand, monthResult.peakDemand());
                    totalConsumerBusEnergyKwh += monthResult.totalConsumerBusEnergyKwh() > 0
                            ? monthResult.totalConsumerBusEnergyKwh()
                            : monthResult.totalMarketEnergyKwh();
                    demandChargeRate = monthResult.demandChargeRate();

                    OaDetailedResult openAccessDetailed = monthResult.oaDetailed();
                    if (openAccessDetailed != null) {
                        OaLedgerTotals openAccessTotals = openAccessDetailed.totals();
                        if (openAccessTotals != null) {
                            CalculatorUtils.addAggregated(aggregatedTotals, "cssCharge", openAccessTotals.cssCharge());
                            CalculatorUtils.addAggregated(aggregatedTotals, "rpoCharge", openAccessTotals.rpoCharge());
                            CalculatorUtils.addAggregated(aggregatedTotals, "pocCharge", openAccessTotals.pocCharge());
                            CalculatorUtils.addAggregated(aggregatedTotals, "stuCharge", openAccessTotals.stuCharge());
                            CalculatorUtils.addAggregated(aggregatedTotals, "dcCharge", openAccessTotals.dcCharge());
                            CalculatorUtils.addAggregated(aggregatedTotals, "iexFee", openAccessTotals.iexFee());
                            CalculatorUtils.addAggregated(aggregatedTotals, "traderMargin", openAccessTotals.traderMargin());
                            CalculatorUtils.addAggregated(aggregatedTotals, "traderMarginGst", openAccessTotals.traderMarginGst());
                            CalculatorUtils.addAggregated(aggregatedTotals, "proltMarginCost", openAccessTotals.proltMarginCost());
                            CalculatorUtils.addAggregated(aggregatedTotals, "consultancyFee", openAccessTotals.consultancyFee());
                            CalculatorUtils.addAggregated(aggregatedTotals, "probusPlatformFee", openAccessTotals.probusPlatformFee());
                        }
                        double nocFee = properties != null && properties.getFees() != null
                                ? properties.getFees().getDiscomNocMonthly()
                                : OverheadFeeConstants.DEFAULT_DISCOM_NOC_FEE_MONTHLY;
                        double regFee = properties != null && properties.getFees() != null
                                ? properties.getFees().getSldcMonthly()
                                : OverheadFeeConstants.DEFAULT_SLDC_MONTHLY_FEE;
                        aggregatedTotals.put("nocFee", nocFee);
                        aggregatedTotals.put("regFee", regFee);

                        aggregatedDailyOverhead += openAccessDetailed.dailyFixedOverhead();
                        aggregatedNldc += openAccessDetailed.nldcSchedulingCost();
                        aggregatedSldc += openAccessDetailed.sldcSchedulingCost();
                        aggregatedBidFees += openAccessDetailed.bidApplicationFees();
                        aggregatedTotalDaysTraded += openAccessDetailed.totalDaysTraded();
                    }
                }
            } catch (Exception ex) {
                failedMonths.add(month);
                if (firstFailure == null) {
                    firstFailure = ex;
                }
                log.error("[SavingsCalculator] Error calculating month '{}' for client '{}' (id: {}) in aggregateMarketDecisionAllMonths: {}",
                        month, entry.getClientName(), entry.getId(), ex.getMessage(), ex);
            }
        }

        if (successfulMonths == 0 && !months.isEmpty()) {
            throw new CalculatorExecutionException(
                "Market Decision",
                String.join(", ", failedMonths),
                entry.getId(),
                "All " + months.size() + " monthly calculations failed" + (firstFailure != null ? ": " + firstFailure.getMessage() : ""),
                firstFailure
            );
        }

        if (!failedMonths.isEmpty()) {
            log.warn("[SavingsCalculator] Multi-month market decision completed with partial failures for client '{}' (id: {}). Succeeded: {}/{}, Failed months: {}",
                    entry.getClientName(), entry.getId(), successfulMonths, months.size(), failedMonths);
        }

        Map<String, Object> oaDetailedFinal = new LinkedHashMap<>();
        oaDetailedFinal.put("breakdown", Collections.emptyList());
        oaDetailedFinal.put("dailyFixedOverhead", aggregatedDailyOverhead);
        oaDetailedFinal.put("nldcSchedulingCost", aggregatedNldc);
        oaDetailedFinal.put("sldcSchedulingCost", aggregatedSldc);
        oaDetailedFinal.put("bidApplicationFees", aggregatedBidFees);
        oaDetailedFinal.put("totalDaysTraded", aggregatedTotalDaysTraded);
        oaDetailedFinal.put("totals", aggregatedTotals);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientId", entry.getId());
        response.put("clientName", entry.getClientName());
        response.put("slotsData", Collections.emptyList());
        response.put("marketDecision", Collections.emptyList());
        response.put("totalEnergyKwh", totalEnergyKwh);
        response.put("totalMarketEnergyKwh", totalMarketEnergyKwh);
        response.put("totalConsumerBusEnergyKwh", totalConsumerBusEnergyKwh);
        response.put("totalBaselineCost", totalBaselineCost);
        response.put("totalLandedExchangeCost", totalLandedExchangeCost);
        response.put("totalDiscomAfterProlt", totalDiscomAfterProlt);
        response.put("totalSavings", totalSavings);
        response.put("demandCharge", demandCharge);
        response.put("electricityDuty", electricityDuty);
        response.put("arrearAmount", entry.getArrearAmount() != null ? entry.getArrearAmount().doubleValue() : 0.0);
        response.put("currentLpsc", entry.getCurrentLpsc() != null ? entry.getCurrentLpsc().doubleValue() : 0.0);
        response.put("discom", entry.getDiscom());
        response.put("peakDemand", peakDemand);
        double defaultPf = properties != null && properties.getRegulatory() != null
                ? properties.getRegulatory().getDefaultPowerFactor()
                : RegulatoryConstants.DEFAULT_POWER_FACTOR;
        response.put("powerFactor", entry.getPowerFactor() != null ? entry.getPowerFactor().doubleValue() : defaultPf);
        response.put("demandChargeRate", demandChargeRate);
        response.put("todSummaries", Collections.emptyList());
        response.put("oaDetailed", oaDetailedFinal);

        return response;
    }
}

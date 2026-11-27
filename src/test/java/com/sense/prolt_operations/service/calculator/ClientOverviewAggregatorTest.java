package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientOverviewAggregator Unit Tests")
class ClientOverviewAggregatorTest {

    private final ClientOverviewAggregator aggregator = new ClientOverviewAggregator();

    private SavingsCalculatorEntry createTestEntry() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("entry-100");
        entry.setClientName("Apex Manufacturing");
        entry.setIndustryName("Automotive");
        entry.setSanctionedLoadKw(BigDecimal.valueOf(1500.0));
        entry.setPowerFactor(BigDecimal.valueOf(0.98));
        entry.setDiscom("NPCL");
        return entry;
    }

    @Test
    @DisplayName("Should return empty months list and zero savings when consumption is empty")
    void testEmptyConsumption() {
        SavingsCalculatorEntry entry = createTestEntry();
        Map<String, Object> result = aggregator.aggregateClientOverview(entry, Collections.emptyMap(), m -> Collections.emptyMap());

        assertThat(result.get("clientName")).isEqualTo("Apex Manufacturing");
        assertThat(result.get("industryName")).isEqualTo("Automotive");
        assertThat((List<?>) result.get("months")).isEmpty();
        assertThat(result.get("totalSavings")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should aggregate multi-month client overview with full financial breakdown")
    void testAggregateClientOverviewSuccess() {
        SavingsCalculatorEntry entry = createTestEntry();

        Map<String, Map<String, Object>> todConsumptions = new LinkedHashMap<>();
        todConsumptions.put("2026-05", Map.of("NORMAL", 50000.0));
        todConsumptions.put("2026-06", Map.of("NORMAL", 60000.0));

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("cssCharge", 5000.0);
        totals.put("rpoCharge", 1000.0);
        totals.put("pocCharge", 2000.0);
        totals.put("stuCharge", 3000.0);
        totals.put("dcCharge", 1500.0);
        totals.put("iexFee", 200.0);
        totals.put("traderMargin", 800.0);
        totals.put("traderMarginGst", 144.0);
        totals.put("proltMarginCost", 10000.0);
        totals.put("consultancyFee", 500.0);
        totals.put("probusPlatformFee", 250.0);
        totals.put("consumerBusUnits", 40000.0);

        Map<String, Object> oaDetailed = new LinkedHashMap<>();
        oaDetailed.put("totals", totals);
        oaDetailed.put("dailyFixedOverhead", 1000.0);
        oaDetailed.put("bidApplicationFees", 500.0);

        Map<String, Object> monthResult = new LinkedHashMap<>();
        monthResult.put("totalSavings", 50000.0);
        monthResult.put("oaDetailed", oaDetailed);
        monthResult.put("totalBaselineCost", 200000.0);
        monthResult.put("demandCharge", 30000.0);
        monthResult.put("electricityDuty", 15000.0);
        monthResult.put("miscellaneousCharges", 5000.0);
        monthResult.put("peakDemand", 1200.0);
        monthResult.put("demandChargeRate", 250.0);
        monthResult.put("totalEnergyKwh", 50000.0);
        monthResult.put("totalMarketEnergyKwh", 45000.0);

        Map<String, Object> overview = aggregator.aggregateClientOverview(
                entry,
                todConsumptions,
                m -> monthResult
        );

        assertThat(overview.get("clientName")).isEqualTo("Apex Manufacturing");
        assertThat(overview.get("totalSavings")).isEqualTo(100000.0);

        List<?> months = (List<?>) overview.get("months");
        assertThat(months).hasSize(2);

        Map<?, ?> month1 = (Map<?, ?>) months.get(0);
        assertThat(month1.get("month")).isEqualTo("2026-05");
        assertThat(month1.get("savings")).isEqualTo(50000.0);
        assertThat(month1.get("grossSavings")).isEqualTo(60000.0); // 50000 + 10000 prolt margin
        assertThat(month1.get("oaCoverage")).isEqualTo(80.0); // 40000 / 50000 * 100

        Map<?, ?> aggCosts = (Map<?, ?>) overview.get("aggregatedCosts");
        assertThat(aggCosts).isNotNull();
        assertThat(aggCosts.get("cssCharge")).isEqualTo(10000.0);
        assertThat(aggCosts.get("proltMarginCost")).isEqualTo(20000.0);
        assertThat(aggCosts.get("peakDemand")).isEqualTo(1200.0);
    }

    @Test
    @DisplayName("Should capture calculation failures gracefully without interrupting remaining months")
    void testAggregateClientOverviewFailureHandling() {
        SavingsCalculatorEntry entry = createTestEntry();

        Map<String, Map<String, Object>> todConsumptions = new LinkedHashMap<>();
        todConsumptions.put("2026-05", Map.of("NORMAL", 50000.0));
        todConsumptions.put("2026-06", Map.of("NORMAL", 60000.0));

        Map<String, Object> successResult = Map.of(
                "totalSavings", 40000.0,
                "oaDetailed", Map.of("totals", Collections.emptyMap()),
                "totalBaselineCost", 150000.0,
                "demandCharge", 20000.0,
                "electricityDuty", 10000.0,
                "miscellaneousCharges", 0.0,
                "peakDemand", 1000.0,
                "demandChargeRate", 250.0,
                "totalEnergyKwh", 50000.0,
                "totalMarketEnergyKwh", 40000.0
        );

        Map<String, Object> overview = aggregator.aggregateClientOverview(
                entry,
                todConsumptions,
                m -> {
                    if ("2026-06".equals(m)) throw new RuntimeException("Simulated calculation error");
                    return successResult;
                }
        );

        List<?> months = (List<?>) overview.get("months");
        assertThat(months).hasSize(2);

        Map<?, ?> month1 = (Map<?, ?>) months.get(0);
        assertThat(month1.get("error")).isNull();
        assertThat(month1.get("savings")).isEqualTo(40000.0);

        Map<?, ?> month2 = (Map<?, ?>) months.get(1);
        assertThat(month2.get("error")).isEqualTo("Calculation failed");
    }

    @Test
    @DisplayName("Should aggregate market decision all months accurately")
    void testAggregateMarketDecisionAllMonths() {
        SavingsCalculatorEntry entry = createTestEntry();

        Map<String, Map<String, Object>> todConsumptions = new LinkedHashMap<>();
        todConsumptions.put("2026-05", Map.of("NORMAL", 50000.0));
        todConsumptions.put("2026-06", Map.of("NORMAL", 60000.0));

        Map<String, Object> res1 = new LinkedHashMap<>();
        res1.put("totalSavings", 30000.0);
        res1.put("totalBaselineCost", 100000.0);
        res1.put("totalEnergyKwh", 50000.0);
        res1.put("totalMarketEnergyKwh", 40000.0);
        res1.put("totalLandedExchangeCost", 60000.0);
        res1.put("totalDiscomAfterProlt", 10000.0);
        res1.put("demandCharge", 15000.0);
        res1.put("electricityDuty", 8000.0);
        res1.put("peakDemand", 1100.0);
        res1.put("demandChargeRate", 250.0);
        res1.put("oaDetailed", Map.of("totals", Map.of("cssCharge", 2000.0), "dailyFixedOverhead", 500.0, "nldcSchedulingCost", 100.0, "sldcSchedulingCost", 200.0, "bidApplicationFees", 50.0, "totalDaysTraded", 20.0));

        Map<String, Object> res2 = new LinkedHashMap<>();
        res2.put("totalSavings", 35000.0);
        res2.put("totalBaselineCost", 120000.0);
        res2.put("totalEnergyKwh", 60000.0);
        res2.put("totalMarketEnergyKwh", 48000.0);
        res2.put("totalLandedExchangeCost", 70000.0);
        res2.put("totalDiscomAfterProlt", 15000.0);
        res2.put("demandCharge", 15000.0);
        res2.put("electricityDuty", 9000.0);
        res2.put("peakDemand", 1250.0);
        res2.put("demandChargeRate", 250.0);
        res2.put("oaDetailed", Map.of("totals", Map.of("cssCharge", 2500.0), "dailyFixedOverhead", 500.0, "nldcSchedulingCost", 100.0, "sldcSchedulingCost", 200.0, "bidApplicationFees", 50.0, "totalDaysTraded", 22.0));

        Map<String, Object> allMonthsRes = aggregator.aggregateMarketDecisionAllMonths(
                entry,
                todConsumptions,
                m -> "2026-05".equals(m) ? res1 : res2
        );

        assertThat(allMonthsRes.get("totalSavings")).isEqualTo(65000.0);
        assertThat(allMonthsRes.get("totalBaselineCost")).isEqualTo(220000.0);
        assertThat(allMonthsRes.get("totalEnergyKwh")).isEqualTo(110000.0);
        assertThat(allMonthsRes.get("peakDemand")).isEqualTo(1250.0);

        Map<?, ?> oaDet = (Map<?, ?>) allMonthsRes.get("oaDetailed");
        assertThat(oaDet.get("dailyFixedOverhead")).isEqualTo(1000.0);
        assertThat(oaDet.get("totalDaysTraded")).isEqualTo(42);
    }
}

package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DemandShiftOptimizer Unit Tests")
class DemandShiftOptimizerTest {

    private final DemandShiftOptimizer optimizer = new DemandShiftOptimizer();

    private SavingsCalculatorEntry createTestEntry(double sanctionedLoadKw) {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("entry-1");
        entry.setClientName("Test Client");
        entry.setSanctionedLoadKw(BigDecimal.valueOf(sanctionedLoadKw));
        entry.setPowerFactor(BigDecimal.valueOf(0.95));
        entry.setStateCode("UP");
        entry.setDiscom("NPCL");
        return entry;
    }

    @Test
    @DisplayName("Should handle empty market results gracefully")
    void testEmptyMarketResult() {
        SavingsCalculatorEntry entry = createTestEntry(100.0);
        Map<String, Object> result = optimizer.calculateDemandShiftInsights(entry, "entry-1", Collections.emptyMap());

        assertThat(result).isNotNull();
        assertThat(result.get("clientId")).isEqualTo("entry-1");
        assertThat(result.get("shiftedEnergy")).isEqualTo(0.0);
        assertThat(result.get("savingsAchieved")).isEqualTo(0.0);
        assertThat((List<?>) result.get("slotsData")).isEmpty();
    }

    @Test
    @DisplayName("Should shift energy from expensive slots to cheap slots when spread > 1.50 Rs")
    void testDemandShiftExecution() {
        SavingsCalculatorEntry entry = createTestEntry(1000.0); // allows headroom

        List<Map<String, Object>> slots = new ArrayList<>();

        // Expensive slot 1 (DISCOM at 10.0 Rs/kWh, consumption 100 kWh, headroom 0)
        Map<String, Object> expensiveSlot = new LinkedHashMap<>();
        expensiveSlot.put("date", "2026-06-01");
        expensiveSlot.put("timeblock", 1.0);
        expensiveSlot.put("tod", "PEAK");
        expensiveSlot.put("shouldBuyFromMarket", false);
        expensiveSlot.put("discomLanding", 10.0);
        expensiveSlot.put("marketEnergy", 0.0);
        expensiveSlot.put("consumedMarketEnergy", 0.0);
        expensiveSlot.put("discomEnergy", 100.0);
        slots.add(expensiveSlot);

        // Cheap slot 2 (Market at 4.0 Rs/kWh, orig market energy 10 kWh, headroom > 0)
        Map<String, Object> cheapSlot = new LinkedHashMap<>();
        cheapSlot.put("date", "2026-06-01");
        cheapSlot.put("timeblock", 2.0);
        cheapSlot.put("tod", "OFFPEAK");
        cheapSlot.put("shouldBuyFromMarket", true);
        cheapSlot.put("bestMarketLanding", 4.0);
        cheapSlot.put("marketEnergy", 10.0);
        cheapSlot.put("consumedMarketEnergy", 9.0);
        cheapSlot.put("discomEnergy", 0.0);
        slots.add(cheapSlot);

        Map<String, Object> marketResult = Map.of("slotsData", slots);

        Map<String, Object> result = optimizer.calculateDemandShiftInsights(entry, "entry-1", marketResult);

        assertThat(result).isNotNull();
        double shiftedEnergy = (double) result.get("shiftedEnergy");
        double savingsAchieved = (double) result.get("savingsAchieved");

        assertThat(shiftedEnergy).isGreaterThan(0.0);
        // Spread is 10.0 - 4.0 = 6.0 Rs/kWh
        assertThat(savingsAchieved).isEqualTo(shiftedEnergy * 6.0);

        List<?> todSummary = (List<?>) result.get("todShiftSummary");
        assertThat(todSummary).isNotEmpty();
    }

    @Test
    @DisplayName("Should not shift energy when spread <= 1.50 Rs/kWh threshold")
    void testNoShiftWhenSpreadBelowThreshold() {
        SavingsCalculatorEntry entry = createTestEntry(1000.0);

        List<Map<String, Object>> slots = new ArrayList<>();

        // Slot 1 (5.005 Rs/kWh)
        Map<String, Object> s1 = new LinkedHashMap<>();
        s1.put("date", "2026-06-01");
        s1.put("timeblock", 1.0);
        s1.put("tod", "NORMAL");
        s1.put("shouldBuyFromMarket", false);
        s1.put("discomLanding", 5.005);
        s1.put("marketEnergy", 0.0);
        s1.put("consumedMarketEnergy", 0.0);
        s1.put("discomEnergy", 100.0);
        slots.add(s1);

        // Slot 2 (5.000 Rs/kWh) -> spread is 0.005 <= 0.01 threshold
        Map<String, Object> s2 = new LinkedHashMap<>();
        s2.put("date", "2026-06-01");
        s2.put("timeblock", 2.0);
        s2.put("tod", "OFFPEAK");
        s2.put("shouldBuyFromMarket", true);
        s2.put("bestMarketLanding", 5.000);
        s2.put("marketEnergy", 10.0);
        s2.put("consumedMarketEnergy", 9.0);
        s2.put("discomEnergy", 0.0);
        slots.add(s2);

        Map<String, Object> marketResult = Map.of("slotsData", slots);

        Map<String, Object> result = optimizer.calculateDemandShiftInsights(entry, "entry-1", marketResult);

        assertThat(result.get("shiftedEnergy")).isEqualTo(0.0);
        assertThat(result.get("savingsAchieved")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should aggregate multi-month demand shift insights correctly")
    void testCalculateDemandShiftInsightsAllMonths() {
        SavingsCalculatorEntry entry = createTestEntry(500.0);

        Map<String, Map<String, Object>> todConsumptions = new LinkedHashMap<>();
        todConsumptions.put("2026-05", Map.of("NORMAL", 1000.0));
        todConsumptions.put("2026-06", Map.of("NORMAL", 1200.0));

        Map<String, Object> month1 = new LinkedHashMap<>();
        month1.put("originalTotalCost", 10000.0);
        month1.put("newTotalCost", 9000.0);
        month1.put("savingsAchieved", 1000.0);
        month1.put("shiftedEnergy", 200.0);
        month1.put("todShiftSummary", List.of(Map.of("tod", "NORMAL", "originalEnergy", 1000.0, "newEnergy", 800.0, "diff", -200.0, "originalMarketEnergy", 0.0, "newMarketEnergy", 0.0)));

        Map<String, Object> month2 = new LinkedHashMap<>();
        month2.put("originalTotalCost", 12000.0);
        month2.put("newTotalCost", 10500.0);
        month2.put("savingsAchieved", 1500.0);
        month2.put("shiftedEnergy", 300.0);
        month2.put("todShiftSummary", List.of(Map.of("tod", "NORMAL", "originalEnergy", 1200.0, "newEnergy", 900.0, "diff", -300.0, "originalMarketEnergy", 0.0, "newMarketEnergy", 0.0)));

        Map<String, Object> aggregated = optimizer.calculateDemandShiftInsightsAllMonths(
                entry,
                "entry-1",
                todConsumptions,
                month -> "2026-05".equals(month) ? month1 : month2
        );

        assertThat(aggregated.get("originalTotalCost")).isEqualTo(22000.0);
        assertThat(aggregated.get("newTotalCost")).isEqualTo(19500.0);
        assertThat(aggregated.get("savingsAchieved")).isEqualTo(2500.0);
        assertThat(aggregated.get("shiftedEnergy")).isEqualTo(500.0);
    }
}

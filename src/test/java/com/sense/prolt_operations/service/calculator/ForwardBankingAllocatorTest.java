package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.service.calculator.model.MarketDecisionContext;
import com.sense.prolt_operations.service.calculator.model.MarketSlotCalculation;
import com.sense.prolt_operations.service.calculator.model.ParsedCategory;
import com.sense.prolt_operations.service.calculator.model.SimulationDateRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ForwardBankingAllocator Unit Tests")
class ForwardBankingAllocatorTest {

    private final ForwardBankingAllocator allocator = new ForwardBankingAllocator();

    @Test
    @DisplayName("Allocate should handle empty or null slots safely")
    void testNullOrEmpty() {
        allocator.allocate(null, null);
        allocator.allocate(new ArrayList<>(), null);
    }

    @Test
    @DisplayName("Allocate should consume energy in 250 kWh increments in forward order")
    void testForwardAllocation() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setSanctionedLoadKw(new BigDecimal("1000")); // maxPerSlot = 250 kWh
        entry.setPowerFactor(new BigDecimal("1.0"));

        Map<String, Object> consumptions = new HashMap<>();
        consumptions.put("NORMAL", 500.0); // 500 kWh across 2 slots = 250 kWh/slot

        SimulationDateRange dateRange = new SimulationDateRange(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1),
                2026, 6, 202607, 202606, 202604, List.of(202606)
        );

        MarketDecisionContext ctx = new MarketDecisionContext(
                entry, "client-1", "2026-06", "2026-06",
                consumptions, dateRange, "UP",
                new ParsedCategory("Industrial", null), "11 kV", List.of("UP"),
                0.02, 0.0036, 1000.0, 250.0,
                true, 7.5, 0.0, 0.0, 0.0,
                0.40, 0.20, 0.50, 1.0, 0.5,
                2.0, 4.0, 300.0, 1000.0, 1000.0,
                0.0, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                false, false, null
        );

        List<MarketSlotCalculation> slots = new ArrayList<>();

        MarketSlotCalculation s0 = new MarketSlotCalculation();
        s0.tod = "NORMAL";
        s0.date = "2026-06-01";
        s0.timeblock = 1;
        s0.shouldBuyFromMarket = true;
        s0.bestMarketLanding = 4.0;
        s0.marketSource = "DAM";
        s0.damMcp = 3.5;
        s0.discomLanding = 8.0;
        s0.istsLoss = 3.5;

        MarketSlotCalculation s1 = new MarketSlotCalculation();
        s1.tod = "NORMAL";
        s1.date = "2026-06-01";
        s1.timeblock = 2;
        s1.shouldBuyFromMarket = false;
        s1.bestMarketLanding = 0.0;
        s1.discomLanding = 8.0;
        s1.istsLoss = 3.5;

        slots.add(s0);
        slots.add(s1);

        allocator.allocate(slots, ctx);

        // s0 bought market energy
        assertThat(s0.marketEnergy).isGreaterThan(0.0);
        assertThat(s0.consumedMarketEnergy).isGreaterThan(0.0);
        // s0 + s1 discom energy
        assertThat(s0.discomEnergy + s1.discomEnergy).isLessThan(500.0);
    }
}

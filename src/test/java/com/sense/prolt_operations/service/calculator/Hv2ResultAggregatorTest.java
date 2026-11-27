package com.sense.prolt_operations.service.calculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.service.calculator.model.EnergyMarketType;
import com.sense.prolt_operations.service.calculator.model.EnergySource;
import com.sense.prolt_operations.service.calculator.model.Hv2CalculationContext;
import com.sense.prolt_operations.service.calculator.model.Hv2SlotRecord;

class Hv2ResultAggregatorTest {

    private Hv2ResultAggregator aggregator;
    private Hv2CalculationContext ctx;

    @BeforeEach
    void setUp() {
        aggregator = new Hv2ResultAggregator();

        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("entry-agg-1");
        entry.setClientName("Test Factory Ltd");
        entry.setSanctionedLoadKw(BigDecimal.valueOf(1200.0));
        entry.setConsumerCategory("HV-2");
        entry.setDiscom("NPCL");
        entry.setVoltageLevel("11 kV");
        entry.setStateCode("UP");
        entry.setTodConsumptions("{\"2024-05\":{\"NORMAL\":10000}}");

        ctx = Hv2CalculationContext.build(entry, "2024-05", null, new ObjectMapper());
    }

    private Hv2SlotRecord createAllocatedSlot(int slot, String todSlab, double energy,
            double lowestPrice, double discomPrice, String source) {
        Hv2SlotRecord initial = Hv2SlotRecord.initial(
                "2024-05-01",
                slot,
                "slot-" + slot,
                todSlab,
                lowestPrice,
                lowestPrice,
                lowestPrice,
                discomPrice,
                lowestPrice,
                source,
                0.03,
                0.02,
                0.01
        );
        return initial.withEnergyAndCosts(energy, energy * lowestPrice, energy * discomPrice);
    }

    @Test
    @DisplayName("Should aggregate ledger totals, market energy, and savings correctly")
    void shouldAggregateLedgerTotalsAndSavingsCorrectly() {
        List<Hv2SlotRecord> slots = new ArrayList<>();
        // Slot 1: GDAM, energy 100, lowestPrice 5.0 (cost 500), discomPrice 7.0 (cost 700)
        slots.add(createAllocatedSlot(1, "NORMAL", 100.0, 5.0, 7.0, EnergyMarketType.GDAM.getValue()));
        // Slot 2: DISCOM, energy 50, lowestPrice 7.0 (cost 350), discomPrice 7.0 (cost 350)
        slots.add(createAllocatedSlot(2, "NORMAL", 50.0, 7.0, 7.0, EnergySource.DISCOM.getValue()));
        // Slot 3: DAM, energy 150, lowestPrice 4.0 (cost 600), discomPrice 7.0 (cost 1050)
        slots.add(createAllocatedSlot(3, "PEAK", 150.0, 4.0, 7.0, EnergyMarketType.DAM.getValue()));

        Map<String, Object> response = aggregator.aggregateResults(ctx, slots);

        // Baseline cost: 700 + 350 + 1050 = 2100.0
        assertThat((Double) response.get("totalBaselineCost")).isEqualTo(2100.0);
        // Optimized cost: 500 + 350 + 600 = 1450.0
        assertThat((Double) response.get("totalOptimizedCost")).isEqualTo(1450.0);
        // Savings: 2100 - 1450 = 650.0
        assertThat((Double) response.get("totalSavings")).isEqualTo(650.0);

        // Total energy: 100 + 50 + 150 = 300.0
        assertThat((Double) response.get("totalEnergyKwh")).isEqualTo(300.0);
        assertThat((Double) response.get("totalConsumerBusEnergyKwh")).isEqualTo(300.0);
        // Market energy: GDAM(100) + DAM(150) = 250.0 (DISCOM 50 excluded)
        assertThat((Double) response.get("totalMarketEnergyKwh")).isEqualTo(250.0);
    }

    @Test
    @DisplayName("Should sort slots within ToD groups and in monthly list by comparedLowestPrice ascending")
    @SuppressWarnings("unchecked")
    void shouldSortTodGroupsAndMonthlyListByPriceAscending() {
        List<Hv2SlotRecord> slots = new ArrayList<>();
        slots.add(createAllocatedSlot(1, "NORMAL", 100.0, 6.0, 7.0, "GDAM"));
        slots.add(createAllocatedSlot(2, "NORMAL", 100.0, 3.5, 7.0, "GDAM"));
        slots.add(createAllocatedSlot(3, "NORMAL", 100.0, 5.0, 7.0, "GDAM"));

        Map<String, Object> response = aggregator.aggregateResults(ctx, slots);

        // Check ToD groups sorting
        Map<String, List<Map<String, Object>>> todGroups =
                (Map<String, List<Map<String, Object>>>) response.get("todGroups");
        assertThat(todGroups).containsKey("NORMAL");
        List<Map<String, Object>> normalSlots = todGroups.get("NORMAL");
        assertThat(normalSlots).hasSize(3);
        assertThat(CalculatorUtils.getDouble(normalSlots.get(0), "comparedLowestPrice")).isEqualTo(3.5);
        assertThat(CalculatorUtils.getDouble(normalSlots.get(1), "comparedLowestPrice")).isEqualTo(5.0);
        assertThat(CalculatorUtils.getDouble(normalSlots.get(2), "comparedLowestPrice")).isEqualTo(6.0);

        // Check monthly list sorting
        List<Map<String, Object>> monthlyList = (List<Map<String, Object>>) response.get("sortedMonthlyList");
        assertThat(monthlyList).hasSize(3);
        assertThat(CalculatorUtils.getDouble(monthlyList.get(0), "comparedLowestPrice")).isEqualTo(3.5);
        assertThat(CalculatorUtils.getDouble(monthlyList.get(1), "comparedLowestPrice")).isEqualTo(5.0);
        assertThat(CalculatorUtils.getDouble(monthlyList.get(2), "comparedLowestPrice")).isEqualTo(6.0);
    }

    @Test
    @DisplayName("Should include all expected response map keys matching existing contract")
    void shouldIncludeAllExpectedResponseMapKeys() {
        List<Hv2SlotRecord> slots = List.of(
                createAllocatedSlot(1, "NORMAL", 100.0, 5.0, 7.0, "GDAM")
        );

        Map<String, Object> response = aggregator.aggregateResults(ctx, slots);

        assertThat(response).containsKeys(
                "clientId",
                "clientName",
                "sanctionedLoad",
                "maxEnergyPerSlot",
                "totalEnergyKwh",
                "totalMarketEnergyKwh",
                "totalConsumerBusEnergyKwh",
                "totalBaselineCost",
                "totalOptimizedCost",
                "totalSavings",
                "todGroups",
                "sortedMonthlyList"
        );
        assertThat(response.get("clientId")).isEqualTo("entry-agg-1");
        assertThat(response.get("clientName")).isEqualTo("Test Factory Ltd");
        assertThat(response.get("sanctionedLoad")).isEqualTo(1200.0);
        assertThat(response.get("maxEnergyPerSlot")).isEqualTo(ctx.maxEnergyPerSlot());
    }
}

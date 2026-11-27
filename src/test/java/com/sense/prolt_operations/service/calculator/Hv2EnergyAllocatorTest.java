package com.sense.prolt_operations.service.calculator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sense.prolt_operations.service.calculator.model.DemandShiftInsights;
import com.sense.prolt_operations.service.calculator.model.Hv2SlotRecord;
import com.sense.prolt_operations.service.calculator.model.TodShiftSummaryItem;

class Hv2EnergyAllocatorTest {

    private Hv2EnergyAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = new Hv2EnergyAllocator();
    }

    private Hv2SlotRecord createMockSlot(int slot, String todSlab, double lowestPrice, double discomPrice) {
        return Hv2SlotRecord.initial(
                "2024-05-01",
                slot,
                "00:00 - 00:15",
                todSlab,
                4.5,
                4.6,
                4.4,
                discomPrice,
                lowestPrice,
                "GDAM",
                0.03,
                0.02,
                0.01
        );
    }

    @Test
    @DisplayName("Should allocate energy evenly across slots matching ToD slab directly")
    void shouldAllocateEnergyAcrossSlotsMatchingTodSlabDirectly() {
        List<Hv2SlotRecord> slots = new ArrayList<>();
        slots.add(createMockSlot(1, "NORMAL", 5.0, 7.0));
        slots.add(createMockSlot(2, "NORMAL", 5.5, 7.0));
        slots.add(createMockSlot(3, "PEAK", 6.0, 8.5));
        slots.add(createMockSlot(4, "PEAK", 6.5, 8.5));

        Map<String, Object> monthConsumptions = Map.of(
                "NORMAL", 200.0,
                "PEAK", 400.0
        );

        allocator.allocateEnergy(slots, monthConsumptions, null);

        // NORMAL slots: 200 total / 2 slots = 100 per slot
        assertThat(slots.get(0).maxEnergyPerSlot()).isEqualTo(100.0);
        assertThat(slots.get(0).optimizedCost()).isEqualTo(500.0); // 100 * 5.0
        assertThat(slots.get(0).baselineCost()).isEqualTo(700.0);  // 100 * 7.0

        assertThat(slots.get(1).maxEnergyPerSlot()).isEqualTo(100.0);
        assertThat(slots.get(1).optimizedCost()).isEqualTo(550.0); // 100 * 5.5
        assertThat(slots.get(1).baselineCost()).isEqualTo(700.0);  // 100 * 7.0

        // PEAK slots: 400 total / 2 slots = 200 per slot
        assertThat(slots.get(2).maxEnergyPerSlot()).isEqualTo(200.0);
        assertThat(slots.get(2).optimizedCost()).isEqualTo(1200.0); // 200 * 6.0
        assertThat(slots.get(2).baselineCost()).isEqualTo(1700.0);  // 200 * 8.5

        assertThat(slots.get(3).maxEnergyPerSlot()).isEqualTo(200.0);
        assertThat(slots.get(3).optimizedCost()).isEqualTo(1300.0); // 200 * 6.5
        assertThat(slots.get(3).baselineCost()).isEqualTo(1700.0);  // 200 * 8.5
    }

    @Test
    @DisplayName("Should prioritize DemandShiftInsights when present")
    void shouldPrioritizeDemandShiftInsightsWhenPresent() {
        List<Hv2SlotRecord> slots = new ArrayList<>();
        slots.add(createMockSlot(1, "NORMAL", 5.0, 7.0));
        slots.add(createMockSlot(2, "NORMAL", 5.0, 7.0));

        TodShiftSummaryItem shiftItem = new TodShiftSummaryItem("NORMAL", 1000.0, 800.0, -200.0, 500.0, 400.0);
        DemandShiftInsights insights = new DemandShiftInsights(
                "client-1", "Client One", 1000.0, 250.0, 7000.0, 5600.0, 1400.0,
                200.0, 1400.0, 0.5, "10:00-14:00",
                Collections.emptyList(), Collections.emptyList(),
                List.of(shiftItem), Collections.emptyList()
        );

        Map<String, Object> monthConsumptions = Map.of("NORMAL", 200.0); // Should be ignored in favor of insights

        allocator.allocateEnergy(slots, monthConsumptions, insights);

        // 800.0 total / 2 slots = 400.0 per slot
        assertThat(slots.get(0).maxEnergyPerSlot()).isEqualTo(400.0);
        assertThat(slots.get(1).maxEnergyPerSlot()).isEqualTo(400.0);
    }

    @Test
    @DisplayName("Should distribute FLAT or TOTAL energy proportionally based on slot count")
    void shouldDistributeFlatOrTotalEnergyProportionally() {
        List<Hv2SlotRecord> slots = new ArrayList<>();
        slots.add(createMockSlot(1, "OFF_PEAK", 4.0, 6.0));
        slots.add(createMockSlot(2, "NORMAL", 5.0, 7.0));
        slots.add(createMockSlot(3, "NORMAL", 5.0, 7.0));
        slots.add(createMockSlot(4, "NORMAL", 5.0, 7.0));

        Map<String, Object> monthConsumptions = Map.of("FLAT", 400.0);

        allocator.allocateEnergy(slots, monthConsumptions, null);

        // 1 OFF_PEAK slot out of 4 total -> 400 * (1/4) = 100 total / 1 slot = 100.0
        assertThat(slots.get(0).maxEnergyPerSlot()).isEqualTo(100.0);

        // 3 NORMAL slots out of 4 total -> 400 * (3/4) = 300 total / 3 slots = 100.0 each
        assertThat(slots.get(1).maxEnergyPerSlot()).isEqualTo(100.0);
        assertThat(slots.get(2).maxEnergyPerSlot()).isEqualTo(100.0);
        assertThat(slots.get(3).maxEnergyPerSlot()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Should fallback to sum of numeric non-metadata entries when no ToD or FLAT key exists")
    void shouldFallbackToSumOfNumericNonMetadataEntries() {
        List<Hv2SlotRecord> slots = new ArrayList<>();
        slots.add(createMockSlot(1, "NORMAL", 5.0, 7.0));
        slots.add(createMockSlot(2, "NORMAL", 5.0, 7.0));

        Map<String, Object> monthConsumptions = Map.of(
                "Energy_A", 300.0,
                "Energy_B", "100.0",
                "Power Factor", 0.95, // metadata ignored
                "peak demand (kva)", 500.0, // metadata ignored
                "_rawkvah", 12345.0 // underscore ignored
        );

        allocator.allocateEnergy(slots, monthConsumptions, null);

        // Sum = 300 + 100 = 400 total / 2 slots = 200 per slot
        assertThat(slots.get(0).maxEnergyPerSlot()).isEqualTo(200.0);
        assertThat(slots.get(1).maxEnergyPerSlot()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("Should handle empty and null collections gracefully")
    void shouldHandleEmptyAndNullGracefully() {
        // Null slots list
        allocator.allocateEnergy(null, Map.of("NORMAL", 100.0), null);

        // Empty slots list
        List<Hv2SlotRecord> emptySlots = new ArrayList<>();
        allocator.allocateEnergy(emptySlots, Map.of("NORMAL", 100.0), null);
        assertThat(emptySlots).isEmpty();

        // Null monthConsumptions
        List<Hv2SlotRecord> slots = new ArrayList<>();
        slots.add(createMockSlot(1, "NORMAL", 5.0, 7.0));
        allocator.allocateEnergy(slots, null, null);
        assertThat(slots.get(0).maxEnergyPerSlot()).isEqualTo(0.0);
    }
}

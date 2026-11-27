package com.sense.prolt_operations.service.calculator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("V18L.2 - DemandShiftInsights & Shift Sub-Models Unit Tests")
class DemandShiftInsightsTest {

    @Test
    @DisplayName("TodShiftSummaryItem construction, getters, toMap, and fromMap parity")
    void testTodShiftSummaryItem() {
        TodShiftSummaryItem item = new TodShiftSummaryItem(
                "TOD1", 10000.0, 12000.0, 2000.0, 5000.0, 6000.0
        );

        assertEquals("TOD1", item.tod());
        assertEquals(10000.0, item.originalEnergy());
        assertEquals(12000.0, item.newEnergy());
        assertEquals(2000.0, item.diff());
        assertEquals(5000.0, item.originalMarketEnergy());
        assertEquals(6000.0, item.newMarketEnergy());

        Map<String, Object> map = item.toMap();
        assertEquals("TOD1", map.get("tod"));
        assertEquals(10000.0, map.get("originalEnergy"));
        assertEquals(12000.0, map.get("newEnergy"));
        assertEquals(2000.0, map.get("diff"));
        assertEquals(5000.0, map.get("originalMarketEnergy"));
        assertEquals(6000.0, map.get("newMarketEnergy"));

        TodShiftSummaryItem deserialized = TodShiftSummaryItem.fromMap(map);
        assertEquals(item, deserialized);

        // Null map fallback
        TodShiftSummaryItem fromNull = TodShiftSummaryItem.fromMap(null);
        assertEquals("", fromNull.tod());
        assertEquals(0.0, fromNull.originalEnergy());
    }

    @Test
    @DisplayName("ShiftedSlotItem construction, getters, toMap, and fromMap parity")
    void testShiftedSlotItem() {
        ShiftedSlotItem slot = new ShiftedSlotItem(
                "2026-06-19",
                12,
                "TOD2",
                250.0,
                300.0,
                4.25,
                "GDAM",
                true,
                280.0,
                20.0,
                4.10,
                4.20,
                3.95
        );

        assertEquals("2026-06-19", slot.date());
        assertEquals(12, slot.timeblock());
        assertEquals("TOD2", slot.tod());
        assertEquals(250.0, slot.originalEnergy());
        assertEquals(300.0, slot.newEnergy());
        assertEquals(4.25, slot.costPerKwh());
        assertEquals("GDAM", slot.marketSource());
        assertTrue(slot.shouldBuyFromMarket());
        assertEquals(280.0, slot.marketEnergy());
        assertEquals(20.0, slot.discomEnergy());
        assertEquals(4.10, slot.damMcp());
        assertEquals(4.20, slot.rtmMcp());
        assertEquals(3.95, slot.gdamMcp());

        Map<String, Object> map = slot.toMap();
        assertEquals("2026-06-19", map.get("date"));
        assertEquals(12, map.get("timeblock"));
        assertEquals("TOD2", map.get("tod"));
        assertEquals(250.0, map.get("originalEnergy"));
        assertEquals(300.0, map.get("newEnergy"));
        assertEquals(4.25, map.get("costPerKwh"));
        assertEquals("GDAM", map.get("marketSource"));
        assertEquals(true, map.get("shouldBuyFromMarket"));
        assertEquals(280.0, map.get("marketEnergy"));
        assertEquals(20.0, map.get("discomEnergy"));
        assertEquals(4.10, map.get("damMcp"));
        assertEquals(4.20, map.get("rtmMcp"));
        assertEquals(3.95, map.get("gdamMcp"));

        ShiftedSlotItem deserialized = ShiftedSlotItem.fromMap(map);
        assertEquals(slot, deserialized);

        // Null map fallback
        ShiftedSlotItem fromNull = ShiftedSlotItem.fromMap(null);
        assertEquals("", fromNull.date());
        assertEquals(1, fromNull.timeblock());
        assertEquals("DISCOM", fromNull.marketSource());
    }

    @Test
    @DisplayName("DemandShiftInsights construction, helper methods, toMap, and fromMap roundtrip")
    void testDemandShiftInsightsRoundtrip() {
        TodShiftSummaryItem todItem = new TodShiftSummaryItem("TOD1", 1000.0, 1200.0, 200.0, 500.0, 600.0);
        ShiftedSlotItem slotItem = new ShiftedSlotItem(
                "2026-06-19", 1, "TOD1", 200.0, 220.0, 4.5, "DAM", true, 220.0, 0.0, 4.0, 4.2, 3.8
        );

        DemandShiftInsights insights = new DemandShiftInsights(
                "client-123",
                "Acme Steel",
                500.0,
                125.0,
                50000.0,
                46000.0,
                4000.0,
                200.0,
                4000.0,
                0.25,
                "11:00 - 15:00 to 00:00 - 05:00",
                List.of("17:00 - 22:00"),
                List.of("10:00 - 16:00"),
                List.of(todItem),
                List.of(slotItem)
        );

        assertEquals("client-123", insights.clientId());
        assertEquals("Acme Steel", insights.clientName());
        assertEquals(500.0, insights.sanctionedLoadKw());
        assertEquals(125.0, insights.maxEnergyPerSlot());
        assertEquals(50000.0, insights.originalTotalCost());
        assertEquals(46000.0, insights.newTotalCost());
        assertEquals(4000.0, insights.savingsAchieved());
        assertEquals(200.0, insights.shiftedEnergy());
        assertEquals(4000.0, insights.potentialSavingsRs());
        assertEquals(0.25, insights.shiftableCapacityMw());

        // Fast lookups
        assertTrue(insights.findTodSummary("TOD1").isPresent());
        assertEquals(1200.0, insights.findTodSummary("TOD1").get().newEnergy());
        assertTrue(insights.findTodSummary("TOD_UNKNOWN").isEmpty());

        assertEquals(1200.0, insights.getNewEnergyForTod("TOD1").orElse(0.0));
        assertEquals(0.0, insights.getNewEnergyForTod("TOD_UNKNOWN").orElse(0.0));

        var foundSlot = insights.findSlot("2026-06-19", 1);
        assertTrue(foundSlot.isPresent());
        assertEquals(220.0, foundSlot.get().newEnergy());

        assertTrue(insights.findSlot("2026-06-19", 99).isEmpty());
        assertTrue(insights.findSlot("2026-06-20", 1).isEmpty());

        // Serialization to Map
        Map<String, Object> map = insights.toMap();
        assertEquals("client-123", map.get("clientId"));
        assertEquals("Acme Steel", map.get("clientName"));
        assertEquals(500.0, map.get("sanctionedLoadKw"));
        assertEquals(125.0, map.get("maxEnergyPerSlot"));
        assertEquals(4000.0, map.get("savingsAchieved"));
        assertThat(map.get("todShiftSummary")).isInstanceOf(List.class);
        assertThat(map.get("slotsData")).isInstanceOf(List.class);

        // Deserialization from Map
        DemandShiftInsights deserialized = DemandShiftInsights.fromMap(map);
        assertEquals(insights.clientId(), deserialized.clientId());
        assertEquals(insights.clientName(), deserialized.clientName());
        assertEquals(insights.sanctionedLoadKw(), deserialized.sanctionedLoadKw());
        assertEquals(insights.savingsAchieved(), deserialized.savingsAchieved());
        assertEquals(1, deserialized.todShiftSummary().size());
        assertEquals(1, deserialized.slotsData().size());
        assertEquals(1200.0, deserialized.getNewEnergyForTod("TOD1").orElse(0.0));
    }

    @Test
    @DisplayName("DemandShiftInsights empty factory produces safe defaults")
    void testDemandShiftInsightsEmpty() {
        DemandShiftInsights empty = DemandShiftInsights.empty("c-1", "Client One");
        assertEquals("c-1", empty.clientId());
        assertEquals("Client One", empty.clientName());
        assertEquals(0.0, empty.sanctionedLoadKw());
        assertEquals(0.0, empty.savingsAchieved());
        assertTrue(empty.todShiftSummary().isEmpty());
        assertTrue(empty.slotsData().isEmpty());
        assertEquals(0.0, empty.getNewEnergyForTod("TOD1").orElse(0.0));
        assertTrue(empty.findSlot("2026-06-19", 1).isEmpty());

        // fromMap with empty or null map
        DemandShiftInsights fromNull = DemandShiftInsights.fromMap(null);
        assertNotNull(fromNull);
        assertEquals("", fromNull.clientId());

        DemandShiftInsights fromEmpty = DemandShiftInsights.fromMap(Collections.emptyMap());
        assertNotNull(fromEmpty);
        assertEquals("", fromEmpty.clientId());
    }

    @Test
    @DisplayName("DemandShiftInsights immutability protects internal collections")
    void testDemandShiftInsightsImmutability() {
        TodShiftSummaryItem todItem = new TodShiftSummaryItem("TOD1", 100.0, 100.0, 0.0, 0.0, 0.0);
        ShiftedSlotItem slotItem = new ShiftedSlotItem("2026-06-19", 1, "TOD1", 10.0, 10.0, 4.0, "DISCOM", false, 0.0, 10.0, null, null, null);

        DemandShiftInsights insights = new DemandShiftInsights(
                "c", "n", 100.0, 25.0, 1000.0, 1000.0, 0.0, 0.0, 250000.0, 0.5,
                "window", List.of("peak"), List.of("solar"), List.of(todItem), List.of(slotItem)
        );

        assertThrows(UnsupportedOperationException.class, () -> insights.todShiftSummary().add(todItem));
        assertThrows(UnsupportedOperationException.class, () -> insights.slotsData().add(slotItem));
        assertThrows(UnsupportedOperationException.class, () -> insights.peakHours().add("extra"));
        assertThrows(UnsupportedOperationException.class, () -> insights.solarHours().add("extra"));
    }
}

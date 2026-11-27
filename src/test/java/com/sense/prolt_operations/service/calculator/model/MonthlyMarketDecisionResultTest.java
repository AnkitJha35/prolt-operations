package com.sense.prolt_operations.service.calculator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("V18L.2 - MonthlyMarketDecisionResult & OA Sub-Models Unit Tests")
class MonthlyMarketDecisionResultTest {

    @Test
    @DisplayName("OaLedgerTotals construction, empty factory, toMap, and fromMap parity")
    void testOaLedgerTotals() {
        OaLedgerTotals totals = new OaLedgerTotals(
                1200.0, 0.40, 500.0, 300.0, 450.0, 150.0, 100.0,
                200.0, 36.0, 800.0, 250.0, 350.0, 1000.0, 500.0, 25000.0
        );

        assertEquals(1200.0, totals.cssCharge());
        assertEquals(0.40, totals.cssRate());
        assertEquals(500.0, totals.rpoCharge());
        assertEquals(300.0, totals.pocCharge());
        assertEquals(450.0, totals.stuCharge());
        assertEquals(150.0, totals.dcCharge());
        assertEquals(100.0, totals.iexFee());
        assertEquals(200.0, totals.traderMargin());
        assertEquals(36.0, totals.traderMarginGst());
        assertEquals(800.0, totals.proltMarginCost());
        assertEquals(250.0, totals.consultancyFee());
        assertEquals(350.0, totals.probusPlatformFee());
        assertEquals(1000.0, totals.nocFee());
        assertEquals(500.0, totals.regFee());
        assertEquals(25000.0, totals.consumerBusUnits());

        Map<String, Object> map = totals.toMap();
        assertEquals(1200.0, map.get("cssCharge"));
        assertEquals(25000.0, map.get("consumerBusUnits"));

        OaLedgerTotals deserialized = OaLedgerTotals.fromMap(map);
        assertEquals(totals, deserialized);

        // Empty factory
        OaLedgerTotals empty = OaLedgerTotals.empty();
        assertEquals(0.0, empty.cssCharge());
        assertEquals(0.0, empty.consumerBusUnits());

        // Null map fallback
        OaLedgerTotals fromNull = OaLedgerTotals.fromMap(null);
        assertEquals(0.0, fromNull.cssCharge());
    }

    @Test
    @DisplayName("OaDetailedResult construction, toMap, and fromMap roundtrip")
    void testOaDetailedResult() {
        OaBreakdownItem breakdownItem = new OaBreakdownItem(
                "TOD1", 2000.0, 3000.0, 14000.0, 11000.0, 2700.0, 12000.0, 7.0
        );
        OaLedgerTotals totals = new OaLedgerTotals(
                100.0, 0.1, 50.0, 30.0, 40.0, 10.0, 15.0, 20.0, 3.6, 50.0, 25.0, 35.0, 100.0, 50.0, 2700.0
        );

        OaDetailedResult oaDetailed = new OaDetailedResult(
                List.of(breakdownItem), 500.0, 200.0, 300.0, 100.0, 22, totals
        );

        assertEquals(1, oaDetailed.breakdown().size());
        assertEquals(500.0, oaDetailed.dailyFixedOverhead());
        assertEquals(200.0, oaDetailed.nldcSchedulingCost());
        assertEquals(300.0, oaDetailed.sldcSchedulingCost());
        assertEquals(100.0, oaDetailed.bidApplicationFees());
        assertEquals(22, oaDetailed.totalDaysTraded());
        assertEquals(totals, oaDetailed.totals());

        Map<String, Object> map = oaDetailed.toMap();
        assertEquals(500.0, map.get("dailyFixedOverhead"));
        assertEquals(22, map.get("totalDaysTraded"));
        assertThat(map.get("breakdown")).isInstanceOf(List.class);
        assertThat(map.get("totals")).isInstanceOf(Map.class);

        OaDetailedResult deserialized = OaDetailedResult.fromMap(map);
        assertEquals(oaDetailed.dailyFixedOverhead(), deserialized.dailyFixedOverhead());
        assertEquals(oaDetailed.totalDaysTraded(), deserialized.totalDaysTraded());
        assertEquals(1, deserialized.breakdown().size());
        assertEquals("TOD1", deserialized.breakdown().get(0).slabName());
        assertEquals(100.0, deserialized.totals().cssCharge());

        // Null map fallback
        OaDetailedResult fromNull = OaDetailedResult.fromMap(null);
        assertNotNull(fromNull);
        assertTrue(fromNull.breakdown().isEmpty());
        assertEquals(0.0, fromNull.dailyFixedOverhead());
    }

    @Test
    @DisplayName("MonthlyMarketDecisionResult construction, immutability, toMap, and fromMap roundtrip")
    void testMonthlyMarketDecisionResultRoundtrip() {
        MarketSlotCalculation slot = new MarketSlotCalculation();
        slot.date = "2026-06-19";
        slot.timeblock = 1;
        slot.tod = "TOD1";
        slot.marketEnergy = 100.0;
        slot.discomEnergy = 50.0;

        TodSlabSummary todSummary = new TodSlabSummary("TOD1", 5000.0, 3500.0, 15000.0);
        OaDetailedResult oaDetailed = new OaDetailedResult(
                Collections.emptyList(), 100.0, 50.0, 50.0, 25.0, 20, OaLedgerTotals.empty()
        );

        MonthlyMarketDecisionResult result = new MonthlyMarketDecisionResult(
                "client-abc",
                "Alpha Industries",
                "UP",
                "2026-06",
                List.of(slot),
                100000.0,
                60000.0,
                55000.0,
                750000.0,
                5.0,
                37500.0,
                15000.0,
                240000.0,
                350000.0,
                500000.0,
                200000.0,
                120000.0,
                45000.0,
                160000.0,
                175000.0,
                100000.0,
                45000.0,
                5000.0,
                1200.0,
                800.0,
                "NPCL",
                450.0,
                0.95,
                350.0,
                List.of(todSummary),
                oaDetailed
        );

        assertEquals("client-abc", result.clientId());
        assertEquals("Alpha Industries", result.clientName());
        assertEquals("UP", result.state());
        assertEquals("2026-06", result.month());
        assertEquals(1, result.slotsData().size());
        assertEquals(100000.0, result.totalEnergyKwh());
        assertEquals(60000.0, result.totalMarketEnergyKwh());
        assertEquals(55000.0, result.totalConsumerBusEnergyKwh());
        assertEquals(750000.0, result.totalBaselineCost());
        assertEquals(160000.0, result.totalSavings());
        assertEquals(1, result.todSummaries().size());
        assertNotNull(result.oaDetailed());

        // Immutability
        assertThrows(UnsupportedOperationException.class, () -> result.slotsData().add(new MarketSlotCalculation()));
        assertThrows(UnsupportedOperationException.class, () -> result.todSummaries().add(new TodSlabSummary("X", 0, 0, 0)));

        // Serialization
        Map<String, Object> map = result.toMap();
        assertEquals("client-abc", map.get("clientId"));
        assertEquals("Alpha Industries", map.get("clientName"));
        assertEquals("UP", map.get("state"));
        assertEquals("2026-06", map.get("month"));
        assertEquals(100000.0, map.get("totalEnergyKwh"));
        assertEquals(160000.0, map.get("totalSavings"));
        assertThat(map.get("slotsData")).isInstanceOf(List.class);
        assertThat(map.get("todSummaries")).isInstanceOf(List.class);
        assertThat(map.get("oaDetailed")).isInstanceOf(Map.class);

        // Roundtrip deserialization
        MonthlyMarketDecisionResult deserialized = MonthlyMarketDecisionResult.fromMap(map);
        assertEquals(result.clientId(), deserialized.clientId());
        assertEquals(result.clientName(), deserialized.clientName());
        assertEquals(result.month(), deserialized.month());
        assertEquals(result.totalEnergyKwh(), deserialized.totalEnergyKwh());
        assertEquals(result.totalSavings(), deserialized.totalSavings());
        assertEquals(1, deserialized.slotsData().size());
        assertEquals("2026-06-19", deserialized.slotsData().get(0).date);
        assertEquals(1, deserialized.todSummaries().size());
        assertEquals("TOD1", deserialized.todSummaries().get(0).slabName());
        assertNotNull(deserialized.oaDetailed());
        assertEquals(100.0, deserialized.oaDetailed().dailyFixedOverhead());
    }

    @Test
    @DisplayName("MonthlyMarketDecisionResult fromMap null and empty map safety")
    void testMonthlyMarketDecisionResultEmpty() {
        MonthlyMarketDecisionResult fromNull = MonthlyMarketDecisionResult.fromMap(null);
        assertNotNull(fromNull);
        assertEquals("", fromNull.clientId());
        assertEquals(0.0, fromNull.totalEnergyKwh());
        assertTrue(fromNull.slotsData().isEmpty());
        assertTrue(fromNull.todSummaries().isEmpty());
        assertNull(fromNull.oaDetailed());

        MonthlyMarketDecisionResult fromEmpty = MonthlyMarketDecisionResult.fromMap(Collections.emptyMap());
        assertNotNull(fromEmpty);
        assertEquals("", fromEmpty.clientId());
        assertEquals(0.0, fromEmpty.totalEnergyKwh());
    }
}

package com.sense.prolt_operations.service.calculator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("V18F - Calculator Internal Typed Models Unit Tests")
class CalculatorModelsTest {

    @Test
    @DisplayName("Hv2SlotRecord initial and withEnergyAndCosts methods maintain immutability and toMap parity")
    void testHv2SlotRecord() {
        Hv2SlotRecord initialSlot = Hv2SlotRecord.initial(
                "2026-06-19",
                1,
                "00:00",
                "TOD1",
                4.50,
                4.20,
                3.80,
                6.80,
                3.80,
                "GDAM",
                1.5,
                3.5,
                4.0
        );

        assertEquals("2026-06-19", initialSlot.date());
        assertEquals(1, initialSlot.slot());
        assertEquals("00:00", initialSlot.timeStr());
        assertEquals("TOD1", initialSlot.todSlab());
        assertEquals(4.50, initialSlot.damLandingPrice());
        assertEquals(4.20, initialSlot.rtmLandingPrice());
        assertEquals(3.80, initialSlot.gdamLandingPrice());
        assertEquals(6.80, initialSlot.discomLandingPrice());
        assertEquals(3.80, initialSlot.comparedLowestPrice());
        assertEquals("GDAM", initialSlot.selectedSource());
        assertEquals(0.0, initialSlot.maxEnergyPerSlot());
        assertEquals(0.0, initialSlot.optimizedCost());
        assertEquals(0.0, initialSlot.baselineCost());
        assertEquals(1.5, initialSlot.istsLoss());
        assertEquals(3.5, initialSlot.stuLoss());
        assertEquals(4.0, initialSlot.wheelingLoss());

        Hv2SlotRecord updatedSlot = initialSlot.withEnergyAndCosts(250.0, 950.0, 1700.0);
        // Verify original slot is unmodified (immutable)
        assertEquals(0.0, initialSlot.maxEnergyPerSlot());
        assertEquals(0.0, initialSlot.optimizedCost());
        // Verify updated slot has new values
        assertEquals(250.0, updatedSlot.maxEnergyPerSlot());
        assertEquals(950.0, updatedSlot.optimizedCost());
        assertEquals(1700.0, updatedSlot.baselineCost());
        assertEquals("TOD1", updatedSlot.todSlab());

        // Verify toMap
        Map<String, Object> map = updatedSlot.toMap();
        assertEquals("2026-06-19", map.get("date"));
        assertEquals(1, map.get("slot"));
        assertEquals("00:00", map.get("timeStr"));
        assertEquals("TOD1", map.get("todSlab"));
        assertEquals(4.50, map.get("damLandingPrice"));
        assertEquals(4.20, map.get("rtmLandingPrice"));
        assertEquals(3.80, map.get("gdamLandingPrice"));
        assertEquals(6.80, map.get("discomLandingPrice"));
        assertEquals(3.80, map.get("comparedLowestPrice"));
        assertEquals("GDAM", map.get("selectedSource"));
        assertEquals(250.0, map.get("maxEnergyPerSlot"));
        assertEquals(950.0, map.get("optimizedCost"));
        assertEquals(1700.0, map.get("baselineCost"));
        assertEquals(1.5, map.get("istsLoss"));
        assertEquals(3.5, map.get("stuLoss"));
        assertEquals(4.0, map.get("wheelingLoss"));
    }

    @Test
    @DisplayName("TodSlabSummary immutability, accessors, and toMap parity")
    void testTodSlabSummary() {
        TodSlabSummary summary = new TodSlabSummary("TOD1", 50000.0, 30000.0, 120000.0);

        assertEquals("TOD1", summary.slabName());
        assertEquals(50000.0, summary.totalEnergyKwh());
        assertEquals(30000.0, summary.marketEnergyKwh());
        assertEquals(120000.0, summary.marketCostBase());

        Map<String, Object> map = summary.toMap();
        assertEquals("TOD1", map.get("slabName"));
        assertEquals(50000.0, map.get("totalEnergyKwh"));
        assertEquals(30000.0, map.get("marketEnergyKwh"));
        assertEquals(120000.0, map.get("marketCostBase"));
    }

    @Test
    @DisplayName("OaBreakdownItem immutability, accessors, and toMap parity")
    void testOaBreakdownItem() {
        OaBreakdownItem item = new OaBreakdownItem(
                "TOD2",
                10000.0,
                8000.0,
                75000.0,
                15000.0,
                7200.0,
                35000.0,
                7.50
        );

        assertEquals("TOD2", item.slabName());
        assertEquals(10000.0, item.discomUnits());
        assertEquals(8000.0, item.oaUnits());
        assertEquals(75000.0, item.discomBill());
        assertEquals(15000.0, item.proltDiscomBill());
        assertEquals(7200.0, item.consumerBusUnits());
        assertEquals(35000.0, item.oaBill());
        assertEquals(7.50, item.discomRate());

        Map<String, Object> map = item.toMap();
        assertEquals("TOD2", map.get("slabName"));
        assertEquals(10000.0, map.get("discomUnits"));
        assertEquals(8000.0, map.get("oaUnits"));
        assertEquals(75000.0, map.get("discomBill"));
        assertEquals(15000.0, map.get("proltDiscomBill"));
        assertEquals(7200.0, map.get("consumerBusUnits"));
        assertEquals(35000.0, map.get("oaBill"));
        assertEquals(7.50, map.get("discomRate"));
    }

    @Test
    @DisplayName("OaLedgerTotals immutability, accessors, and toMap parity")
    void testOaLedgerTotals() {
        OaLedgerTotals totals = new OaLedgerTotals(
                1200.0,
                0.68,
                450.0,
                890.0,
                690.0,
                380.0,
                36.0,
                36.0,
                6.48,
                5000.0,
                20000.0,
                36.0,
                7000.0,
                8333.0,
                1600.0
        );

        assertEquals(1200.0, totals.cssCharge());
        assertEquals(0.68, totals.cssRate());
        assertEquals(450.0, totals.rpoCharge());
        assertEquals(890.0, totals.pocCharge());
        assertEquals(690.0, totals.stuCharge());
        assertEquals(380.0, totals.dcCharge());
        assertEquals(36.0, totals.iexFee());
        assertEquals(36.0, totals.traderMargin());
        assertEquals(6.48, totals.traderMarginGst());
        assertEquals(5000.0, totals.proltMarginCost());
        assertEquals(20000.0, totals.consultancyFee());
        assertEquals(36.0, totals.probusPlatformFee());
        assertEquals(7000.0, totals.nocFee());
        assertEquals(8333.0, totals.regFee());
        assertEquals(1600.0, totals.consumerBusUnits());

        Map<String, Object> map = totals.toMap();
        assertThat(map).containsEntry("cssCharge", 1200.0)
                .containsEntry("cssRate", 0.68)
                .containsEntry("rpoCharge", 450.0)
                .containsEntry("pocCharge", 890.0)
                .containsEntry("stuCharge", 690.0)
                .containsEntry("dcCharge", 380.0)
                .containsEntry("iexFee", 36.0)
                .containsEntry("traderMargin", 36.0)
                .containsEntry("traderMarginGst", 6.48)
                .containsEntry("proltMarginCost", 5000.0)
                .containsEntry("consultancyFee", 20000.0)
                .containsEntry("probusPlatformFee", 36.0)
                .containsEntry("nocFee", 7000.0)
                .containsEntry("regFee", 8333.0)
                .containsEntry("consumerBusUnits", 1600.0);
    }

    @Test
    @DisplayName("ClientOverviewMonthRecord success and failure factory methods and toMap parity")
    void testClientOverviewMonthRecord() {
        ClientOverviewMonthRecord successMonth = ClientOverviewMonthRecord.success(
                "2026-06",
                45000.0,
                50000.0,
                200000.0,
                150000.0,
                135000.0,
                67.5,
                5000.0,
                42.48,
                20000.0,
                3000.0,
                1500000.0,
                1455000.0
        );

        assertTrue(successMonth.isSuccess());
        assertNull(successMonth.error());
        assertEquals("2026-06", successMonth.month());
        assertEquals(45000.0, successMonth.savings());
        assertEquals(50000.0, successMonth.grossSavings());
        assertEquals(200000.0, successMonth.totalEnergyKwh());
        assertEquals(67.5, successMonth.oaCoverage());

        Map<String, Object> successMap = successMonth.toMap();
        assertFalse(successMap.containsKey("error"));
        assertEquals("2026-06", successMap.get("month"));
        assertEquals(45000.0, successMap.get("savings"));
        assertEquals(50000.0, successMap.get("grossSavings"));
        assertEquals(200000.0, successMap.get("totalEnergyKwh"));
        assertEquals(67.5, successMap.get("oaCoverage"));

        ClientOverviewMonthRecord failureMonth = ClientOverviewMonthRecord.failure("2026-07", "Calculation failed");
        assertFalse(failureMonth.isSuccess());
        assertEquals("Calculation failed", failureMonth.error());
        assertEquals(0.0, failureMonth.savings());

        Map<String, Object> failureMap = failureMonth.toMap();
        assertEquals("2026-07", failureMap.get("month"));
        assertEquals(0.0, failureMap.get("savings"));
        assertEquals("Calculation failed", failureMap.get("error"));
        assertFalse(failureMap.containsKey("grossSavings"));
    }

    @Test
    @DisplayName("AggregatedCostsAccumulator accurately accumulates charges and tracks max peak demand")
    void testAggregatedCostsAccumulator() {
        AggregatedCostsAccumulator acc = new AggregatedCostsAccumulator();
        assertEquals(0.0, acc.getCssCharge());
        assertEquals(0.0, acc.getPeakDemand());

        acc.addCssCharge(100.0);
        acc.addCssCharge(150.0);
        assertEquals(250.0, acc.getCssCharge());

        acc.addRpoCharge(50.0);
        acc.addPocCharge(60.0);
        acc.addStuCharge(70.0);
        acc.addDcCharge(80.0);
        acc.addIexFee(10.0);
        acc.addTraderMarginTotal(15.0);
        acc.addDailyFixedOverhead(200.0);
        acc.addBidApplicationFees(300.0);
        acc.addProltMarginCost(400.0);
        acc.addConsultancyFee(500.0);
        acc.addProbusPlatformFee(600.0);
        acc.addTotalDiscomCost(10000.0);
        acc.addEnergyCharges(8000.0);
        acc.addDemandAndFixedCharges(1500.0);
        acc.addPenaltiesAndAdjustments(50.0);
        acc.addMiscellaneousCharges(450.0);

        // Peak demand tracking with Math.max
        acc.updatePeakDemand(450.0);
        assertEquals(450.0, acc.getPeakDemand());
        acc.updatePeakDemand(350.0);
        assertEquals(450.0, acc.getPeakDemand()); // Remains 450
        acc.updatePeakDemand(550.0);
        assertEquals(550.0, acc.getPeakDemand()); // Updated to 550

        acc.setDemandChargeRate(330.0);
        assertEquals(330.0, acc.getDemandChargeRate());

        Map<String, Object> map = acc.toMap();
        assertEquals(19, map.size());
        assertEquals(250.0, map.get("cssCharge"));
        assertEquals(50.0, map.get("rpoCharge"));
        assertEquals(60.0, map.get("pocCharge"));
        assertEquals(70.0, map.get("stuCharge"));
        assertEquals(80.0, map.get("dcCharge"));
        assertEquals(10.0, map.get("iexFee"));
        assertEquals(15.0, map.get("traderMarginTotal"));
        assertEquals(200.0, map.get("dailyFixedOverhead"));
        assertEquals(300.0, map.get("bidApplicationFees"));
        assertEquals(400.0, map.get("proltMarginCost"));
        assertEquals(550.0, map.get("peakDemand"));
        assertEquals(330.0, map.get("demandChargeRate"));
    }

    @Test
    @DisplayName("MarketSlotCalculation preserves all market landing queries and serializes exact 31-entry map")
    void testMarketSlotCalculation() {
        MarketSlotCalculation s = new MarketSlotCalculation();
        s.date = "2026-06-19";
        s.timeblock = 4;
        s.hour = 0;
        s.tod = "TOD1";
        s.damMcp = 4.50;
        s.rtmMcp = 4.35;
        s.gdamMcp = 4.20;
        s.damLanding = 5.25;
        s.rtmLanding = 5.10;
        s.gdamLanding = 4.95;
        s.bestMarketLanding = 4.95;
        s.marketSource = "GDAM";
        s.discomLanding = 6.80;
        s.shouldBuyFromMarket = true;
        s.savingsPerKwh = 1.85;
        s.istsLoss = 1.5;
        s.stuLoss = 3.5;
        s.wheelingLoss = 4.0;
        s.marketEnergy = 250.0;
        s.discomEnergy = 0.0;
        s.consumedMarketEnergy = 227.5;
        s.exactMarketEnergyCost = 1050.0;

        assertEquals(5.25, s.getMarketLanding("DAM"));
        assertEquals(5.10, s.getMarketLanding("RTM"));
        assertEquals(4.95, s.getMarketLanding("GDAM"));
        assertNull(s.getMarketLanding("UNKNOWN"));

        Map<String, Object> map = s.toMap();
        assertEquals(31, map.size());
        assertEquals("2026-06-19", map.get("date"));
        assertEquals(4, map.get("timeblock"));
        assertEquals(4, map.get("timeBlock")); // CALC-01 alias
        assertEquals("00:45", map.get("startTime"));
        assertEquals("01:00", map.get("endTime"));
        assertEquals("GDAM", map.get("selectedSource"));
        assertEquals(4.95, map.get("recommendedPrice"));
        assertEquals(6.80, map.get("discomPrice"));
        assertEquals(5.25, map.get("damPrice"));
        assertEquals(4.95, map.get("gdamPrice"));
        assertEquals(5.10, map.get("rtmPrice"));
    }

    @Test
    @DisplayName("ShiftableSlot holds demand shift headroom and loss multipliers")
    void testShiftableSlot() {
        ShiftableSlot slot = new ShiftableSlot();
        slot.originalIndex = 12;
        slot.costPerKwh = 5.50;
        slot.currentEnergy = 200.0;
        slot.originalEnergy = 200.0;
        slot.headroom = 50.0;
        slot.lossMultiplier = 0.91;
        slot.date = "2026-06-19";
        slot.timeblock = 12;
        slot.tod = "TOD2";
        slot.shouldBuyFromMarket = true;

        assertEquals(12, slot.originalIndex);
        assertEquals(5.50, slot.costPerKwh);
        assertEquals(200.0, slot.currentEnergy);
        assertEquals(50.0, slot.headroom);
        assertEquals(0.91, slot.lossMultiplier);
        assertTrue(slot.shouldBuyFromMarket);
    }
}

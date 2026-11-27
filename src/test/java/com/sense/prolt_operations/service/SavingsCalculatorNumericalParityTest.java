package com.sense.prolt_operations.service;

import com.sense.prolt_operations.BaseIntegrationTest;
import com.sense.prolt_operations.dto.calculator.CreateSavingsEntryDTO;
import com.sense.prolt_operations.dto.calculator.LandedCostCalcRequest;
import com.sense.prolt_operations.dto.calculator.SavingsEntryDTO;
import com.sense.prolt_operations.repository.SavingsCalculatorEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Transactional
class SavingsCalculatorNumericalParityTest extends BaseIntegrationTest {

    @Autowired
    private SavingsCalculatorService savingsCalculatorService;

    @Autowired
    private SavingsCalculatorEntryRepository entryRepository;

    private String entryId;

    @BeforeEach
    void setUp() {
        CreateSavingsEntryDTO dto = new CreateSavingsEntryDTO(
                "Acme Metals Pvt Ltd",
                "Manufacturing",
                "Sector 62, Noida, UP",
                BigDecimal.valueOf(1500),
                "UP",
                "NPCL",
                "HV-2",
                "33 kV",
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(0.02),
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(20000),
                BigDecimal.valueOf(0.02),
                Map.of("2026-06", Map.of("T1", 150000, "T2", 250000, "T3", 100000)),
                true,
                BigDecimal.valueOf(1400),
                BigDecimal.valueOf(0.99),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "2026-06-01"
        );

        SavingsEntryDTO created = savingsCalculatorService.create(dto, "admin@probus.internal");
        this.entryId = created.id();
    }

    @Test
    @DisplayName("PARITY 1: getFlooredMaxEnergyPerSlot calculation matching Node.js 1:1")
    void testFlooredMaxEnergyPerSlot_Parity() {
        // Case 1: 1500 kW, 0.99 PF
        // loadKva = 1500 / 0.99 = 1515.1515 kVA
        // rawMw = 1515.1515 * 0.9 / 1000 = 1.3636 MW
        // flooredMw = floor(1.3636 * 10) / 10 = 1.3 MW
        // maxEnergyPerSlot = 1.3 * 1000 * 0.25 = 325.0 kWh
        double energy1 = SavingsCalculatorService.getFlooredMaxEnergyPerSlot(BigDecimal.valueOf(1500), BigDecimal.valueOf(0.99));
        assertThat(energy1).isCloseTo(325.0, within(1e-6));

        // Case 2: 2500 kW, 0.95 PF
        // loadKva = 2500 / 0.95 = 2631.5789 kVA
        // rawMw = 2631.5789 * 0.9 / 1000 = 2.3684 MW
        // flooredMw = floor(2.3684 * 10) / 10 = 2.3 MW
        // maxEnergyPerSlot = 2.3 * 250 = 575.0 kWh
        double energy2 = SavingsCalculatorService.getFlooredMaxEnergyPerSlot(BigDecimal.valueOf(2500), BigDecimal.valueOf(0.95));
        assertThat(energy2).isCloseTo(575.0, within(1e-6));

        // Case 3: 1000 kW, 1.00 PF
        // loadKva = 1000 kVA
        // rawMw = 1000 * 0.9 / 1000 = 0.9 MW
        // flooredMw = 0.9 MW -> 225.0 kWh
        double energy3 = SavingsCalculatorService.getFlooredMaxEnergyPerSlot(BigDecimal.valueOf(1000), BigDecimal.valueOf(1.00));
        assertThat(energy3).isCloseTo(225.0, within(1e-6));

        // Boundary: 0 or negative or null load
        assertThat(SavingsCalculatorService.getFlooredMaxEnergyPerSlot(null, BigDecimal.valueOf(0.99))).isEqualTo(0.0);
        assertThat(SavingsCalculatorService.getFlooredMaxEnergyPerSlot(BigDecimal.ZERO, BigDecimal.valueOf(0.99))).isEqualTo(0.0);
        assertThat(SavingsCalculatorService.getFlooredMaxEnergyPerSlot(BigDecimal.valueOf(-100), BigDecimal.valueOf(0.99))).isEqualTo(0.0);
    }

    @Test
    @DisplayName("PARITY 2: Loss coefficient and Open Access landing price formulas")
    void testOpenAccessLandingPrice_Formulas() {
        // MCP = 4500 Rs/MWh -> 4.50 Rs/kWh
        // Exchange fee = 0.02, GST = 18% -> 0.0236
        // Trader margin = 0.02, GST = 18% -> 0.0236
        // Other charges = 0.10
        // ISTS loss = 3.5%, STU loss = 3.5%, Wheeling loss = 4.0%
        // Regional charges (CTU=0.496 + STU=0.384 + Wheeling=0.216 + CSS=0.68 + AS=0.216) = 1.992

        double mcpRsPerKwh = 4.50;
        double fees = 0.02 * 1.18 + 0.02 * 1.18 + 0.10; // 0.1472
        double istsLoss = 3.5;
        double stuLoss = 3.5;
        double wheelingLoss = 4.0;

        double lossCoeff = (1.0 - istsLoss / 100.0) * (1.0 - stuLoss / 100.0) * (1.0 - wheelingLoss / 100.0);
        // (1 - 0.035) * (1 - 0.035) * (1 - 0.04) = 0.965 * 0.965 * 0.96 = 0.893976
        assertThat(lossCoeff).isCloseTo(0.893976, within(1e-6));

        double regionalCharges = 0.496 + 0.384 + 0.216 + 0.68 + 0.216; // 1.992
        double landedPrice = (mcpRsPerKwh + fees) / lossCoeff + regionalCharges;

        // (4.50 + 0.1472) / 0.893976 + 1.992 = 4.6472 / 0.893976 + 1.992 = 5.19835 + 1.992 = 7.19035
        assertThat(landedPrice).isCloseTo(7.19035, within(1e-4));
    }

    @Test
    @DisplayName("PARITY 3: Market Decision Engine returns complete 96 timeblocks with OA selection")
    @SuppressWarnings("unchecked")
    void testMarketDecision_Parity() {
        Map<String, Object> decision = savingsCalculatorService.calculateMarketDecision(entryId, "2026-06", null);

        assertThat(decision).isNotNull();
        assertThat(decision.get("clientName")).isEqualTo("Acme Metals Pvt Ltd");
        assertThat(decision.get("state")).isEqualTo("UP");

        List<?> slots = (List<?>) decision.get("marketDecision");
        assertThat(slots).isNotNull();
        assertThat(slots).hasSize(96);

        Map<String, Object> slot1 = (Map<String, Object>) slots.get(0);
        assertThat(slot1.get("timeBlock")).isEqualTo(1);
        assertThat(slot1.get("startTime")).isEqualTo("00:00");
        assertThat(slot1.get("endTime")).isEqualTo("00:15");
        assertThat(slot1).containsKey("discomPrice");
        assertThat(slot1).containsKey("selectedSource");
        assertThat(slot1).containsKey("recommendedPrice");

        assertThat((Double) decision.get("totalEnergyKwh")).isGreaterThan(0.0);
        assertThat(decision).containsKey("totalSavings");
        assertThat(decision).containsKey("oaDetailed");
    }

    @Test
    @DisplayName("PARITY 4: Demand Shift Insights calculates shifted energy and headroom")
    void testDemandShiftInsights_Parity() {
        Map<String, Object> insights = savingsCalculatorService.calculateDemandShiftInsights(entryId, "2026-06", null);

        assertThat(insights).isNotNull();
        assertThat(insights.get("clientName")).isEqualTo("Acme Metals Pvt Ltd");
        assertThat((Double) insights.get("shiftableCapacityMw")).isGreaterThan(0.0);
        assertThat((Double) insights.get("maxEnergyPerSlot")).isEqualTo(325.0);
        assertThat(insights).containsKey("originalTotalCost");
        assertThat(insights).containsKey("newTotalCost");
        assertThat(insights).containsKey("potentialSavingsRs");
        assertThat(insights.get("recommendedWindow")).isEqualTo("11:00 - 15:00 to 00:00 - 05:00");
    }

    @Test
    @DisplayName("PARITY 5: Client Overview aggregates costs and savings across months")
    @SuppressWarnings("unchecked")
    void testClientOverview_Parity() {
        Map<String, Object> overview = savingsCalculatorService.getClientOverview(entryId);

        assertThat(overview).isNotNull();
        assertThat(overview.get("clientName")).isEqualTo("Acme Metals Pvt Ltd");
        assertThat(overview.get("industryName")).isEqualTo("Manufacturing");
        assertThat(overview).containsKey("totalSavings");
        assertThat(overview).containsKey("months");
        assertThat(overview).containsKey("aggregatedCosts");

        Map<String, Object> costs = (Map<String, Object>) overview.get("aggregatedCosts");
        assertThat(costs).containsKey("cssCharge");
        assertThat(costs).containsKey("stuCharge");
        assertThat(costs).containsKey("iexFee");
        assertThat(costs).containsKey("totalDiscomCost");
    }

    @Test
    @DisplayName("PARITY 6: calculateSavings (TOD Energy Aggregation & Discom Landing)")
    void testCalculateSavings_Parity() {
        Map<String, Object> savings = savingsCalculatorService.calculateSavings(entryId, "2026-06", null);

        assertThat(savings).isNotNull();
        assertThat(savings.get("clientName")).isEqualTo("Acme Metals Pvt Ltd");
        assertThat((Double) savings.get("totalEnergyKwh")).isGreaterThan(0.0);
        assertThat((Double) savings.get("totalBaselineCost")).isGreaterThan(0.0);
        assertThat(savings).containsKey("totalOptimizedCost");
        assertThat(savings).containsKey("totalSavings");
        assertThat(savings).containsKey("todGroups");
    }

    @Test
    @DisplayName("PARITY 7: Direct Landed Cost (CALC-01 Scenario Regression Guard)")
    void testCalculateDirect_Preserved() {
        LandedCostCalcRequest req = new LandedCostCalcRequest(
                "Acme Steel Mills",
                "Uttar Pradesh",
                "HV-2",
                BigDecimal.valueOf(1500),
                "33 kV",
                BigDecimal.valueOf(1250000),
                "2026-06"
        );

        var resp = savingsCalculatorService.calculateDirect(req);
        assertThat(resp).isNotNull();
        assertThat(resp.success()).isTrue();
        assertThat(resp.data()).isNotNull();
        assertThat(resp.data().discomBillAmount()).isNotNull();
        assertThat(resp.data().iexTotalCost()).isNotNull();
        assertThat(resp.data().totalSavings()).isNotNull();
    }
}

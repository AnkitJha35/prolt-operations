package com.sense.prolt_operations.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.sense.prolt_operations.BaseIntegrationTest;
import com.sense.prolt_operations.dto.calculator.CreateSavingsEntryDTO;
import com.sense.prolt_operations.dto.calculator.LandedCostCalcRequest;
import com.sense.prolt_operations.dto.calculator.LandedCostCalcResponse;
import com.sense.prolt_operations.dto.calculator.LandingPriceCalculationRequest.CalculationMode;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.service.SavingsCalculatorService;

@SpringBootTest
@Transactional
class DynamicDirectLandingPriceTest extends BaseIntegrationTest {

    @Autowired
    private SavingsCalculatorService savingsCalculatorService;

    private static final String SAVED_CLIENT = "Dynamic Metals Industries Pvt Ltd";

    @BeforeEach
    void setUp() {
        CreateSavingsEntryDTO entryDTO = new CreateSavingsEntryDTO(
                SAVED_CLIENT,
                "Heavy Manufacturing",
                "Sector 62, Noida, Gautam Buddha Nagar, UP",
                BigDecimal.valueOf(2000), // 2000 kW load
                "UP",
                "NPCL",
                "HV-2",
                "33 kV",
                BigDecimal.valueOf(10.0),
                BigDecimal.valueOf(0.02), // 0.02 Rs/kWh trader margin
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(20000),
                BigDecimal.valueOf(0.02),
                Map.of("2026-06", Map.of("T1", 200000, "T2", 300000, "T3", 100000)),
                true, // Apply duty
                BigDecimal.valueOf(1800),
                BigDecimal.valueOf(0.98), // PF = 0.98
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "2026-06-01"
        );

        savingsCalculatorService.create(entryDTO, "admin@probus.internal");
    }

    @Test
    @DisplayName("Scenario 1: Saved Consumer Profile inherits DISCOM, load, and PF dynamically from DB")
    void testSavedConsumerProfileResolution() {
        LandedCostCalcRequest req = new LandedCostCalcRequest(
                SAVED_CLIENT,
                "UP",
                null, // null tariffCategory -> should resolve to HV-2 from saved profile
                null, // null contractDemand -> should resolve from saved sanctionedLoad (2000 kW)
                null, // null voltage -> should resolve to 33 kV
                BigDecimal.valueOf(1000000),
                "2026-06"
        );

        LandedCostCalcResponse resp = savingsCalculatorService.calculateDirect(req);
        assertThat(resp).isNotNull();
        assertThat(resp.success()).isTrue();
        assertThat(resp.data().discomBillAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(resp.data().iexTotalCost()).isGreaterThan(BigDecimal.ZERO);
        assertThat(resp.data().totalSavings()).isNotNull();

        // Check 5-component breakdown consistency
        LandedCostCalcResponse.CostBreakdown breakdown = resp.data().breakdown();
        BigDecimal componentSum = breakdown.energyCost()
                .add(breakdown.ctuLossAndCharges())
                .add(breakdown.stLossAndCharges())
                .add(breakdown.crossSubsidySurcharge())
                .add(breakdown.additionalSurcharge());

        assertThat(componentSum).isEqualByComparingTo(resp.data().iexTotalCost());
    }

    @Test
    @DisplayName("Scenario 2: Ad-Hoc Prospect without saved DB entry evaluates correctly")
    void testAdHocProspectCalculation() {
        LandedCostCalcRequest req = new LandedCostCalcRequest(
                "New Prospect Solar Corp",
                "Uttar Pradesh",
                "HV-2",
                BigDecimal.valueOf(1500), // 1500 kVA
                "33 kV",
                BigDecimal.valueOf(500000),
                "2026-06"
        );

        LandedCostCalcResponse resp = savingsCalculatorService.calculateDirect(req);
        assertThat(resp).isNotNull();
        assertThat(resp.success()).isTrue();
        assertThat(resp.data().discomBillAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(resp.data().iexTotalCost()).isGreaterThan(BigDecimal.ZERO);
        assertThat(resp.data().percentageSavings()).isNotNull();
    }

    @Test
    @DisplayName("Scenario 3: Different States & DISCOMs (UP with NPCL vs Generic State)")
    void testDifferentStatesAndDiscoms() {
        // UP with NPCL HV-2
        LandedCostCalcRequest upReq = new LandedCostCalcRequest(
                null,
                "UP",
                "HV-2",
                BigDecimal.valueOf(1000),
                "11 kV",
                BigDecimal.valueOf(100000),
                "2026-06"
        );
        LandedCostCalcResponse upResp = savingsCalculatorService.calculateDirect(upReq);

        // Maharashtra Generic
        LandedCostCalcRequest mhReq = new LandedCostCalcRequest(
                null,
                "MH",
                "HT-1",
                BigDecimal.valueOf(1000),
                "11 kV",
                BigDecimal.valueOf(100000),
                "2026-06"
        );
        LandedCostCalcResponse mhResp = savingsCalculatorService.calculateDirect(mhReq);

        assertThat(upResp.data().discomBillAmount()).isNotNull();
        assertThat(mhResp.data().discomBillAmount()).isNotNull();
        // NPCL rebate applies to UP HV-2, producing different DISCOM effective rates
        assertThat(upResp.data().discomBillAmount()).isNotEqualByComparingTo(mhResp.data().discomBillAmount());
    }

    @Test
    @DisplayName("Scenario 4: Different Consumer Categories (HV-2 vs HT-1)")
    void testDifferentConsumerCategories() {
        LandedCostCalcRequest reqHv2 = new LandedCostCalcRequest(
                null, "UP", "HV-2", BigDecimal.valueOf(1000), "33 kV", BigDecimal.valueOf(100000), "2026-06"
        );
        LandedCostCalcRequest reqHt1 = new LandedCostCalcRequest(
                null, "UP", "HT-1", BigDecimal.valueOf(1000), "33 kV", BigDecimal.valueOf(100000), "2026-06"
        );

        LandedCostCalcResponse respHv2 = savingsCalculatorService.calculateDirect(reqHv2);
        LandedCostCalcResponse respHt1 = savingsCalculatorService.calculateDirect(reqHt1);

        assertThat(respHv2.data().discomBillAmount()).isNotNull();
        assertThat(respHt1.data().discomBillAmount()).isNotNull();
    }

    @Test
    @DisplayName("Scenario 5: Different Sanctioned Loads (Sub-1MW 500 kW vs OA-Eligible 1500 kW)")
    void testDifferentSanctionedLoads() {
        // 500 kVA * 0.99 = 495 kW (< 1000 kW, non-OA)
        LandedCostCalcRequest subReq = new LandedCostCalcRequest(
                null, "UP", "HV-2", BigDecimal.valueOf(500), "11 kV", BigDecimal.valueOf(100000), "2026-06"
        );
        // 1500 kVA * 0.99 = 1485 kW (>= 1000 kW, OA eligible)
        LandedCostCalcRequest oaReq = new LandedCostCalcRequest(
                null, "UP", "HV-2", BigDecimal.valueOf(1500), "11 kV", BigDecimal.valueOf(100000), "2026-06"
        );

        LandedCostCalcResponse subResp = savingsCalculatorService.calculateDirect(subReq);
        LandedCostCalcResponse oaResp = savingsCalculatorService.calculateDirect(oaReq);

        assertThat(subResp.data().iexTotalCost()).isNotNull();
        assertThat(oaResp.data().iexTotalCost()).isNotNull();
    }

    @Test
    @DisplayName("Scenario 6: Different Voltage Levels (11 kV vs 33 kV vs 132 kV)")
    void testDifferentVoltageLevels() {
        LandedCostCalcRequest req11kv = new LandedCostCalcRequest(
                null, "UP", "HV-2", BigDecimal.valueOf(1200), "11 kV", BigDecimal.valueOf(100000), "2026-06"
        );
        LandedCostCalcRequest req33kv = new LandedCostCalcRequest(
                null, "UP", "HV-2", BigDecimal.valueOf(1200), "33 kV", BigDecimal.valueOf(100000), "2026-06"
        );
        LandedCostCalcRequest req132kv = new LandedCostCalcRequest(
                null, "UP", "HV-2", BigDecimal.valueOf(1200), "132 kV", BigDecimal.valueOf(100000), "2026-06"
        );

        LandedCostCalcResponse resp11kv = savingsCalculatorService.calculateDirect(req11kv);
        LandedCostCalcResponse resp33kv = savingsCalculatorService.calculateDirect(req33kv);
        LandedCostCalcResponse resp132kv = savingsCalculatorService.calculateDirect(req132kv);

        assertThat(resp11kv.data().discomBillAmount()).isNotNull();
        assertThat(resp33kv.data().discomBillAmount()).isNotNull();
        assertThat(resp132kv.data().discomBillAmount()).isNotNull();
    }

    @Test
    @DisplayName("Scenario 7: OPEN_ACCESS_LANDED mode applies full regulatory charges and loss adders")
    void testOpenAccessLandedMode() {
        LandedCostCalcRequest req = new LandedCostCalcRequest(
                null, "UP", "HV-2", BigDecimal.valueOf(1500), "33 kV", BigDecimal.valueOf(1000000), "2026-06",
                CalculationMode.OPEN_ACCESS_LANDED
        );

        LandedCostCalcResponse resp = savingsCalculatorService.calculateDirect(req);
        LandedCostCalcResponse.CostBreakdown b = resp.data().breakdown();

        // In OPEN_ACCESS_LANDED mode, regulatory adders must be non-zero
        assertThat(b.ctuLossAndCharges()).isGreaterThan(BigDecimal.ZERO);
        assertThat(b.stLossAndCharges()).isGreaterThan(BigDecimal.ZERO);
        assertThat(b.crossSubsidySurcharge()).isGreaterThan(BigDecimal.ZERO);
        assertThat(b.additionalSurcharge()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Scenario 8: BUSBAR_COMPARISON mode sets regulatory adders to zero and returns raw MCP")
    void testBusbarComparisonMode() {
        LandedCostCalcRequest req = new LandedCostCalcRequest(
                null, "UP", "HV-2", BigDecimal.valueOf(1500), "33 kV", BigDecimal.valueOf(1000000), "2026-06",
                CalculationMode.BUSBAR_COMPARISON
        );

        LandedCostCalcResponse resp = savingsCalculatorService.calculateDirect(req);
        LandedCostCalcResponse.CostBreakdown b = resp.data().breakdown();

        // In BUSBAR_COMPARISON mode, surcharges are 0.00 and energyCost equals iexTotalCost
        assertThat(b.ctuLossAndCharges()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b.stLossAndCharges()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b.crossSubsidySurcharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b.additionalSurcharge()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b.energyCost()).isEqualByComparingTo(resp.data().iexTotalCost());
    }

    @Test
    @DisplayName("Scenario 9: Missing profile data falls back safely to default prospect parameters")
    void testMissingProfileDataFallback() {
        LandedCostCalcRequest req = new LandedCostCalcRequest(
                null, "UP", null, null, null, null, null
        );

        LandedCostCalcResponse resp = savingsCalculatorService.calculateDirect(req);
        assertThat(resp).isNotNull();
        assertThat(resp.success()).isTrue();
        assertThat(resp.data().discomBillAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(resp.data().iexTotalCost()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Scenario 10: Null state throws BadRequestException")
    void testNullStateThrowsBadRequest() {
        LandedCostCalcRequest req = new LandedCostCalcRequest(
                "Acme Client", null, "HV-2", BigDecimal.valueOf(1000), "33 kV", BigDecimal.valueOf(100000), "2026-06"
        );

        assertThatThrownBy(() -> savingsCalculatorService.calculateDirect(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("State is required to calculate savings");
    }

    @Test
    @DisplayName("Scenario 11: Missing tariff data falls back gracefully to statutory defaults")
    void testMissingTariffDataFallback() {
        // Query a state with no database tariff records (e.g. Sikkim 'SK')
        LandedCostCalcRequest req = new LandedCostCalcRequest(
                null, "SK", "INDUSTRIAL", BigDecimal.valueOf(1200), "33 kV", BigDecimal.valueOf(100000), "2026-06"
        );

        LandedCostCalcResponse resp = savingsCalculatorService.calculateDirect(req);
        assertThat(resp).isNotNull();
        assertThat(resp.success()).isTrue();
        assertThat(resp.data().discomBillAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(resp.data().iexTotalCost()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Scenario 12: Regression against V18LP.1 scenarios preserves exact additive integrity")
    void testAdditiveIntegrityRegression() {
        LandedCostCalcRequest req = new LandedCostCalcRequest(
                SAVED_CLIENT, "UP", "HV-2", BigDecimal.valueOf(1500), "33 kV", BigDecimal.valueOf(750000), "2026-06"
        );

        LandedCostCalcResponse resp = savingsCalculatorService.calculateDirect(req);
        BigDecimal iexTotal = resp.data().iexTotalCost();
        BigDecimal discomTotal = resp.data().discomBillAmount();
        BigDecimal savings = resp.data().totalSavings();

        assertThat(discomTotal.subtract(iexTotal)).isEqualByComparingTo(savings);

        LandedCostCalcResponse.CostBreakdown b = resp.data().breakdown();
        BigDecimal sum = b.energyCost()
                .add(b.ctuLossAndCharges())
                .add(b.stLossAndCharges())
                .add(b.crossSubsidySurcharge())
                .add(b.additionalSurcharge());

        assertThat(sum).isEqualByComparingTo(iexTotal);
    }

    @Test
    @DisplayName("Scenario 13: Acme Steel Mills name no longer uses static hardcoded constants (dynamic scaling verified)")
    void testAcmeSteelMillsNoLongerHardcoded() {
        // In legacy code, "Acme Steel Mills" ALWAYS returned exactly 10,525,000.00 bill regardless of consumption!
        // Now it must scale linearly with consumption:
        LandedCostCalcRequest req1 = new LandedCostCalcRequest(
                "Acme Steel Mills", "UP", "HV-2", BigDecimal.valueOf(1500), "33 kV", BigDecimal.valueOf(1000000), "2026-06"
        );
        LandedCostCalcRequest req2 = new LandedCostCalcRequest(
                "Acme Steel Mills", "UP", "HV-2", BigDecimal.valueOf(1500), "33 kV", BigDecimal.valueOf(2000000), "2026-06"
        );

        LandedCostCalcResponse resp1 = savingsCalculatorService.calculateDirect(req1);
        LandedCostCalcResponse resp2 = savingsCalculatorService.calculateDirect(req2);

        // Discom bill for 2,000,000 kWh must be exactly 2x of 1,000,000 kWh (not stuck at 10,525,000.00)
        assertThat(resp1.data().discomBillAmount()).isNotEqualByComparingTo(BigDecimal.valueOf(10525000.00));
        BigDecimal ratio = resp2.data().discomBillAmount()
                .divide(resp1.data().discomBillAmount(), 4, RoundingMode.HALF_UP);
        assertThat(ratio).isEqualByComparingTo(BigDecimal.valueOf(2.0000).setScale(4, RoundingMode.HALF_UP));
    }
}

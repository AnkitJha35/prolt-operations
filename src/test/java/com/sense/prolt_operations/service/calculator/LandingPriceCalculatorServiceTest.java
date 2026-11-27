package com.sense.prolt_operations.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.dto.calculator.LandingPriceCalculationRequest;
import com.sense.prolt_operations.dto.calculator.LandingPriceCalculationResponse;
import com.sense.prolt_operations.entity.CtuCharges;
import com.sense.prolt_operations.entity.IstsCharges;
import com.sense.prolt_operations.entity.StateCharges;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.service.SavingsCalculatorDataAccessService;
import com.sense.prolt_operations.service.calculator.model.MatchedTariffRate;

class LandingPriceCalculatorServiceTest {

    private SavingsCalculatorDataAccessService dataAccessService;
    private RegulatoryChargeResolver regulatoryChargeResolver;
    private LandedCostCalculator landedCostCalculator;
    private ElectricalLossCalculator electricalLossCalculator;
    private CalculatorProperties properties;
    private LandingPriceCalculatorService service;

    @BeforeEach
    void setUp() {
        dataAccessService = mock(SavingsCalculatorDataAccessService.class);
        regulatoryChargeResolver = mock(RegulatoryChargeResolver.class);
        electricalLossCalculator = new ElectricalLossCalculator();
        properties = new CalculatorProperties();
        landedCostCalculator = new LandedCostCalculator(electricalLossCalculator, properties);

        service = new LandingPriceCalculatorService(
                dataAccessService,
                regulatoryChargeResolver,
                landedCostCalculator,
                electricalLossCalculator,
                properties
        );

        when(dataAccessService.resolveStateFormats(anyString())).thenAnswer(inv -> List.of(inv.getArgument(0, String.class)));
    }

    @Test
    @DisplayName("Validation: Null request throws BadRequestException")
    void testNullRequestThrows() {
        assertThatThrownBy(() -> service.calculate(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    @DisplayName("Validation: Missing state throws BadRequestException")
    void testMissingStateThrows() {
        LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                "", "HV-2", "33kV", null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.calculate(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("State is required");
    }

    @Test
    @DisplayName("Validation: Missing consumer category throws BadRequestException")
    void testMissingCategoryThrows() {
        LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                "UP", "", "33kV", null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.calculate(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Consumer category is required");
    }

    @Test
    @DisplayName("Validation: Missing voltage level throws BadRequestException")
    void testMissingVoltageThrows() {
        LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                "UP", "HV-2", null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.calculate(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Voltage level is required");
    }

    @Test
    @DisplayName("Valid calculation: Full Open Access landed calculation with DAM, RTM, GDAM")
    void testValidOpenAccessCalculation() {
        LocalDate date = LocalDate.of(2026, 11, 27);
        // Mock ISTS: 3.4%
        when(regulatoryChargeResolver.resolveIstsLossPercent(any(), anyList())).thenReturn(3.4);

        // Mock StateCharges: STU Loss=3.5%, WH Loss=4.0%, STU=0.35, WH=0.20, CSS=0.65, AS=0.20
        StateCharges sc = StateCharges.create();
        sc.setStuLossPercent(BigDecimal.valueOf(3.5));
        sc.setWheelingLossPercent(BigDecimal.valueOf(4.0));
        sc.setStuCharges(BigDecimal.valueOf(0.35));
        sc.setDistributionWheelingCharges(BigDecimal.valueOf(0.20));
        sc.setCrossSubsidy(BigDecimal.valueOf(0.65));
        sc.setAdditionalCharge(BigDecimal.valueOf(0.20));
        when(dataAccessService.getStateCharges(anyList(), anyString(), anyString(), any())).thenReturn(Optional.of(sc));

        // Mock CTU: 0.45
        CtuCharges ctu = CtuCharges.create();
        ctu.setCtuChargesRsPerKwh(BigDecimal.valueOf(0.45));
        when(dataAccessService.getCtuCharges(anyList(), anyInt())).thenReturn(Optional.of(ctu));

        // Mock DISCOM tariff: 7.50 normal
        when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                .thenReturn(new MatchedTariffRate("NORMAL", 7.50));

        // Request with DAM=4500 (4.50), RTM=4350 (4.35), GDAM=4200 (4.20)
        LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                "UP", "HV-2", "33 kV", null, date, 40, null, 4500.0, 4350.0, 4200.0,
                1500.0, 0.99, false, 0.02, LandingPriceCalculationRequest.CalculationMode.OPEN_ACCESS_LANDED);

        LandingPriceCalculationResponse res = service.calculate(req);

        assertThat(res).isNotNull();
        assertThat(res.state()).isEqualTo("UP");
        assertThat(res.consumerCategory()).isEqualTo("HV-2");
        assertThat(res.voltageLevel()).isEqualTo("33 kV");
        assertThat(res.timeblock()).isEqualTo(40);
        assertThat(res.calculationMode()).isEqualTo("OPEN_ACCESS_LANDED");

        // Verify compound loss coefficient
        // (1 - 0.034) * (1 - 0.035) * (1 - 0.04) = 0.966 * 0.965 * 0.96 = 0.8949024
        double expectedLossCoeff = (1 - 0.034) * (1 - 0.035) * (1 - 0.04);
        assertThat(res.losses().compoundLossCoefficient()).isCloseTo(expectedLossCoeff, within(1e-6));

        // Verify regulatory charges sum:
        // CTU(0.45) + STU(0.35) + WH(0.20) + Other(0.10) + Fee(0.02) + GST(0.0036) + Margin(0.02) + GST(0.0036) + CSS(0.65) + AS(0.20) = 1.9972
        double expectedRegSum = 0.45 + 0.35 + 0.20 + 0.10 + 0.02 + 0.0036 + 0.02 + 0.0036 + 0.65 + 0.20;
        assertThat(res.regulatoryCharges().totalRegulatoryCharges()).isCloseTo(expectedRegSum, within(1e-6));

        // Market Landed prices: (mcp + regSum) / lossCoeff
        double expectedDamLanding = (4.50 + expectedRegSum) / expectedLossCoeff;
        double expectedRtmLanding = (4.35 + expectedRegSum) / expectedLossCoeff;
        double expectedGdamLanding = (4.20 + expectedRegSum) / expectedLossCoeff;

        assertThat(res.marketLandings().get("DAM").landedPriceRsPerKwh()).isCloseTo(expectedDamLanding, within(1e-4));
        assertThat(res.marketLandings().get("RTM").landedPriceRsPerKwh()).isCloseTo(expectedRtmLanding, within(1e-4));
        assertThat(res.marketLandings().get("GDAM").landedPriceRsPerKwh()).isCloseTo(expectedGdamLanding, within(1e-4));

        // GDAM is cheapest (4.20 MCP)
        assertThat(res.bestMarketSource()).isEqualTo("GDAM");
        assertThat(res.bestMarketLandedPrice()).isCloseTo(expectedGdamLanding, within(1e-4));

        // DISCOM tariff: 7.50
        assertThat(res.discomTariff().effectiveDiscomLandingPrice()).isEqualTo(7.50);
        assertThat(res.discomTariff().todSlab()).isEqualTo("NORMAL");

        // Comparison
        double expectedSpread = 7.50 - expectedGdamLanding;
        assertThat(res.comparison().spreadVsDiscomRsPerKwh()).isCloseTo(expectedSpread, within(1e-4));
        assertThat(res.comparison().cheapestSource()).isEqualTo("GDAM");
    }

    @Test
    @DisplayName("Non-OA Threshold (< 1000 kW): DAM and RTM ineligible, GDAM eligible")
    void testNonOpenAccessThreshold() {
        when(regulatoryChargeResolver.resolveIstsLossPercent(any(), anyList())).thenReturn(0.0);
        when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                .thenReturn(new MatchedTariffRate("NORMAL", 8.00));

        // Sanctioned load 500 kW < 1000 kW
        LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                "UP", "HV-2", "11 kV", null, null, 1, null, 4000.0, 3900.0, 4200.0,
                500.0, null, null, null, null);

        LandingPriceCalculationResponse res = service.calculate(req);

        assertThat(res.marketLandings().get("DAM").eligibleForOa()).isFalse();
        assertThat(res.marketLandings().get("RTM").eligibleForOa()).isFalse();
        assertThat(res.marketLandings().get("GDAM").eligibleForOa()).isTrue();

        // Best market source must be GDAM since DAM and RTM are ineligible
        assertThat(res.bestMarketSource()).isEqualTo("GDAM");
    }

    @Test
    @DisplayName("Distinct Calculation Path: BUSBAR_COMPARISON mode uses raw MCP without charges or losses")
    void testBusbarComparisonMode() {
        when(regulatoryChargeResolver.resolveIstsLossPercent(any(), anyList())).thenReturn(3.5);
        when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                .thenReturn(new MatchedTariffRate("NORMAL", 7.00));

        LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                "UP", "HV-2", "33 kV", null, null, 1, 4.50, null, null, null,
                1500.0, null, null, null,
                LandingPriceCalculationRequest.CalculationMode.BUSBAR_COMPARISON);

        LandingPriceCalculationResponse res = service.calculate(req);

        assertThat(res.calculationMode()).isEqualTo("BUSBAR_COMPARISON");
        // In busbar mode, landed price equals raw MCP
        assertThat(res.marketLandings().get("DAM").landedPriceRsPerKwh()).isEqualTo(4.50);
        assertThat(res.marketLandings().get("DAM").lossAdjustedMcp()).isEqualTo(4.50);
        assertThat(res.bestMarketLandedPrice()).isEqualTo(4.50);
    }

    @Test
    @DisplayName("NPCL prompt payment discount: applies 0.891 factor to DISCOM landed tariff")
    void testNpclRebateDiscount() {
        when(regulatoryChargeResolver.resolveIstsLossPercent(any(), anyList())).thenReturn(0.0);
        when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                .thenReturn(new MatchedTariffRate("NORMAL", 10.00));

        LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                "UP", "HV-2", "33 kV", "NPCL", null, 1, null, null, null, null,
                null, null, null, null, null);

        LandingPriceCalculationResponse res = service.calculate(req);

        assertThat(res.discom()).isEqualTo("NPCL");
        // 10.00 * 0.90 * 0.99 = 8.91
        assertThat(res.discomTariff().effectiveDiscomLandingPrice()).isCloseTo(8.91, within(1e-4));
        assertThat(res.discomTariff().npclRebateDiscount()).isCloseTo(1.09, within(1e-4));
    }

    @Test
    @DisplayName("Fallback behaviour: when database returns empty records, applies standard regulatory fallbacks")
    void testFallbackWhenDatabaseEmpty() {
        when(dataAccessService.getStateCharges(anyList(), anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(dataAccessService.getCtuCharges(anyList(), anyInt())).thenReturn(Optional.empty());
        when(dataAccessService.getIstsCharges(any(), any())).thenReturn(Collections.emptyList());
        when(regulatoryChargeResolver.resolveIstsLossPercent(any(), anyList())).thenReturn(0.0);
        when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                .thenReturn(new MatchedTariffRate("normal", 7.50));

        LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                "UnknownState", "Industrial", "11kV", null, null, null, 4.00, null, null, null,
                null, null, null, null, null);

        LandingPriceCalculationResponse res = service.calculate(req);

        assertThat(res).isNotNull();
        // STU Loss fallback: 3.5%, Wheeling Loss fallback: 4.0%
        assertThat(res.losses().stuLossPercent()).isEqualTo(3.5);
        assertThat(res.losses().wheelingLossPercent()).isEqualTo(4.0);
        // CTU fallback: 0.496, STU fallback: 0.384, Wheeling fallback: 0.216, CSS fallback: 0.68, AS fallback: 0.216
        assertThat(res.regulatoryCharges().ctuCharge()).isEqualTo(0.496);
        assertThat(res.regulatoryCharges().stuCharge()).isEqualTo(0.384);
        assertThat(res.regulatoryCharges().wheelingCharge()).isEqualTo(0.216);
        assertThat(res.regulatoryCharges().crossSubsidy()).isEqualTo(0.68);
        assertThat(res.regulatoryCharges().additionalSurcharge()).isEqualTo(0.216);
    }

    @Test
    @DisplayName("Zero MCP handling: calculates landed price correctly for zero energy cost")
    void testZeroMcpHandling() {
        when(regulatoryChargeResolver.resolveIstsLossPercent(any(), anyList())).thenReturn(0.0);
        when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                .thenReturn(new MatchedTariffRate("NORMAL", 7.50));

        LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                "UP", "HV-2", "33 kV", null, null, 1, 0.0, null, null, null,
                null, null, null, null, null);

        LandingPriceCalculationResponse res = service.calculate(req);

        assertThat(res.marketLandings().get("DAM").mcpRsPerKwh()).isEqualTo(0.0);
        // Landed price should just be regulatory charges divided by loss coeff
        assertThat(res.marketLandings().get("DAM").landedPriceRsPerKwh()).isGreaterThan(0.0);
    }
}

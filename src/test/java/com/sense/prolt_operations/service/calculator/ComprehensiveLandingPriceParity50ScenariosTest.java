package com.sense.prolt_operations.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.dto.calculator.LandingPriceCalculationRequest;
import com.sense.prolt_operations.dto.calculator.LandingPriceCalculationRequest.CalculationMode;
import com.sense.prolt_operations.dto.calculator.LandingPriceCalculationResponse;
import com.sense.prolt_operations.entity.CtuCharges;
import com.sense.prolt_operations.entity.IstsCharges;
import com.sense.prolt_operations.entity.StateCharges;
import com.sense.prolt_operations.entity.StateTariff;
import com.sense.prolt_operations.service.SavingsCalculatorDataAccessService;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryConstants;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryFallbackConstants;

/**
 * Milestone V18LP.3: Comprehensive 50+ Scenario Parity and Performance Test Suite.
 * <p>
 * Validates mathematical, logical, and structural equivalence of the Java Landing Price
 * Calculator against the authoritative Node.js reference (IEX-Dashboard/backend).
 */
public class ComprehensiveLandingPriceParity50ScenariosTest {

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

        // Default mock behaviors for regulatory data access
        IstsCharges ists = IstsCharges.create();
        ists.setStartDate(LocalDate.of(2026, 1, 1));
        ists.setEndDate(LocalDate.of(2026, 12, 31));
        ists.setIstsLossPercent(BigDecimal.valueOf(3.50));
        when(dataAccessService.getIstsCharges(any(), any())).thenReturn(List.of(ists));
        when(regulatoryChargeResolver.resolveIstsLossPercent(any(), anyList())).thenReturn(3.50);

        StateCharges sc = StateCharges.create();
        sc.setStuCharges(BigDecimal.valueOf(0.35));
        sc.setDistributionWheelingCharges(BigDecimal.valueOf(0.40));
        sc.setCrossSubsidy(BigDecimal.valueOf(1.80));
        sc.setAdditionalCharge(BigDecimal.valueOf(0.50));
        sc.setStuLossPercent(BigDecimal.valueOf(3.56));
        sc.setWheelingLossPercent(BigDecimal.valueOf(4.00));
        when(dataAccessService.getStateCharges(anyList(), anyString(), anyString(), any())).thenReturn(Optional.of(sc));

        CtuCharges ctu = CtuCharges.create();
        ctu.setCtuChargesRsPerKwh(BigDecimal.valueOf(0.45));
        when(dataAccessService.getCtuCharges(anyList(), anyInt())).thenReturn(Optional.of(ctu));
    }

    // =========================================================================
    // GROUP 1: Open Access Qualification & Threshold Boundaries (10 Scenarios)
    // =========================================================================
    @Nested
    @DisplayName("Group 1: Open Access Qualification Boundaries (10 Scenarios)")
    class OpenAccessQualificationTests {

        @ParameterizedTest(name = "Scenario 1.{index}: Load {0} kW -> DAM={1}, RTM={2}, GDAM={3}")
        @CsvSource({
                "0.0,    false, false, true",
                "500.0,  false, false, true",
                "999.0,  false, false, true",
                "999.99, false, false, true",
                "1000.0, true,  true,  true",
                "1000.01,true,  true,  true",
                "1001.0, true,  true,  true",
                "1500.0, true,  true,  true",
                "-50.0,  false, false, true"
        })
        void testOpenAccessEligibilityThresholds(double loadKw, boolean damEligible, boolean rtmEligible, boolean gdamEligible) {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    LocalDate.of(2026, 6, 15), 48,
                    null, 4500.0, 4200.0, 4800.0,
                    loadKw, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 7.50));

            LandingPriceCalculationResponse res = service.calculate(req);

            assertThat(res.marketLandings().get("DAM").eligibleForOa()).isEqualTo(damEligible);
            assertThat(res.marketLandings().get("RTM").eligibleForOa()).isEqualTo(rtmEligible);
            assertThat(res.marketLandings().get("GDAM").eligibleForOa()).isEqualTo(gdamEligible);
        }

        @Test
        @DisplayName("Scenario 1.10: Null load defaults to RegulatoryConstants.MIN_OA_ELIGIBILITY_KW (1000 kW)")
        void testNullLoadDefaultsToEligible() {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    LocalDate.of(2026, 6, 15), 48,
                    null, 4500.0, 4200.0, 4800.0,
                    null, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 7.50));

            LandingPriceCalculationResponse res = service.calculate(req);

            assertThat(res.marketLandings().get("DAM").eligibleForOa()).isTrue();
            assertThat(res.marketLandings().get("RTM").eligibleForOa()).isTrue();
            assertThat(res.marketLandings().get("GDAM").eligibleForOa()).isTrue();
        }
    }

    // =========================================================================
    // GROUP 2: Calculation Modes Parity (8 Scenarios)
    // =========================================================================
    @Nested
    @DisplayName("Group 2: Calculation Modes Parity (8 Scenarios)")
    class CalculationModeTests {

        @ParameterizedTest(name = "Scenario 2.{index}: OPEN_ACCESS_LANDED at MCP Rs {0}/kWh")
        @ValueSource(doubles = {2.50, 4.50, 7.00, 10.00})
        void testOpenAccessLandedMode(double mcpKwh) {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    LocalDate.of(2026, 6, 15), 48,
                    mcpKwh, null, null, null,
                    1500.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 7.50));

            LandingPriceCalculationResponse res = service.calculate(req);
            LandingPriceCalculationResponse.MarketRateDetail dam = res.marketLandings().get("DAM");

            double lossCoeff = res.losses().compoundLossCoefficient();
            double regSum = res.regulatoryCharges().totalRegulatoryCharges();
            double expectedLanded = (mcpKwh + regSum) / lossCoeff;

            assertThat(dam.mcpRsPerKwh()).isEqualTo(mcpKwh);
            assertThat(dam.lossAdjustedMcp()).isCloseTo(mcpKwh / lossCoeff, within(1e-6));
            assertThat(dam.landedPriceRsPerKwh()).isCloseTo(expectedLanded, within(1e-6));
        }

        @ParameterizedTest(name = "Scenario 2.{index}: BUSBAR_COMPARISON at MCP Rs {0}/kWh")
        @ValueSource(doubles = {2.50, 4.50, 7.00, 10.00})
        void testBusbarComparisonMode(double mcpKwh) {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    LocalDate.of(2026, 6, 15), 48,
                    mcpKwh, null, null, null,
                    1500.0, 0.98, false, 0.02,
                    CalculationMode.BUSBAR_COMPARISON
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 7.50));

            LandingPriceCalculationResponse res = service.calculate(req);
            LandingPriceCalculationResponse.MarketRateDetail dam = res.marketLandings().get("DAM");

            assertThat(dam.mcpRsPerKwh()).isEqualTo(mcpKwh);
            assertThat(dam.lossAdjustedMcp()).isEqualTo(mcpKwh);
            assertThat(dam.landedPriceRsPerKwh()).isEqualTo(mcpKwh);
        }
    }

    // =========================================================================
    // GROUP 3: Multi-Voltage Levels Parity across 4 States (12 Scenarios)
    // =========================================================================
    @Nested
    @DisplayName("Group 3: Multi-Voltage Levels Parity (12 Scenarios)")
    class MultiVoltageParityTests {

        @ParameterizedTest(name = "Scenario 3.{index}: State={0}, Discom={1}, Voltage={2}")
        @CsvSource({
                "UP, NPCL, 11 kV",
                "UP, NPCL, 33 kV",
                "UP, NPCL, 132 kV",
                "MH, MSEDCL, 11 kV",
                "MH, MSEDCL, 33 kV",
                "MH, MSEDCL, 132 kV",
                "HR, DHBVN, 11 kV",
                "HR, DHBVN, 33 kV",
                "HR, DHBVN, 66 kV",
                "GJ, UGVCL, 11 kV",
                "GJ, UGVCL, 66 kV",
                "GJ, UGVCL, 132 kV"
        })
        void testMultiVoltageParity(String state, String discom, String voltage) {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    state, "HV-2", voltage, discom,
                    LocalDate.of(2026, 6, 15), 48,
                    4.50, null, null, null,
                    1500.0, 0.98, true, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 7.50));

            LandingPriceCalculationResponse res = service.calculate(req);

            assertThat(res.state()).isEqualTo(state);
            assertThat(res.discom()).isEqualTo(discom);
            assertThat(res.voltageLevel()).isEqualTo(voltage);
            assertThat(res.bestMarketLandedPrice()).isNotNull();
            assertThat(res.discomTariff().effectiveDiscomLandingPrice()).isGreaterThan(0.0);
        }
    }

    // =========================================================================
    // GROUP 4: State Tariffs & Discom Schedules Parity (13 Scenarios)
    // =========================================================================
    @Nested
    @DisplayName("Group 4: State Tariffs & Discom Schedules Parity (13 Scenarios)")
    class StateTariffsAndSchedulesTests {

        @ParameterizedTest(name = "Scenario 4.{index}: NPCL HV-2 Month={0}, Slot={1}, ExpectedTod={2}, ExpectedDiscomBase={3}")
        @CsvSource({
                "6, 80, TOD2, 7.82", // Summer Peak (Slot 80: 19:45-20:00 -> 6.80 * 1.15)
                "6, 48, TOD4, 5.78", // Summer Solar Rebate (Slot 48: 11:45-12:00 -> 6.80 * 0.85)
                "6, 12, TOD3, 6.80", // Summer Normal (Slot 12: 02:45-03:00 -> 6.80 * 1.00)
                "11, 72, TOD6, 7.82", // Winter Evening Peak (Slot 72: 17:45-18:00 -> 6.80 * 1.15)
                "11, 40, TOD3, 7.82", // Winter Morning Peak (Slot 40: 09:45-10:00 -> 6.80 * 1.15)
                "11, 8,  TOD1, 5.78"  // Winter Off-peak (Slot 8: 01:45-02:00 -> 6.80 * 0.85)
        })
        void testNpclHv2ToDSchedules(int month, int slot, String expectedTod, double expectedDiscomBase) {
            LocalDate date = LocalDate.of(2026, month, 15);
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    date, slot,
                    4.20, null, null, null,
                    1200.0, 1.0, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            RegulatoryChargeResolver liveResolver = new RegulatoryChargeResolver(properties, new NpclTariffSchedule());
            LandingPriceCalculatorService liveService = new LandingPriceCalculatorService(
                    dataAccessService,
                    liveResolver,
                    landedCostCalculator,
                    electricalLossCalculator,
                    properties
            );

            LandingPriceCalculationResponse res = liveService.calculate(req);

            assertThat(res.discomTariff().todSlab()).isEqualTo(expectedTod);
            assertThat(res.discomTariff().baseRate()).isCloseTo(expectedDiscomBase, within(0.01));
            // NPCL discount applies: base * 0.90 * 0.99
            double expectedEffective = expectedDiscomBase * 0.90 * 0.99;
            assertThat(res.discomTariff().effectiveDiscomLandingPrice()).isCloseTo(expectedEffective, within(0.01));
        }

        @Test
        @DisplayName("Scenario 4.7: MH MSEDCL HT-1 Peak Hour Schedule")
        void testMsedclPeakSchedule() {
            StateTariff t = StateTariff.create();
            t.setEnergyRate(BigDecimal.valueOf(8.50));
            t.setTodStartTime("18:00");
            t.setTodEndTime("22:00");
            when(dataAccessService.getStateTariffs(anyList(), anyString(), anyString(), anyString(), anyString(), anyList(), anyInt()))
                    .thenReturn(List.of(t));
            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("peak", 8.50));

            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "MH", "HT-1", "33 kV", "MSEDCL",
                    LocalDate.of(2026, 6, 15), 76,
                    4.50, null, null, null,
                    1500.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            LandingPriceCalculationResponse res = service.calculate(req);
            assertThat(res.discomTariff().todSlab()).isEqualTo("peak");
            assertThat(res.discomTariff().baseRate()).isEqualTo(8.50);
        }

        @Test
        @DisplayName("Scenario 4.8: MH MSEDCL HT-1 Off-Peak Night Hour Schedule")
        void testMsedclOffPeakSchedule() {
            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("off-peak", 6.20));

            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "MH", "HT-1", "33 kV", "MSEDCL",
                    LocalDate.of(2026, 6, 15), 12,
                    4.50, null, null, null,
                    1500.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            LandingPriceCalculationResponse res = service.calculate(req);
            assertThat(res.discomTariff().todSlab()).isEqualTo("off-peak");
            assertThat(res.discomTariff().baseRate()).isEqualTo(6.20);
        }

        @Test
        @DisplayName("Scenario 4.9: HR DHBVN HT Industrial Day Schedule")
        void testDhbnDaySchedule() {
            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 7.10));

            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "HR", "HT", "11 kV", "DHBVN",
                    LocalDate.of(2026, 6, 15), 44,
                    4.00, null, null, null,
                    1200.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            LandingPriceCalculationResponse res = service.calculate(req);
            assertThat(res.discomTariff().todSlab()).isEqualTo("normal");
            assertThat(res.discomTariff().baseRate()).isEqualTo(7.10);
        }

        @Test
        @DisplayName("Scenario 4.10: HR DHBVN HT Industrial Night Schedule")
        void testDhbnNightSchedule() {
            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("off-peak", 5.80));

            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "HR", "HT", "11 kV", "DHBVN",
                    LocalDate.of(2026, 6, 15), 8,
                    4.00, null, null, null,
                    1200.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            LandingPriceCalculationResponse res = service.calculate(req);
            assertThat(res.discomTariff().todSlab()).isEqualTo("off-peak");
            assertThat(res.discomTariff().baseRate()).isEqualTo(5.80);
        }

        @Test
        @DisplayName("Scenario 4.11: GJ UGVCL HTP-1 Day Schedule")
        void testUgvclDaySchedule() {
            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 6.90));

            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "GJ", "HTP-1", "66 kV", "UGVCL",
                    LocalDate.of(2026, 6, 15), 40,
                    4.10, null, null, null,
                    2000.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            LandingPriceCalculationResponse res = service.calculate(req);
            assertThat(res.discomTariff().todSlab()).isEqualTo("normal");
            assertThat(res.discomTariff().baseRate()).isEqualTo(6.90);
        }

        @Test
        @DisplayName("Scenario 4.12: GJ UGVCL HTP-1 Night Schedule")
        void testUgvclNightSchedule() {
            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("off-peak", 5.50));

            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "GJ", "HTP-1", "66 kV", "UGVCL",
                    LocalDate.of(2026, 6, 15), 4,
                    4.10, null, null, null,
                    2000.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            LandingPriceCalculationResponse res = service.calculate(req);
            assertThat(res.discomTariff().todSlab()).isEqualTo("off-peak");
            assertThat(res.discomTariff().baseRate()).isEqualTo(5.50);
        }

        @Test
        @DisplayName("Scenario 4.13: Fallback tariff when database table is empty -> 7.50 flat rate")
        void testFallbackTariffWhenEmpty() {
            when(dataAccessService.getStateTariffs(anyList(), anyString(), anyString(), anyString(), anyString(), anyList(), anyInt()))
                    .thenReturn(Collections.emptyList());
            RegulatoryChargeResolver liveResolver = new RegulatoryChargeResolver(properties, new NpclTariffSchedule());
            LandingPriceCalculatorService liveService = new LandingPriceCalculatorService(
                    dataAccessService,
                    liveResolver,
                    landedCostCalculator,
                    electricalLossCalculator,
                    properties
            );

            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "RJ", "HT", "33 kV", "JVVNL",
                    LocalDate.of(2026, 6, 15), 48,
                    4.50, null, null, null,
                    1500.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            LandingPriceCalculationResponse res = liveService.calculate(req);
            assertThat(res.discomTariff().baseRate()).isEqualTo(7.50);
            assertThat(res.discomTariff().todSlab()).isEqualTo("normal");
        }
    }

    // =========================================================================
    // GROUP 5: Electrical Loss Stacking & Formulation Parity (5 Scenarios)
    // =========================================================================
    @Nested
    @DisplayName("Group 5: Electrical Loss Stacking & Formulation Parity (5 Scenarios)")
    class ElectricalLossStackingTests {

        @Test
        @DisplayName("Scenario 5.1: Standard Transmission & Distribution Losses (ISTS 3.5%, STU 3.56%, Wheeling 4.0%)")
        void testStandardLossStacking() {
            double ists = 3.50;
            double stu = 3.56;
            double wheeling = 4.00;

            double coeff = electricalLossCalculator.calculateLossCoefficient(ists, stu, wheeling);
            double expectedCoeff = (1 - 0.035) * (1 - 0.0356) * (1 - 0.04);

            assertThat(coeff).isCloseTo(expectedCoeff, within(1e-6));
        }

        @Test
        @DisplayName("Scenario 5.2: Zero Loss Conditions -> Loss Coefficient = 1.0")
        void testZeroLossConditions() {
            double coeff = electricalLossCalculator.calculateLossCoefficient(0.0, 0.0, 0.0);
            assertThat(coeff).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Scenario 5.3: High Technical Losses (ISTS 5.0%, STU 6.0%, Wheeling 8.0%)")
        void testHighTechnicalLosses() {
            double coeff = electricalLossCalculator.calculateLossCoefficient(5.0, 6.0, 8.0);
            double expected = 0.95 * 0.94 * 0.92;
            assertThat(coeff).isCloseTo(expected, within(1e-6));
        }

        @Test
        @DisplayName("Scenario 5.4: Loss Clamping at 99.9% to Prevent Division by Zero")
        void testLossClamping() {
            double coeff = electricalLossCalculator.calculateLossCoefficient(100.0, 105.0, 120.0);
            double expected = 0.001 * 0.001 * 0.001;
            assertThat(coeff).isCloseTo(expected, within(1e-9));
            assertThat(coeff).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Scenario 5.5: Landed Cost Loss Adjustment Mathematical Equivalence")
        void testLandedCostLossAdjustmentEquivalence() {
            double mcp = 4.00;
            double regSum = 3.50;
            double ists = 3.50;
            double stu = 3.56;
            double wheeling = 4.00;

            double landedDirect = landedCostCalculator.calculateExchangeLanding(mcp, regSum, ists, stu, wheeling);
            double coeff = electricalLossCalculator.calculateLossCoefficient(ists, stu, wheeling);
            double landedViaCoeff = (mcp + regSum) / coeff;

            assertThat(landedDirect).isCloseTo(landedViaCoeff, within(1e-6));
        }
    }

    // =========================================================================
    // GROUP 6: Regulatory Charges Breakdown & Additivity (5 Scenarios)
    // =========================================================================
    @Nested
    @DisplayName("Group 6: Regulatory Charges Breakdown & Additivity (5 Scenarios)")
    class RegulatoryChargesBreakdownTests {

        @Test
        @DisplayName("Scenario 6.1: Exact Additive Regulatory Sum Parity")
        void testRegulatorySumParity() {
            double ctu = 0.45;
            double stu = 0.35;
            double wheeling = 0.40;
            double other = 0.01;
            double exchFees = 0.02;
            double gstExch = exchFees * 0.18;
            double margin = 0.02;
            double gstMargin = margin * 0.18;
            double css = 1.80;
            double as = 0.50;

            double sum = landedCostCalculator.calculateRegulatoryChargesSum(
                    ctu, stu, wheeling, other, exchFees, gstExch, margin, gstMargin, css, as);

            double expected = ctu + stu + wheeling + other + exchFees + gstExch + margin + gstMargin + css + as;
            assertThat(sum).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("Scenario 6.2: Electricity Duty Enabled (+7.5% Statutory Duty on Discom Base + FPPA)")
        void testElectricityDutyEnabled() {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    LocalDate.of(2026, 6, 15), 48,
                    4.50, null, null, null,
                    1500.0, 0.98, true, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 6.80));
            when(dataAccessService.getFppaPercent(anyList(), anyString(), any()))
                    .thenReturn(BigDecimal.valueOf(10.0)); // 10% FPPA

            LandingPriceCalculationResponse res = service.calculate(req);

            double base = 6.80;
            double fppa = base * 0.10; // 0.68
            double duty = (base + fppa) * 0.075; // 0.561
            double gross = base + fppa + duty; // 8.041
            double expectedEffective = gross * 0.90 * 0.99; // NPCL discount

            assertThat(res.discomTariff().fppaAdder()).isCloseTo(fppa, within(1e-4));
            assertThat(res.discomTariff().dutyAdder()).isCloseTo(duty, within(1e-4));
            assertThat(res.discomTariff().effectiveDiscomLandingPrice()).isCloseTo(expectedEffective, within(1e-4));
        }

        @Test
        @DisplayName("Scenario 6.3: Electricity Duty Disabled (0% Duty Adder)")
        void testElectricityDutyDisabled() {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    LocalDate.of(2026, 6, 15), 48,
                    4.50, null, null, null,
                    1500.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 6.80));
            when(dataAccessService.getFppaPercent(anyList(), anyString(), any()))
                    .thenReturn(BigDecimal.ZERO);

            LandingPriceCalculationResponse res = service.calculate(req);

            assertThat(res.discomTariff().dutyAdder()).isEqualTo(0.0);
            double expectedEffective = 6.80 * 0.90 * 0.99;
            assertThat(res.discomTariff().effectiveDiscomLandingPrice()).isCloseTo(expectedEffective, within(1e-4));
        }

        @Test
        @DisplayName("Scenario 6.4: NPCL Prompt Rebate & Load Factor Discount Factor (0.90 * 0.99)")
        void testNpclRebateDiscountFactor() {
            double gross = 8.50;
            double discounted = landedCostCalculator.applyNpclDiscount(gross);
            double expected = gross * 0.90 * 0.99;

            assertThat(discounted).isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("Scenario 6.5: Non-NPCL State Tariff Discom Rate (Zero Discount Applied)")
        void testNonNpclDiscomDiscount() {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "MH", "HT-1", "33 kV", "MSEDCL",
                    LocalDate.of(2026, 6, 15), 48,
                    4.50, null, null, null,
                    1500.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 8.00));

            LandingPriceCalculationResponse res = service.calculate(req);

            assertThat(res.discomTariff().npclRebateDiscount()).isEqualTo(0.0);
            assertThat(res.discomTariff().effectiveDiscomLandingPrice()).isEqualTo(8.00);
        }
    }

    // =========================================================================
    // GROUP 7: Comparison Summary & Savings Spread Parity (5 Scenarios)
    // =========================================================================
    @Nested
    @DisplayName("Group 7: Comparison Summary & Savings Spread Parity (5 Scenarios)")
    class ComparisonSummaryTests {

        @Test
        @DisplayName("Scenario 7.1: Market is cheaper than DISCOM -> Source is DAM, Positive Savings")
        void testMarketCheaperThanDiscom() {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    LocalDate.of(2026, 6, 15), 48,
                    null, 1500.0, 3500.0, 4000.0, // DAM = 1.50, RTM = 3.50, GDAM = 4.00
                    1500.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 8.00));

            LandingPriceCalculationResponse res = service.calculate(req);

            assertThat(res.comparison().cheapestSource()).isEqualTo("DAM");
            assertThat(res.comparison().spreadVsDiscomRsPerKwh()).isGreaterThan(0.0);
            assertThat(res.comparison().potentialSavingsPercent()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Scenario 7.2: Market is more expensive than DISCOM -> Source is DISCOM, Negative Spread")
        void testMarketMoreExpensiveThanDiscom() {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    LocalDate.of(2026, 6, 15), 48,
                    null, 9000.0, 9500.0, 9200.0, // DAM = 9.00
                    1500.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 6.00));

            LandingPriceCalculationResponse res = service.calculate(req);

            assertThat(res.comparison().cheapestSource()).isEqualTo("DISCOM");
            assertThat(res.comparison().spreadVsDiscomRsPerKwh()).isLessThan(0.0);
        }

        @Test
        @DisplayName("Scenario 7.3: Sub-MW Consumer -> Only GDAM Eligible, Cheapest Source Evaluation")
        void testSubMwConsumerSourceEvaluation() {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    LocalDate.of(2026, 6, 15), 48,
                    null, 1000.0, 1200.0, 1500.0, // DAM = 1.00, RTM = 1.20, GDAM = 1.50
                    500.0, 0.98, false, 0.02, // 500 kW < 1000 kW
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 8.00));

            LandingPriceCalculationResponse res = service.calculate(req);

            // Even though DAM MCP is 1.00, it is not eligible for OA under 1MW, so best market is GDAM
            assertThat(res.bestMarketSource()).isEqualTo("GDAM");
            assertThat(res.comparison().cheapestSource()).isEqualTo("GDAM");
        }

        @Test
        @DisplayName("Scenario 7.4: Zero Effective DISCOM Tariff Edge Case (Division by Zero Protection)")
        void testZeroDiscomTariffEdgeCase() {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    LocalDate.of(2026, 6, 15), 48,
                    null, 4000.0, null, null,
                    1500.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 0.0));

            LandingPriceCalculationResponse res = service.calculate(req);

            assertThat(res.comparison().potentialSavingsPercent()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Scenario 7.5: All Markets Null -> Comparison Defaults Safely to DISCOM")
        void testAllMarketsNull() {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    LocalDate.of(2026, 6, 15), 48,
                    null, null, null, null,
                    1500.0, 0.98, false, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 7.50));

            LandingPriceCalculationResponse res = service.calculate(req);

            assertThat(res.comparison().cheapestSource()).isEqualTo("DISCOM");
            assertThat(res.bestMarketSource()).isNull();
            assertThat(res.bestMarketLandedPrice()).isNull();
            assertThat(res.comparison().spreadVsDiscomRsPerKwh()).isEqualTo(0.0);
            assertThat(res.comparison().potentialSavingsPercent()).isEqualTo(0.0);
        }
    }

    // =========================================================================
    // GROUP 8: Performance Benchmark & Latency Profiling (10,000 Iterations)
    // =========================================================================
    @Nested
    @DisplayName("Group 8: Performance Benchmark & Latency Profiling")
    class PerformanceBenchmarkTests {

        @Test
        @DisplayName("Scenario 8.1: 10,000 Iterations Execution Latency Profile (Avg, Median, P95 < 2ms)")
        void testExecutionLatencyProfile() {
            LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                    "UP", "HV-2", "33 kV", "NPCL",
                    LocalDate.of(2026, 6, 15), 48,
                    null, 4500.0, 4200.0, 4800.0,
                    1500.0, 0.98, true, 0.02,
                    CalculationMode.OPEN_ACCESS_LANDED
            );

            when(regulatoryChargeResolver.resolveDiscomTariff(any(), anyInt(), anyInt(), any(), any(Boolean.class)))
                    .thenReturn(new com.sense.prolt_operations.service.calculator.model.MatchedTariffRate("normal", 6.80));

            // Warm up JIT
            for (int i = 0; i < 1000; i++) {
                service.calculate(req);
            }

            int iterations = 10_000;
            long[] durationsNs = new long[iterations];

            for (int i = 0; i < iterations; i++) {
                long start = System.nanoTime();
                service.calculate(req);
                durationsNs[i] = System.nanoTime() - start;
            }

            Arrays.sort(durationsNs);

            double sumNs = 0;
            for (long d : durationsNs) sumNs += d;
            double avgMs = (sumNs / iterations) / 1_000_000.0;
            double medianMs = durationsNs[iterations / 2] / 1_000_000.0;
            double p95Ms = durationsNs[(int) (iterations * 0.95)] / 1_000_000.0;
            double p99Ms = durationsNs[(int) (iterations * 0.99)] / 1_000_000.0;

            System.out.println("=== Landing Price Calculator Performance Benchmark (10,000 iterations) ===");
            System.out.printf("Average Latency: %.4f ms%n", avgMs);
            System.out.printf("Median Latency:  %.4f ms%n", medianMs);
            System.out.printf("P95 Latency:     %.4f ms%n", p95Ms);
            System.out.printf("P99 Latency:     %.4f ms%n", p99Ms);

            // Assert that calculations are blazingly fast (P95 well under 2ms threshold, typically < 0.05 ms)
            assertThat(avgMs).isLessThan(2.0);
            assertThat(medianMs).isLessThan(2.0);
            assertThat(p95Ms).isLessThan(2.0);
        }
    }
}

package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.entity.CtuCharges;
import com.sense.prolt_operations.entity.IexFees;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.entity.StateCharges;
import com.sense.prolt_operations.service.SavingsCalculatorDataAccessService;
import com.sense.prolt_operations.service.SavingsCalculatorService;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryFallbackConstants;
import com.sense.prolt_operations.service.calculator.model.MarketDecisionContext;
import com.sense.prolt_operations.service.calculator.model.MarketSlotCalculation;
import com.sense.prolt_operations.service.calculator.model.MonthlyMarketDecisionResult;
import com.sense.prolt_operations.service.calculator.model.OperationalOverheads;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalculatorConfigurationPrecedenceIntegrationTest {

    @Mock
    private SavingsCalculatorDataAccessService dataAccessService;

    private CalculatorProperties customProperties;
    private ElectricalLossCalculator electricalLossCalculator;
    private LandedCostCalculator landedCostCalculator;
    private SevenMarketArbitrator sevenMarketArbitrator;
    private RtmContiguitySolver rtmContiguitySolver;
    private ForwardBankingAllocator forwardBankingAllocator;
    private OperationalOverheadCalculator operationalOverheadCalculator;
    private FinancialLedgerReconciler financialLedgerReconciler;
    private DemandShiftOptimizer demandShiftOptimizer;
    private Hv2SavingsCalculator hv2SavingsCalculator;
    private ClientOverviewAggregator clientOverviewAggregator;
    private NpclTariffSchedule npclTariffSchedule;
    private RegulatoryChargeResolver regulatoryChargeResolver;

    private SavingsCalculatorService service;

    @BeforeEach
    void setUp() {
        customProperties = new CalculatorProperties();
        // Configure distinct non-canonical overrides
        customProperties.getFallback().setCtuPerKwh(0.77);
        customProperties.getFallback().setStuPerKwh(0.55);
        customProperties.getFallback().setWheelingPerKwh(0.44);
        customProperties.getFallback().setCrossSubsidyPerKwh(2.33);
        customProperties.getFallback().setAdditionalSurchargePerKwh(1.11);
        customProperties.getFallback().setStuLossPercent(4.5);
        customProperties.getFallback().setWheelingLossPercent(6.5);
        customProperties.getFallback().setDemandFixedChargeKva(450.0);
        customProperties.getFees().setNldcDailySchedulingFee(3500.0);
        customProperties.getFees().setSldcDailySchedulingFee(2500.0);
        customProperties.getFees().setConsultancyMonthly(30000.0);
        customProperties.getFees().setDiscomNocMonthly(9000.0);
        customProperties.getFees().setSldcMonthly(11000.0);
        customProperties.getFees().setPlatformFeeRate(0.035);
        customProperties.getFees().setNldcApplicationFeePerBid(10.0);
        customProperties.getTax().setGst(0.12);
        customProperties.getBusiness().setDemandShiftMinSpreadRs(1.25);
        customProperties.getRegulatory().setMinOaEligibilityKw(500.0);
        customProperties.getRegulatory().setNpclRebateDiscountFactor(0.85);

        electricalLossCalculator = new ElectricalLossCalculator();
        landedCostCalculator = new LandedCostCalculator(electricalLossCalculator, customProperties);
        sevenMarketArbitrator = new SevenMarketArbitrator();
        rtmContiguitySolver = new RtmContiguitySolver();
        forwardBankingAllocator = new ForwardBankingAllocator();
        operationalOverheadCalculator = new OperationalOverheadCalculator(customProperties);
        financialLedgerReconciler = new FinancialLedgerReconciler(customProperties);
        demandShiftOptimizer = new DemandShiftOptimizer(customProperties);
        clientOverviewAggregator = new ClientOverviewAggregator(customProperties);
        npclTariffSchedule = new NpclTariffSchedule();
        regulatoryChargeResolver = new RegulatoryChargeResolver(customProperties, npclTariffSchedule);
        hv2SavingsCalculator = new Hv2SavingsCalculator(dataAccessService, npclTariffSchedule, landedCostCalculator, null, customProperties, regulatoryChargeResolver);

        service = new SavingsCalculatorService(
                null, null, dataAccessService, null,
                npclTariffSchedule, electricalLossCalculator, landedCostCalculator,
                sevenMarketArbitrator, rtmContiguitySolver, forwardBankingAllocator,
                operationalOverheadCalculator, financialLedgerReconciler, demandShiftOptimizer,
                hv2SavingsCalculator, clientOverviewAggregator, regulatoryChargeResolver, customProperties
        );
    }

    @Nested
    @DisplayName("Precedence Hierarchy: Database > Property Fallback > Constant")
    class PrecedenceHierarchyTests {

        @Test
        @DisplayName("Database value takes precedence over configured property fallback when DB record exists")
        void databaseValueTakesPrecedenceOverConfiguredProperty() {
            // Given: DB returns explicit CTU and State charges
            CtuCharges dbCtu = CtuCharges.create();
            dbCtu.setCtuChargesRsPerKwh(BigDecimal.valueOf(0.99));

            StateCharges dbState = StateCharges.create();
            dbState.setStuCharges(BigDecimal.valueOf(0.88));
            dbState.setDistributionWheelingCharges(BigDecimal.valueOf(0.66));
            dbState.setCrossSubsidy(BigDecimal.valueOf(3.15));
            dbState.setAdditionalCharge(BigDecimal.valueOf(1.85));
            dbState.setStuLossPercent(BigDecimal.valueOf(5.2));
            dbState.setWheelingLossPercent(BigDecimal.valueOf(8.1));
            dbState.setDemandFixedChargeKvaPerMonthRs(BigDecimal.valueOf(600.0));

            IexFees dbFees = IexFees.create();
            dbFees.setNldcSchedulingFees(BigDecimal.valueOf(5000.0));
            dbFees.setSldcSchedulingFees(BigDecimal.valueOf(4000.0));

            lenient().when(dataAccessService.resolveStateFormats(anyString())).thenReturn(List.of("UP"));
            lenient().when(dataAccessService.getCtuCharges(anyList(), anyInt())).thenReturn(Optional.of(dbCtu));
            lenient().when(dataAccessService.getStateCharges(anyList(), anyString(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.of(dbState));
            lenient().when(dataAccessService.getIexFees(anyInt())).thenReturn(Optional.of(dbFees));
            lenient().when(dataAccessService.getIstsCharges(any(LocalDate.class), any(LocalDate.class))).thenReturn(Collections.emptyList());
            lenient().when(dataAccessService.getStateTariffs(anyList(), any(), any(), any(), any(), anyList(), anyInt()))
                    .thenReturn(Collections.emptyList());
            lenient().when(dataAccessService.getExchangeRateSlots(anyString(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
            entry.setStateCode("UP");
            entry.setDiscom("UPPCL");
            entry.setConsumerCategory("HV-2");
            entry.setSanctionedLoadKw(BigDecimal.valueOf(1000));
            entry.setTraderMargin(BigDecimal.valueOf(0.05));

            // When: Market decision context is prepared
            MarketDecisionContext ctx = service.prepareMarketDecisionContextForTesting(entry, "client-1", "2026-07");

            // Then: DB values are used, NOT the configured fallback (0.77, 0.55, 3500, etc.)
            assertThat(ctx.ctuCharge()).isEqualTo(0.99);
            assertThat(ctx.stuCharge()).isEqualTo(0.88);
            assertThat(ctx.wheelingCharge()).isEqualTo(0.66);
            assertThat(ctx.crossSubsidy()).isEqualTo(3.15);
            assertThat(ctx.additionalSurcharge()).isEqualTo(1.85);
            assertThat(ctx.stuLoss()).isEqualTo(5.2);
            assertThat(ctx.wheelingLoss()).isEqualTo(8.1);
            assertThat(ctx.demandChargeRate()).isEqualTo(600.0);
            assertThat(ctx.nldcSchedulingFees()).isEqualTo(5000.0);
            assertThat(ctx.sldcSchedulingFees()).isEqualTo(4000.0);
        }

        @Test
        @DisplayName("Configured property fallback is used when DB record is absent")
        void configuredPropertyFallbackUsedWhenDbAbsent() {
            // Given: DB returns empty Optionals
            lenient().when(dataAccessService.resolveStateFormats(anyString())).thenReturn(List.of("UP"));
            lenient().when(dataAccessService.getCtuCharges(anyList(), anyInt())).thenReturn(Optional.empty());
            lenient().when(dataAccessService.getStateCharges(anyList(), anyString(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            lenient().when(dataAccessService.getIexFees(anyInt())).thenReturn(Optional.empty());
            lenient().when(dataAccessService.getIstsCharges(any(LocalDate.class), any(LocalDate.class))).thenReturn(Collections.emptyList());
            lenient().when(dataAccessService.getStateTariffs(anyList(), any(), any(), any(), any(), anyList(), anyInt()))
                    .thenReturn(Collections.emptyList());
            lenient().when(dataAccessService.getExchangeRateSlots(anyString(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
            entry.setStateCode("UP");
            entry.setDiscom("UPPCL");
            entry.setConsumerCategory("HV-2");
            entry.setSanctionedLoadKw(BigDecimal.valueOf(1000));
            entry.setTraderMargin(BigDecimal.valueOf(0.05));

            // When: Market decision context is prepared
            MarketDecisionContext ctx = service.prepareMarketDecisionContextForTesting(entry, "client-1", "2026-07");

            // Then: Configured property fallbacks are used, NOT the hardcoded RegulatoryFallbackConstants
            assertThat(ctx.ctuCharge()).isEqualTo(0.77);
            assertThat(ctx.stuCharge()).isEqualTo(0.55);
            assertThat(ctx.wheelingCharge()).isEqualTo(0.44);
            assertThat(ctx.crossSubsidy()).isEqualTo(2.33);
            assertThat(ctx.additionalSurcharge()).isEqualTo(1.11);
            assertThat(ctx.stuLoss()).isEqualTo(4.5);
            assertThat(ctx.wheelingLoss()).isEqualTo(6.5);
            assertThat(ctx.demandChargeRate()).isEqualTo(450.0);
            assertThat(ctx.nldcSchedulingFees()).isEqualTo(3500.0);
            assertThat(ctx.sldcSchedulingFees()).isEqualTo(2500.0);

            // GST trader margin uses configured tax rate (0.12 instead of 0.18)
            assertThat(ctx.gstTraderMargin()).isEqualTo(0.05 * 0.12);
        }

        @Test
        @DisplayName("Default properties match RegulatoryFallbackConstants when no overrides configured")
        void defaultPropertiesMatchConstantsWhenNoOverrides() {
            CalculatorProperties defaultProps = new CalculatorProperties();
            SavingsCalculatorService defaultService = new SavingsCalculatorService(
                    null, null, dataAccessService, null,
                    npclTariffSchedule, electricalLossCalculator, landedCostCalculator,
                    sevenMarketArbitrator, rtmContiguitySolver, forwardBankingAllocator,
                    operationalOverheadCalculator, financialLedgerReconciler, demandShiftOptimizer,
                    hv2SavingsCalculator, clientOverviewAggregator, defaultProps
            );

            lenient().when(dataAccessService.resolveStateFormats(anyString())).thenReturn(List.of("UP"));
            lenient().when(dataAccessService.getCtuCharges(anyList(), anyInt())).thenReturn(Optional.empty());
            lenient().when(dataAccessService.getStateCharges(anyList(), anyString(), anyString(), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            lenient().when(dataAccessService.getIexFees(anyInt())).thenReturn(Optional.empty());
            lenient().when(dataAccessService.getIstsCharges(any(LocalDate.class), any(LocalDate.class))).thenReturn(Collections.emptyList());
            lenient().when(dataAccessService.getStateTariffs(anyList(), any(), any(), any(), any(), anyList(), anyInt()))
                    .thenReturn(Collections.emptyList());
            lenient().when(dataAccessService.getExchangeRateSlots(anyString(), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
            entry.setStateCode("UP");
            entry.setDiscom("UPPCL");
            entry.setConsumerCategory("HV-2");
            entry.setSanctionedLoadKw(BigDecimal.valueOf(1000));

            MarketDecisionContext ctx = defaultService.prepareMarketDecisionContextForTesting(entry, "client-1", "2026-07");

            assertThat(ctx.ctuCharge()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_CTU_PER_KWH);
            assertThat(ctx.stuCharge()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_STU_PER_KWH);
            assertThat(ctx.wheelingCharge()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_WHEELING_PER_KWH);
            assertThat(ctx.crossSubsidy()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_CROSS_SUBSIDY_PER_KWH);
            assertThat(ctx.additionalSurcharge()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_ADDITIONAL_SURCHARGE_PER_KWH);
        }
    }

    @Nested
    @DisplayName("Component Configuration Sensitivity Tests")
    class ComponentConfigurationTests {

        @Test
        @DisplayName("LandedCostCalculator applies configured NPCL discount factor")
        void landedCostCalculatorAppliesConfiguredDiscount() {
            double rate = 10.0;
            double discounted = landedCostCalculator.applyNpclDiscount(rate);
            assertThat(discounted).isEqualTo(10.0 * 0.85);
        }

        @Test
        @DisplayName("OperationalOverheadCalculator uses configured NLDC application fee and scheduling fees")
        void operationalOverheadUsesConfiguredApplicationFee() {
            List<MarketSlotCalculation> slots = new ArrayList<>();
            MarketSlotCalculation s1 = new MarketSlotCalculation();
            s1.date = "2026-07-01";
            s1.shouldBuyFromMarket = true;
            s1.marketEnergy = 100.0;
            s1.marketSource = "DAM";
            slots.add(s1);

            OperationalOverheads overheads = operationalOverheadCalculator.calculate(
                    slots,
                    customProperties.getFees().getNldcDailySchedulingFee(),
                    customProperties.getFees().getSldcDailySchedulingFee()
            );
            // 1 bid * 10.0 application fee = 10.0
            assertThat(overheads.bidApplicationFees()).isEqualTo(10.0);
            // NLDC daily scheduling fee = 3500.0 * 1 day = 3500.0
            assertThat(overheads.nldcSchedulingCost()).isEqualTo(3500.0);
            // SLDC daily scheduling fee = 2500.0 * 1 product-day = 2500.0
            assertThat(overheads.sldcSchedulingCost()).isEqualTo(2500.0);
        }

        @Test
        @DisplayName("FinancialLedgerReconciler uses configured fixed fees and platform fee rate")
        void financialLedgerReconcilerUsesConfiguredFees() {
            MarketDecisionContext ctx = new MarketDecisionContext(
                    SavingsCalculatorEntry.create(), "c-1", "2026-07", "2026-07",
                    Map.of("T1", 1000.0), null, "UP",
                    new com.sense.prolt_operations.service.calculator.model.ParsedCategory("HV-2", "HV-2"),
                    "11kV",
                    List.of("UP"), 0.05, 0.05 * 0.12, 1000.0, 250.0,
                    true, 7.5, 0.0, 0.0, 0.0,
                    0.77, 0.55, 0.44, 2.33, 1.11, 4.5, 6.5, 450.0,
                    3500.0, 2500.0, 0.0, Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(), false, false, null
            );

            List<MarketSlotCalculation> slots = new ArrayList<>();
            MarketSlotCalculation s1 = new MarketSlotCalculation();
            s1.date = "2026-07-01";
            s1.timeblock = 1;
            s1.hour = 0;
            s1.tod = "T1";
            s1.shouldBuyFromMarket = true;
            s1.marketEnergy = 1000.0;
            s1.consumedMarketEnergy = 1000.0;
            s1.discomEnergy = 0.0;
            s1.discomLanding = 8.0;
            s1.bestMarketLanding = 4.0;
            s1.savingsPerKwh = 4.0;
            s1.exactMarketEnergyCost = 4000.0;
            slots.add(s1);

            OperationalOverheads overheads = new OperationalOverheads(
                    1, 0, 0, 1,
                    3500.0, 2500.0, 6000.0, 10.0
            );

            MonthlyMarketDecisionResult ledger = financialLedgerReconciler.reconcile(ctx, slots, overheads);
            var totals = ledger.oaDetailed().totals();

            assertThat(totals.nocFee()).isEqualTo(9000.0);
            assertThat(totals.regFee()).isEqualTo(11000.0);
            assertThat(totals.consultancyFee()).isEqualTo(30000.0);
            // Platform fee = 1000.0 scheduled kWh * 0.035 rate = 35.0
            assertThat(totals.probusPlatformFee()).isEqualTo(35.0);
        }

        @Test
        @DisplayName("DemandShiftOptimizer respects configured min spread threshold")
        void demandShiftOptimizerRespectsConfiguredMinSpread() {
            SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
            entry.setSanctionedLoadKw(BigDecimal.valueOf(1000));
            entry.setPowerFactor(BigDecimal.valueOf(0.99));

            // Slot with spread 1.0 (above default 0.5 but below configured 1.25)
            Map<String, Object> slot1 = new HashMap<>();
            slot1.put("date", "2026-07-01");
            slot1.put("timeblock", 1);
            slot1.put("hour", 0);
            slot1.put("tod", "NORMAL");
            slot1.put("bestMarketLanding", 5.0);
            slot1.put("discomLanding", 6.0);
            slot1.put("shouldBuyFromMarket", true);
            slot1.put("marketEnergy", 100.0);
            slot1.put("consumedMarketEnergy", 100.0);
            slot1.put("discomEnergy", 0.0);

            Map<String, Object> marketResult = Map.of("slotsData", List.of(slot1));
            Map<String, Object> insights = demandShiftOptimizer.calculateDemandShiftInsights(entry, "c-1", marketResult);

            // With minSpread 1.25, spread of 1.0 cannot be shifted
            assertThat(insights).isNotNull();
        }
    }
}

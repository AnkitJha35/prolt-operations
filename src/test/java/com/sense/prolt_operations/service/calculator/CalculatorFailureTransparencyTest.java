package com.sense.prolt_operations.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.service.SavingsCalculatorDataAccessService;
import com.sense.prolt_operations.service.calculator.exception.CalculatorExecutionException;
import com.sense.prolt_operations.service.calculator.exception.CalculatorInputException;
import com.sense.prolt_operations.service.calculator.model.DemandShiftInsights;

@DisplayName("Calculator Failure Transparency & Error Safety Tests")
class CalculatorFailureTransparencyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ClientOverviewAggregator clientOverviewAggregator;
    private DemandShiftOptimizer demandShiftOptimizer;
    private Hv2SavingsCalculator hv2SavingsCalculator;

    @BeforeEach
    void setUp() {
        clientOverviewAggregator = new ClientOverviewAggregator();
        demandShiftOptimizer = new DemandShiftOptimizer();
        SavingsCalculatorDataAccessService dataAccessService = Mockito.mock(SavingsCalculatorDataAccessService.class);
        NpclTariffSchedule npclTariffSchedule = new NpclTariffSchedule();
        LandedCostCalculator landedCostCalculator = new LandedCostCalculator();
        hv2SavingsCalculator = new Hv2SavingsCalculator(dataAccessService, npclTariffSchedule, landedCostCalculator, objectMapper);
    }

    private SavingsCalculatorEntry createTestEntry() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("test-entry-fail-safe");
        entry.setClientName("Precision Components Ltd");
        entry.setIndustryName("Engineering");
        entry.setSanctionedLoadKw(BigDecimal.valueOf(1000.0));
        entry.setPowerFactor(BigDecimal.valueOf(0.95));
        entry.setStateCode("UP");
        entry.setDiscom("NPCL");
        entry.setConsumerCategory("HV-2 | Heavy Industrial");
        entry.setVoltageLevel("11 kV");
        return entry;
    }

    // -------------------------------------------------------------------------
    // 1. Hv2SavingsCalculator.calculateSavingsAllMonths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("calculateSavingsAllMonths: Throws CalculatorExecutionException when all months fail")
    void testHv2SavingsAllMonths_AllMonthsFail_ThrowsCalculatorExecutionException() {
        SavingsCalculatorEntry entry = createTestEntry();
        Map<String, Map<String, Object>> todConsumptions = new LinkedHashMap<>();
        todConsumptions.put("2026-05", Map.of("NORMAL", 10000.0));
        todConsumptions.put("2026-06", Map.of("NORMAL", 12000.0));

        assertThatThrownBy(() -> hv2SavingsCalculator.calculateSavingsAllMonths(
                entry,
                todConsumptions,
                (m, d) -> {
                    throw new RuntimeException("Simulated tariff DB outage for " + m);
                }
        ))
        .isInstanceOf(CalculatorExecutionException.class)
        .satisfies(ex -> {
            CalculatorExecutionException cee = (CalculatorExecutionException) ex;
            assertThat(cee.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(cee.getCalculationType()).isEqualTo("HV-2 Savings");
            assertThat(cee.getClientId()).isEqualTo("test-entry-fail-safe");
            assertThat(cee.getMessage()).contains("All 2 monthly calculations failed");
        });
    }

    @Test
    @DisplayName("calculateSavingsAllMonths: Partial failure accumulates successful months and logs warning")
    void testHv2SavingsAllMonths_PartialFailure_AccumulatesSuccessfulMonths() {
        SavingsCalculatorEntry entry = createTestEntry();
        Map<String, Map<String, Object>> todConsumptions = new LinkedHashMap<>();
        todConsumptions.put("2026-05", Map.of("NORMAL", 10000.0));
        todConsumptions.put("2026-06", Map.of("NORMAL", 12000.0));

        Map<String, Object> m1 = Map.of(
                "totalSavings", 10000.0,
                "totalOptimizedCost", 50000.0,
                "totalBaselineCost", 60000.0,
                "totalEnergyKwh", 10000.0,
                "totalMarketEnergyKwh", 8000.0
        );

        Map<String, Object> result = hv2SavingsCalculator.calculateSavingsAllMonths(
                entry,
                todConsumptions,
                (m, d) -> {
                    if ("2026-06".equals(m)) {
                        throw new RuntimeException("Simulated corrupt slot data for 2026-06");
                    }
                    return m1;
                }
        );

        assertThat(result.get("totalSavings")).isEqualTo(10000.0);
        assertThat(result.get("totalBaselineCost")).isEqualTo(60000.0);
        assertThat(result.get("totalEnergyKwh")).isEqualTo(10000.0);
    }

    // -------------------------------------------------------------------------
    // 2. ClientOverviewAggregator.aggregateClientOverview
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("aggregateClientOverview: Single month failure marks month as failed without crashing overview")
    void testClientOverview_PartialMonthFailure_PopulatesFailureRecord() {
        SavingsCalculatorEntry entry = createTestEntry();
        Map<String, Map<String, Object>> todConsumptions = new LinkedHashMap<>();
        todConsumptions.put("2026-05", Map.of("NORMAL", 10000.0));
        todConsumptions.put("2026-06", Map.of("NORMAL", 12000.0));

        Map<String, Object> successResult = Map.of(
                "totalSavings", 15000.0,
                "oaDetailed", Map.of("totals", Collections.emptyMap()),
                "totalBaselineCost", 80000.0,
                "demandCharge", 10000.0,
                "electricityDuty", 5000.0,
                "miscellaneousCharges", 0.0,
                "peakDemand", 800.0,
                "demandChargeRate", 250.0,
                "totalEnergyKwh", 10000.0,
                "totalMarketEnergyKwh", 9000.0
        );

        Map<String, Object> overview = clientOverviewAggregator.aggregateClientOverview(
                entry,
                todConsumptions,
                m -> {
                    if ("2026-06".equals(m)) {
                        throw new RuntimeException("Missing state charges record");
                    }
                    return successResult;
                }
        );

        assertThat(overview.get("clientName")).isEqualTo("Precision Components Ltd");
        assertThat(overview.get("totalSavings")).isEqualTo(15000.0);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> months = (List<Map<String, Object>>) overview.get("months");
        assertThat(months).hasSize(2);

        Map<String, Object> month1 = months.get(0);
        assertThat(month1.get("month")).isEqualTo("2026-05");
        assertThat(month1.get("savings")).isEqualTo(15000.0);
        assertThat(month1.get("error")).isNull();

        Map<String, Object> month2 = months.get(1);
        assertThat(month2.get("month")).isEqualTo("2026-06");
        assertThat(month2.get("savings")).isEqualTo(0.0);
        assertThat(month2.get("error")).isEqualTo("Calculation failed");
    }

    // -------------------------------------------------------------------------
    // 3. ClientOverviewAggregator.aggregateMarketDecisionAllMonths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("aggregateMarketDecisionAllMonths: Throws CalculatorExecutionException when all months fail")
    void testMarketDecisionAllMonths_AllMonthsFail_ThrowsCalculatorExecutionException() {
        SavingsCalculatorEntry entry = createTestEntry();
        Map<String, Map<String, Object>> todConsumptions = new LinkedHashMap<>();
        todConsumptions.put("2026-05", Map.of("NORMAL", 10000.0));

        assertThatThrownBy(() -> clientOverviewAggregator.aggregateMarketDecisionAllMonths(
                entry,
                todConsumptions,
                m -> {
                    throw new RuntimeException("Market decision simulation failure");
                }
        ))
        .isInstanceOf(CalculatorExecutionException.class)
        .satisfies(ex -> {
            CalculatorExecutionException cee = (CalculatorExecutionException) ex;
            assertThat(cee.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(cee.getCalculationType()).isEqualTo("Market Decision");
            assertThat(cee.getClientId()).isEqualTo("test-entry-fail-safe");
        });
    }

    // -------------------------------------------------------------------------
    // 4. DemandShiftOptimizer.calculateDemandShiftAllMonths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("calculateDemandShiftAllMonths: Throws CalculatorExecutionException when all months fail")
    void testDemandShiftAllMonths_AllMonthsFail_ThrowsCalculatorExecutionException() {
        SavingsCalculatorEntry entry = createTestEntry();
        Map<String, Map<String, Object>> todConsumptions = new LinkedHashMap<>();
        todConsumptions.put("2026-05", Map.of("NORMAL", 10000.0));

        assertThatThrownBy(() -> demandShiftOptimizer.calculateDemandShiftAllMonths(
                entry,
                entry.getId(),
                todConsumptions,
                m -> {
                    throw new RuntimeException("Optimization engine calculation error");
                }
        ))
        .isInstanceOf(CalculatorExecutionException.class)
        .satisfies(ex -> {
            CalculatorExecutionException cee = (CalculatorExecutionException) ex;
            assertThat(cee.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(cee.getCalculationType()).isEqualTo("Demand Shift Insights");
        });
    }

    // -------------------------------------------------------------------------
    // 5. CalculatorUtils.parseTodConsumptions (Malformed JSON handling)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("parseTodConsumptions: Throws CalculatorInputException on malformed JSON")
    void testParseTodConsumptions_MalformedJson_ThrowsCalculatorInputException() {
        String malformedJson = "{ \"2026-05\": { invalid_json } }";

        assertThatThrownBy(() -> CalculatorUtils.parseTodConsumptions(malformedJson, objectMapper))
                .isInstanceOf(CalculatorInputException.class)
                .satisfies(ex -> {
                    CalculatorInputException cie = (CalculatorInputException) ex;
                    assertThat(cie.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(cie.getMessage()).contains("Malformed TOD consumption data JSON");
                });
    }

    @Test
    @DisplayName("parseTodConsumptions: Returns empty map on null or blank input")
    void testParseTodConsumptions_NullOrBlank_ReturnsEmptyMap() {
        assertThat(CalculatorUtils.parseTodConsumptions(null, objectMapper)).isEmpty();
        assertThat(CalculatorUtils.parseTodConsumptions("", objectMapper)).isEmpty();
        assertThat(CalculatorUtils.parseTodConsumptions("   ", objectMapper)).isEmpty();
    }

    @Test
    @DisplayName("parseTodConsumptions: Successfully parses valid JSON")
    void testParseTodConsumptions_ValidJson_ReturnsParsedMap() {
        String validJson = "{\"2026-05\": {\"NORMAL\": 20000.0, \"PEAK\": 10000.0}}";
        Map<String, Map<String, Object>> result = CalculatorUtils.parseTodConsumptions(validJson, objectMapper);

        assertThat(result).hasSize(1);
        assertThat(result).containsKey("2026-05");
        assertThat(result.get("2026-05")).containsEntry("NORMAL", 20000.0);
    }
}

package com.sense.prolt_operations.service.calculator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.service.calculator.model.DemandShiftInsights;
import com.sense.prolt_operations.entity.StateCharges;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.service.SavingsCalculatorDataAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@DisplayName("Hv2SavingsCalculator Unit Tests")
class Hv2SavingsCalculatorTest {

    private SavingsCalculatorDataAccessService dataAccessService;
    private NpclTariffSchedule npclTariffSchedule;
    private LandedCostCalculator landedCostCalculator;
    private ObjectMapper objectMapper;
    private Hv2SavingsCalculator calculator;

    @BeforeEach
    void setUp() {
        dataAccessService = Mockito.mock(SavingsCalculatorDataAccessService.class);
        npclTariffSchedule = new NpclTariffSchedule();
        landedCostCalculator = new LandedCostCalculator();
        objectMapper = new ObjectMapper();
        calculator = new Hv2SavingsCalculator(dataAccessService, npclTariffSchedule, landedCostCalculator, objectMapper);
    }

    private SavingsCalculatorEntry createTestEntry(String todJson) {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("entry-hv2");
        entry.setClientName("Indo German Forgings");
        entry.setSanctionedLoadKw(BigDecimal.valueOf(1200.0));
        entry.setPowerFactor(BigDecimal.valueOf(0.96));
        entry.setStateCode("UP");
        entry.setDiscom("NPCL");
        entry.setConsumerCategory("HV-2 | Heavy Industrial");
        entry.setVoltageLevel("33 kV");
        entry.setTodConsumptions(todJson);
        return entry;
    }

    @Test
    @DisplayName("Should throw BadRequestException if ToD consumption data is empty or missing")
    void testEmptyConsumptionThrowsBadRequest() {
        SavingsCalculatorEntry entry = createTestEntry("{}");

        assertThatThrownBy(() -> calculator.calculateSavingsHV2(entry, "2026-06", (DemandShiftInsights) null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No TOD consumption data found");
    }

    @Test
    @DisplayName("Should throw BadRequestException if target month is missing from consumption data")
    void testTargetMonthNotFoundThrowsBadRequest() {
        SavingsCalculatorEntry entry = createTestEntry("{\"2026-05\": {\"NORMAL\": 1000.0}}");

        assertThatThrownBy(() -> calculator.calculateSavingsHV2(entry, "2026-07", (DemandShiftInsights) null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No consumption data found for month 2026-07");
    }

    @Test
    @DisplayName("Should execute HV-2 dispatch simulation successfully and produce sorted slot results")
    void testCalculateSavingsHV2SuccessfulExecution() {
        String todJson = "{\"2026-06\": {\"NORMAL\": 20000.0, \"PEAK\": 10000.0, \"OFFPEAK\": 15000.0}}";
        SavingsCalculatorEntry entry = createTestEntry(todJson);

        when(dataAccessService.resolveStateFormats(anyString())).thenReturn(List.of("UP"));

        StateCharges sc = StateCharges.create();
        sc.setStuLossPercent(BigDecimal.valueOf(2.5));
        sc.setWheelingLossPercent(BigDecimal.valueOf(4.0));
        when(dataAccessService.getStateCharges(any(), any(), any(), any())).thenReturn(Optional.of(sc));

        when(dataAccessService.getIstsCharges(any(), any())).thenReturn(Collections.emptyList());
        when(dataAccessService.getStateTariffs(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(Collections.emptyList());
        when(dataAccessService.getExchangeRateSlots(anyString(), any(), any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = calculator.calculateSavingsHV2(entry, "2026-06", (DemandShiftInsights) null);

        assertThat(result).isNotNull();
        assertThat(result.get("clientId")).isEqualTo("entry-hv2");
        assertThat((double) result.get("totalEnergyKwh")).isGreaterThan(0.0);
        assertThat((double) result.get("totalBaselineCost")).isGreaterThan(0.0);
        assertThat((double) result.get("totalOptimizedCost")).isGreaterThan(0.0);

        Map<?, ?> todGroups = (Map<?, ?>) result.get("todGroups");
        assertThat(todGroups).isNotEmpty();

        List<?> sortedMonthlyList = (List<?>) result.get("sortedMonthlyList");
        assertThat(sortedMonthlyList).isNotEmpty();
    }

    @Test
    @DisplayName("Should calculate baseline DISCOM cost matching the HV-2 baseline bill")
    void testCalculateBaselineCost() {
        String todJson = "{\"2026-06\": {\"NORMAL\": 20000.0}}";
        SavingsCalculatorEntry entry = createTestEntry(todJson);

        when(dataAccessService.resolveStateFormats(anyString())).thenReturn(List.of("UP"));
        when(dataAccessService.getStateCharges(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(dataAccessService.getIstsCharges(any(), any())).thenReturn(Collections.emptyList());
        when(dataAccessService.getStateTariffs(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(Collections.emptyList());
        when(dataAccessService.getExchangeRateSlots(anyString(), any(), any())).thenReturn(Collections.emptyList());

        double baselineCost = calculator.calculateBaselineCost(entry, "2026-06");
        assertThat(baselineCost).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should aggregate HV-2 savings across all months")
    void testCalculateSavingsAllMonths() {
        SavingsCalculatorEntry entry = createTestEntry("{}");

        Map<String, Map<String, Object>> todConsumptions = new LinkedHashMap<>();
        todConsumptions.put("2026-05", Map.of("NORMAL", 20000.0));
        todConsumptions.put("2026-06", Map.of("NORMAL", 25000.0));

        Map<String, Object> m1 = Map.of(
                "totalSavings", 15000.0,
                "totalOptimizedCost", 85000.0,
                "totalBaselineCost", 100000.0,
                "totalEnergyKwh", 20000.0,
                "totalMarketEnergyKwh", 18000.0
        );

        Map<String, Object> m2 = Map.of(
                "totalSavings", 20000.0,
                "totalOptimizedCost", 100000.0,
                "totalBaselineCost", 120000.0,
                "totalEnergyKwh", 25000.0,
                "totalMarketEnergyKwh", 22000.0
        );

        Map<String, Object> allMonths = calculator.calculateSavingsAllMonths(
                entry,
                todConsumptions,
                (m, unused) -> "2026-05".equals(m) ? m1 : m2
        );

        assertThat(allMonths.get("totalSavings")).isEqualTo(35000.0);
        assertThat(allMonths.get("totalBaselineCost")).isEqualTo(220000.0);
        assertThat(allMonths.get("totalEnergyKwh")).isEqualTo(45000.0);
    }
}

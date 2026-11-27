package com.sense.prolt_operations.service.calculator;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.entity.StateCharges;
import com.sense.prolt_operations.service.SavingsCalculatorDataAccessService;
import com.sense.prolt_operations.service.calculator.model.EnergyMarketType;
import com.sense.prolt_operations.service.calculator.model.Hv2CalculationContext;
import com.sense.prolt_operations.service.calculator.model.Hv2SlotRecord;

class Hv2SlotProcessorTest {

    private SavingsCalculatorDataAccessService dataAccessService;
    private RegulatoryChargeResolver regulatoryChargeResolver;
    private LandedCostCalculator landedCostCalculator;
    private CalculatorProperties properties;
    private Hv2SlotProcessor slotProcessor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        dataAccessService = Mockito.mock(SavingsCalculatorDataAccessService.class);
        properties = new CalculatorProperties();
        regulatoryChargeResolver = new RegulatoryChargeResolver(properties, new NpclTariffSchedule());
        landedCostCalculator = new LandedCostCalculator();
        slotProcessor = new Hv2SlotProcessor(dataAccessService, regulatoryChargeResolver, landedCostCalculator, properties);
        objectMapper = new ObjectMapper();

        when(dataAccessService.resolveStateFormats(anyString())).thenReturn(List.of("UP"));
        StateCharges sc = StateCharges.create();
        sc.setStuLossPercent(BigDecimal.valueOf(2.5));
        sc.setWheelingLossPercent(BigDecimal.valueOf(4.0));
        when(dataAccessService.getStateCharges(any(), any(), any(), any())).thenReturn(Optional.of(sc));
        when(dataAccessService.getIstsCharges(any(), any())).thenReturn(Collections.emptyList());
        when(dataAccessService.getStateTariffs(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(Collections.emptyList());
        when(dataAccessService.getExchangeRateSlots(anyString(), any(), any())).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Should generate 96 slots for an input month")
    void shouldProcess96Slots() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("entry-slot");
        entry.setSanctionedLoadKw(BigDecimal.valueOf(1500.0));
        entry.setDiscom("NPCL");
        entry.setConsumerCategory("HV-2");
        entry.setVoltageLevel("11 kV");
        entry.setStateCode("UP");
        entry.setTodConsumptions("{\"2024-05\":{\"NORMAL\":10000}}");

        Hv2CalculationContext ctx = Hv2CalculationContext.build(entry, "2024-05", null, objectMapper);
        List<Hv2SlotRecord> slots = slotProcessor.processMonthSlots(ctx, "2024-05", Map.of("NORMAL", 10000));

        assertThat(slots).hasSize(31 * 96);
        Hv2SlotRecord slot1 = slots.get(0);
        assertThat(slot1.slot()).isEqualTo(1);
        assertThat(slot1.discomLandingPrice()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Should exclude DAM and RTM market options when sanctioned load is below OA threshold (< 1000 kW)")
    void shouldExcludeDamAndRtmWhenSanctionedLoadBelowThreshold() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("entry-sub-1000");
        entry.setSanctionedLoadKw(BigDecimal.valueOf(500.0)); // < 1000 kW
        entry.setDiscom("NPCL");
        entry.setConsumerCategory("HV-2");
        entry.setVoltageLevel("11 kV");
        entry.setStateCode("UP");
        entry.setTodConsumptions("{\"2024-05\":{\"NORMAL\":10000}}");

        Hv2CalculationContext ctx = Hv2CalculationContext.build(entry, "2024-05", null, objectMapper);
        List<Hv2SlotRecord> slots = slotProcessor.processMonthSlots(ctx, "2024-05", Map.of("NORMAL", 10000));

        // When load < 1000 kW, DAM and RTM are not eligible. Only GDAM or DISCOM can be selected.
        for (Hv2SlotRecord slot : slots) {
            assertThat(slot.selectedSource()).isNotEqualTo(EnergyMarketType.DAM.getValue());
            assertThat(slot.selectedSource()).isNotEqualTo(EnergyMarketType.RTM.getValue());
        }
    }
}

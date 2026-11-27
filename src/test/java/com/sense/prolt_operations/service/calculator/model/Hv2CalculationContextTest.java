package com.sense.prolt_operations.service.calculator.model;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.exception.BadRequestException;

class Hv2CalculationContextTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should successfully build context for valid entry and target month")
    void shouldBuildContextForValidTargetMonth() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("client-1");
        entry.setClientName("Acme Corp");
        entry.setStateCode("UP");
        entry.setSanctionedLoadKw(new BigDecimal("1200.0"));
        entry.setConsumerCategory("HV-2");
        entry.setVoltageLevel("11 kV");
        entry.setPowerFactor(new BigDecimal("0.95"));
        entry.setTodConsumptions("{\"2024-05\":{\"TOD1\":1000,\"TOD2\":2000}}");

        Hv2CalculationContext ctx = Hv2CalculationContext.build(entry, "2024-05", null, objectMapper);

        assertThat(ctx.id()).isEqualTo("client-1");
        assertThat(ctx.stateCode()).isEqualTo("UP");
        assertThat(ctx.sanctionedLoad()).isEqualTo(1200.0);
        assertThat(ctx.parsedCategory().category()).isEqualTo("HV-2");
        assertThat(ctx.parsedSupplyVoltage()).isEqualTo("11 kV");
        assertThat(ctx.monthsToProcess()).hasSize(1);
        assertThat(ctx.monthsToProcess().get(0).getKey()).isEqualTo("2024-05");
    }

    @Test
    @DisplayName("Should process all months when targetMonth is null or empty")
    void shouldProcessAllMonthsWhenTargetMonthOmitted() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("client-2");
        entry.setTodConsumptions("{\"2024-05\":{\"FLAT\":1000},\"2024-06\":{\"FLAT\":2000}}");

        Hv2CalculationContext ctx = Hv2CalculationContext.build(entry, null, null, objectMapper);
        assertThat(ctx.monthsToProcess()).hasSize(2);

        Hv2CalculationContext ctxBlank = Hv2CalculationContext.build(entry, "   ", null, objectMapper);
        assertThat(ctxBlank.monthsToProcess()).hasSize(2);
    }

    @Test
    @DisplayName("Should apply fallback defaults when optional fields are null")
    void shouldApplyDefaultValues() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("client-default");
        entry.setStateCode(null);
        entry.setSanctionedLoadKw(null);
        entry.setConsumerCategory(null);
        entry.setVoltageLevel(null);
        entry.setTodConsumptions("{\"2024-05\":{\"FLAT\":1000}}");

        Hv2CalculationContext ctx = Hv2CalculationContext.build(entry, "2024-05", null, objectMapper);

        assertThat(ctx.stateCode()).isEqualTo("MH");
        assertThat(ctx.sanctionedLoad()).isEqualTo(100.0);
        assertThat(ctx.parsedCategory().category()).isEqualTo("Industrial");
        assertThat(ctx.parsedSupplyVoltage()).isEqualTo("11 kV");
    }

    @Test
    @DisplayName("Should throw BadRequestException when todConsumptions is missing or empty")
    void shouldThrowWhenConsumptionsMissing() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("client-empty");
        entry.setTodConsumptions(null);

        assertThatThrownBy(() -> Hv2CalculationContext.build(entry, "2024-05", null, objectMapper))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No TOD consumption data found");

        entry.setTodConsumptions("{}");
        assertThatThrownBy(() -> Hv2CalculationContext.build(entry, "2024-05", null, objectMapper))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Should throw BadRequestException when requested target month does not exist")
    void shouldThrowWhenTargetMonthNotFound() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("client-notfound");
        entry.setTodConsumptions("{\"2024-05\":{\"FLAT\":1000}}");

        assertThatThrownBy(() -> Hv2CalculationContext.build(entry, "2024-06", null, objectMapper))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No consumption data found for month 2024-06");
    }
}

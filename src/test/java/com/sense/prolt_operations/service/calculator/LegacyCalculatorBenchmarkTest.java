package com.sense.prolt_operations.service.calculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.dto.calculator.ExchangeRateSlotProjection;
import com.sense.prolt_operations.entity.IstsCharges;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.entity.StateCharges;
import com.sense.prolt_operations.entity.StateTariff;
import com.sense.prolt_operations.service.SavingsCalculatorDataAccessService;
import com.sense.prolt_operations.service.calculator.model.Hv2CalculationContext;
import com.sense.prolt_operations.service.calculator.model.Hv2SlotRecord;

/**
 * Benchmark test harness for measuring in-memory lookup and slot calculation performance
 * before and after V18N optimizations.
 */
class LegacyCalculatorBenchmarkTest {

    private SavingsCalculatorDataAccessService dataAccessService;
    private RegulatoryChargeResolver regulatoryChargeResolver;
    private LandedCostCalculator landedCostCalculator;
    private CalculatorProperties properties;
    private Hv2SlotProcessor slotProcessor;
    private ObjectMapper objectMapper;

    private List<IstsCharges> istsChargesList;
    private List<StateTariff> tariffs;
    private List<ExchangeRateSlotProjection> exchangeSlots;

    @BeforeEach
    void setUp() {
        dataAccessService = Mockito.mock(SavingsCalculatorDataAccessService.class);
        properties = new CalculatorProperties();
        regulatoryChargeResolver = new RegulatoryChargeResolver(properties, new NpclTariffSchedule());
        landedCostCalculator = new LandedCostCalculator();
        slotProcessor = new Hv2SlotProcessor(dataAccessService, regulatoryChargeResolver, landedCostCalculator, properties);
        objectMapper = new ObjectMapper();

        // 1. ISTS charges (multiple overlapping and contiguous date ranges)
        istsChargesList = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            IstsCharges c = IstsCharges.create();
            c.setStartDate(LocalDate.of(2024, m, 1));
            c.setEndDate(LocalDate.of(2024, m, m == 2 ? 28 : (m == 4 || m == 6 || m == 9 || m == 11 ? 30 : 31)));
            c.setIstsLossPercent(BigDecimal.valueOf(3.5 + (m * 0.1)));
            istsChargesList.add(c);
        }

        // 2. State tariffs (multi-slab ToD and FLAT across multiple months)
        tariffs = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            StateTariff t1 = StateTariff.create();
            t1.setMonth(m);
            t1.setTodStartTime("06:00");
            t1.setTodEndTime("18:00");
            t1.setEnergyRate(BigDecimal.valueOf(7.20));
            tariffs.add(t1);

            StateTariff t2 = StateTariff.create();
            t2.setMonth(m);
            t2.setTodStartTime("18:00");
            t2.setTodEndTime("22:00");
            t2.setEnergyRate(BigDecimal.valueOf(8.80));
            tariffs.add(t2);

            StateTariff t3 = StateTariff.create();
            t3.setMonth(m);
            t3.setTodStartTime("22:00");
            t3.setTodEndTime("06:00");
            t3.setEnergyRate(BigDecimal.valueOf(5.90));
            tariffs.add(t3);

            StateTariff flat = StateTariff.create();
            flat.setMonth(m);
            flat.setTodStartTime("—");
            flat.setTodEndTime("—");
            flat.setEnergyRate(BigDecimal.valueOf(7.50));
            tariffs.add(flat);
        }

        // 3. Exchange rate slots for 31 days (2,976 slots)
        exchangeSlots = new ArrayList<>(2976);
        LocalDate start = LocalDate.of(2024, 5, 1);
        for (int day = 0; day < 31; day++) {
            LocalDate d = start.plusDays(day);
            for (int slot = 1; slot <= 96; slot++) {
                final int s = slot;
                final LocalDate curDate = d;
                exchangeSlots.add(new ExchangeRateSlotProjection() {
                    @Override public LocalDate getDate() { return curDate; }
                    @Override public Integer getTimeblock() { return s; }
                    @Override public BigDecimal getDamMcp() { return BigDecimal.valueOf(3200 + (s * 10)); }
                    @Override public BigDecimal getRtmMcp() { return BigDecimal.valueOf(3100 + (s * 12)); }
                    @Override public BigDecimal getGdamMcp() { return BigDecimal.valueOf(2900 + (s * 8)); }
                });
            }
        }

        when(dataAccessService.resolveStateFormats(anyString())).thenReturn(List.of("UP"));
        StateCharges sc = StateCharges.create();
        sc.setStuLossPercent(BigDecimal.valueOf(2.5));
        sc.setWheelingLossPercent(BigDecimal.valueOf(4.0));
        when(dataAccessService.getStateCharges(any(), any(), any(), any())).thenReturn(Optional.of(sc));
        when(dataAccessService.getIstsCharges(any(), any())).thenReturn(istsChargesList);
        when(dataAccessService.getStateTariffs(any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(tariffs);
        when(dataAccessService.getExchangeRateSlots(anyString(), any(), any())).thenReturn(exchangeSlots);
    }

    @Test
    @DisplayName("Benchmark Hv2SlotProcessor 2,976 slot monthly calculation loop")
    void benchmarkHv2SlotProcessorMonthlyCalculation() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("entry-benchmark");
        entry.setSanctionedLoadKw(BigDecimal.valueOf(1500.0));
        entry.setDiscom("UPPCL"); // Non-NPCL to exercise tariff matching
        entry.setConsumerCategory("HV-2");
        entry.setVoltageLevel("11 kV");
        entry.setStateCode("UP");
        entry.setTodConsumptions("{\"2024-05\":{\"NORMAL\":10000}}");

        Hv2CalculationContext ctx = Hv2CalculationContext.build(entry, "2024-05", null, objectMapper);

        // Warmup iterations
        for (int i = 0; i < 10; i++) {
            slotProcessor.processMonthSlots(ctx, "2024-05", Map.of("NORMAL", 10000));
        }

        // Measured iterations
        int iterations = 40;
        long startNanos = System.nanoTime();
        int totalSlotsProcessed = 0;
        for (int i = 0; i < iterations; i++) {
            List<Hv2SlotRecord> slots = slotProcessor.processMonthSlots(ctx, "2024-05", Map.of("NORMAL", 10000));
            totalSlotsProcessed += slots.size();
        }
        long durationNanos = System.nanoTime() - startNanos;
        double durationMs = durationNanos / 1_000_000.0;
        double msPerMonth = durationMs / iterations;
        double throughputSlotsPerSec = (totalSlotsProcessed * 1_000_000_000.0) / durationNanos;

        System.out.printf("--- Hv2SlotProcessor Benchmark Result ---%n");
        System.out.printf("Iterations: %d, Total Slots Processed: %d%n", iterations, totalSlotsProcessed);
        System.out.printf("Total Time: %.2f ms%n", durationMs);
        System.out.printf("Latency per Month (2,976 slots): %.2f ms%n", msPerMonth);
        System.out.printf("Throughput: %.0f slots/second%n", throughputSlotsPerSec);
        System.out.printf("-----------------------------------------%n");

        assertThat(totalSlotsProcessed).isEqualTo(iterations * 31 * 96);
    }

    @Test
    @DisplayName("Benchmark Hv2SlotProcessor representative scenarios (single-day, single-month, multi-month)")
    void benchmarkHv2SlotProcessorRepresentativeScenarios() {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setId("entry-benchmark-scenarios");
        entry.setSanctionedLoadKw(BigDecimal.valueOf(1500.0));
        entry.setDiscom("UPPCL"); // Non-NPCL to exercise tariff matching
        entry.setConsumerCategory("HV-2");
        entry.setVoltageLevel("11 kV");
        entry.setStateCode("UP");
        entry.setTodConsumptions("{\"2024-05\":{\"NORMAL\":10000},\"2024-06\":{\"NORMAL\":10000},\"2024-07\":{\"NORMAL\":10000}}");

        Hv2CalculationContext ctx = Hv2CalculationContext.build(entry, null, null, objectMapper);

        // 1. Single-day simulation (1 day = 96 slots)
        // Using a 1-day consumption entry
        Map<String, Object> singleDayConsumptions = Map.of("Start Date", "2024-05-01", "End Date", "2024-05-01", "NORMAL", 500);
        for (int i = 0; i < 50; i++) {
            slotProcessor.processMonthSlots(ctx, "2024-05", singleDayConsumptions);
        }
        int singleDayIterations = 200;
        long startSingleDay = System.nanoTime();
        for (int i = 0; i < singleDayIterations; i++) {
            slotProcessor.processMonthSlots(ctx, "2024-05", singleDayConsumptions);
        }
        double singleDayMs = (System.nanoTime() - startSingleDay) / 1_000_000.0;
        double singleDayAvgMs = singleDayMs / singleDayIterations;

        // 2. Single-month simulation (31 days = 2,976 slots)
        for (int i = 0; i < 10; i++) {
            slotProcessor.processMonthSlots(ctx, "2024-05", Map.of("NORMAL", 10000));
        }
        int singleMonthIterations = 40;
        long startSingleMonth = System.nanoTime();
        int totalSingleMonthSlots = 0;
        for (int i = 0; i < singleMonthIterations; i++) {
            List<Hv2SlotRecord> slots = slotProcessor.processMonthSlots(ctx, "2024-05", Map.of("NORMAL", 10000));
            totalSingleMonthSlots += slots.size();
        }
        double singleMonthMs = (System.nanoTime() - startSingleMonth) / 1_000_000.0;
        double singleMonthAvgMs = singleMonthMs / singleMonthIterations;

        // 3. Multi-month simulation (3 months = May, June, July: 31+30+31 = 92 days = 8,832 slots)
        int multiMonthIterations = 20;
        long startMultiMonth = System.nanoTime();
        int totalMultiMonthSlots = 0;
        for (int i = 0; i < multiMonthIterations; i++) {
            List<Hv2SlotRecord> m1 = slotProcessor.processMonthSlots(ctx, "2024-05", Map.of("NORMAL", 10000));
            List<Hv2SlotRecord> m2 = slotProcessor.processMonthSlots(ctx, "2024-06", Map.of("NORMAL", 10000));
            List<Hv2SlotRecord> m3 = slotProcessor.processMonthSlots(ctx, "2024-07", Map.of("NORMAL", 10000));
            totalMultiMonthSlots += (m1.size() + m2.size() + m3.size());
        }
        double multiMonthMs = (System.nanoTime() - startMultiMonth) / 1_000_000.0;
        double multiMonthAvgMs = multiMonthMs / multiMonthIterations;

        System.out.printf("=== Representative Scenario Baseline Benchmark ===%n");
        System.out.printf("Scenario 1 (Single-Day, 96 slots): %.3f ms/day (Total: %.2f ms across %d runs)%n",
                singleDayAvgMs, singleDayMs, singleDayIterations);
        System.out.printf("Scenario 2 (Single-Month, 2,976 slots): %.2f ms/month (Total: %.2f ms across %d runs)%n",
                singleMonthAvgMs, singleMonthMs, singleMonthIterations);
        System.out.printf("Scenario 3 (Multi-Month 3-mo, 8,832 slots): %.2f ms/3-mo (Total: %.2f ms across %d runs)%n",
                multiMonthAvgMs, multiMonthMs, multiMonthIterations);
        System.out.printf("==================================================%n");

        assertThat(totalSingleMonthSlots).isEqualTo(singleMonthIterations * 31 * 96);
        assertThat(totalMultiMonthSlots).isGreaterThan(0);
    }

    @Test
    @DisplayName("Benchmark RegulatoryChargeResolver lookups (ISTS and StateTariff)")
    void benchmarkRegulatoryLookups() {
        LocalDate testDate = LocalDate.of(2024, 5, 15);

        // Warmup
        for (int i = 0; i < 5000; i++) {
            regulatoryChargeResolver.resolveIstsLossPercent(testDate, istsChargesList);
            regulatoryChargeResolver.resolveDiscomTariff(tariffs, i % 24, 5, 0.95, false);
        }

        // Measured
        int lookupCount = 50_000;
        long startNanos = System.nanoTime();
        for (int i = 0; i < lookupCount; i++) {
            regulatoryChargeResolver.resolveIstsLossPercent(testDate, istsChargesList);
            regulatoryChargeResolver.resolveDiscomTariff(tariffs, i % 24, 5, 0.95, false);
        }
        long durationNanos = System.nanoTime() - startNanos;
        double durationMs = durationNanos / 1_000_000.0;
        double opsPerSec = (lookupCount * 1_000_000_000.0) / durationNanos;

        System.out.printf("--- RegulatoryChargeResolver Benchmark Result ---%n");
        System.out.printf("Lookups: %d pairs (ISTS + Tariff)%n", lookupCount);
        System.out.printf("Total Time: %.2f ms%n", durationMs);
        System.out.printf("Throughput: %.0f ops/second%n", opsPerSec);
        System.out.printf("-------------------------------------------------%n");

        assertThat(durationMs).isGreaterThan(0.0);
    }
}

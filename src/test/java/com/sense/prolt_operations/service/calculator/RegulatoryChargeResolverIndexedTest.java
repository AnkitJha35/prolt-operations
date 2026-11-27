package com.sense.prolt_operations.service.calculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.entity.IstsCharges;
import com.sense.prolt_operations.entity.StateTariff;
import com.sense.prolt_operations.service.calculator.RegulatoryChargeResolver.IstsLossCache;
import com.sense.prolt_operations.service.calculator.RegulatoryChargeResolver.ParsedStateTariff;
import com.sense.prolt_operations.service.calculator.RegulatoryChargeResolver.TariffIndex;
import com.sense.prolt_operations.service.calculator.RegulatoryChargeResolver.TariffRateCache;
import com.sense.prolt_operations.service.calculator.model.MatchedTariffRate;

@DisplayName("RegulatoryChargeResolver Indexed & Caching Tests (Milestone V18N)")
class RegulatoryChargeResolverIndexedTest {

    private RegulatoryChargeResolver resolver;
    private CalculatorProperties properties;
    private NpclTariffSchedule npclTariffSchedule;

    @BeforeEach
    void setUp() {
        properties = new CalculatorProperties();
        npclTariffSchedule = new NpclTariffSchedule();
        resolver = new RegulatoryChargeResolver(properties, npclTariffSchedule);
    }

    private StateTariff createTariff(Integer month, String start, String end, double rate) {
        StateTariff t = StateTariff.create();
        t.setMonth(month);
        t.setTodStartTime(start);
        t.setTodEndTime(end);
        t.setEnergyRate(BigDecimal.valueOf(rate));
        return t;
    }

    @Nested
    @DisplayName("IstsLossCache Equivalence & Isolation Tests")
    class IstsCacheTests {

        @Test
        @DisplayName("Should yield identical results between unindexed and cached ISTS resolution across dates")
        void shouldMatchUnindexedIstsResolution() {
            List<IstsCharges> charges = new ArrayList<>();

            IstsCharges c1 = IstsCharges.create();
            c1.setStartDate(LocalDate.of(2024, 5, 1));
            c1.setEndDate(LocalDate.of(2024, 5, 15));
            c1.setIstsLossPercent(BigDecimal.valueOf(3.45));
            charges.add(c1);

            IstsCharges c2 = IstsCharges.create();
            c2.setStartDate(LocalDate.of(2024, 5, 16));
            c2.setEndDate(LocalDate.of(2024, 5, 31));
            c2.setIstsLossPercent(BigDecimal.valueOf(3.80));
            charges.add(c2);

            IstsLossCache cache = resolver.createIstsLossCache(charges);

            // Test dates: before range, exactly on boundaries, inside ranges, after range
            List<LocalDate> testDates = List.of(
                    LocalDate.of(2024, 4, 30),
                    LocalDate.of(2024, 5, 1),
                    LocalDate.of(2024, 5, 10),
                    LocalDate.of(2024, 5, 15),
                    LocalDate.of(2024, 5, 16),
                    LocalDate.of(2024, 5, 25),
                    LocalDate.of(2024, 5, 31),
                    LocalDate.of(2024, 6, 1)
            );

            for (LocalDate date : testDates) {
                double unindexed = resolver.resolveIstsLossPercent(date, charges);
                double cached = resolver.resolveIstsLossPercentCached(date, cache);
                assertThat(cached)
                        .as("Mismatch on date %s", date)
                        .isEqualTo(unindexed);

                // Second call should hit the cache and return identical value
                double secondCached = resolver.resolveIstsLossPercentCached(date, cache);
                assertThat(secondCached).isEqualTo(cached);
            }
        }

        @Test
        @DisplayName("Should handle null or empty cache gracefully")
        void shouldHandleNullOrEmptyCache() {
            assertThat(resolver.resolveIstsLossPercentCached(LocalDate.of(2024, 5, 1), null)).isEqualTo(0.0);
            assertThat(resolver.resolveIstsLossPercentCached(null, resolver.createIstsLossCache(Collections.emptyList()))).isEqualTo(0.0);

            IstsLossCache emptyCache = resolver.createIstsLossCache(Collections.emptyList());
            assertThat(resolver.resolveIstsLossPercentCached(LocalDate.of(2024, 5, 1), emptyCache)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Cache isolation: two distinct caches do not cross-contaminate")
        void shouldIsolateSeparateCaches() {
            IstsCharges c1 = IstsCharges.create();
            c1.setStartDate(LocalDate.of(2024, 5, 1));
            c1.setEndDate(LocalDate.of(2024, 5, 31));
            c1.setIstsLossPercent(BigDecimal.valueOf(3.0));

            IstsCharges c2 = IstsCharges.create();
            c2.setStartDate(LocalDate.of(2024, 5, 1));
            c2.setEndDate(LocalDate.of(2024, 5, 31));
            c2.setIstsLossPercent(BigDecimal.valueOf(4.5));

            IstsLossCache cache1 = resolver.createIstsLossCache(List.of(c1));
            IstsLossCache cache2 = resolver.createIstsLossCache(List.of(c2));

            LocalDate date = LocalDate.of(2024, 5, 15);
            assertThat(resolver.resolveIstsLossPercentCached(date, cache1)).isEqualTo(3.0);
            assertThat(resolver.resolveIstsLossPercentCached(date, cache2)).isEqualTo(4.5);
        }
    }

    @Nested
    @DisplayName("TariffIndex & TariffRateCache Equivalence Tests")
    class TariffIndexTests {

        @Test
        @DisplayName("Should yield identical ToD match between unindexed matchTariff and TariffIndex")
        void shouldMatchUnindexedTariffMatching() {
            List<StateTariff> tariffs = List.of(
                    createTariff(5, "06:00", "18:00", 7.20),
                    createTariff(5, "18:00", "22:00", 8.80),
                    createTariff(5, "22:00", "06:00", 5.90), // overnight wrap
                    createTariff(5, "—", "—", 7.50),        // FLAT
                    createTariff(6, "00:00", "24:00", 7.00)
            );

            TariffIndex index = resolver.createTariffIndex(tariffs);

            for (int month = 4; month <= 7; month++) {
                for (int hour = 0; hour < 24; hour++) {
                    Optional<StateTariff> unindexed = resolver.matchTariff(tariffs, hour, month);
                    Optional<ParsedStateTariff> indexed = index.matchTariff(hour, month);

                    if (unindexed.isPresent()) {
                        assertThat(indexed).isPresent();
                        assertThat(indexed.get().tariff()).isSameAs(unindexed.get());
                        assertThat(indexed.get().formattedTodName()).isEqualTo(resolver.resolveTariffName(unindexed.get()));
                    } else {
                        assertThat(indexed).isEmpty();
                    }
                }
            }
        }

        @Test
        @DisplayName("Should yield identical rate between unindexed and indexed resolveDiscomTariff")
        void shouldMatchUnindexedResolveDiscomTariff() {
            List<StateTariff> tariffs = List.of(
                    createTariff(5, "06:00", "18:00", 7.20),
                    createTariff(5, "18:00", "22:00", 8.80),
                    createTariff(5, "22:00", "06:00", 5.90),
                    createTariff(5, "—", "—", 7.50)
            );

            TariffIndex index = resolver.createTariffIndex(tariffs);

            // Test NPCL and non-NPCL, with and without power factor
            for (boolean isNpclHv2 : List.of(false, true)) {
                TariffRateCache cache = resolver.createTariffRateCache();
                for (int hour = 0; hour < 24; hour++) {
                    MatchedTariffRate unindexed = resolver.resolveDiscomTariff(tariffs, hour, 5, 0.95, isNpclHv2);
                    MatchedTariffRate indexed = resolver.resolveDiscomTariff(index, cache, hour, 5, 0.95, isNpclHv2);

                    assertThat(indexed.todName()).isEqualTo(unindexed.todName());
                    assertThat(indexed.rate()).isEqualTo(unindexed.rate());

                    // Verify second hit from cache is also identical
                    MatchedTariffRate cachedHit = resolver.resolveDiscomTariff(index, cache, hour, 5, 0.95, isNpclHv2);
                    assertThat(cachedHit.rate()).isEqualTo(indexed.rate());
                }
            }
        }

        @Test
        @DisplayName("Should yield identical rate between unindexed and indexed resolveHv2DiscomTariff")
        void shouldMatchUnindexedResolveHv2DiscomTariff() {
            List<StateTariff> tariffs = List.of(
                    createTariff(5, "06:00", "18:00", 7.20),
                    createTariff(5, "18:00", "22:00", 8.80),
                    createTariff(5, "22:00", "06:00", 5.90)
            );

            TariffIndex index = resolver.createTariffIndex(tariffs);

            for (boolean isNpclHv2 : List.of(false, true)) {
                TariffRateCache cache = resolver.createTariffRateCache();
                for (int hour = 0; hour < 24; hour++) {
                    MatchedTariffRate unindexed = resolver.resolveHv2DiscomTariff(tariffs, hour, 5, isNpclHv2);
                    MatchedTariffRate indexed = resolver.resolveHv2DiscomTariff(index, cache, hour, 5, isNpclHv2);

                    assertThat(indexed.todName()).isEqualTo(unindexed.todName());
                    assertThat(indexed.rate()).isEqualTo(unindexed.rate());
                }
            }
        }

        @Test
        @DisplayName("Should handle empty tariffs with HV-2 fallback correctly in indexed resolver")
        void shouldHandleEmptyTariffsHv2Fallback() {
            TariffIndex emptyIndex = resolver.createTariffIndex(Collections.emptyList());
            TariffRateCache cache = resolver.createTariffRateCache();

            for (int hour = 0; hour < 24; hour++) {
                MatchedTariffRate unindexed = resolver.resolveHv2DiscomTariff(Collections.emptyList(), hour, 5, false);
                MatchedTariffRate indexed = resolver.resolveHv2DiscomTariff(emptyIndex, cache, hour, 5, false);

                assertThat(indexed.todName()).isEqualTo(unindexed.todName());
                assertThat(indexed.rate()).isEqualTo(unindexed.rate());
            }
        }
    }
}

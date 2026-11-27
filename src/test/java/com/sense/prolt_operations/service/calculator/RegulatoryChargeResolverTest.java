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
import com.sense.prolt_operations.service.calculator.model.MatchedTariffRate;

class RegulatoryChargeResolverTest {

    private RegulatoryChargeResolver resolver;
    private CalculatorProperties properties;
    private NpclTariffSchedule npclTariffSchedule;

    @BeforeEach
    void setUp() {
        properties = new CalculatorProperties();
        npclTariffSchedule = new NpclTariffSchedule();
        resolver = new RegulatoryChargeResolver(properties, npclTariffSchedule);
    }

    @Nested
    @DisplayName("ISTS Transmission Loss Resolution Tests")
    class IstsResolutionTests {

        @Test
        @DisplayName("Should resolve ISTS loss when deliveryDate is within [startDate, endDate]")
        void shouldResolveIstsLossWithinRange() {
            IstsCharges charge = IstsCharges.create();
            charge.setStartDate(LocalDate.of(2024, 5, 1));
            charge.setEndDate(LocalDate.of(2024, 5, 31));
            charge.setIstsLossPercent(new BigDecimal("3.75"));

            double loss = resolver.resolveIstsLossPercent(LocalDate.of(2024, 5, 15), List.of(charge));
            assertThat(loss).isEqualTo(3.75);
        }

        @Test
        @DisplayName("Should resolve ISTS loss on exact boundary dates (inclusive)")
        void shouldResolveIstsLossOnBoundaries() {
            IstsCharges charge = IstsCharges.create();
            charge.setStartDate(LocalDate.of(2024, 5, 1));
            charge.setEndDate(LocalDate.of(2024, 5, 31));
            charge.setIstsLossPercent(new BigDecimal("4.20"));

            double lossStart = resolver.resolveIstsLossPercent(LocalDate.of(2024, 5, 1), List.of(charge));
            double lossEnd = resolver.resolveIstsLossPercent(LocalDate.of(2024, 5, 31), List.of(charge));

            assertThat(lossStart).isEqualTo(4.20);
            assertThat(lossEnd).isEqualTo(4.20);
        }

        @Test
        @DisplayName("Should return 0.0 when deliveryDate is before or after range")
        void shouldReturnZeroWhenOutOfRange() {
            IstsCharges charge = IstsCharges.create();
            charge.setStartDate(LocalDate.of(2024, 5, 1));
            charge.setEndDate(LocalDate.of(2024, 5, 31));
            charge.setIstsLossPercent(new BigDecimal("3.50"));

            double lossBefore = resolver.resolveIstsLossPercent(LocalDate.of(2024, 4, 30), List.of(charge));
            double lossAfter = resolver.resolveIstsLossPercent(LocalDate.of(2024, 6, 1), List.of(charge));

            assertThat(lossBefore).isEqualTo(0.0);
            assertThat(lossAfter).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should return first match when multiple overlapping records exist")
        void shouldPickFirstMatchingRecord() {
            IstsCharges first = IstsCharges.create();
            first.setStartDate(LocalDate.of(2024, 5, 1));
            first.setEndDate(LocalDate.of(2024, 5, 31));
            first.setIstsLossPercent(new BigDecimal("3.10"));

            IstsCharges second = IstsCharges.create();
            second.setStartDate(LocalDate.of(2024, 5, 10));
            second.setEndDate(LocalDate.of(2024, 5, 20));
            second.setIstsLossPercent(new BigDecimal("4.50"));

            double loss = resolver.resolveIstsLossPercent(LocalDate.of(2024, 5, 15), List.of(first, second));
            assertThat(loss).isEqualTo(3.10);
        }

        @Test
        @DisplayName("Should gracefully handle null and empty inputs")
        void shouldHandleNullAndEmptyInputs() {
            assertThat(resolver.resolveIstsLossPercent(null, Collections.emptyList())).isEqualTo(0.0);
            assertThat(resolver.resolveIstsLossPercent(LocalDate.of(2024, 5, 15), null)).isEqualTo(0.0);
            assertThat(resolver.resolveIstsLossPercent(LocalDate.of(2024, 5, 15), Collections.emptyList())).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should safely skip records with null dates or null loss percentage")
        void shouldSkipMalformedRecords() {
            IstsCharges malformed1 = IstsCharges.create();
            malformed1.setStartDate(null);
            malformed1.setEndDate(LocalDate.of(2024, 5, 31));

            IstsCharges malformed2 = IstsCharges.create();
            malformed2.setStartDate(LocalDate.of(2024, 5, 1));
            malformed2.setEndDate(null);

            IstsCharges nullPercent = IstsCharges.create();
            nullPercent.setStartDate(LocalDate.of(2024, 5, 1));
            nullPercent.setEndDate(LocalDate.of(2024, 5, 31));
            nullPercent.setIstsLossPercent(null);

            List<IstsCharges> list = new ArrayList<>();
            list.add(null);
            list.add(malformed1);
            list.add(malformed2);
            list.add(nullPercent);

            double loss = resolver.resolveIstsLossPercent(LocalDate.of(2024, 5, 15), list);
            assertThat(loss).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("StateTariff Matching Tests")
    class StateTariffMatchingTests {

        private StateTariff createTariff(Integer month, String start, String end, Double energyRate) {
            StateTariff t = StateTariff.create();
            t.setMonth(month);
            t.setTodStartTime(start);
            t.setTodEndTime(end);
            if (energyRate != null) {
                t.setEnergyRate(BigDecimal.valueOf(energyRate));
            }
            return t;
        }

        @Test
        @DisplayName("Should filter tariffs by month when matching month records exist")
        void shouldFilterByMonth() {
            StateTariff mayTariff = createTariff(5, "10:00", "17:00", 7.20);
            StateTariff juneTariff = createTariff(6, "10:00", "17:00", 7.80);

            Optional<StateTariff> matched = resolver.matchTariff(List.of(mayTariff, juneTariff), 12, 5);
            assertThat(matched).isPresent();
            assertThat(matched.get().getEnergyRate()).isEqualByComparingTo("7.20");
        }

        @Test
        @DisplayName("Should fall back to all tariffs when no record matches requested month")
        void shouldFallbackWhenMonthNotFound() {
            StateTariff defaultTariff = createTariff(null, "10:00", "17:00", 6.80);

            Optional<StateTariff> matched = resolver.matchTariff(List.of(defaultTariff), 12, 5);
            assertThat(matched).isPresent();
            assertThat(matched.get().getEnergyRate()).isEqualByComparingTo("6.80");
        }

        @Test
        @DisplayName("Should match standard daytime ToD time range")
        void shouldMatchDaytimeTodRange() {
            StateTariff morning = createTariff(null, "06:00", "10:00", 6.50);
            StateTariff normal = createTariff(null, "10:00", "17:00", 7.00);
            StateTariff peak = createTariff(null, "17:00", "22:00", 8.50);

            Optional<StateTariff> matched = resolver.matchTariff(List.of(morning, normal, peak), 14, null);
            assertThat(matched).isPresent();
            assertThat(matched.get().getEnergyRate()).isEqualByComparingTo("7.00");
        }

        @Test
        @DisplayName("Should match overnight ToD range where start > end (e.g. 22:00 to 06:00)")
        void shouldMatchOvernightTodRange() {
            StateTariff overnight = createTariff(null, "22:00", "06:00", 5.50);
            StateTariff day = createTariff(null, "06:00", "22:00", 7.50);

            Optional<StateTariff> lateNight = resolver.matchTariff(List.of(overnight, day), 23, null);
            Optional<StateTariff> earlyMorning = resolver.matchTariff(List.of(overnight, day), 3, null);
            Optional<StateTariff> daytime = resolver.matchTariff(List.of(overnight, day), 12, null);

            assertThat(lateNight).isPresent();
            assertThat(lateNight.get().getEnergyRate()).isEqualByComparingTo("5.50");

            assertThat(earlyMorning).isPresent();
            assertThat(earlyMorning.get().getEnergyRate()).isEqualByComparingTo("5.50");

            assertThat(daytime).isPresent();
            assertThat(daytime.get().getEnergyRate()).isEqualByComparingTo("7.50");
        }

        @Test
        @DisplayName("Should fall back to FLAT tariff when no ToD range matches")
        void shouldFallbackToFlatTariff() {
            StateTariff peak = createTariff(null, "17:00", "22:00", 8.50);
            StateTariff flat = createTariff(null, "—", "—", 6.50);

            Optional<StateTariff> matched = resolver.matchTariff(List.of(peak, flat), 10, null);
            assertThat(matched).isPresent();
            assertThat(matched.get().getEnergyRate()).isEqualByComparingTo("6.50");
        }

        @Test
        @DisplayName("Should return empty when tariffs list is empty or null")
        void shouldReturnEmptyForNullOrEmptyList() {
            assertThat(resolver.matchTariff(null, 10, 5)).isEmpty();
            assertThat(resolver.matchTariff(Collections.emptyList(), 10, 5)).isEmpty();
        }
    }

    @Nested
    @DisplayName("DISCOM Tariff Name Resolution Tests")
    class TariffNameResolutionTests {

        @Test
        @DisplayName("Should format start-end uppercase name for ToD slabs")
        void shouldFormatTodRangeName() {
            StateTariff t = StateTariff.create();
            t.setTodStartTime("17:00");
            t.setTodEndTime("22:00");

            assertThat(resolver.resolveTariffName(t)).isEqualTo("17:00-22:00");
        }

        @Test
        @DisplayName("Should return FLAT for un-timed or dash-marked tariffs")
        void shouldReturnFlatForUntimedTariffs() {
            StateTariff dash = StateTariff.create();
            dash.setTodStartTime("—");
            dash.setTodEndTime("—");
            assertThat(resolver.resolveTariffName(dash)).isEqualTo("FLAT");

            StateTariff nulls = StateTariff.create();
            assertThat(resolver.resolveTariffName(nulls)).isEqualTo("FLAT");
        }

        @Test
        @DisplayName("Should return normal for null matched record")
        void shouldReturnNormalForNullMatched() {
            assertThat(resolver.resolveTariffName(null)).isEqualTo("normal");
        }
    }

    @Nested
    @DisplayName("Standard DISCOM Tariff Resolution Tests (resolveDiscomTariff)")
    class StandardDiscomTariffTests {

        @Test
        @DisplayName("Should route to NpclTariffSchedule when isNpclHv2 is true")
        void shouldRouteToNpclWhenNpclHv2() {
            // Month 5 (summer), hour 19 -> Peak (TOD2) -> 6.80 * 1.15 = 7.82
            MatchedTariffRate result = resolver.resolveDiscomTariff(Collections.emptyList(), 19, 5, 0.95, true);
            assertThat(result.todName()).isEqualTo("TOD2");
            assertThat(result.rate()).isCloseTo(7.82, org.assertj.core.data.Offset.offset(1e-4));
        }

        @Test
        @DisplayName("Should resolve energyRate from matching tariff")
        void shouldResolveEnergyRate() {
            StateTariff t = StateTariff.create();
            t.setTodStartTime("09:00");
            t.setTodEndTime("18:00");
            t.setEnergyRate(new BigDecimal("7.35"));

            MatchedTariffRate result = resolver.resolveDiscomTariff(List.of(t), 12, 5, 0.95, false);
            assertThat(result.todName()).isEqualTo("09:00-18:00");
            assertThat(result.rate()).isEqualTo(7.35);
        }

        @Test
        @DisplayName("Should fall back to baseEnergyRate if energyRate is null")
        void shouldFallbackToBaseEnergyRate() {
            StateTariff t = StateTariff.create();
            t.setTodStartTime("—");
            t.setTodEndTime("—");
            t.setBaseEnergyRate(new BigDecimal("6.90"));

            MatchedTariffRate result = resolver.resolveDiscomTariff(List.of(t), 12, 5, 0.95, false);
            assertThat(result.todName()).isEqualTo("FLAT");
            assertThat(result.rate()).isEqualTo(6.90);
        }

        @Test
        @DisplayName("Should divide by powerFactor when unit is kvah")
        void shouldDivideByPowerFactorForKvahUnit() {
            StateTariff t = StateTariff.create();
            t.setTodStartTime("—");
            t.setTodEndTime("—");
            t.setBaseEnergyUnit("kvah");
            t.setEnergyRate(new BigDecimal("6.30"));

            // 6.30 / 0.90 = 7.00
            MatchedTariffRate result = resolver.resolveDiscomTariff(List.of(t), 12, 5, 0.90, false);
            assertThat(result.rate()).isEqualTo(7.0);
        }

        @Test
        @DisplayName("Should use default power factor when client power factor is null or <= 0 for kvah")
        void shouldUseDefaultPfForInvalidClientPf() {
            StateTariff t = StateTariff.create();
            t.setBaseEnergyUnit("kvah");
            t.setEnergyRate(new BigDecimal("6.30"));

            // defaultPf = 0.99 -> 6.30 / 0.99 = 6.3636...
            MatchedTariffRate nullPf = resolver.resolveDiscomTariff(List.of(t), 12, 5, null, false);
            MatchedTariffRate zeroPf = resolver.resolveDiscomTariff(List.of(t), 12, 5, 0.0, false);

            assertThat(nullPf.rate()).isCloseTo(6.30 / 0.99, org.assertj.core.data.Offset.offset(1e-4));
            assertThat(zeroPf.rate()).isCloseTo(6.30 / 0.99, org.assertj.core.data.Offset.offset(1e-4));
        }

        @Test
        @DisplayName("Should return normal with fallback rate when tariffs list is empty")
        void shouldReturnFallbackWhenTariffsEmpty() {
            MatchedTariffRate result = resolver.resolveDiscomTariff(Collections.emptyList(), 12, 5, 0.95, false);
            assertThat(result.todName()).isEqualTo("normal");
            assertThat(result.rate()).isEqualTo(7.50);
        }
    }

    @Nested
    @DisplayName("HV-2 DISCOM Tariff Resolution Tests (resolveHv2DiscomTariff)")
    class Hv2DiscomTariffTests {

        @Test
        @DisplayName("Should route to NpclTariffSchedule when isNpclHv2 is true")
        void shouldRouteToNpclSchedule() {
            // Month 1 (winter), hour 23 -> Night (TOD1) -> 6.80 * 0.85 = 5.78
            MatchedTariffRate result = resolver.resolveHv2DiscomTariff(Collections.emptyList(), 23, 1, true);
            assertThat(result.todName()).isEqualTo("TOD1");
            assertThat(result.rate()).isCloseTo(5.78, org.assertj.core.data.Offset.offset(1e-4));
        }

        @Test
        @DisplayName("Should use matched tariff when tariffs are configured")
        void shouldUseMatchedTariffWhenPresent() {
            StateTariff t = StateTariff.create();
            t.setTodStartTime("10:00");
            t.setTodEndTime("18:00");
            t.setEnergyRate(new BigDecimal("7.45"));

            MatchedTariffRate result = resolver.resolveHv2DiscomTariff(List.of(t), 14, 5, false);
            assertThat(result.todName()).isEqualTo("10:00-18:00");
            assertThat(result.rate()).isEqualTo(7.45);
        }

        @Test
        @DisplayName("Should apply Node.js legacy offpeak/peak fallback when tariffs are unconfigured")
        void shouldApplyNodeJsLegacyFallbacksWhenTariffsEmpty() {
            // Offpeak hours: [22..24) and [0..6) -> 6.0, offpeak
            MatchedTariffRate night = resolver.resolveHv2DiscomTariff(Collections.emptyList(), 23, 5, false);
            MatchedTariffRate early = resolver.resolveHv2DiscomTariff(Collections.emptyList(), 4, 5, false);
            assertThat(night.todName()).isEqualTo("offpeak");
            assertThat(night.rate()).isEqualTo(6.0);
            assertThat(early.todName()).isEqualTo("offpeak");
            assertThat(early.rate()).isEqualTo(6.0);

            // Peak hours: [18..22) -> 8.5, peak
            MatchedTariffRate peak = resolver.resolveHv2DiscomTariff(Collections.emptyList(), 20, 5, false);
            assertThat(peak.todName()).isEqualTo("peak");
            assertThat(peak.rate()).isEqualTo(8.5);

            // Normal daytime hours -> fallbackRate (7.5), normal
            MatchedTariffRate normal = resolver.resolveHv2DiscomTariff(Collections.emptyList(), 12, 5, false);
            assertThat(normal.todName()).isEqualTo("normal");
            assertThat(normal.rate()).isEqualTo(7.5);
        }
    }
}

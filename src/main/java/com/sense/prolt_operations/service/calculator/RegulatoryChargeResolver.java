package com.sense.prolt_operations.service.calculator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.entity.IstsCharges;
import com.sense.prolt_operations.entity.StateTariff;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryConstants;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryFallbackConstants;
import com.sense.prolt_operations.service.calculator.model.MatchedTariffRate;

/**
 * Spring component encapsulating shared regulatory charge and DISCOM tariff
 * resolution for the Legacy Saving Calculator.
 *
 * <p>Unifies ISTS date-based lookup, StateTariff month and ToD matching, and
 * DISCOM base tariff calculations across {@link com.sense.prolt_operations.service.SavingsCalculatorService}
 * and {@link Hv2SavingsCalculator} with 1:1 fidelity to the Node.js legacy implementation.</p>
 */
@Component
public class RegulatoryChargeResolver {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryChargeResolver.class);

    private final CalculatorProperties properties;
    private final NpclTariffSchedule npclTariffSchedule;

    @Autowired
    public RegulatoryChargeResolver(CalculatorProperties properties, NpclTariffSchedule npclTariffSchedule) {
        this.properties = properties != null ? properties : new CalculatorProperties();
        this.npclTariffSchedule = npclTariffSchedule != null ? npclTariffSchedule : new NpclTariffSchedule();
    }

    public RegulatoryChargeResolver(CalculatorProperties properties) {
        this(properties, new NpclTariffSchedule());
    }

    public RegulatoryChargeResolver() {
        this(new CalculatorProperties(), new NpclTariffSchedule());
    }

    /**
     * Resolves the applicable ISTS transmission loss percentage for a delivery date
     * by finding the first matching range [startDate, endDate] (inclusive).
     *
     * @param deliveryDate    the delivery date to evaluate
     * @param istsChargesList the list of configured ISTS charge records
     * @return the resolved ISTS loss percentage (e.g. 3.5), or 0.0 if not found/null
     */
    public double resolveIstsLossPercent(LocalDate deliveryDate, List<IstsCharges> istsChargesList) {
        if (deliveryDate == null || istsChargesList == null || istsChargesList.isEmpty()) {
            return 0.0;
        }

        for (IstsCharges istsCharge : istsChargesList) {
            if (istsCharge != null && istsCharge.getStartDate() != null && istsCharge.getEndDate() != null
                    && !deliveryDate.isBefore(istsCharge.getStartDate())
                    && !deliveryDate.isAfter(istsCharge.getEndDate())) {
                return istsCharge.getIstsLossPercent() != null ? istsCharge.getIstsLossPercent().doubleValue() : 0.0;
            }
        }
        return 0.0;
    }

    /**
     * Matches the most specific {@link StateTariff} record for a given hour and month.
     *
     * <p>Selection order:
     * <ol>
     *   <li>Filter tariffs by month (if any records match; falls back to all tariffs if none match).</li>
     *   <li>Find first tariff matching ToD start and end hours (handling overnight wrap-around where start &gt; end).</li>
     *   <li>If no ToD matches, fall back to the first FLAT tariff (where ToD times are null or "—").</li>
     * </ol>
     *
     * @param tariffs the candidate state tariffs
     * @param hour    the hour of day (0-23)
     * @param month   the month (1-12), or null
     * @return optional containing the matched tariff, or empty if no match
     */
    public Optional<StateTariff> matchTariff(List<StateTariff> tariffs, int hour, Integer month) {
        if (tariffs == null || tariffs.isEmpty()) {
            return Optional.empty();
        }

        List<StateTariff> tariffsForMonth = month != null
                ? tariffs.stream().filter(t -> t != null && t.getMonth() != null && t.getMonth().intValue() == month.intValue()).toList()
                : Collections.emptyList();
        if (tariffsForMonth.isEmpty()) {
            tariffsForMonth = tariffs;
        }

        for (StateTariff t : tariffsForMonth) {
            if (t == null || t.getTodStartTime() == null || "—".equals(t.getTodStartTime())
                    || t.getTodEndTime() == null || "—".equals(t.getTodEndTime())) {
                continue;
            }
            int start = CalculatorUtils.parseHour(t.getTodStartTime());
            int end = CalculatorUtils.parseHour(t.getTodEndTime());
            if (start <= end) {
                if (hour >= start && hour < end) {
                    return Optional.of(t);
                }
            } else {
                if (hour >= start || hour < end) {
                    return Optional.of(t);
                }
            }
        }

        return tariffsForMonth.stream()
                .filter(t -> t != null && (t.getTodStartTime() == null || "—".equals(t.getTodStartTime())
                        || t.getTodEndTime() == null || "—".equals(t.getTodEndTime())))
                .findFirst();
    }

    /**
     * Formats the ToD slab name for a matched tariff record.
     *
     * @param matched the matched StateTariff, or null
     * @return uppercase ToD time range (e.g. "17:00-22:00"), "FLAT", or "normal" if null
     */
    public String resolveTariffName(StateTariff matched) {
        if (matched == null) {
            return "normal";
        }
        if (matched.getTodStartTime() != null && !"—".equals(matched.getTodStartTime())
                && matched.getTodEndTime() != null && !"—".equals(matched.getTodEndTime())) {
            return (matched.getTodStartTime() + "-" + matched.getTodEndTime()).toUpperCase();
        }
        return "FLAT";
    }

    /**
     * Resolves the DISCOM tariff slab and effective rate for standard savings calculations,
     * including NPCL HV-2 schedule resolution and kVAh power-factor division.
     *
     * @param tariffs       the candidate state tariffs
     * @param hour          the hour of day (0-23)
     * @param month         the delivery month (1-12)
     * @param powerFactor   the client power factor (used if tariff unit is kVAh)
     * @param isNpclHv2     whether the client is on NPCL HV-2 tariff
     * @param fallbackRate  fallback rate when no tariff matches
     * @param defaultPf     default power factor fallback if client pf is null/invalid
     * @return typed {@link MatchedTariffRate} containing ToD name and effective INR/kWh rate
     */
    public MatchedTariffRate resolveDiscomTariff(List<StateTariff> tariffs, int hour, Integer month,
            Double powerFactor, boolean isNpclHv2, double fallbackRate, double defaultPf) {
        if (isNpclHv2) {
            int m = month != null ? month : 1;
            NpclTariffSchedule.NpclTodRate tod = npclTariffSchedule.resolve(m, hour);
            return new MatchedTariffRate(tod.todName(), tod.rate());
        }

        if (tariffs != null && !tariffs.isEmpty()) {
            Optional<StateTariff> matchedOpt = matchTariff(tariffs, hour, month);
            if (matchedOpt.isPresent()) {
                StateTariff matched = matchedOpt.get();
                double discomBase = matched.getEnergyRate() != null ? matched.getEnergyRate().doubleValue()
                        : (matched.getBaseEnergyRate() != null ? matched.getBaseEnergyRate().doubleValue()
                                : fallbackRate);
                if ("kvah".equalsIgnoreCase(matched.getBaseEnergyUnit())) {
                    double pf = (powerFactor != null && powerFactor > 0) ? powerFactor : defaultPf;
                    discomBase = discomBase / pf;
                }
                String matchedTariffName = resolveTariffName(matched);
                return new MatchedTariffRate(matchedTariffName, discomBase);
            }
        }

        return new MatchedTariffRate("normal", fallbackRate);
    }

    /**
     * Convenience overload using configured property fallbacks.
     */
    public MatchedTariffRate resolveDiscomTariff(List<StateTariff> tariffs, int hour, Integer month,
            Double powerFactor, boolean isNpclHv2) {
        double fallbackRate = properties != null && properties.getFallback() != null
                ? properties.getFallback().getBaseDiscomRate()
                : RegulatoryFallbackConstants.DEFAULT_BASE_DISCOM_RATE;
        double defaultPf = properties != null && properties.getRegulatory() != null
                ? properties.getRegulatory().getDefaultPowerFactor()
                : RegulatoryConstants.DEFAULT_POWER_FACTOR;
        return resolveDiscomTariff(tariffs, hour, month, powerFactor, isNpclHv2, fallbackRate, defaultPf);
    }

    /**
     * Resolves the DISCOM tariff slab and effective rate for HV-2 calculations,
     * preserving Node.js legacy offpeak/peak fallback when tariffs are unconfigured.
     *
     * @param tariffs      the candidate state tariffs
     * @param hour         the hour of day (0-23)
     * @param month        the delivery month (1-12)
     * @param isNpclHv2    whether the client is on NPCL HV-2 tariff
     * @param fallbackRate fallback rate when tariffs are missing or unmatched
     * @return typed {@link MatchedTariffRate} containing ToD name and effective INR/kWh rate
     */
    public MatchedTariffRate resolveHv2DiscomTariff(List<StateTariff> tariffs, int hour, Integer month,
            boolean isNpclHv2, double fallbackRate) {
        if (isNpclHv2) {
            int m = month != null ? month : 1;
            NpclTariffSchedule.NpclTodRate tod = npclTariffSchedule.resolve(m, hour);
            return new MatchedTariffRate(tod.todName(), tod.rate());
        }

        if (tariffs != null && !tariffs.isEmpty()) {
            Optional<StateTariff> matchedOpt = matchTariff(tariffs, hour, month);
            if (matchedOpt.isPresent()) {
                StateTariff matched = matchedOpt.get();
                double discomLandingPrice = matched.getEnergyRate() != null ? matched.getEnergyRate().doubleValue()
                        : (matched.getBaseEnergyRate() != null ? matched.getBaseEnergyRate().doubleValue()
                                : fallbackRate);
                String matchedTariffName = resolveTariffName(matched);
                return new MatchedTariffRate(matchedTariffName, discomLandingPrice);
            }
        }

        // HV-2 empty/unmatched tariff fallback (Node.js legacy 1:1 parity)
        double discomLandingPrice = fallbackRate;
        String matchedTariffName = "normal";
        if (hour >= 22 || hour < 6) {
            discomLandingPrice = 6.0;
            matchedTariffName = "offpeak";
        } else if (hour >= 18 && hour < 22) {
            discomLandingPrice = 8.5;
            matchedTariffName = "peak";
        }
        return new MatchedTariffRate(matchedTariffName, discomLandingPrice);
    }

    /**
     * Convenience overload for HV-2 using configured property fallback.
     */
    public MatchedTariffRate resolveHv2DiscomTariff(List<StateTariff> tariffs, int hour, Integer month,
            boolean isNpclHv2) {
        double fallbackRate = properties != null && properties.getFallback() != null
                ? properties.getFallback().getBaseDiscomRate()
                : RegulatoryFallbackConstants.DEFAULT_BASE_DISCOM_RATE;
        return resolveHv2DiscomTariff(tariffs, hour, month, isNpclHv2, fallbackRate);
    }

    // =========================================================================
    // In-Memory Indexing & Caching Extensions (Milestone V18N)
    // =========================================================================

    /**
     * Pre-parsed representation of a {@link StateTariff} record.
     * Caches parsed hour bounds and uppercase ToD slab name to eliminate repeated
     * string parsing and formatting in high-frequency slot loops.
     */
    public record ParsedStateTariff(
            StateTariff tariff,
            boolean isTod,
            int startHour,
            int endHour,
            String formattedTodName) {

        public static ParsedStateTariff of(StateTariff t) {
            if (t == null) {
                return null;
            }
            boolean hasTod = t.getTodStartTime() != null && !"—".equals(t.getTodStartTime())
                    && t.getTodEndTime() != null && !"—".equals(t.getTodEndTime());
            if (!hasTod) {
                return new ParsedStateTariff(t, false, -1, -1, "FLAT");
            }
            int start = CalculatorUtils.parseHour(t.getTodStartTime());
            int end = CalculatorUtils.parseHour(t.getTodEndTime());
            String todName = (t.getTodStartTime() + "-" + t.getTodEndTime()).toUpperCase();
            return new ParsedStateTariff(t, true, start, end, todName);
        }

        public boolean matchesHour(int hour) {
            if (!isTod) {
                return false;
            }
            if (startHour <= endHour) {
                return hour >= startHour && hour < endHour;
            } else {
                return hour >= startHour || hour < endHour;
            }
        }
    }

    /**
     * Calculation-scoped index of {@link StateTariff} records.
     * Pre-groups tariffs by month and pre-parses ToD hour ranges to avoid
     * inner-loop stream filtering and string parsing.
     */
    public static class TariffIndex {
        private final Map<Integer, List<ParsedStateTariff>> tariffsByMonth;
        private final List<ParsedStateTariff> defaultTariffs;
        private final boolean isEmpty;

        public TariffIndex(List<StateTariff> tariffs) {
            if (tariffs == null || tariffs.isEmpty()) {
                this.tariffsByMonth = Collections.emptyMap();
                this.defaultTariffs = Collections.emptyList();
                this.isEmpty = true;
            } else {
                this.isEmpty = false;
                List<ParsedStateTariff> allParsed = new ArrayList<>(tariffs.size());
                Map<Integer, List<ParsedStateTariff>> byMonth = new HashMap<>();
                for (StateTariff t : tariffs) {
                    if (t == null) {
                        continue;
                    }
                    ParsedStateTariff parsed = ParsedStateTariff.of(t);
                    allParsed.add(parsed);
                    if (t.getMonth() != null) {
                        byMonth.computeIfAbsent(t.getMonth(), k -> new ArrayList<>()).add(parsed);
                    }
                }
                this.tariffsByMonth = byMonth;
                this.defaultTariffs = Collections.unmodifiableList(allParsed);
            }
        }

        public Optional<ParsedStateTariff> matchTariff(int hour, Integer month) {
            if (isEmpty) {
                return Optional.empty();
            }
            List<ParsedStateTariff> candidates = (month != null && tariffsByMonth.containsKey(month))
                    ? tariffsByMonth.get(month)
                    : defaultTariffs;
            if (candidates.isEmpty()) {
                candidates = defaultTariffs;
            }

            for (ParsedStateTariff pt : candidates) {
                if (pt.matchesHour(hour)) {
                    return Optional.of(pt);
                }
            }
            // Fall back to first FLAT tariff
            for (ParsedStateTariff pt : candidates) {
                if (!pt.isTod()) {
                    return Optional.of(pt);
                }
            }
            return Optional.empty();
        }

        public boolean isEmpty() {
            return isEmpty;
        }

        public List<ParsedStateTariff> defaultTariffs() {
            return defaultTariffs;
        }
    }

    /**
     * Calculation-scoped memoization table for resolved {@link MatchedTariffRate} instances.
     * Keyed by [month 0..12][hour 0..23].
     */
    public static class TariffRateCache {
        private final MatchedTariffRate[][] cache = new MatchedTariffRate[13][24];

        public MatchedTariffRate get(int month, int hour) {
            if (month >= 0 && month <= 12 && hour >= 0 && hour < 24) {
                return cache[month][hour];
            }
            return null;
        }

        public void put(int month, int hour, MatchedTariffRate rate) {
            if (month >= 0 && month <= 12 && hour >= 0 && hour < 24) {
                cache[month][hour] = rate;
            }
        }
    }

    /**
     * Calculation-scoped cache for ISTS transmission loss lookups.
     * Memoizes resolved loss percentages by {@link LocalDate} to avoid repeated linear scans.
     */
    public static class IstsLossCache {
        private final List<IstsCharges> istsChargesList;
        private final Map<LocalDate, Double> cache = new HashMap<>();

        public IstsLossCache(List<IstsCharges> istsChargesList) {
            this.istsChargesList = istsChargesList != null ? istsChargesList : Collections.emptyList();
        }

        public double resolve(LocalDate deliveryDate, RegulatoryChargeResolver resolver) {
            if (deliveryDate == null) {
                return 0.0;
            }
            Double cached = cache.get(deliveryDate);
            if (cached != null) {
                return cached;
            }
            double loss = resolver.resolveIstsLossPercent(deliveryDate, istsChargesList);
            cache.put(deliveryDate, loss);
            return loss;
        }

        public List<IstsCharges> istsChargesList() {
            return istsChargesList;
        }
    }

    public TariffIndex createTariffIndex(List<StateTariff> tariffs) {
        return new TariffIndex(tariffs);
    }

    public TariffRateCache createTariffRateCache() {
        return new TariffRateCache();
    }

    public IstsLossCache createIstsLossCache(List<IstsCharges> istsChargesList) {
        return new IstsLossCache(istsChargesList);
    }

    /**
     * Indexed ISTS loss resolution using calculation-scoped {@link IstsLossCache}.
     */
    public double resolveIstsLossPercentCached(LocalDate deliveryDate, IstsLossCache cache) {
        if (cache == null) {
            return 0.0;
        }
        return cache.resolve(deliveryDate, this);
    }

    /**
     * Indexed DISCOM tariff resolution using {@link TariffIndex} and calculation-scoped {@link TariffRateCache}.
     */
    public MatchedTariffRate resolveDiscomTariff(TariffIndex index, TariffRateCache cache, int hour, Integer month,
            Double powerFactor, boolean isNpclHv2, double fallbackRate, double defaultPf) {
        int m = month != null ? month : 0;
        if (cache != null) {
            MatchedTariffRate cached = cache.get(m, hour);
            if (cached != null) {
                return cached;
            }
        }

        MatchedTariffRate rate;
        if (isNpclHv2) {
            int scheduleMonth = month != null ? month : 1;
            NpclTariffSchedule.NpclTodRate tod = npclTariffSchedule.resolve(scheduleMonth, hour);
            rate = new MatchedTariffRate(tod.todName(), tod.rate());
        } else if (index != null && !index.isEmpty()) {
            Optional<ParsedStateTariff> matchedOpt = index.matchTariff(hour, month);
            if (matchedOpt.isPresent()) {
                ParsedStateTariff pt = matchedOpt.get();
                StateTariff matched = pt.tariff();
                double discomBase = matched.getEnergyRate() != null ? matched.getEnergyRate().doubleValue()
                        : (matched.getBaseEnergyRate() != null ? matched.getBaseEnergyRate().doubleValue()
                                : fallbackRate);
                if ("kvah".equalsIgnoreCase(matched.getBaseEnergyUnit())) {
                    double pf = (powerFactor != null && powerFactor > 0) ? powerFactor : defaultPf;
                    discomBase = discomBase / pf;
                }
                rate = new MatchedTariffRate(pt.formattedTodName(), discomBase);
            } else {
                rate = new MatchedTariffRate("normal", fallbackRate);
            }
        } else {
            rate = new MatchedTariffRate("normal", fallbackRate);
        }

        if (cache != null) {
            cache.put(m, hour, rate);
        }
        return rate;
    }

    /**
     * Indexed DISCOM tariff resolution convenience overload.
     */
    public MatchedTariffRate resolveDiscomTariff(TariffIndex index, TariffRateCache cache, int hour, Integer month,
            Double powerFactor, boolean isNpclHv2) {
        double fallbackRate = properties != null && properties.getFallback() != null
                ? properties.getFallback().getBaseDiscomRate()
                : RegulatoryFallbackConstants.DEFAULT_BASE_DISCOM_RATE;
        double defaultPf = properties != null && properties.getRegulatory() != null
                ? properties.getRegulatory().getDefaultPowerFactor()
                : RegulatoryConstants.DEFAULT_POWER_FACTOR;
        return resolveDiscomTariff(index, cache, hour, month, powerFactor, isNpclHv2, fallbackRate, defaultPf);
    }

    /**
     * Indexed HV-2 DISCOM tariff resolution using {@link TariffIndex} and calculation-scoped {@link TariffRateCache}.
     */
    public MatchedTariffRate resolveHv2DiscomTariff(TariffIndex index, TariffRateCache cache, int hour, Integer month,
            boolean isNpclHv2, double fallbackRate) {
        int m = month != null ? month : 0;
        if (cache != null) {
            MatchedTariffRate cached = cache.get(m, hour);
            if (cached != null) {
                return cached;
            }
        }

        MatchedTariffRate rate;
        if (isNpclHv2) {
            int scheduleMonth = month != null ? month : 1;
            NpclTariffSchedule.NpclTodRate tod = npclTariffSchedule.resolve(scheduleMonth, hour);
            rate = new MatchedTariffRate(tod.todName(), tod.rate());
        } else if (index != null && !index.isEmpty()) {
            Optional<ParsedStateTariff> matchedOpt = index.matchTariff(hour, month);
            if (matchedOpt.isPresent()) {
                ParsedStateTariff pt = matchedOpt.get();
                StateTariff matched = pt.tariff();
                double discomLandingPrice = matched.getEnergyRate() != null ? matched.getEnergyRate().doubleValue()
                        : (matched.getBaseEnergyRate() != null ? matched.getBaseEnergyRate().doubleValue()
                                : fallbackRate);
                rate = new MatchedTariffRate(pt.formattedTodName(), discomLandingPrice);
            } else {
                rate = fallbackHv2Tariff(hour, fallbackRate);
            }
        } else {
            rate = fallbackHv2Tariff(hour, fallbackRate);
        }

        if (cache != null) {
            cache.put(m, hour, rate);
        }
        return rate;
    }

    /**
     * Indexed HV-2 DISCOM tariff resolution convenience overload.
     */
    public MatchedTariffRate resolveHv2DiscomTariff(TariffIndex index, TariffRateCache cache, int hour, Integer month,
            boolean isNpclHv2) {
        double fallbackRate = properties != null && properties.getFallback() != null
                ? properties.getFallback().getBaseDiscomRate()
                : RegulatoryFallbackConstants.DEFAULT_BASE_DISCOM_RATE;
        return resolveHv2DiscomTariff(index, cache, hour, month, isNpclHv2, fallbackRate);
    }

    private MatchedTariffRate fallbackHv2Tariff(int hour, double fallbackRate) {
        double discomLandingPrice = fallbackRate;
        String matchedTariffName = "normal";
        if (hour >= 22 || hour < 6) {
            discomLandingPrice = 6.0;
            matchedTariffName = "offpeak";
        } else if (hour >= 18 && hour < 22) {
            discomLandingPrice = 8.5;
            matchedTariffName = "peak";
        }
        return new MatchedTariffRate(matchedTariffName, discomLandingPrice);
    }
}

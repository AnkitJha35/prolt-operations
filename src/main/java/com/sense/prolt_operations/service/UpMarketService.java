package com.sense.prolt_operations.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sense.prolt_operations.entity.ExchangeDamRate;
import com.sense.prolt_operations.entity.ExchangeGdamRate;
import com.sense.prolt_operations.entity.ExchangeRtmRate;
import com.sense.prolt_operations.repository.ExchangeDamRateRepository;
import com.sense.prolt_operations.repository.ExchangeGdamRateRepository;
import com.sense.prolt_operations.repository.ExchangeRtmRateRepository;

@Service
@Transactional(readOnly = true)
public class UpMarketService {

    private static final Logger log = LoggerFactory.getLogger(UpMarketService.class);

    private static final Map<String, List<String>> REGION_ALIASES = Map.of(
        "N1", List.of("N1", "Haryana"),
        "N2", List.of("N2", "UP", "Uttar Pradesh", "Uttarakhand"),
        "N3", List.of("N3", "Punjab"),
        "Haryana", List.of("Haryana", "N1"),
        "UP", List.of("UP", "Uttar Pradesh", "N2"),
        "Uttar Pradesh", List.of("Uttar Pradesh", "UP", "N2"),
        "Uttarakhand", List.of("Uttarakhand", "N2"),
        "Punjab", List.of("Punjab", "N3")
    );

    private final ExchangeDamRateRepository exchangeDamRateRepository;
    private final ExchangeRtmRateRepository exchangeRtmRateRepository;
    private final ExchangeGdamRateRepository exchangeGdamRateRepository;

    public UpMarketService(
            ExchangeDamRateRepository exchangeDamRateRepository,
            ExchangeRtmRateRepository exchangeRtmRateRepository,
            ExchangeGdamRateRepository exchangeGdamRateRepository) {
        this.exchangeDamRateRepository = exchangeDamRateRepository;
        this.exchangeRtmRateRepository = exchangeRtmRateRepository;
        this.exchangeGdamRateRepository = exchangeGdamRateRepository;
    }

    public Map<String, Object> getMarketData(String market, String startDateStr, String endDateStr, String selectedState) {
        LocalDate start = LocalDate.parse(startDateStr.trim());
        LocalDate end = LocalDate.parse(endDateStr.trim());

        List<String> allowedStates = null;
        if (selectedState != null && !selectedState.trim().isEmpty() && !"ALL".equalsIgnoreCase(selectedState)) {
            allowedStates = REGION_ALIASES.getOrDefault(selectedState.trim(), List.of(selectedState.trim()));
        }

        List<UnifiedMarketRow> rows = new ArrayList<>();
        String marketUpper = market.trim().toUpperCase();

        if ("DAM".equals(marketUpper)) {
            final List<String> filterStates = allowedStates;
            List<ExchangeDamRate> records = exchangeDamRateRepository.findAll().stream()
                    .filter(r -> r.getDate() != null && !r.getDate().isBefore(start) && !r.getDate().isAfter(end))
                    .filter(r -> filterStates == null || filterStates.contains(r.getState()))
                    .sorted(Comparator.comparing(ExchangeDamRate::getDate).thenComparing(ExchangeDamRate::getIntervalTime))
                    .toList();

            for (ExchangeDamRate r : records) {
                rows.add(new UnifiedMarketRow(
                    String.valueOf(r.getId()),
                    r.getDate(),
                    r.getIntervalTime(),
                    r.getState(),
                    r.getMcp(),
                    r.getMcv(),
                    r.getPurchaseBid(),
                    r.getSellBid()
                ));
            }
        } else if ("RTM".equals(marketUpper)) {
            final List<String> filterStates = allowedStates;
            List<ExchangeRtmRate> records = exchangeRtmRateRepository.findAll().stream()
                    .filter(r -> r.getDate() != null && !r.getDate().isBefore(start) && !r.getDate().isAfter(end))
                    .filter(r -> filterStates == null || filterStates.contains(r.getState()))
                    .sorted(Comparator.comparing(ExchangeRtmRate::getDate).thenComparing(ExchangeRtmRate::getIntervalTime))
                    .toList();

            for (ExchangeRtmRate r : records) {
                rows.add(new UnifiedMarketRow(
                    String.valueOf(r.getId()),
                    r.getDate(),
                    r.getIntervalTime(),
                    r.getState(),
                    r.getMcp(),
                    r.getMcv(),
                    r.getPurchaseBid(),
                    r.getSellBid()
                ));
            }
        } else if ("GDAM".equals(marketUpper)) {
            final List<String> filterStates = allowedStates;
            List<ExchangeGdamRate> records = exchangeGdamRateRepository.findAll().stream()
                    .filter(r -> r.getDate() != null && !r.getDate().isBefore(start) && !r.getDate().isAfter(end))
                    .filter(r -> filterStates == null || filterStates.contains(r.getState()))
                    .sorted(Comparator.comparing(ExchangeGdamRate::getDate).thenComparing(ExchangeGdamRate::getIntervalTime))
                    .toList();

            for (ExchangeGdamRate r : records) {
                rows.add(new UnifiedMarketRow(
                    String.valueOf(r.getId()),
                    r.getDate(),
                    r.getIntervalTime(),
                    r.getState(),
                    r.getMcp(),
                    r.getMcv(),
                    r.getPurchaseBid(),
                    r.getSellBid()
                ));
            }
        }

        if (rows.isEmpty()) {
            return Map.of("intervals", List.of(), "analytics", getEmptyAnalytics());
        }

        List<Map<String, Object>> intervals = new ArrayList<>();
        double totalMcp = 0.0;
        double maxMcp = -Double.MAX_VALUE;
        double minMcp = Double.MAX_VALUE;
        double maxMcv = -Double.MAX_VALUE;

        for (UnifiedMarketRow r : rows) {
            LocalTime t = r.intervalTime != null ? r.intervalTime : LocalTime.of(0, 0);
            int hh = t.getHour();
            int mm = t.getMinute();
            int endMm = mm + 15;
            int endHh = hh;
            if (endMm >= 60) {
                endMm -= 60;
                endHh += 1;
            }

            String timeStr = String.format("%02d:%02d", hh, mm);
            String timeBlockStr = String.format("%02d:%02d-%02d:%02d", hh, mm, endHh, endMm);
            String hourStr = String.format("%02d:00-%02d:00", hh, (hh + 1));

            double mcpVal = r.mcp != null ? r.mcp.doubleValue() : 0.0;
            double mcvVal = r.mcv != null ? r.mcv.doubleValue() : 0.0;
            double pBidVal = r.purchaseBid != null ? r.purchaseBid.doubleValue() : 0.0;
            double sBidVal = r.sellBid != null ? r.sellBid.doubleValue() : 0.0;

            totalMcp += mcpVal;
            if (mcpVal > maxMcp) maxMcp = mcpVal;
            if (mcpVal < minMcp) minMcp = mcpVal;
            if (mcvVal > maxMcv) maxMcv = mcvVal;

            Map<String, Object> inv = new HashMap<>();
            inv.put("id", r.id);
            inv.put("date", r.date.toString());
            inv.put("timeblock", timeStr);
            inv.put("hour", hourStr);
            inv.put("timeBlock", timeBlockStr);
            inv.put("state", r.state);
            inv.put("region", r.state);
            inv.put("mcp", mcpVal);
            inv.put("mcv", mcvVal);
            inv.put("purchaseBid", pBidVal);
            inv.put("sellBid", sBidVal);
            intervals.add(inv);
        }

        double avgMcp = BigDecimal.valueOf(totalMcp / intervals.size()).setScale(2, RoundingMode.HALF_UP).doubleValue();

        Map<String, Object> analytics = Map.of(
            "averageMcp", avgMcp,
            "maxMcp", maxMcp == -Double.MAX_VALUE ? 0.0 : maxMcp,
            "minMcp", minMcp == Double.MAX_VALUE ? 0.0 : minMcp,
            "maxMcv", maxMcv == -Double.MAX_VALUE ? 0.0 : maxMcv
        );

        return Map.of("intervals", intervals, "analytics", analytics);
    }

    private Map<String, Object> getEmptyAnalytics() {
        return Map.of(
            "averageMcp", 0.0,
            "totalVolume", 0.0,
            "maxMcv", 0.0,
            "maxFsv", 0.0,
            "maxMcp", 0.0,
            "minMcp", 0.0
        );
    }

    private record UnifiedMarketRow(
        String id,
        LocalDate date,
        LocalTime intervalTime,
        String state,
        BigDecimal mcp,
        BigDecimal mcv,
        BigDecimal purchaseBid,
        BigDecimal sellBid
    ) {}
}

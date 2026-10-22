package com.sense.prolt_operations.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sense.prolt_operations.entity.DamRecord;
import com.sense.prolt_operations.entity.Dataset;
import com.sense.prolt_operations.entity.DatasetStatus;
import com.sense.prolt_operations.entity.GdamNewRecord;
import com.sense.prolt_operations.entity.GdamRecord;
import com.sense.prolt_operations.entity.MarketType;
import com.sense.prolt_operations.entity.RtmRecord;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.repository.DamRecordRepository;
import com.sense.prolt_operations.repository.DatasetRepository;
import com.sense.prolt_operations.repository.GdamNewRecordRepository;
import com.sense.prolt_operations.repository.GdamRecordRepository;
import com.sense.prolt_operations.repository.RtmRecordRepository;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private static final LocalDate GDAM_TRANSITION_DATE = LocalDate.of(2026, 7, 13);
    private static final DateTimeFormatter PRETTY_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final DatasetRepository datasetRepository;
    private final DamRecordRepository damRecordRepository;
    private final GdamRecordRepository gdamRecordRepository;
    private final GdamNewRecordRepository gdamNewRecordRepository;
    private final RtmRecordRepository rtmRecordRepository;

    public DashboardService(DatasetRepository datasetRepository,
                            DamRecordRepository damRecordRepository,
                            GdamRecordRepository gdamRecordRepository,
                            GdamNewRecordRepository gdamNewRecordRepository,
                            RtmRecordRepository rtmRecordRepository) {
        this.datasetRepository = datasetRepository;
        this.damRecordRepository = damRecordRepository;
        this.gdamRecordRepository = gdamRecordRepository;
        this.gdamNewRecordRepository = gdamNewRecordRepository;
        this.rtmRecordRepository = rtmRecordRepository;
    }

    public Map<String, Object> getDashboardData(String marketStr, String startDateStr, String endDateStr, String interval) {
        if (startDateStr == null || startDateStr.isBlank() || endDateStr == null || endDateStr.isBlank()) {
            throw new BadRequestException("Date range parameters are required");
        }

        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = LocalDate.parse(startDateStr.trim());
            endDate = LocalDate.parse(endDateStr.trim());
        } catch (Exception e) {
            throw new BadRequestException("Invalid date format. Expected YYYY-MM-DD");
        }

        MarketType market;
        try {
            market = MarketType.valueOf(marketStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid market: " + marketStr);
        }

        List<Dataset> datasets = datasetRepository.findByMarketAndStatusAndDeliveryDateBetweenOrderByDeliveryDateAsc(
                market, DatasetStatus.ACTIVE, startDate, endDate);

        if (datasets.isEmpty()) {
            throw new NotFoundException(String.format("No ACTIVE datasets found for %s between %s and %s",
                    market.name(), startDateStr, endDateStr));
        }

        Map<String, Object> datasetMeta = Map.of(
            "market", datasets.get(0).getMarket().name(),
            "startDate", startDateStr,
            "endDate", endDateStr,
            "status", datasets.get(0).getStatus().name()
        );

        List<Map<String, Object>> intervals = new ArrayList<>();

        for (Dataset ds : datasets) {
            List<Map<String, Object>> rawRows = fetchRawRows(ds, market);
            String prettyDate = ds.getDeliveryDate().format(PRETTY_DATE_FORMATTER);
            String dateString = ds.getDeliveryDate().toString();

            if ("daily".equalsIgnoreCase(interval)) {
                Map<String, Object> daily = averageRows(rawRows, Map.of("date", prettyDate, "sortDate", dateString));
                if (daily != null) {
                    intervals.add(daily);
                }
            } else if ("hourly".equalsIgnoreCase(interval)) {
                for (int i = 0; i < 24; i++) {
                    int from = i * 4;
                    int to = Math.min(from + 4, rawRows.size());
                    if (from >= rawRows.size()) break;
                    List<Map<String, Object>> chunk = rawRows.subList(from, to);
                    String hourStr = String.format("%02d", i + 1);
                    Map<String, Object> hourly = averageRows(chunk, Map.of("date", prettyDate, "hour", hourStr));
                    if (hourly != null) {
                        intervals.add(hourly);
                    }
                }
            } else {
                // Default 15min
                for (Map<String, Object> row : rawRows) {
                    Map<String, Object> formatted = new LinkedHashMap<>(row);
                    formatted.put("date", prettyDate);
                    int intervalNum = ((Number) row.getOrDefault("intervalNumber", 1)).intValue();
                    int hourNum = ((intervalNum - 1) / 4) + 1;
                    formatted.put("hour", String.format("%02d", hourNum));
                    formatted.put("timeBlock", row.getOrDefault("intervalTime", ""));
                    intervals.add(formatted);
                }
            }
        }

        Map<String, Object> analytics = computeAnalytics(market.name(), startDateStr + " to " + endDateStr, intervals);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dataset", datasetMeta);
        result.put("intervals", intervals);
        result.put("analytics", analytics);
        return result;
    }

    public Object getAnalytics(String market, String startDateStr, String endDateStr, String interval) {
        Map<String, Object> data = getDashboardData(market, startDateStr, endDateStr, interval);
        return data.get("analytics");
    }

    public Map<String, Object> getOverview() {
        List<Dataset> activeDatasets = datasetRepository.findByStatus(DatasetStatus.ACTIVE);

        Map<String, Object> overview = new LinkedHashMap<>();
        for (String m : List.of("dam", "gdam", "rtm")) {
            overview.put(m, Map.of(
                "coverageStart", "",
                "coverageEnd", "",
                "activeDatasetCount", 0
            ));
        }

        Map<MarketType, List<Dataset>> grouped = new HashMap<>();
        for (Dataset d : activeDatasets) {
            grouped.computeIfAbsent(d.getMarket(), k -> new ArrayList<>()).add(d);
        }

        for (var entry : grouped.entrySet()) {
            String key = entry.getKey().name().toLowerCase();
            List<Dataset> list = entry.getValue();
            LocalDate minDate = list.stream().map(Dataset::getDeliveryDate).min(LocalDate::compareTo).orElse(null);
            LocalDate maxDate = list.stream().map(Dataset::getDeliveryDate).max(LocalDate::compareTo).orElse(null);

            overview.put(key, Map.of(
                "coverageStart", minDate != null ? minDate.toString() : "",
                "coverageEnd", maxDate != null ? maxDate.toString() : "",
                "activeDatasetCount", list.size()
            ));
        }

        return overview;
    }

    private List<Map<String, Object>> fetchRawRows(Dataset ds, MarketType market) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (market == MarketType.DAM) {
            List<DamRecord> records = damRecordRepository.findByDatasetIdOrderByIntervalNumberAsc(ds.getId());
            for (DamRecord r : records) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("intervalNumber", r.getIntervalNumber() != null ? r.getIntervalNumber().intValue() : 1);
                m.put("intervalTime", r.getIntervalTime());
                m.put("purchaseBid", r.getPurchaseBid() != null ? r.getPurchaseBid().doubleValue() : 0.0);
                m.put("sellBid", r.getSellBid() != null ? r.getSellBid().doubleValue() : 0.0);
                m.put("mcv", r.getMcv() != null ? r.getMcv().doubleValue() : 0.0);
                m.put("fsv", r.getFsv() != null ? r.getFsv().doubleValue() : 0.0);
                m.put("mcp", r.getMcp() != null ? r.getMcp().doubleValue() : 0.0);
                list.add(m);
            }
        } else if (market == MarketType.GDAM) {
            if (ds.getDeliveryDate().isBefore(GDAM_TRANSITION_DATE)) {
                List<GdamRecord> records = gdamRecordRepository.findByDatasetIdOrderByIntervalNumberAsc(ds.getId());
                for (GdamRecord r : records) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("intervalNumber", r.getIntervalNumber() != null ? r.getIntervalNumber().intValue() : 1);
                    m.put("intervalTime", r.getIntervalTime());
                    m.put("purchaseBid", r.getPurchaseBid() != null ? r.getPurchaseBid().doubleValue() : 0.0);
                    m.put("sellBid", r.getSellBidTotal() != null ? r.getSellBidTotal().doubleValue() : 0.0);
                    m.put("mcv", r.getMcvTotal() != null ? r.getMcvTotal().doubleValue() : 0.0);
                    m.put("mcp", r.getMcp() != null ? r.getMcp().doubleValue() : 0.0);
                    list.add(m);
                }
            } else {
                List<GdamNewRecord> records = gdamNewRecordRepository.findByDatasetIdOrderByIntervalNumberAsc(ds.getId());
                for (GdamNewRecord r : records) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("intervalNumber", r.getIntervalNumber() != null ? r.getIntervalNumber().intValue() : 1);
                    m.put("intervalTime", r.getIntervalTime());
                    m.put("purchaseBidTotal", r.getPurchaseBid() != null ? r.getPurchaseBid().doubleValue() : 0.0);
                    m.put("sellBidTotal", r.getSellBidTotal() != null ? r.getSellBidTotal().doubleValue() : 0.0);
                    m.put("mcvTotal", r.getMcvTotal() != null ? r.getMcvTotal().doubleValue() : 0.0);
                    m.put("fsvTotal", r.getMcvTotal() != null ? r.getMcvTotal().doubleValue() : 0.0);
                    m.put("mcp", r.getMcp() != null ? r.getMcp().doubleValue() : 0.0);
                    list.add(m);
                }
            }
        } else if (market == MarketType.RTM) {
            List<RtmRecord> records = rtmRecordRepository.findByDatasetIdOrderByIntervalNumberAsc(ds.getId());
            for (RtmRecord r : records) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("intervalNumber", r.getIntervalNumber() != null ? r.getIntervalNumber().intValue() : 1);
                m.put("intervalTime", r.getIntervalTime());
                m.put("purchaseBid", r.getPurchaseBid() != null ? r.getPurchaseBid().doubleValue() : 0.0);
                m.put("sellBid", r.getSellBid() != null ? r.getSellBid().doubleValue() : 0.0);
                m.put("mcv", r.getMcv() != null ? r.getMcv().doubleValue() : 0.0);
                m.put("fsv", r.getFsv() != null ? r.getFsv().doubleValue() : 0.0);
                m.put("mcp", r.getMcp() != null ? r.getMcp().doubleValue() : 0.0);
                list.add(m);
            }
        }
        return list;
    }

    private Map<String, Object> averageRows(List<Map<String, Object>> rows, Map<String, Object> extra) {
        if (rows.isEmpty()) return null;
        Map<String, Object> avg = new LinkedHashMap<>(extra);
        Map<String, Double> sums = new HashMap<>();

        for (Map<String, Object> r : rows) {
            for (var entry : r.entrySet()) {
                if (entry.getValue() instanceof Number n &&
                    !entry.getKey().equals("intervalNumber") &&
                    !entry.getKey().equals("id")) {
                    sums.put(entry.getKey(), sums.getOrDefault(entry.getKey(), 0.0) + n.doubleValue());
                }
            }
        }

        int count = rows.size();
        for (var entry : sums.entrySet()) {
            double mean = entry.getValue() / count;
            avg.put(entry.getKey(), BigDecimal.valueOf(mean).setScale(4, RoundingMode.HALF_UP).doubleValue());
        }
        return avg;
    }

    private Map<String, Object> computeAnalytics(String market, String dateStr, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return Map.of(
                "market", market,
                "deliveryDate", dateStr,
                "maxMcp", 0.0, "minMcp", 0.0, "averageMcp", 0.0,
                "maxMcv", 0.0, "maxFsv", 0.0, "totalVolume", 0.0,
                "recordCount", 0
            );
        }

        double sumMcp = 0.0;
        double sumFsv = 0.0;
        double maxMcp = -Double.MAX_VALUE;
        double minMcp = Double.MAX_VALUE;
        double maxMcv = -Double.MAX_VALUE;
        double maxFsv = -Double.MAX_VALUE;

        String fsvKey = market.equalsIgnoreCase("GDAM") ? "fsvTotal" : "fsv";
        String mcvKey = market.equalsIgnoreCase("GDAM") ? "mcvTotal" : "mcv";

        for (Map<String, Object> r : rows) {
            double mcp = getDouble(r, "mcp");
            double fsv = getDouble(r, fsvKey);
            double mcv = getDouble(r, mcvKey);

            sumMcp += mcp;
            sumFsv += fsv;
            if (mcp > maxMcp) maxMcp = mcp;
            if (mcp < minMcp) minMcp = mcp;
            if (mcv > maxMcv) maxMcv = mcv;
            if (fsv > maxFsv) maxFsv = fsv;
        }

        double averageMcp = sumMcp / rows.size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("market", market);
        result.put("deliveryDate", dateStr);
        result.put("maxMcp", round(maxMcp));
        result.put("minMcp", round(minMcp == Double.MAX_VALUE ? 0.0 : minMcp));
        result.put("averageMcp", round(averageMcp));
        result.put("maxMcv", round(maxMcv == -Double.MAX_VALUE ? 0.0 : maxMcv));
        result.put("maxFsv", round(maxFsv == -Double.MAX_VALUE ? 0.0 : maxFsv));
        result.put("totalVolume", round(sumFsv));
        result.put("recordCount", rows.size());
        return result;
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private double round(double val) {
        return BigDecimal.valueOf(val).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}

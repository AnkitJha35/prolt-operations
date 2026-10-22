package com.sense.prolt_operations.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.ExchangeDamRate;
import com.sense.prolt_operations.entity.ExchangeGdamRate;
import com.sense.prolt_operations.entity.ExchangeRtmRate;
import com.sense.prolt_operations.entity.forecasting.DamForecasting;
import com.sense.prolt_operations.entity.forecasting.ForecastAllIndiaDemand;
import com.sense.prolt_operations.entity.forecasting.ForecastConsumerDemand;
import com.sense.prolt_operations.entity.forecasting.GenerationForecasting;
import com.sense.prolt_operations.entity.forecasting.MarketForecasting;
import com.sense.prolt_operations.repository.DamForecastingRepository;
import com.sense.prolt_operations.repository.ExchangeDamRateRepository;
import com.sense.prolt_operations.repository.ExchangeGdamRateRepository;
import com.sense.prolt_operations.repository.ExchangeRtmRateRepository;
import com.sense.prolt_operations.repository.ForecastAllIndiaDemandRepository;
import com.sense.prolt_operations.repository.ForecastConsumerDemandRepository;
import com.sense.prolt_operations.repository.GenerationForecastingRepository;
import com.sense.prolt_operations.repository.MarketForecastingRepository;

@Service
@Transactional(readOnly = true)
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);

    private final DamForecastingRepository damForecastingRepository;
    private final ForecastAllIndiaDemandRepository forecastAllIndiaDemandRepository;
    private final ForecastConsumerDemandRepository forecastConsumerDemandRepository;
    private final GenerationForecastingRepository generationForecastingRepository;
    private final MarketForecastingRepository marketForecastingRepository;
    private final ExchangeDamRateRepository exchangeDamRateRepository;
    private final ExchangeRtmRateRepository exchangeRtmRateRepository;
    private final ExchangeGdamRateRepository exchangeGdamRateRepository;
    private final StarrocksService starrocksService;
    private final ObjectMapper objectMapper;

    public ForecastService(
            DamForecastingRepository damForecastingRepository,
            ForecastAllIndiaDemandRepository forecastAllIndiaDemandRepository,
            ForecastConsumerDemandRepository forecastConsumerDemandRepository,
            GenerationForecastingRepository generationForecastingRepository,
            MarketForecastingRepository marketForecastingRepository,
            ExchangeDamRateRepository exchangeDamRateRepository,
            ExchangeRtmRateRepository exchangeRtmRateRepository,
            ExchangeGdamRateRepository exchangeGdamRateRepository,
            StarrocksService starrocksService,
            ObjectMapper objectMapper) {
        this.damForecastingRepository = damForecastingRepository;
        this.forecastAllIndiaDemandRepository = forecastAllIndiaDemandRepository;
        this.forecastConsumerDemandRepository = forecastConsumerDemandRepository;
        this.generationForecastingRepository = generationForecastingRepository;
        this.marketForecastingRepository = marketForecastingRepository;
        this.exchangeDamRateRepository = exchangeDamRateRepository;
        this.exchangeRtmRateRepository = exchangeRtmRateRepository;
        this.exchangeGdamRateRepository = exchangeGdamRateRepository;
        this.starrocksService = starrocksService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> getPriceForecast(String market, String startDateStr, String endDateStr, String interval, String model) {
        if ("NPP".equalsIgnoreCase(market) || "GENERATION".equalsIgnoreCase(market)) {
            return getGenerationForecast(startDateStr, endDateStr, interval, model);
        }

        LocalDate start = LocalDate.parse(startDateStr.trim());
        LocalDate end = LocalDate.parse(endDateStr.trim());
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atTime(23, 59, 59);

        // Fetch actuals for UP
        Map<String, Double> actualMap = new HashMap<>();
        if ("DAM".equalsIgnoreCase(market)) {
            List<ExchangeDamRate> actuals = exchangeDamRateRepository.findAll().stream()
                    .filter(r -> r.getDate() != null && !r.getDate().isBefore(start) && !r.getDate().isAfter(end))
                    .filter(r -> r.getState() != null && List.of("UP", "Uttar Pradesh", "N2").contains(r.getState()))
                    .toList();

            for (ExchangeDamRate act : actuals) {
                if (act.getIntervalTime() != null && act.getMcp() != null) {
                    int timeblock = (act.getIntervalTime().getHour() * 60 + act.getIntervalTime().getMinute()) / 15 + 1;
                    String key = String.format("%s_%d", act.getDate(), timeblock);
                    actualMap.put(key, act.getMcp().doubleValue() / 1000.0);
                }
            }
        }

        List<DamForecasting> rows = damForecastingRepository.findAll().stream()
                .filter(r -> r.getForecastingFor() != null && !r.getForecastingFor().isBefore(startDt) && !r.getForecastingFor().isAfter(endDt))
                .sorted((a, b) -> {
                    int c = a.getForecastingFor().compareTo(b.getForecastingFor());
                    if (c != 0) return c;
                    return Short.compare(a.getTimeBlock() != null ? a.getTimeBlock() : 0, b.getTimeBlock() != null ? b.getTimeBlock() : 0);
                })
                .toList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (DamForecasting r : rows) {
            int tb = r.getTimeBlock() != null ? r.getTimeBlock() : 1;
            String hour = String.format("%02d", (tb - 1) / 4 + 1);
            String timeBlock = formatIntervalTime(tb);
            String dateStr = r.getForecastingFor().toLocalDate().toString();
            String key = String.format("%s_%d", dateStr, tb);

            Double predictedMcp = r.getPredictedMcp() != null ? r.getPredictedMcp().doubleValue() / 1000.0 : null;
            Double actualMcp = actualMap.get(key);

            Map<String, Object> map = new HashMap<>();
            map.put("date", dateStr);
            map.put("hour", hour);
            map.put("timeBlock", timeBlock);
            map.put("intervalNumber", tb);
            map.put("purchaseBid", 0);
            map.put("sellBid", 0);
            map.put("mcv", 0);
            map.put("fsv", 0);
            map.put("mcp", predictedMcp);
            map.put("actualMcp", actualMcp);
            list.add(map);
        }
        return list;
    }

    public List<Map<String, Object>> getDemandForecast(String type, String startDateStr, String endDateStr, String interval, String meterNo) {
        LocalDate start = LocalDate.parse(startDateStr.trim());
        LocalDate end = LocalDate.parse(endDateStr.trim());
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atTime(23, 59, 59);

        if ("consumer".equalsIgnoreCase(type) && meterNo != null && !meterNo.trim().isEmpty()) {
            List<ForecastConsumerDemand> rows = forecastConsumerDemandRepository.findAll().stream()
                    .filter(r -> meterNo.trim().equalsIgnoreCase(r.getMeterId()))
                    .filter(r -> r.getTimestamp() != null && !r.getTimestamp().isBefore(startDt) && !r.getTimestamp().isAfter(endDt))
                    .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                    .toList();

            List<Map<String, Object>> list = new ArrayList<>();
            for (ForecastConsumerDemand r : rows) {
                int slot = r.getSlotNumber() != null ? r.getSlotNumber() : 1;
                Map<String, Object> m = new HashMap<>();
                m.put("date", r.getTimestamp().toLocalDate().toString());
                m.put("hour", String.format("%02d", (slot - 1) / 4 + 1));
                m.put("timeBlock", formatIntervalTime(slot));
                m.put("intervalNumber", slot);
                m.put("demand", r.getForecastedEnergy() != null ? r.getForecastedEnergy() : 0);
                m.put("actualDemand", r.getActualEnergy());
                list.add(m);
            }
            return list;
        }

        // All India Demand default
        List<ForecastAllIndiaDemand> rows = forecastAllIndiaDemandRepository.findAll().stream()
                .filter(r -> r.getDate() != null && r.getDate().compareTo(startDateStr.trim()) >= 0 && r.getDate().compareTo(endDateStr.trim()) <= 0)
                .sorted((a, b) -> {
                    int c = a.getDate().compareTo(b.getDate());
                    if (c != 0) return c;
                    return Short.compare(a.getIntervalNumber() != null ? a.getIntervalNumber() : 0, b.getIntervalNumber() != null ? b.getIntervalNumber() : 0);
                })
                .toList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (ForecastAllIndiaDemand r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("date", r.getDate());
            m.put("hour", r.getHour());
            m.put("timeBlock", r.getTimeBlock());
            m.put("intervalNumber", r.getIntervalNumber());
            m.put("demand", r.getForecastedDemand() != null ? r.getForecastedDemand() : 0);
            m.put("actualDemand", r.getActualDemand());
            list.add(m);
        }
        return list;
    }

    public List<Map<String, Object>> getGenerationForecast(String startDateStr, String endDateStr, String interval, String model) {
        LocalDate start = LocalDate.parse(startDateStr.trim());
        LocalDate end = LocalDate.parse(endDateStr.trim());
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atTime(23, 59, 59);

        List<GenerationForecasting> rows = generationForecastingRepository.findAll().stream()
                .filter(r -> r.getForecastForTimestamp() != null && !r.getForecastForTimestamp().isBefore(startDt) && !r.getForecastForTimestamp().isAfter(endDt))
                .sorted((a, b) -> a.getForecastForTimestamp().compareTo(b.getForecastForTimestamp()))
                .toList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (GenerationForecasting r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("timestamp", r.getForecastForTimestamp());
            m.put("source", r.getSource());
            m.put("forecastMw", r.getForecastMw());
            m.put("modelName", r.getModelName());
            list.add(m);
        }
        return list;
    }

    public List<String> getForecastDates(String market) {
        return damForecastingRepository.findAll().stream()
                .map(DamForecasting::getForecastingFor)
                .filter(dt -> dt != null)
                .map(dt -> dt.toLocalDate().toString())
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> getMeters() {
        List<String> meters = starrocksService.getAvailableMeters();
        if (meters.isEmpty()) {
            return forecastConsumerDemandRepository.findAll().stream()
                    .map(ForecastConsumerDemand::getMeterId)
                    .filter(m -> m != null && !m.trim().isEmpty())
                    .distinct()
                    .sorted()
                    .toList();
        }
        return meters;
    }

    public List<Map<String, Object>> getMarketSelectionForecast(String startDateStr, String endDateStr) {
        List<MarketForecasting> rows = marketForecastingRepository.findAll().stream()
                .filter(r -> r.getDate() != null && r.getDate().compareTo(startDateStr.trim()) >= 0 && r.getDate().compareTo(endDateStr.trim()) <= 0)
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .toList();

        List<Map<String, Object>> list = new ArrayList<>();
        for (MarketForecasting r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("date", r.getDate());
            try {
                if (r.getSlots() != null) {
                    m.put("slots", objectMapper.readValue(r.getSlots(), new TypeReference<List<Map<String, Object>>>() {}));
                } else {
                    m.put("slots", List.of());
                }
            } catch (Exception e) {
                m.put("slots", List.of());
            }
            list.add(m);
        }
        return list;
    }

    private String formatIntervalTime(int intervalNum) {
        int totalMinutes = (intervalNum - 1) * 15;
        int startHour = totalMinutes / 60;
        int startMin = totalMinutes % 60;
        int endMinutes = totalMinutes + 15;
        int endHour = endMinutes / 60;
        int endMin = endMinutes % 60;
        return String.format("%02d:%02d-%02d:%02d", startHour, startMin, endHour, endMin);
    }
}

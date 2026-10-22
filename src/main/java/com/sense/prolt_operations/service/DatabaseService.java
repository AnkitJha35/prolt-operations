package com.sense.prolt_operations.service;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sense.prolt_operations.entity.CityStateData;
import com.sense.prolt_operations.entity.NppRawDemandData;
import com.sense.prolt_operations.entity.NppRawGenerationData;
import com.sense.prolt_operations.entity.StateDemandData;
import com.sense.prolt_operations.entity.WeatherForecastHourly;
import com.sense.prolt_operations.entity.WeatherHistorical;
import com.sense.prolt_operations.repository.CityStateDataRepository;
import com.sense.prolt_operations.repository.NppAdjustedDemandDataRepository;
import com.sense.prolt_operations.repository.NppAdjustedGenerationDataRepository;
import com.sense.prolt_operations.repository.NppRawDemandDataRepository;
import com.sense.prolt_operations.repository.NppRawGenerationDataRepository;
import com.sense.prolt_operations.repository.StateDemandDataRepository;
import com.sense.prolt_operations.repository.WeatherForecastHourlyRepository;
import com.sense.prolt_operations.repository.WeatherHistoricalRepository;

@Service
@Transactional(readOnly = true)
public class DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);

    private final NppRawDemandDataRepository nppRawDemandDataRepository;
    private final NppAdjustedDemandDataRepository nppAdjustedDemandDataRepository;
    private final NppRawGenerationDataRepository nppRawGenerationDataRepository;
    private final NppAdjustedGenerationDataRepository nppAdjustedGenerationDataRepository;
    private final StateDemandDataRepository stateDemandDataRepository;
    private final CityStateDataRepository cityStateDataRepository;
    private final WeatherForecastHourlyRepository weatherForecastHourlyRepository;
    private final WeatherHistoricalRepository weatherHistoricalRepository;

    public DatabaseService(
            NppRawDemandDataRepository nppRawDemandDataRepository,
            NppAdjustedDemandDataRepository nppAdjustedDemandDataRepository,
            NppRawGenerationDataRepository nppRawGenerationDataRepository,
            NppAdjustedGenerationDataRepository nppAdjustedGenerationDataRepository,
            StateDemandDataRepository stateDemandDataRepository,
            CityStateDataRepository cityStateDataRepository,
            WeatherForecastHourlyRepository weatherForecastHourlyRepository,
            WeatherHistoricalRepository weatherHistoricalRepository) {
        this.nppRawDemandDataRepository = nppRawDemandDataRepository;
        this.nppAdjustedDemandDataRepository = nppAdjustedDemandDataRepository;
        this.nppRawGenerationDataRepository = nppRawGenerationDataRepository;
        this.nppAdjustedGenerationDataRepository = nppAdjustedGenerationDataRepository;
        this.stateDemandDataRepository = stateDemandDataRepository;
        this.cityStateDataRepository = cityStateDataRepository;
        this.weatherForecastHourlyRepository = weatherForecastHourlyRepository;
        this.weatherHistoricalRepository = weatherHistoricalRepository;
    }

    public Map<String, Object> getAllIndiaDemand(String startDate, String endDate) {
        String targetStart = startDate != null ? startDate : LocalDate.now().toString();
        String targetEnd = endDate != null ? endDate : targetStart;

        List<NppRawDemandData> rawRecords = nppRawDemandDataRepository.findAll().stream()
                .filter(r -> r.getDate() != null && r.getDate().compareTo(targetStart) >= 0 && r.getDate().compareTo(targetEnd) <= 0)
                .toList();

        Map<String, DemandBucket> bucketMap = new TreeMap<>();
        for (NppRawDemandData rec : rawRecords) {
            String timePart = rec.getTimeStr() != null && rec.getTimeStr().contains(" ") 
                ? rec.getTimeStr().split(" ")[1] 
                : (rec.getTimeStr() != null ? rec.getTimeStr() : "00:00");

            String[] parts = timePart.split(":");
            int hh = 0, mm = 0;
            try {
                hh = Integer.parseInt(parts[0]);
                mm = Integer.parseInt(parts[1]);
            } catch (Exception ignored) {}
            if (hh == 24) hh = 0;

            int blockStartMin = (mm / 15) * 15;
            int blockEndMin = blockStartMin + 14;
            String adjustedTimeStr = String.format("%s %02d:%02d", rec.getDate(), hh, blockEndMin);

            bucketMap.putIfAbsent(adjustedTimeStr, new DemandBucket());
            DemandBucket bucket = bucketMap.get(adjustedTimeStr);
            double demand = rec.getDemandMet() != null ? rec.getDemandMet() : 0.0;
            bucket.total += demand;
            bucket.count += 1;
            if (demand > bucket.max) bucket.max = demand;
            if (demand < bucket.min) bucket.min = demand;
        }

        List<Map<String, Object>> adjustedData = new ArrayList<>();
        for (Map.Entry<String, DemandBucket> entry : bucketMap.entrySet()) {
            DemandBucket b = entry.getValue();
            adjustedData.add(Map.of(
                "timeStr", entry.getKey(),
                "avgDemand", b.count > 0 ? (int) Math.round(b.total / (double) b.count) : 0,
                "maxDemand", b.max == -Double.MAX_VALUE ? 0 : (int) Math.round(b.max),
                "minDemand", b.min == Double.MAX_VALUE ? 0 : (int) Math.round(b.min)
            ));
        }

        List<Map<String, Object>> rawList = rawRecords.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("date", r.getDate());
            m.put("timeStr", String.format("%s %s", r.getDate(), r.getTimeStr() != null && r.getTimeStr().contains(" ") ? r.getTimeStr().split(" ")[1] : r.getTimeStr()));
            m.put("demandMet", r.getDemandMet());
            m.put("dataUpdatedAt", r.getDataUpdatedAt());
            m.put("fetchedAt", r.getFetchedAt());
            return m;
        }).toList();

        return Map.of("raw", rawList, "adjusted", adjustedData);
    }

    public Map<String, Object> getStateWiseDemand(String dateStr, String timeStr) {
        String finalDate = dateStr;
        String finalTime = timeStr;

        if (finalDate == null || finalTime == null) {
            List<StateDemandData> all = stateDemandDataRepository.findAll();
            if (!all.isEmpty()) {
                StateDemandData latest = all.get(all.size() - 1);
                finalDate = latest.getDate();
                finalTime = latest.getTimeStr();
            } else {
                finalDate = LocalDate.now().toString();
                finalTime = "00:00";
            }
        }

        final String matchDate = finalDate;
        final String matchTime = finalTime;
        List<StateDemandData> states = stateDemandDataRepository.findAll().stream()
                .filter(s -> matchDate.equals(s.getDate()) && matchTime.equals(s.getTimeStr()))
                .toList();

        Map<String, Map<String, Object>> regionMap = new HashMap<>();
        for (StateDemandData s : states) {
            String region = s.getRegion() != null ? s.getRegion() : "Other";
            regionMap.putIfAbsent(region, new HashMap<>(Map.of(
                "name", region,
                "price", s.getPrice() != null ? s.getPrice() : 0,
                "states", new ArrayList<Map<String, Object>>()
            )));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stateList = (List<Map<String, Object>>) regionMap.get(region).get("states");
            stateList.add(Map.of(
                "name", s.getStateName() != null ? s.getStateName() : "",
                "demand", s.getDemand() != null ? s.getDemand() : 0,
                "unit", s.getUnit() != null ? s.getUnit() : "MW"
            ));
        }

        return Map.of(
            "timestamp", String.format("%s %s", finalDate, finalTime),
            "regions", new ArrayList<>(regionMap.values())
        );
    }

    public Object getGenerationData(String startDate, String endDate) {
        String targetStart = startDate != null ? startDate : LocalDate.now().toString();
        String targetEnd = endDate != null ? endDate : targetStart;

        List<NppRawGenerationData> raw = nppRawGenerationDataRepository.findAll().stream()
                .filter(r -> r.getDate() != null && r.getDate().compareTo(targetStart) >= 0 && r.getDate().compareTo(targetEnd) <= 0)
                .toList();

        return Map.of(
            "raw", raw,
            "adjusted", List.of()
        );
    }

    public List<?> getWeatherData(String startDate, String endDate, String type, String latitude, String longitude) {
        if ("historical".equalsIgnoreCase(type)) {
            return weatherHistoricalRepository.findAll();
        }
        return weatherForecastHourlyRepository.findAll();
    }

    public List<CityStateData> getCityStateData() {
        return cityStateDataRepository.findAll();
    }

    @Transactional
    public CityStateData createCityStateData(Map<String, Object> data) {
        CityStateData entity = CityStateData.create();
        entity.setCityName((String) data.get("cityName"));
        entity.setStateName((String) data.get("stateName"));
        if (data.get("population") != null) {
            entity.setPopulation(((Number) data.get("population")).intValue());
        }
        if (data.get("latitude") != null) {
            entity.setLatitude(((Number) data.get("latitude")).doubleValue());
        }
        if (data.get("longitude") != null) {
            entity.setLongitude(((Number) data.get("longitude")).doubleValue());
        }
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return cityStateDataRepository.save(entity);
    }

    public void exportDataAsCsvStream(String dataset, String startDate, String endDate, PrintWriter writer) {
        if ("npp".equalsIgnoreCase(dataset)) {
            writer.println("date,timeStr,demandMet");
            for (NppRawDemandData r : nppRawDemandDataRepository.findAll()) {
                if (r.getDate() != null && r.getDate().compareTo(startDate) >= 0 && r.getDate().compareTo(endDate) <= 0) {
                    writer.printf("%s,%s,%s\n", r.getDate(), r.getTimeStr(), r.getDemandMet());
                }
            }
        } else if ("generation".equalsIgnoreCase(dataset)) {
            writer.println("date,timeStr,thermal,gas,nuclear,hydro,wind,solar");
            for (NppRawGenerationData r : nppRawGenerationDataRepository.findAll()) {
                if (r.getDate() != null && r.getDate().compareTo(startDate) >= 0 && r.getDate().compareTo(endDate) <= 0) {
                    writer.printf("%s,%s,%s,%s,%s,%s,%s,%s\n",
                        r.getDate(), r.getTimeStr(), r.getThermal(), r.getGas(), r.getNuclear(), r.getHydro(), r.getWind(), r.getSolar());
                }
            }
        } else if ("state".equalsIgnoreCase(dataset)) {
            writer.println("date,timeStr,stateName,region,demand,unit,price");
            for (StateDemandData r : stateDemandDataRepository.findAll()) {
                if (r.getDate() != null && r.getDate().compareTo(startDate) >= 0 && r.getDate().compareTo(endDate) <= 0) {
                    writer.printf("%s,%s,\"%s\",\"%s\",%s,%s,%s\n",
                        r.getDate(), r.getTimeStr(), r.getStateName(), r.getRegion(), r.getDemand(), r.getUnit(), r.getPrice());
                }
            }
        } else if ("weather_forecast".equalsIgnoreCase(dataset) || "weather".equalsIgnoreCase(dataset)) {
            writer.println("date,timeStr,maxTemp,minTemp,windSpeed,relativeHumidity,precipitationProb,precipitationSum,sunshineDuration,sunrise,sunset");
            for (WeatherForecastHourly r : weatherForecastHourlyRepository.findAll()) {
                if (r.getDate() != null && r.getDate().compareTo(startDate) >= 0 && r.getDate().compareTo(endDate) <= 0) {
                    writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                        r.getDate(), r.getTimeStr(), r.getMaxTemp(), r.getMinTemp(), r.getWindSpeed(), r.getRelativeHumidity(),
                        r.getPrecipitationProb(), r.getPrecipitationSum(), r.getSunshineDuration(), r.getSunrise(), r.getSunset());
                }
            }
        } else if ("weather_historical".equalsIgnoreCase(dataset)) {
            writer.println("date,maxTemp,minTemp,windSpeed,relativeHumidity,precipitationProb,precipitationSum,sunshineDuration,sunrise,sunset");
            for (WeatherHistorical r : weatherHistoricalRepository.findAll()) {
                if (r.getDate() != null && r.getDate().compareTo(startDate) >= 0 && r.getDate().compareTo(endDate) <= 0) {
                    writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                        r.getDate(), r.getMaxTemp(), r.getMinTemp(), r.getWindSpeed(), r.getRelativeHumidity(),
                        r.getPrecipitationProb(), r.getPrecipitationSum(), r.getSunshineDuration(), r.getSunrise(), r.getSunset());
                }
            }
        }
    }

    public void exportCityStateAsCsvStream(PrintWriter writer) {
        writer.println("cityName,stateName,population,latitude,longitude");
        for (CityStateData c : cityStateDataRepository.findAll()) {
            writer.printf("\"%s\",\"%s\",%d,%f,%f\n",
                c.getCityName(), c.getStateName(), c.getPopulation(), c.getLatitude(), c.getLongitude());
        }
    }

    private static class DemandBucket {
        double total = 0.0;
        int count = 0;
        double max = -Double.MAX_VALUE;
        double min = Double.MAX_VALUE;
    }
}

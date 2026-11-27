package com.sense.prolt_operations.service.calculator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.dto.calculator.ExchangeRateSlotProjection;
import com.sense.prolt_operations.service.calculator.exception.CalculatorInputException;
import com.sense.prolt_operations.service.calculator.model.ConsumptionType;
import com.sense.prolt_operations.service.calculator.model.DemandShiftInsights;
import com.sense.prolt_operations.service.calculator.model.InternalRateSlot;
import com.sense.prolt_operations.service.calculator.model.ParsedCategory;
import com.sense.prolt_operations.service.calculator.model.SimulationDateRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

public final class CalculatorUtils {

    private static final Logger log = LoggerFactory.getLogger(CalculatorUtils.class);

    private CalculatorUtils() {}

    public static double getDouble(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) return 0.0;
        Object val = map.get(key);
        if (val == null) return 0.0;
        if (val instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(val).trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static Map<String, Object> getMap(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) return Collections.emptyMap();
        Object val = map.get(key);
        if (val instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return copy;
        }
        return Collections.emptyMap();
    }

    public static int parseHour(String val) {
        if (val == null || val.isBlank() || "—".equals(val)) return 0;
        String trimmed = val.trim();
        if (trimmed.contains(":")) {
            try {
                return Integer.parseInt(trimmed.split(":")[0]);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String parseSupplyVoltageCategory(String voltageLevel) {
        if (voltageLevel == null || voltageLevel.isBlank()) {
            return "11 kV";
        }
        String v = voltageLevel.trim();
        if (v.contains(" - ")) {
            return v.split(" - ")[0].trim();
        }
        return v;
    }

    public static ParsedCategory parseCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            return new ParsedCategory("Industrial", null);
        }
        String cat = rawCategory.trim();
        if (cat.contains(" | ")) {
            String[] parts = cat.split(" \\| ", 2);
            return new ParsedCategory(parts[0].trim(), parts[1].trim());
        } else if ("HV-1 A".equalsIgnoreCase(cat)) {
            return new ParsedCategory("HV-1", "Commercial, Private Inst");
        } else if ("HV-1 B".equalsIgnoreCase(cat)) {
            return new ParsedCategory("HV-1", "Public Inst., Societies");
        } else if ("LMV-11 (Multistoried Buildings)".equalsIgnoreCase(cat)) {
            return new ParsedCategory("LMV-11", "Multistoried Buildings");
        } else if ("LMV-11 (Public Charging)".equalsIgnoreCase(cat)) {
            return new ParsedCategory("LMV-11", "Public Charging");
        }
        return new ParsedCategory(cat, null);
    }

    public static SimulationDateRange resolveSimulationDateRange(
            String yearMonth,
            Map<String, Object> monthConsumptions,
            String discom) {
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();
        if (yearMonth != null && yearMonth.contains("-")) {
            String[] parts = yearMonth.split("-");
            if (parts.length >= 2) {
                try {
                    year = Integer.parseInt(parts[0].trim());
                    month = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        int nextYear = (month == 12) ? year + 1 : year;
        int nextMonth = (month == 12) ? 1 : month + 1;
        int yyyymmMonth = nextYear * 100 + nextMonth;
        int calendarMonth = year * 100 + month;

        int prevYear = (month <= 2) ? year - 1 : year;
        int prevMonth = (month == 1) ? 11 : (month == 2 ? 12 : month - 2);
        int fppaQueryMonth = prevYear * 100 + prevMonth;

        boolean isNpcl = discom != null && "NPCL".equalsIgnoreCase(discom.trim());

        int startDay = isNpcl ? 19 : 1;
        Object startDayInput = monthConsumptions.get("Start Date");
        if (startDayInput != null) {
            String s = String.valueOf(startDayInput).trim();
            if (s.contains("-")) {
                String[] p = s.split("-");
                try { startDay = Integer.parseInt(p[p.length - 1]); } catch (Exception ignored) {}
            } else {
                try { startDay = Integer.parseInt(s); } catch (Exception ignored) {}
            }
        }

        int endDay = isNpcl ? 18 : 0;
        Object endDayInput = monthConsumptions.get("End Date");
        if (endDayInput != null) {
            String s = String.valueOf(endDayInput).trim();
            if (s.contains("-")) {
                String[] p = s.split("-");
                try { endDay = Integer.parseInt(p[p.length - 1]); } catch (Exception ignored) {}
            } else {
                try { endDay = Integer.parseInt(s); } catch (Exception ignored) {}
            }
        }

        int endYear = year;
        int endMonth = month;
        if (endDay == 0) {
            endDay = YearMonth.of(year, month).lengthOfMonth();
        } else if (endDay <= startDay) {
            endYear = nextYear;
            endMonth = nextMonth;
        }

        int maxDays = YearMonth.of(endYear, endMonth).lengthOfMonth();
        if (endDay > maxDays) {
            endDay = maxDays;
        }

        LocalDate startDate = LocalDate.of(year, month, Math.min(startDay, YearMonth.of(year, month).lengthOfMonth()));
        LocalDate endDate = LocalDate.of(endYear, endMonth, endDay);

        List<Integer> monthsInPlay = new ArrayList<>();
        YearMonth curYm = YearMonth.of(startDate.getYear(), startDate.getMonthValue());
        YearMonth limitYm = YearMonth.of(endDate.getYear(), endDate.getMonthValue());
        while (!curYm.isAfter(limitYm)) {
            monthsInPlay.add(curYm.getYear() * 100 + curYm.getMonthValue());
            curYm = curYm.plusMonths(1);
        }
        if (monthsInPlay.isEmpty()) {
            monthsInPlay.add(yyyymmMonth);
        }

        return new SimulationDateRange(startDate, endDate, year, month, yyyymmMonth, calendarMonth, fppaQueryMonth, monthsInPlay);
    }

    public static double getSlabConsumption(
            String groupKey,
            Map<String, Object> monthConsumptions,
            int slotsInGroup,
            int totalSlotsInMonth,
            boolean isKvahBilling,
            boolean consumptionValuesAreKwh,
            double globalPf,
            DemandShiftInsights shiftInsights) {

        if (shiftInsights != null) {
            Optional<Double> shiftedEnergy = shiftInsights.getNewEnergyForTod(groupKey);
            if (shiftedEnergy.isPresent()) {
                return shiftedEnergy.get();
            }
        }

        String matchedKey = null;
        for (String consumptionKey : monthConsumptions.keySet()) {
            String kLower = consumptionKey.toLowerCase();
            if (kLower.contains("peak demand") || kLower.contains("sanctioned") || consumptionKey.startsWith("_")) continue;
            if (consumptionKey.toUpperCase().contains(groupKey) || consumptionKey.equalsIgnoreCase(groupKey)) {
                matchedKey = consumptionKey;
                break;
            }
        }

        double slabConsumption = 0.0;
        if (matchedKey != null && monthConsumptions.get(matchedKey) != null && !String.valueOf(monthConsumptions.get(matchedKey)).isBlank()) {
            slabConsumption = getDouble(monthConsumptions, matchedKey);
            if (isKvahBilling && !consumptionValuesAreKwh) slabConsumption *= globalPf;
        } else {
            List<String> metadataKeys = List.of("power factor", "electricity duty", "peak demand (kva)", "start date", "end date", "arrears", "lpsc", "miscellaneous charges", "_rawkvah");
            String flatKey = null;
            for (String consumptionKey : monthConsumptions.keySet()) {
                if (ConsumptionType.isFallbackTotalKey(consumptionKey)) {
                    flatKey = consumptionKey;
                    break;
                }
            }

            double flatTotal = 0.0;
            if (flatKey != null && monthConsumptions.get(flatKey) != null && !String.valueOf(monthConsumptions.get(flatKey)).isBlank()) {
                flatTotal = getDouble(monthConsumptions, flatKey);
            } else {
                for (Map.Entry<String, Object> cEntry : monthConsumptions.entrySet()) {
                    String entryKey = cEntry.getKey();
                    Object entryValue = cEntry.getValue();
                    if (entryValue != null && !metadataKeys.contains(entryKey.toLowerCase()) && !entryKey.startsWith("_")) {
                        try {
                            flatTotal += Double.parseDouble(String.valueOf(entryValue).trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (flatTotal > 0 && totalSlotsInMonth > 0) {
                slabConsumption = flatTotal * ((double) slotsInGroup / (double) totalSlotsInMonth);
                if (isKvahBilling && !consumptionValuesAreKwh) slabConsumption *= globalPf;
            }
        }

        return slabConsumption;
    }

    public static double getSlabConsumption(
            String groupKey,
            Map<String, Object> monthConsumptions,
            int slotsInGroup,
            int totalSlotsInMonth,
            boolean isKvahBilling,
            boolean consumptionValuesAreKwh,
            double globalPf,
            Map<String, Object> shiftInsights) {
        return getSlabConsumption(
                groupKey,
                monthConsumptions,
                slotsInGroup,
                totalSlotsInMonth,
                isKvahBilling,
                consumptionValuesAreKwh,
                globalPf,
                shiftInsights != null ? DemandShiftInsights.fromMap(shiftInsights) : null
        );
    }

    public static List<InternalRateSlot> toInternalSlots(List<ExchangeRateSlotProjection> records, LocalDate startDate, LocalDate endDate) {
        if (records != null && !records.isEmpty()) {
            return records.stream().map(projection -> new InternalRateSlot(
                    projection.getDate() != null ? projection.getDate() : startDate,
                    projection.getTimeblock() != null ? projection.getTimeblock() : 1,
                    projection.getDamMcp() != null ? projection.getDamMcp().doubleValue() : null,
                    projection.getRtmMcp() != null ? projection.getRtmMcp().doubleValue() : null,
                    projection.getGdamMcp() != null ? projection.getGdamMcp().doubleValue() : null
            )).toList();
        }

        // Representative 96-slot fallback when table has no data for the future/simulated range
        log.warn("[SavingsCalculator] No exchange rate records found in database for range {} to {}. Generating representative 96-slot fallback prices (DAM=4.50, RTM=4.35, GDAM=4.20 Rs/kWh).", startDate, endDate);
        List<InternalRateSlot> fallback = new ArrayList<>();
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            for (int slotNumber = 1; slotNumber <= 96; slotNumber++) {
                fallback.add(new InternalRateSlot(currentDate, slotNumber, 4500.0, 4350.0, 4200.0));
            }
            currentDate = currentDate.plusDays(1);
        }
        return fallback;
    }

    public static Map<String, Map<String, Object>> parseTodConsumptions(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank() || objectMapper == null) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("[SavingsCalculator] Malformed TOD consumption data JSON: {}", e.getMessage());
            throw new CalculatorInputException("Malformed TOD consumption data JSON: " + e.getMessage(), e);
        }
    }

    public static void addAggregated(Map<String, Object> map, String key, double delta) {
        double current = getDouble(map, key);
        map.put(key, current + delta);
    }
}

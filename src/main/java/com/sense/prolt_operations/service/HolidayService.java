package com.sense.prolt_operations.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sense.prolt_operations.entity.HolidayCalendar;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.repository.HolidayCalendarRepository;

@Service
@Transactional
public class HolidayService {

    private static final Logger log = LoggerFactory.getLogger(HolidayService.class);

    private final HolidayCalendarRepository holidayCalendarRepository;

    public HolidayService(HolidayCalendarRepository holidayCalendarRepository) {
        this.holidayCalendarRepository = holidayCalendarRepository;
    }

    public List<HolidayCalendar> getHolidays() {
        return holidayCalendarRepository.findAll();
    }

    public HolidayCalendar createHoliday(Map<String, Object> data) {
        String month = (String) data.get("month");
        String holidayDate = (String) data.get("holidayDate");
        String holidayName = (String) data.get("holidayName");
        String holidayType = (String) data.getOrDefault("holidayType", "SH");
        String state = (String) data.getOrDefault("state", "National");

        HolidayCalendar holiday = HolidayCalendar.create();
        holiday.setMonth(month);
        holiday.setHolidayDate(holidayDate);
        holiday.setHolidayName(holidayName);
        holiday.setHolidayType(holidayType);
        holiday.setState(state);
        holiday.setIsActive(true);
        holiday.setCreatedAt(LocalDateTime.now());
        holiday.setUpdatedAt(LocalDateTime.now());
        return holidayCalendarRepository.save(holiday);
    }

    public HolidayCalendar updateHoliday(String id, Map<String, Object> data) {
        HolidayCalendar holiday = holidayCalendarRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Holiday calendar record not found"));

        if (data.containsKey("month")) holiday.setMonth((String) data.get("month"));
        if (data.containsKey("holidayDate")) holiday.setHolidayDate((String) data.get("holidayDate"));
        if (data.containsKey("holidayName")) holiday.setHolidayName((String) data.get("holidayName"));
        if (data.containsKey("holidayType")) holiday.setHolidayType((String) data.get("holidayType"));
        if (data.containsKey("state")) holiday.setState((String) data.get("state"));
        if (data.containsKey("isActive")) holiday.setIsActive((Boolean) data.get("isActive"));
        holiday.setUpdatedAt(LocalDateTime.now());
        return holidayCalendarRepository.save(holiday);
    }

    public HolidayCalendar toggleHoliday(String id) {
        HolidayCalendar holiday = holidayCalendarRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Holiday calendar record not found"));
        holiday.setIsActive(holiday.getIsActive() == null || !holiday.getIsActive());
        holiday.setUpdatedAt(LocalDateTime.now());
        return holidayCalendarRepository.save(holiday);
    }

    public Map<String, Object> uploadHolidays(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file uploaded");
        }

        List<HolidayCalendar> list = new ArrayList<>();
        String filename = (file.getOriginalFilename() != null ? file.getOriginalFilename() : "").toLowerCase();

        if (filename.endsWith(".csv")) {
            list = parseHolidaysFromCsv(file);
        } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            list = parseHolidaysFromExcel(file);
        } else {
            throw new BadRequestException("Invalid file type. Only Excel (.xlsx, .xls) and CSV (.csv) are supported.");
        }

        if (list.isEmpty()) {
            throw new BadRequestException("No valid holiday records could be parsed from the file.");
        }

        holidayCalendarRepository.deleteAll();
        holidayCalendarRepository.saveAll(list);

        return Map.of("success", true, "count", list.size());
    }

    private List<HolidayCalendar> parseHolidaysFromCsv(MultipartFile file) {
        List<HolidayCalendar> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build())) {

            for (CSVRecord rec : csvParser) {
                String month = getCsvVal(rec, "month");
                String date = getCsvVal(rec, "holiday_date", "holiday date", "date");
                String name = getCsvVal(rec, "holiday_name", "holiday name", "name");
                String type = getCsvVal(rec, "holiday_type", "holiday type", "type");
                String state = getCsvVal(rec, "state");

                if (name.isEmpty() || date.isEmpty()) continue;

                HolidayCalendar h = HolidayCalendar.create();
                h.setMonth(month);
                h.setHolidayDate(date);
                h.setHolidayName(name);
                h.setHolidayType(!type.isEmpty() ? type : "SH");
                h.setState(!state.isEmpty() ? state : "National");
                h.setIsActive(true);
                h.setCreatedAt(LocalDateTime.now());
                h.setUpdatedAt(LocalDateTime.now());
                list.add(h);
            }
        } catch (Exception e) {
            log.error("Error parsing holiday CSV: {}", e.getMessage());
            throw new BadRequestException("Failed to upload holiday calendar: " + e.getMessage());
        }
        return list;
    }

    private List<HolidayCalendar> parseHolidaysFromExcel(MultipartFile file) {
        List<HolidayCalendar> list = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            if (!rowIterator.hasNext()) {
                throw new BadRequestException("The uploaded file is empty.");
            }

            Row headerRow = rowIterator.next();
            Map<String, Integer> colIndex = new HashMap<>();
            for (Cell cell : headerRow) {
                String val = cell.getStringCellValue();
                if (val != null) {
                    colIndex.put(val.trim().toLowerCase(), cell.getColumnIndex());
                }
            }

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                String month = getExcelVal(row, colIndex, "month");
                String date = getExcelVal(row, colIndex, "holiday_date", "holiday date", "date");
                String name = getExcelVal(row, colIndex, "holiday_name", "holiday name", "name");
                String type = getExcelVal(row, colIndex, "holiday_type", "holiday type", "type");
                String state = getExcelVal(row, colIndex, "state");

                if (name.isEmpty() || date.isEmpty()) continue;

                HolidayCalendar h = HolidayCalendar.create();
                h.setMonth(month);
                h.setHolidayDate(date);
                h.setHolidayName(name);
                h.setHolidayType(!type.isEmpty() ? type : "SH");
                h.setState(!state.isEmpty() ? state : "National");
                h.setIsActive(true);
                h.setCreatedAt(LocalDateTime.now());
                h.setUpdatedAt(LocalDateTime.now());
                list.add(h);
            }
        } catch (Exception e) {
            log.error("Error parsing holiday Excel: {}", e.getMessage());
            throw new BadRequestException("Failed to upload holiday calendar: " + e.getMessage());
        }
        return list;
    }

    private String getCsvVal(CSVRecord rec, String... keys) {
        for (String k : keys) {
            if (rec.isMapped(k)) {
                String val = rec.get(k);
                if (val != null && !val.trim().isEmpty()) return val.trim();
            }
        }
        return "";
    }

    private String getExcelVal(Row row, Map<String, Integer> colIndex, String... keys) {
        for (String k : keys) {
            Integer idx = colIndex.get(k);
            if (idx != null) {
                Cell cell = row.getCell(idx);
                if (cell != null) {
                    if (cell.getCellType() == CellType.STRING) {
                        return cell.getStringCellValue().trim();
                    } else if (cell.getCellType() == CellType.NUMERIC) {
                        if (DateUtil.isCellDateFormatted(cell)) {
                            return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                        }
                        return String.valueOf((long) cell.getNumericCellValue());
                    }
                }
            }
        }
        return "";
    }
}

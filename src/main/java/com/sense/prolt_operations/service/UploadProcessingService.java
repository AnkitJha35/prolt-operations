package com.sense.prolt_operations.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.sense.prolt_operations.dto.dataset.PersistDatasetDTO;
import com.sense.prolt_operations.entity.Dataset;
import com.sense.prolt_operations.exception.BadRequestException;

@Service
public class UploadProcessingService {

    private static final Logger log = LoggerFactory.getLogger(UploadProcessingService.class);

    private final PersistenceService persistenceService;

    public UploadProcessingService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public Map<String, Object> processUpload(String marketStr, LocalDate deliveryDate, MultipartFile file, String action) {
        if (marketStr == null || marketStr.trim().isEmpty()) {
            throw new BadRequestException("Market type is required.");
        }
        String marketNorm = marketStr.trim().toUpperCase();
        if (!List.of("DAM", "GDAM", "RTM", "REC").contains(marketNorm)) {
            throw new BadRequestException("Invalid market type. Allowed: DAM, GDAM, RTM, REC");
        }
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File upload failed");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.csv";
        log.info("Processing upload for market: {}, deliveryDate: {}, file: {}", marketNorm, deliveryDate, fileName);

        List<Map<String, Object>> records = parseFile(file, marketNorm);

        PersistDatasetDTO persistDTO = new PersistDatasetDTO(
            marketNorm,
            deliveryDate,
            fileName,
            records,
            action,
            false
        );

        Dataset dataset = persistenceService.persistDataset(persistDTO);

        Map<String, Object> result = new HashMap<>();
        result.put("datasetId", dataset.getId());
        result.put("rowCount", records.size());
        result.put("parsed", true);
        return result;
    }

    private List<Map<String, Object>> parseFile(MultipartFile file, String market) {
        String lowerName = (file.getOriginalFilename() != null ? file.getOriginalFilename() : "").toLowerCase();
        if (lowerName.endsWith(".csv")) {
            return parseCsv(file);
        } else if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
            return parseExcel(file);
        } else {
            return parseCsv(file);
        }
    }

    private List<Map<String, Object>> parseCsv(MultipartFile file) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build())) {

            int interval = 1;
            for (CSVRecord rec : csvParser) {
                Map<String, Object> row = new HashMap<>();
                row.put("intervalNumber", parseIntervalNumber(rec, interval));
                row.put("purchaseBid", parseDecimal(rec, "purchase bid (mw)", "purchase bid", "purchasebid", "pbid"));
                row.put("sellBid", parseDecimal(rec, "sell bid (mw)", "sell bid", "sellbid", "sbid"));
                row.put("mcv", parseDecimal(rec, "mcv (mw)", "mcv", "cleared volume", "market clearing volume"));
                row.put("fsv", parseDecimal(rec, "final scheduled volume (mw)", "fsv", "final scheduled volume"));
                row.put("mcp", parseDecimal(rec, "mcp (rs/mwh)", "mcp", "market clearing price"));
                list.add(row);
                interval++;
            }
        } catch (Exception e) {
            log.error("Failed to parse CSV upload: {}", e.getMessage());
            throw new BadRequestException("Data transformation failed: " + e.getMessage());
        }
        return list;
    }

    private List<Map<String, Object>> parseExcel(MultipartFile file) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            if (!rowIterator.hasNext()) {
                throw new BadRequestException("Uploaded Excel sheet is empty");
            }

            Row headerRow = rowIterator.next();
            Map<String, Integer> colIndex = new HashMap<>();
            for (Cell cell : headerRow) {
                String val = cell.getStringCellValue();
                if (val != null) {
                    colIndex.put(val.trim().toLowerCase(), cell.getColumnIndex());
                }
            }

            int interval = 1;
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Map<String, Object> rowMap = new HashMap<>();
                rowMap.put("intervalNumber", interval);
                rowMap.put("purchaseBid", getCellDecimal(row, colIndex, "purchase bid (mw)", "purchase bid", "pbid"));
                rowMap.put("sellBid", getCellDecimal(row, colIndex, "sell bid (mw)", "sell bid", "sbid"));
                rowMap.put("mcv", getCellDecimal(row, colIndex, "mcv (mw)", "mcv", "cleared volume"));
                rowMap.put("fsv", getCellDecimal(row, colIndex, "final scheduled volume (mw)", "fsv"));
                rowMap.put("mcp", getCellDecimal(row, colIndex, "mcp (rs/mwh)", "mcp"));
                list.add(rowMap);
                interval++;
            }
        } catch (Exception e) {
            log.error("Failed to parse Excel upload: {}", e.getMessage());
            throw new BadRequestException("Data transformation failed: " + e.getMessage());
        }
        return list;
    }

    private int parseIntervalNumber(CSVRecord rec, int fallback) {
        for (String col : List.of("interval", "intervalnumber", "timeblock", "block", "no")) {
            if (rec.isMapped(col)) {
                try {
                    return Integer.parseInt(rec.get(col).trim());
                } catch (Exception ignored) {}
            }
        }
        return fallback;
    }

    private BigDecimal parseDecimal(CSVRecord rec, String... cols) {
        for (String col : cols) {
            if (rec.isMapped(col)) {
                String val = rec.get(col);
                if (val != null && !val.trim().isEmpty()) {
                    try {
                        return new BigDecimal(val.trim().replace(",", ""));
                    } catch (Exception ignored) {}
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal getCellDecimal(Row row, Map<String, Integer> colIndex, String... colNames) {
        for (String name : colNames) {
            Integer idx = colIndex.get(name);
            if (idx != null) {
                Cell cell = row.getCell(idx);
                if (cell != null) {
                    if (cell.getCellType() == CellType.NUMERIC) {
                        return BigDecimal.valueOf(cell.getNumericCellValue());
                    } else if (cell.getCellType() == CellType.STRING) {
                        try {
                            return new BigDecimal(cell.getStringCellValue().trim().replace(",", ""));
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return BigDecimal.ZERO;
    }
}

package com.sense.prolt_operations.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class ExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportService.class);

    public byte[] exportSavingsAnalysis(String clientName, Map<String, Object> calculationResult) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Savings Analysis");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Client Info block
            int rowIdx = 0;
            Row r0 = sheet.createRow(rowIdx++);
            r0.createCell(0).setCellValue("Client / Industry Name:");
            r0.createCell(1).setCellValue(clientName != null ? clientName : "Unknown");
            r0.getCell(0).setCellStyle(headerStyle);

            Row r1 = sheet.createRow(rowIdx++);
            r1.createCell(0).setCellValue("Sanctioned Load (kW):");
            Object load = calculationResult != null ? calculationResult.get("sanctionedLoad") : "-";
            r1.createCell(1).setCellValue(String.valueOf(load));
            r1.getCell(0).setCellStyle(headerStyle);

            rowIdx++; // Blank row

            // Summary Table
            Row headerRow = sheet.createRow(rowIdx++);
            String[] headers = {"Metric", "Value"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            if (calculationResult != null) {
                addRow(sheet, rowIdx++, "Total Baseline Cost (Rs)", String.valueOf(calculationResult.get("totalBaselineCost")));
                addRow(sheet, rowIdx++, "Total Optimized Cost (Rs)", String.valueOf(calculationResult.get("totalOptimizedCost")));
                addRow(sheet, rowIdx++, "Total Savings (Rs)", String.valueOf(calculationResult.get("totalSavings")));
                addRow(sheet, rowIdx++, "Total Energy (kWh)", String.valueOf(calculationResult.get("totalEnergyKwh")));
                addRow(sheet, rowIdx++, "Total Market Energy (kWh)", String.valueOf(calculationResult.get("totalMarketEnergyKwh")));
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("[ExcelExportService] Failed to export savings analysis:", e);
            throw new RuntimeException("Excel export failed: " + e.getMessage(), e);
        }
    }

    public byte[] exportDemandShift(String clientName, Map<String, Object> insightsResult) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Demand Shift Insights");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            int rowIdx = 0;
            Row r0 = sheet.createRow(rowIdx++);
            r0.createCell(0).setCellValue("Client Name:");
            r0.createCell(1).setCellValue(clientName != null ? clientName : "Unknown");
            r0.getCell(0).setCellStyle(headerStyle);

            rowIdx++;

            Row headerRow = sheet.createRow(rowIdx++);
            String[] headers = {"Parameter", "Insight Value"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            if (insightsResult != null) {
                addRow(sheet, rowIdx++, "Shiftable Capacity (MW)", String.valueOf(insightsResult.getOrDefault("shiftableCapacityMw", 0.5)));
                addRow(sheet, rowIdx++, "Potential Additional Savings (Rs)", String.valueOf(insightsResult.getOrDefault("potentialSavingsRs", 250000)));
                addRow(sheet, rowIdx++, "Recommended Shift Window", String.valueOf(insightsResult.getOrDefault("recommendedWindow", "11:00 - 15:00 to 00:00 - 05:00")));
            }

            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("[ExcelExportService] Failed to export demand shift:", e);
            throw new RuntimeException("Demand shift export failed: " + e.getMessage(), e);
        }
    }

    private void addRow(Sheet sheet, int rowIdx, String col0, String col1) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(col0);
        row.createCell(1).setCellValue(col1 != null && !col1.equals("null") ? col1 : "-");
    }
}

package com.sense.prolt_operations.service;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

@Service
public class ProposalService {

    private static final Logger log = LoggerFactory.getLogger(ProposalService.class);
    private final ChartGeneratorService chartGeneratorService;

    public ProposalService(ChartGeneratorService chartGeneratorService) {
        this.chartGeneratorService = chartGeneratorService;
    }

    public static String formatRupee(Object val) {
        if (val == null || String.valueOf(val).isBlank()) return "";
        double num;
        if (val instanceof Number n) {
            num = n.doubleValue();
        } else {
            String str = String.valueOf(val).trim().replace(",", "").replace("₹", "");
            try {
                num = Double.parseDouble(str);
            } catch (Exception e) {
                return String.valueOf(val);
            }
        }
        DecimalFormat df = new DecimalFormat("₹##,##,##0.00");
        return df.format(num);
    }

    public byte[] generateProposal(Map<String, Object> clientData, String type) {
        if (type == null || type.isBlank()) {
            type = "default";
        }

        String templateFile;
        if ("commercial".equalsIgnoreCase(type)) {
            templateFile = "templates/COMMERCIAL PROPOSAL_33KV.docx";
        } else if ("technical".equalsIgnoreCase(type)) {
            templateFile = "templates/TECHNICAL PROPOSAL_11KV.docx";
        } else {
            templateFile = "templates/commercial_proposal_template.docx";
        }

        // Apply UP state & voltage dynamic defaults
        applyDynamicDefaults(clientData);

        try (InputStream in = getTemplateInputStream(templateFile);
             XWPFDocument document = in != null ? new XWPFDocument(in) : createBlankProposal(clientData)) {

            replacePlaceholders(document, clientData);
            processTables(document, clientData, type);

            // If technical proposal, embed generated charts
            if ("technical".equalsIgnoreCase(type)) {
                embedCharts(document, clientData);
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.write(out);
                return out.toByteArray();
            }
        } catch (Exception e) {
            log.error("[ProposalService] Error generating proposal for type {}: {}", type, e.getMessage(), e);
            throw new RuntimeException("Proposal generation failed: " + e.getMessage(), e);
        }
    }

    private void applyDynamicDefaults(Map<String, Object> clientData) {
        if (clientData == null) return;
        String state = String.valueOf(clientData.getOrDefault("state", "")).trim().toUpperCase();
        String discom = String.valueOf(clientData.getOrDefault("discom", clientData.getOrDefault("discom_name", ""))).trim().toUpperCase();
        String voltage = String.valueOf(clientData.getOrDefault("voltage_level", clientData.getOrDefault("connectivity", ""))).trim().toUpperCase();

        double defaultSupply = 450000;
        double defaultService = 350000;
        double defaultLiaison = 300000;
        double defaultSmart = 125000;
        String indoorCount = "1 Nos.";

        if ("UP".equals(state) || state.contains("UTTAR")) {
            defaultLiaison = 395000;
            defaultSmart = 170000;
            if (voltage.contains("33")) {
                if (discom.contains("NPCL")) {
                    defaultSupply = 845000;
                    defaultService = 358000;
                    indoorCount = "2 Nos.";
                } else {
                    defaultSupply = 745000;
                    defaultService = 358000;
                }
            } else {
                defaultSupply = 565000;
                defaultService = 338000;
            }
        }

        clientData.putIfAbsent("abt_supply_cost", defaultSupply);
        clientData.putIfAbsent("abt_service_cost", defaultService);
        clientData.putIfAbsent("utility_liaisoning_cost", defaultLiaison);
        clientData.putIfAbsent("smart_metering_infra", defaultSmart);
        clientData.putIfAbsent("indoor_ctpt_count", indoorCount);
    }

    private InputStream getTemplateInputStream(String resourcePath) {
        try {
            ClassPathResource res = new ClassPathResource(resourcePath);
            if (res.exists()) {
                return res.getInputStream();
            }
        } catch (Exception e) {
            log.warn("Template {} not found in classpath: {}", resourcePath, e.getMessage());
        }
        return null;
    }

    private XWPFDocument createBlankProposal(Map<String, Object> clientData) {
        XWPFDocument doc = new XWPFDocument();
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setFontFamily("Aptos");
        r.setFontSize(22);
        r.setBold(true);
        r.setText("ENERGY PROCUREMENT PROPOSAL");

        String client = String.valueOf(clientData.getOrDefault("client_name", clientData.getOrDefault("clientName", "CLIENT"))).toUpperCase();
        XWPFParagraph p2 = doc.createParagraph();
        p2.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r2 = p2.createRun();
        r2.setFontFamily("Aptos");
        r2.setFontSize(16);
        r2.setText("PREPARED FOR: " + client);
        return doc;
    }

    private void replacePlaceholders(XWPFDocument doc, Map<String, Object> clientData) {
        String clientName = String.valueOf(clientData.getOrDefault("client_name", clientData.getOrDefault("clientName", "CLIENT"))).toUpperCase();
        for (XWPFParagraph p : doc.getParagraphs()) {
            for (XWPFRun r : p.getRuns()) {
                String text = r.getText(0);
                if (text != null && text.contains("XXXXXXXXXXXXX")) {
                    text = text.replace("XXXXXXXXXXXXX", clientName);
                    r.setText(text, 0);
                }
            }
        }
    }

    private void processTables(XWPFDocument doc, Map<String, Object> clientData, String type) {
        List<XWPFTable> tables = doc.getTables();
        if (tables.isEmpty()) return;

        // Process Table 1 (Key Client Profile)
        if (tables.size() > 0) {
            XWPFTable t0 = tables.get(0);
            if (t0.getRows().size() > 1) {
                XWPFTableRow r1 = t0.getRow(1);
                setCell(r1, 0, String.valueOf(clientData.getOrDefault("sanctioned_load", "1 MW")), true);
                setCell(r1, 1, String.valueOf(clientData.getOrDefault("connectivity", "11 kV")), true);
                setCell(r1, 2, String.valueOf(clientData.getOrDefault("discom_name", "DISCOM")), true);
                setCell(r1, 3, String.valueOf(clientData.getOrDefault("feeder_type", "Dedicated Feeder")), true);
            }
        }

        // Process Table 3 (BOQ / Cost Table)
        if (tables.size() > 3) {
            XWPFTable t3 = tables.get(3);
            double supply = getNumber(clientData.get("abt_supply_cost"), 450000);
            double service = getNumber(clientData.get("abt_service_cost"), 350000);
            double liaison = getNumber(clientData.get("utility_liaisoning_cost"), 300000);
            double totalAbt = supply + service + liaison;

            if (t3.getRows().size() > 1) setLastCell(t3.getRow(1), formatRupee(totalAbt), true);
            if (t3.getRows().size() > 2) setLastCell(t3.getRow(2), formatRupee(supply), false);
            if (t3.getRows().size() > 3) setLastCell(t3.getRow(3), formatRupee(service), false);
            if (t3.getRows().size() > 4) setLastCell(t3.getRow(4), formatRupee(liaison), false);
        }
    }

    private void embedCharts(XWPFDocument doc, Map<String, Object> clientData) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> monthlyData = (List<Map<String, Object>>) clientData.get("monthlyData");
            byte[] chartBytes = chartGeneratorService.generateSavingsChart(monthlyData, "₹3,10,000", "Thirty One Lakhs");

            if (chartBytes.length > 0) {
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun r = p.createRun();
                r.addPicture(
                    new ByteArrayInputStream(chartBytes),
                    Document.PICTURE_TYPE_PNG,
                    "savings_chart.png",
                    Units.toEMU(450),
                    Units.toEMU(270)
                );
            }
        } catch (Exception e) {
            log.warn("Chart embedding skipped: {}", e.getMessage());
        }
    }

    private void setCell(XWPFTableRow row, int colIdx, String text, boolean bold) {
        if (row.getTableCells().size() > colIdx) {
            XWPFTableCell cell = row.getCell(colIdx);
            cell.removeParagraph(0);
            XWPFParagraph p = cell.addParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r = p.createRun();
            r.setFontFamily("Aptos");
            r.setFontSize(10);
            r.setBold(bold);
            r.setText(text);
        }
    }

    private void setLastCell(XWPFTableRow row, String text, boolean bold) {
        int lastIdx = row.getTableCells().size() - 1;
        if (lastIdx >= 0) {
            setCell(row, lastIdx, text, bold);
        }
    }

    private double getNumber(Object val, double fallback) {
        if (val instanceof Number n) return n.doubleValue();
        if (val != null) {
            try {
                return Double.parseDouble(String.valueOf(val).replace(",", ""));
            } catch (Exception ignored) {}
        }
        return fallback;
    }
}

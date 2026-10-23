package com.sense.prolt_operations.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class TradeReportParser {

    private static final Logger log = LoggerFactory.getLogger(TradeReportParser.class);

    public static Double toFloat(String val) {
        if (val == null || val.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(val.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static String cleanText(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("(?U)\\s+", " ").trim();
    }

    public static String normalizeKey(String label) {
        if (label == null) {
            return "";
        }
        String s = label.toLowerCase();
        s = s.replace("&", "and");
        s = s.replaceAll("(?U)[^a-z0-9]+", "_");
        s = s.replaceAll("(?U)_+", "_");
        s = s.replaceAll("(?U)^_+|_+$", "");
        return s;
    }

    public Map<String, Object> parsePdfBytes(byte[] bytes, String filename) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String fullText = stripper.getText(document);
            return extractReportData(fullText, filename);
        }
    }

    public Map<String, Object> parsePdfFile(File file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String fullText = stripper.getText(document);
            return extractReportData(fullText, file.getName());
        }
    }

    public Map<String, Object> parseFiles(List<MultipartFile> files) {
        Map<String, Object> results = new LinkedHashMap<>();
        List<Map<String, Object>> filesStatus = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        if (files != null) {
            for (MultipartFile file : files) {
                String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "report.pdf";
                try {
                    Map<String, Object> parsed = parsePdfBytes(file.getBytes(), filename);
                    results.put(filename, parsed);
                    Map<String, Object> statusMap = new LinkedHashMap<>();
                    statusMap.put("file_name", filename);
                    statusMap.put("status", "SUCCESS");
                    statusMap.put("message", "OK");
                    statusMap.put("data", parsed);
                    filesStatus.add(statusMap);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Failed to parse trade report {}: {}", filename, e.getMessage());
                    Map<String, Object> statusMap = new LinkedHashMap<>();
                    statusMap.put("file_name", filename);
                    statusMap.put("status", "FAILURE");
                    statusMap.put("message", e.getMessage());
                    statusMap.put("data", null);
                    filesStatus.add(statusMap);
                    failedCount++;
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_files", files != null ? files.size() : 0);
        summary.put("successful_files", successCount);
        summary.put("failed_files", failedCount);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("success", true);
        output.put("data", filesStatus); // List for compatibility with $.data hasSize
        output.put("reports", results);  // Dictionary of filename -> report
        output.put("files_status", filesStatus);
        output.put("summary", summary);
        return output;
    }

    public Map<String, Object> extractReportData(String fullText, String filename) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("trading_date", null);
        data.put("delivery_date", null);
        data.put("entity_id", null);
        data.put("entity_name", null);
        data.put("portfolio_code", null);
        data.put("portfolio_name", null);
        data.put("charges", new LinkedHashMap<String, Double>());
        data.put("funds_payin_payout", null);
        data.put("total_amount", null);
        data.put("total_trade_mwh", null);
        data.put("remarks", null);
        data.put("trades", new ArrayList<Map<String, Object>>());
        data.put("oa_market_type", null);

        if (filename != null) {
            if (filename.startsWith("RTM_IEX")) {
                data.put("oa_market_type", "RTM");
            } else if (filename.startsWith("GDAM_IEX")) {
                data.put("oa_market_type", "GDAM");
            } else if (filename.startsWith("IEX")) {
                data.put("oa_market_type", "DAM");
            }
        }

        if (fullText != null && !fullText.isBlank()) {
            Matcher mTradeDate = Pattern.compile("(?U)(?:Trading Date|TRADE DATE)\\s*:\\s*(\\d{1,2}-\\w{3}-\\d{2,4}|[0-9]{2}-[0-9]{2}-[0-9]{4}|[0-9]{2}/[0-9]{2}/[0-9]{4})").matcher(fullText);
            if (mTradeDate.find()) {
                data.put("trading_date", mTradeDate.group(1));
            }

            Matcher mDeliveryDate = Pattern.compile("(?U)(?:Delivery Date|DELIVERY DATE)\\s*:\\s*(\\d{1,2}-\\w{3}-\\d{2,4}|[0-9]{2}-[0-9]{2}-[0-9]{4}|[0-9]{2}/[0-9]{2}/[0-9]{4})").matcher(fullText);
            if (mDeliveryDate.find()) {
                data.put("delivery_date", mDeliveryDate.group(1));
            }

            if (data.get("oa_market_type") == null) {
                Object td = data.get("trading_date");
                Object dd = data.get("delivery_date");
                if (td == null && dd != null) {
                    data.put("trading_date", dd);
                    data.put("oa_market_type", "RTM");
                } else if (td != null && dd != null && !td.equals(dd)) {
                    data.put("oa_market_type", "DAM");
                }
            }

            Matcher mEntityId = Pattern.compile("(?U)(?:Entity ID|CLIENT CODE)\\s*:\\s*([A-Za-z0-9_-]+)").matcher(fullText);
            if (mEntityId.find()) {
                data.put("entity_id", mEntityId.group(1));
            }

            Matcher mEntityName = Pattern.compile("(?U)(?:Entity Name|CLIENT NAME)\\s*:\\s*(.+?)(?:Portfolio Code|SEGMENT|PRODUCT|Trading Date|TRADE DATE|$)", Pattern.DOTALL).matcher(fullText);
            if (mEntityName.find()) {
                data.put("entity_name", cleanText(mEntityName.group(1)));
            }

            Matcher mPortfolioCode = Pattern.compile("(?U)Portfolio Code\\s*:\\s*([A-Z0-9]+)").matcher(fullText);
            if (mPortfolioCode.find()) {
                data.put("portfolio_code", mPortfolioCode.group(1));
            }

            Matcher mPortfolioName = Pattern.compile("(?U)Portfolio Name\\s*:\\s*(.+?)(?:Funds Payin|Charges|$)", Pattern.DOTALL).matcher(fullText);
            if (mPortfolioName.find()) {
                data.put("portfolio_name", cleanText(mPortfolioName.group(1)));
            }

            Matcher mSegment = Pattern.compile("(?U)SEGMENT\\s*:\\s*([A-Za-z0-9 _-]+)").matcher(fullText);
            if (mSegment.find()) {
                data.put("segment", cleanText(mSegment.group(1)));
            }

            Matcher mProduct = Pattern.compile("(?U)PRODUCT\\s*:\\s*([A-Za-z0-9 _-]+)").matcher(fullText);
            if (mProduct.find()) {
                data.put("product", cleanText(mProduct.group(1)));
            }

            Matcher mFunds = Pattern.compile("(?U)Funds Payin\\(-\\)\\s*/\\s*Payout\\(\\+\\)\\s+(-?[\\d,]+\\.\\d+)").matcher(fullText);
            if (mFunds.find()) {
                data.put("funds_payin_payout", Math.abs(toFloat(mFunds.group(1))));
            }

            Map<String, Double> charges = new LinkedHashMap<>();
            Matcher mCharges = Pattern.compile("(?U)(?:>\\s*|^)([A-Za-z &\\-/]+?)\\s+(-?[\\d,]+\\.\\d+)", Pattern.MULTILINE).matcher(fullText);
            while (mCharges.find()) {
                String label = cleanText(mCharges.group(1));
                if (label != null) {
                    String lower = label.toLowerCase();
                    if (lower.equals("total") || lower.equals("funds payin(-) / payout(+)")) {
                        continue;
                    }
                    String key = normalizeKey(label);
                    charges.put(key, Math.abs(toFloat(mCharges.group(2))));
                }
            }

            if (!charges.containsKey("fees")) {
                Matcher mFees = Pattern.compile("(?U)Fees\\s+(-?[\\d,]+\\.\\d+)").matcher(fullText);
                if (mFees.find()) {
                    charges.put("fees", Math.abs(toFloat(mFees.group(1))));
                }
            }
            data.put("charges", charges);

            Matcher mTotal = Pattern.compile("(?U)Total\\s+(-?[\\d,]+\\.\\d+)").matcher(fullText);
            if (mTotal.find()) {
                data.put("total_amount", Math.abs(toFloat(mTotal.group(1))));
            }

            Matcher mRemarks = Pattern.compile("(?U)Remarks\\s*:(.*?)(\\*{2} This is a computer generated report|\\Z)", Pattern.DOTALL).matcher(fullText);
            if (mRemarks.find()) {
                data.put("remarks", cleanText(mRemarks.group(1)));
            }

            Matcher mTotalTrade = Pattern.compile("(?U)Total Trade\\s*\\(Buy \\+ Sell\\)\\s*MWh\\s+([\\d,.]+)").matcher(fullText);
            if (!mTotalTrade.find()) {
                mTotalTrade = Pattern.compile("(?U)^Total(?: Trade|).*?MWh\\s+([\\d,.]+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(fullText);
            }
            if (mTotalTrade.find()) {
                data.put("total_trade_mwh", toFloat(mTotalTrade.group(1)));
            }

            List<Map<String, Object>> trades = new ArrayList<>();
            Matcher mTrades = Pattern.compile("(?U)(\\d{2}:\\d{2}\\s*-\\s*\\d{2}:\\d{2})\\s+(-?[\\d,.]+)\\s+([\\d,]+(?:\\.\\d+)?)\\s+(-?[\\d,]+(?:\\.\\d+)?)").matcher(fullText);
            double sumQty = 0.0;
            while (mTrades.find()) {
                String period = mTrades.group(1);
                double qty = toFloat(mTrades.group(2));
                double rate = Math.abs(toFloat(mTrades.group(3)));
                double amount = Math.abs(toFloat(mTrades.group(4)));

                Map<String, Object> trade = new LinkedHashMap<>();
                trade.put("period", period);
                trade.put("qty_mw", qty);
                trade.put("rate_mwh", rate);
                trade.put("amount", amount);
                trades.add(trade);
                if (qty > 0) {
                    sumQty += qty;
                }
            }
            data.put("trades", trades);

            if (data.get("total_trade_mwh") == null && sumQty > 0) {
                data.put("total_trade_mwh", sumQty * 0.25);
            }
        }

        if (filename != null) {
            data.put("fileKey", System.currentTimeMillis() + "-" + filename);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tradesList = (List<Map<String, Object>>) data.get("trades");
        if (data.get("trading_date") == null && data.get("delivery_date") == null && (tradesList == null || tradesList.isEmpty())) {
            throw new IllegalArgumentException("Could not extract any meaningful data. Is this a scanned PDF or a different format?");
        }

        return data;
    }
}

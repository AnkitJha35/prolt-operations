package com.sense.prolt_operations.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

@Service
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

    @Value("${app.puppeteer-executable-path:}")
    private String executablePath;

    public List<Map<String, Object>> scrapeDam() {
        log.info("Starting Puppeteer for DAM Market Data Scrape...");
        List<List<String>> rows = scrapeTableRows(
                "https://www.iexindia.com/market-data/day-ahead-market/market-snapshot", 15_000);
        List<Map<String, Object>> records = parseDamRows(rows);
        log.info("Successfully scraped {} DAM records", records.size());
        return records;
    }

    public List<Map<String, Object>> scrapeGdam() {
        log.info("Starting Puppeteer for GDAM Market Data Scrape...");
        List<List<String>> rows = scrapeTableRows(
                "https://www.iexindia.com/market-data/green-day-ahead-market/market-snapshot", 15_000);
        List<Map<String, Object>> records = parseGdamRows(rows);
        log.info("Successfully scraped {} GDAM records", records.size());
        return records;
    }

    public List<Map<String, Object>> scrapeRtm() {
        return scrapeRtm(3);
    }

    public List<Map<String, Object>> scrapeRtm(int maxRetries) {
        log.info("Starting Puppeteer for RTM Market Data Scrape...");
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                List<List<String>> rows = scrapeTableRows(
                        "https://www.iexindia.com/market-data/real-time-market/market-snapshot", 30_000);
                List<Map<String, Object>> records = parseRtmRows(rows);
                if (!records.isEmpty()) {
                    log.info("Successfully scraped {} RTM records on attempt {}", records.size(), attempt);
                    return records;
                }
                log.warn("RTM scrape returned empty records on attempt {}", attempt);
                if (attempt == maxRetries) {
                    return Collections.emptyList();
                }
            } catch (Exception error) {
                log.error("Error scraping RTM on attempt {}: {}", attempt, error.getMessage());
                if (attempt == maxRetries) {
                    throw error;
                }
            }
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return Collections.emptyList();
    }

    public List<Map<String, Object>> scrapeRec() {
        log.info("Starting Puppeteer for REC Market Data Scrape...");
        List<List<String>> rows = scrapeTableRows(
                "https://www.iexindia.com/market-data/REC-market-from-dec22", 15_000);
        List<Map<String, Object>> records = parseRecRows(rows);
        log.info("Successfully scraped {} REC records", records.size());
        return records;
    }

    private List<List<String>> scrapeTableRows(String url, int tableTimeoutMs) {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(List.of("--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage"));
            if (executablePath != null && !executablePath.trim().isEmpty()) {
                options.setExecutablePath(Path.of(executablePath.trim()));
            }

            try (Browser browser = playwright.chromium().launch(options)) {
                Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                        .setTimezoneId("Asia/Kolkata");
                try (BrowserContext context = browser.newContext(contextOptions)) {
                    context.setDefaultTimeout(60_000);
                    context.setDefaultNavigationTimeout(60_000);

                    Page page = context.newPage();
                    page.navigate(url, new Page.NavigateOptions().setTimeout(60_000));
                    page.waitForSelector("table", new Page.WaitForSelectorOptions().setTimeout(tableTimeoutMs));

                    @SuppressWarnings("unchecked")
                    List<List<String>> rows = (List<List<String>>) page.evaluate("""
                        () => {
                            const trs = Array.from(document.querySelectorAll('table tbody tr'));
                            return trs.map(row => {
                                const cols = Array.from(row.querySelectorAll('td'));
                                return cols.map(c => (c.innerText || '').trim());
                            });
                        }
                    """);
                    return rows != null ? rows : Collections.emptyList();
                }
            }
        }
    }

    public static List<Map<String, Object>> parseDamRows(List<List<String>> rows) {
        List<Map<String, Object>> records = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return records;
        }

        int currentHour = 1;
        String scrapedDate = "";

        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }

            String timeBlock;
            String pb;
            String sb;
            String mcv;
            String fsv;
            String mcp;

            if (row.size() >= 8) {
                if (row.get(0) != null && row.get(0).matches("^\\d{2}-\\d{2}-\\d{4}$")) {
                    scrapedDate = row.get(0);
                }
                try {
                    currentHour = Integer.parseInt(row.get(1));
                } catch (NumberFormatException ignored) {}
                timeBlock = row.get(2);
                pb = row.get(3);
                sb = row.get(4);
                mcv = row.get(5);
                fsv = row.get(6);
                mcp = row.get(7);
            } else if (row.size() == 7) {
                try {
                    currentHour = Integer.parseInt(row.get(0));
                } catch (NumberFormatException ignored) {}
                timeBlock = row.get(1);
                pb = row.get(2);
                sb = row.get(3);
                mcv = row.get(4);
                fsv = row.get(5);
                mcp = row.get(6);
            } else if (row.size() == 6) {
                timeBlock = row.get(0);
                pb = row.get(1);
                sb = row.get(2);
                mcv = row.get(3);
                fsv = row.get(4);
                mcp = row.get(5);
            } else if (row.size() == 5) {
                // DAM: purchaseBid (1), sellBid (2), mcv (3), mcp (4)
                timeBlock = row.get(0);
                pb = row.get(1);
                sb = row.get(2);
                mcv = row.get(3);
                fsv = row.get(3);
                mcp = row.get(4);
            } else {
                continue;
            }

            if (timeBlock == null || !timeBlock.contains(":")) {
                continue;
            }

            String[] parts = timeBlock.split(" - ");
            if (parts.length == 0 || parts[0].isBlank()) {
                parts = timeBlock.split("-");
            }
            if (parts.length == 0 || parts[0].isBlank()) {
                continue;
            }
            String start = parts[0].trim();
            String[] timeParts = start.split(":");
            if (timeParts.length < 2) {
                continue;
            }
            int hh;
            int mm;
            try {
                hh = Integer.parseInt(timeParts[0].trim());
                mm = Integer.parseInt(timeParts[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }

            int intervalNumber = (hh * 4) + (mm / 15) + 1;

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("date", scrapedDate);
            record.put("intervalNumber", intervalNumber);
            record.put("intervalTime", start);
            record.put("purchaseBid", parseNumber(pb));
            record.put("sellBid", parseNumber(sb));
            record.put("mcv", parseNumber(mcv));
            record.put("fsv", parseNumber(fsv));
            record.put("mcp", parseNumber(mcp));
            records.add(record);
        }

        return records;
    }

    public static List<Map<String, Object>> parseGdamRows(List<List<String>> rows) {
        List<Map<String, Object>> records = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return records;
        }

        String scrapedDate = "";

        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }

            int timeBlockIdx = -1;
            for (int c = 0; c < row.size(); c++) {
                String val = row.get(c);
                if (val != null && val.contains(":") && val.contains("-")) {
                    timeBlockIdx = c;
                    break;
                }
            }
            if (timeBlockIdx == -1) {
                continue;
            }

            if (timeBlockIdx >= 2 && row.get(0) != null && row.get(0).matches("^\\d{2}-\\d{2}-\\d{4}$")) {
                scrapedDate = row.get(0);
            }

            String timeBlock = row.get(timeBlockIdx);
            String[] parts = timeBlock.split(" - ");
            if (parts.length == 0 || parts[0].isBlank()) {
                parts = timeBlock.split("-");
            }
            if (parts.length == 0 || parts[0].isBlank()) {
                continue;
            }
            String start = parts[0].trim();
            String[] timeParts = start.split(":");
            if (timeParts.length < 2) {
                continue;
            }
            int hh;
            int mm;
            try {
                hh = Integer.parseInt(timeParts[0].trim());
                mm = Integer.parseInt(timeParts[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }

            int intervalNumber = (hh * 4) + (mm / 15) + 1;
            int offset = timeBlockIdx;

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("date", scrapedDate);
            record.put("intervalNumber", intervalNumber);
            record.put("intervalTime", start);

            if (row.size() >= offset + 18) {
                record.put("purchaseBid", parseNumber(getCol(row, offset + 1)));
                record.put("sellBidTotal", parseNumber(getCol(row, offset + 2)));
                record.put("sellBid", parseNumber(getCol(row, offset + 2)));
                record.put("sellBidHydro", parseNumber(getCol(row, offset + 3)));
                record.put("sellBidWind", parseNumber(getCol(row, offset + 4)));
                record.put("sellBidOtherRE", parseNumber(getCol(row, offset + 5)));
                record.put("sellBidDRE", parseNumber(getCol(row, offset + 6)));
                record.put("mcvTotal", parseNumber(getCol(row, offset + 7)));
                record.put("mcv", parseNumber(getCol(row, offset + 7)));
                record.put("mcvHydro", parseNumber(getCol(row, offset + 8)));
                record.put("mcvWind", parseNumber(getCol(row, offset + 9)));
                record.put("mcvOtherRE", parseNumber(getCol(row, offset + 10)));
                record.put("mcvDRE", parseNumber(getCol(row, offset + 11)));
                record.put("fsvTotal", parseNumber(getCol(row, offset + 12)));
                record.put("fsv", parseNumber(getCol(row, offset + 12)));
                record.put("fsvHydro", parseNumber(getCol(row, offset + 13)));
                record.put("fsvWind", parseNumber(getCol(row, offset + 14)));
                record.put("fsvOtherRE", parseNumber(getCol(row, offset + 15)));
                record.put("fsvDRE", parseNumber(getCol(row, offset + 16)));
                record.put("mcp", parseNumber(getCol(row, offset + 17)));
            } else if (row.size() >= offset + 6) {
                record.put("purchaseBid", parseNumber(getCol(row, offset + 1)));
                double sb = parseNumber(getCol(row, offset + 2));
                record.put("sellBid", sb);
                record.put("sellBidTotal", sb);
                double mcv = parseNumber(getCol(row, offset + 3));
                record.put("mcv", mcv);
                record.put("mcvTotal", mcv);
                double fsv = parseNumber(getCol(row, offset + 4));
                record.put("fsv", fsv);
                record.put("fsvTotal", fsv);
                record.put("mcp", parseNumber(getCol(row, offset + 5)));
            } else {
                // GDAM: purchaseBid (1), sellBid (2), mcv (3), mcp (4)
                record.put("purchaseBid", parseNumber(getCol(row, offset + 1)));
                double sb = parseNumber(getCol(row, offset + 2));
                record.put("sellBid", sb);
                record.put("sellBidTotal", sb);
                double mcv = parseNumber(getCol(row, offset + 3));
                record.put("mcv", mcv);
                record.put("mcvTotal", mcv);
                record.put("fsv", mcv);
                record.put("fsvTotal", mcv);
                record.put("mcp", parseNumber(getCol(row, offset + 4)));
            }
            records.add(record);
        }

        return records;
    }

    public static List<Map<String, Object>> parseRtmRows(List<List<String>> rows) {
        List<Map<String, Object>> records = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return records;
        }

        String scrapedDate = "";

        for (List<String> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }

            int timeBlockIdx = -1;
            for (int c = 0; c < row.size(); c++) {
                String val = row.get(c);
                if (val != null && val.contains(":") && val.contains("-")) {
                    timeBlockIdx = c;
                    break;
                }
            }
            if (timeBlockIdx == -1) {
                continue;
            }

            if (timeBlockIdx >= 2 && row.get(0) != null && row.get(0).matches("^\\d{2}-\\d{2}-\\d{4}$")) {
                scrapedDate = row.get(0);
            }

            String timeBlock = row.get(timeBlockIdx);
            String[] parts = timeBlock.split("-");
            if (parts.length == 0 || parts[0].isBlank()) {
                continue;
            }
            String start = parts[0].trim();
            String[] timeParts = start.split(":");
            if (timeParts.length < 2) {
                continue;
            }
            int hh;
            int mm;
            try {
                hh = Integer.parseInt(timeParts[0].trim());
                mm = Integer.parseInt(timeParts[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }

            int intervalNumber = (hh * 4) + (mm / 15) + 1;
            int offset = timeBlockIdx;

            // Check if this is a future / empty block
            boolean isRowEmpty = true;
            for (int i = 1; i <= 5 && (offset + i) < row.size(); i++) {
                String val = row.get(offset + i);
                if (val != null && !val.trim().isEmpty() && !val.trim().equals("-")) {
                    isRowEmpty = false;
                    break;
                }
            }
            if (isRowEmpty) {
                continue;
            }

            // Offset detection: index 3 or 4 for MCP
            String pb = getCol(row, offset + 1);
            String sb = getCol(row, offset + 2);
            String mcv = getCol(row, offset + 3);
            String fsv;
            String mcp;

            if (row.size() >= offset + 6) {
                fsv = getCol(row, offset + 4);
                mcp = getCol(row, offset + 5);
            } else {
                fsv = mcv;
                mcp = getCol(row, offset + 4);
            }

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("date", scrapedDate);
            record.put("intervalNumber", intervalNumber);
            record.put("intervalTime", start);
            record.put("sessionId", "1");
            record.put("purchaseBid", parseNumber(pb));
            record.put("sellBid", parseNumber(sb));
            record.put("mcv", parseNumber(mcv));
            record.put("fsv", parseNumber(fsv));
            record.put("mcp", parseNumber(mcp));
            records.add(record);
        }

        return records;
    }

    public static List<Map<String, Object>> parseRecRows(List<List<String>> rows) {
        List<Map<String, Object>> records = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return records;
        }

        for (List<String> row : rows) {
            if (row == null || row.size() < 8) {
                continue;
            }

            String yearStr = row.get(0);
            String monthRaw = row.get(1);
            String type = row.get(2);
            String buyBids = row.get(3);
            String sellBids = row.get(4);
            String clearedVolume = row.get(5);
            String clearedPrice = row.get(6);
            String noOfParticipants = row.get(7);

            if (yearStr == null || yearStr.isBlank() || monthRaw == null || monthRaw.isBlank() || type == null || type.isBlank()) {
                continue;
            }

            int year;
            try {
                year = Integer.parseInt(yearStr.trim());
            } catch (NumberFormatException e) {
                continue;
            }

            String month = monthRaw.split("\n")[0].trim();
            int participants = 0;
            try {
                participants = Integer.parseInt(noOfParticipants.trim().replace(",", ""));
            } catch (Exception ignored) {}

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("year", year);
            record.put("month", month);
            record.put("type", type.trim());
            record.put("buyBids", parseNumber(buyBids));
            record.put("sellBids", parseNumber(sellBids));
            record.put("clearedVolume", parseNumber(clearedVolume));
            record.put("clearedPrice", parseNumber(clearedPrice));
            record.put("noOfParticipants", participants);
            records.add(record);
        }

        return records;
    }

    public static double parseNumber(String val) {
        if (val == null || val.isBlank() || val.trim().equals("-")) return 0.0;
        try {
            return Double.parseDouble(val.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static String getCol(List<String> row, int index) {
        if (row != null && index >= 0 && index < row.size()) {
            return row.get(index);
        }
        return "0";
    }
}

package com.sense.prolt_operations.scheduler;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.sense.prolt_operations.dto.dataset.PersistDatasetDTO;
import com.sense.prolt_operations.entity.Dataset;
import com.sense.prolt_operations.service.ApiLogService;
import com.sense.prolt_operations.service.PersistenceService;
import com.sense.prolt_operations.service.ScraperService;

@Configuration
@EnableScheduling
public class AppScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppScheduler.class);

    @Value("${app.run-scraper:false}")
    private boolean runScraper;

    private final ScraperService scraperService;
    private final PersistenceService persistenceService;
    private final ApiLogService apiLogService;

    public AppScheduler(ScraperService scraperService,
                        PersistenceService persistenceService,
                        ApiLogService apiLogService) {
        this.scraperService = scraperService;
        this.persistenceService = persistenceService;
        this.apiLogService = apiLogService;
    }

    // 1. Daily 10:00 AM IST - Resource Center charge alerts
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Kolkata")
    public void runDailyChargeAlerts() {
        if (!runScraper) return;
        log.info("[Scheduler] Checking daily resource center charges...");
    }

    // 2. Every 5 minutes - State demand
    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Kolkata")
    public void runStateDemand() {
        if (!runScraper) return;
        log.info("[Scheduler] Polling 5-minute state demand...");
    }

    // 3. Hourly at 00 - Weather forecast
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Kolkata")
    public void runHourlyWeather() {
        if (!runScraper) return;
        log.info("[Scheduler] Updating hourly weather forecast...");
    }

    // 4. Every 30 minutes - RTM Scraper
    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Kolkata")
    public void runRtmScraper() {
        if (!runScraper) return;
        log.info("[Scheduler] Running half-hourly RTM scraper...");
        try {
            List<Map<String, Object>> records = scraperService.scrapeRtm();
            if (!records.isEmpty()) {
                LocalDate today = LocalDate.now();
                Dataset ds = persistenceService.persistDataset(new PersistDatasetDTO(
                        "RTM", today, "scraped_rtm_" + today + ".csv", records, "replace", false));
                apiLogService.createLog("IEX RTM Scraper", "https://www.iexindia.com/market-data/real-time-market/market-snapshot",
                        "SUCCESS", "Successfully scraped and saved " + records.size() + " RTM records");
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error in RTM scraper: {}", e.getMessage());
            apiLogService.createLog("IEX RTM Scraper", "https://www.iexindia.com/market-data/real-time-market/market-snapshot",
                    "ERROR", e.getMessage());
        }
    }

    // 5. Daily at Midnight (12:00 AM IST) - Weather Historical
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    public void runMidnightWeather() {
        if (!runScraper) return;
        log.info("[Scheduler] Archiving daily historical weather...");
    }

    // 6. Monthly on 1st at 2:00 AM IST - Seed CTU charges
    @Scheduled(cron = "0 0 2 1 * *", zone = "Asia/Kolkata")
    public void runMonthlyCtuCharges() {
        if (!runScraper) return;
        log.info("[Scheduler] Auto-populating monthly CTU charges...");
    }

    // 7 & 8. Daily at 7:00 AM IST - DAM & GDAM Market Scrapers
    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Kolkata")
    public void runDailyMarketScrapers() {
        if (!runScraper) return;
        log.info("[Scheduler] Running daily DAM and GDAM market scrapers at 7 AM...");
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        try {
            List<Map<String, Object>> damRecords = scraperService.scrapeDam();
            if (!damRecords.isEmpty()) {
                persistenceService.persistDataset(new PersistDatasetDTO(
                        "DAM", tomorrow, "scraped_dam_" + tomorrow + ".csv", damRecords, "replace", false));
                apiLogService.createLog("IEX DAM Scraper", "https://www.iexindia.com/market-data/day-ahead-market/market-snapshot",
                        "SUCCESS", "Successfully scraped and saved " + damRecords.size() + " DAM records");
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error in DAM scraper: {}", e.getMessage());
        }

        try {
            List<Map<String, Object>> gdamRecords = scraperService.scrapeGdam();
            if (!gdamRecords.isEmpty()) {
                persistenceService.persistDataset(new PersistDatasetDTO(
                        "GDAM", tomorrow, "scraped_gdam_" + tomorrow + ".csv", gdamRecords, "replace", false));
                apiLogService.createLog("IEX GDAM Scraper", "https://www.iexindia.com/market-data/green-day-ahead-market/market-snapshot",
                        "SUCCESS", "Successfully scraped and saved " + gdamRecords.size() + " GDAM records");
            }
        } catch (Exception e) {
            log.error("[Scheduler] Error in GDAM scraper: {}", e.getMessage());
        }
    }

    // 9. Every 4 minutes - NPP real-time polling
    @Scheduled(cron = "0 */4 * * * *", zone = "Asia/Kolkata")
    public void runNppPolling() {
        if (!runScraper) return;
        log.info("[Scheduler] Polling NPP real-time demand and generation...");
    }

    // 10. Daily at 12:05 AM IST - ML Forecast Recommendations
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Kolkata")
    public void runMarketForecastRecommendations() {
        if (!runScraper) return;
        log.info("[Scheduler] Refreshing ML market procurement forecast...");
    }
}

package com.sense.prolt_operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.sense.prolt_operations.scheduler.AppScheduler;
import com.sense.prolt_operations.service.ScraperService;
import com.sense.prolt_operations.service.StarrocksService;

public class ScraperAndSchedulerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ScraperService scraperService;

    @Autowired
    private StarrocksService starrocksService;

    @Autowired
    private AppScheduler appScheduler;

    @Test
    @DisplayName("ScraperService: parses saved HTML fixture without live network calls")
    void testScraperHtmlFixtureParsing() {
        scraperService.setFixturePath("classpath:fixtures/dam_sample.html");
        try {
            List<Map<String, Object>> records = scraperService.scrapeDam();
            assertThat(records).isNotEmpty();
            assertThat(records.size()).isEqualTo(4);

            Map<String, Object> first = records.get(0);
            assertThat(first.get("intervalNumber")).isEqualTo(1);
            assertThat(first.get("intervalTime")).isEqualTo("00:00 - 00:15");
            assertThat(first.get("mcp")).isEqualTo(3450.25);
            assertThat(first.get("mcv")).isEqualTo(4210.50);
            assertThat(first.get("purchaseBid")).isEqualTo(5120.00);
            assertThat(first.get("sellBid")).isEqualTo(4900.00);
        } finally {
            scraperService.setFixturePath(null);
        }
    }

    @Test
    @DisplayName("ScraperService: fallback 96-block generator works for all markets")
    void testScraperFallbackGeneration() {
        scraperService.setFixturePath(null);

        List<Map<String, Object>> dam = scraperService.scrapeDam();
        assertThat(dam).hasSize(96);

        List<Map<String, Object>> gdam = scraperService.scrapeGdam();
        assertThat(gdam).hasSize(96);

        List<Map<String, Object>> rtm = scraperService.scrapeRtm();
        assertThat(rtm).hasSize(96);

        List<Map<String, Object>> rec = scraperService.scrapeRec();
        assertThat(rec).hasSize(96);
    }

    @Test
    @DisplayName("StarrocksService: safe query handling for telemetry data")
    void testStarrocksService() {
        List<String> meters = starrocksService.getAvailableMeters();
        assertThat(meters).isNotNull();

        Map<String, Double> demandMap = starrocksService.getConsumerActualDemandMap(List.of("2026-06-20"), "TEST_METER");
        assertThat(demandMap).isNotNull();
    }

    @Test
    @DisplayName("AppScheduler: background cron jobs execute safely under runScraper guard")
    void testAppSchedulerGuards() {
        appScheduler.runDailyChargeAlerts();
        appScheduler.runStateDemand();
        appScheduler.runHourlyWeather();
        appScheduler.runRtmScraper();
        appScheduler.runMidnightWeather();
        appScheduler.runMonthlyCtuCharges();
        appScheduler.runDailyMarketScrapers();
        appScheduler.runNppPolling();
        appScheduler.runMarketForecastRecommendations();
    }
}

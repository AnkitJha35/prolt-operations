package com.sense.prolt_operations.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("live")
@Disabled("Manual run against live IEX site: ./mvnw test -Dgroups=live")
public class ScraperLiveManualTest {

    @Test
    @DisplayName("Live end-to-end scrape of DAM market data from IEX website")
    void testLiveDamScrape() {
        ScraperService scraperService = new ScraperService();
        List<Map<String, Object>> records = scraperService.scrapeDam();
        assertThat(records).isNotNull();
        assertThat(records).isNotEmpty();
        Map<String, Object> first = records.get(0);
        assertThat(first).containsKey("intervalNumber");
        assertThat(first).containsKey("mcp");
    }
}

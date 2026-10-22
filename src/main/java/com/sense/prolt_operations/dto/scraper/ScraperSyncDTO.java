package com.sense.prolt_operations.dto.scraper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScraperSyncDTO(
    String market
) {
}

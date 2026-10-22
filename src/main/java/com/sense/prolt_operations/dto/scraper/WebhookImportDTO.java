package com.sense.prolt_operations.dto.scraper;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WebhookImportDTO(
    String type,
    Map<String, Object> payload,
    String market,
    String deliveryDate,
    List<Map<String, Object>> records
) {
}

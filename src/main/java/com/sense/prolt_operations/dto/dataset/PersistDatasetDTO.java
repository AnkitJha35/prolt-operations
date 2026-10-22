package com.sense.prolt_operations.dto.dataset;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PersistDatasetDTO(
    String market,
    LocalDate deliveryDate,
    String fileName,
    List<Map<String, Object>> records,
    String action,
    Boolean isWebhookImport
) {
}

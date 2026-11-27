package com.sense.prolt_operations.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScraperTriggerResponse(
    boolean success,
    String message,
    String datasetId,
    String market,
    String date,
    Integer inserted,
    Integer updated,
    Integer skipped,
    Integer records
) {
    public static ScraperTriggerResponse ok(String message, String datasetId) {
        return new ScraperTriggerResponse(true, message, datasetId, null, null, null, null, null, null);
    }
}

package com.sense.prolt_operations.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScraperImportResponse(
    boolean success,
    String message,
    String datasetId,
    Integer count
) {
    public static ScraperImportResponse ok(String message, String datasetId) {
        return new ScraperImportResponse(true, message, datasetId, null);
    }

    public static ScraperImportResponse ok(int count) {
        return new ScraperImportResponse(true, null, null, count);
    }

    public static ScraperImportResponse ok(String message, int count) {
        return new ScraperImportResponse(true, message, null, count);
    }

    public static ScraperImportResponse error(String message) {
        return new ScraperImportResponse(false, message, null, null);
    }
}

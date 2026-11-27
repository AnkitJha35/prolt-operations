package com.sense.prolt_operations.dto.response;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DatasetSummaryResponse(
    long totalDatasets,
    long activeDatasets,
    long replacedDatasets,
    long deletedDatasets,
    long totalUploadHistoryRecords,
    LocalDateTime latestUploadTimestamp
) {
}

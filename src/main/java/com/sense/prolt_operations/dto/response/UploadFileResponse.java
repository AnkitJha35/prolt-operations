package com.sense.prolt_operations.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UploadFileResponse(
    boolean success,
    Object datasetId,
    String market,
    String deliveryDate,
    String fileName,
    Object rowCount,
    Object parsed
) {
}

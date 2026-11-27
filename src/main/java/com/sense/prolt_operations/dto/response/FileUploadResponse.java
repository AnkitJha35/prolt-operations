package com.sense.prolt_operations.dto.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FileUploadResponse(
    boolean success,
    String market,
    String date,
    Object inserted,
    Object updated,
    Object skipped,
    Object message,
    Object errors
) {
}

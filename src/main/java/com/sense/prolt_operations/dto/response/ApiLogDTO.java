package com.sense.prolt_operations.dto.response;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiLogDTO(
    String id,
    String apiName,
    String endpoint,
    String status,
    LocalDateTime createdAt,
    String details
) {
}

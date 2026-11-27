package com.sense.prolt_operations.dto.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResponse<T>(
    List<T> data,
    long total,
    int page,
    int limit,
    int totalPages
) {
}

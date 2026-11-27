package com.sense.prolt_operations.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginationMeta(
    int page,
    int limit,
    long total,
    int totalPages
) {
}

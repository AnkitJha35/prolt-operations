package com.sense.prolt_operations.dto.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaginatedResponse<T>(
    boolean success,
    List<T> data,
    PaginationMeta meta
) {
    public static <T> PaginatedResponse<T> of(List<T> data, int page, int limit, long total, int totalPages) {
        return new PaginatedResponse<>(true, data, new PaginationMeta(page, limit, total, totalPages));
    }
}

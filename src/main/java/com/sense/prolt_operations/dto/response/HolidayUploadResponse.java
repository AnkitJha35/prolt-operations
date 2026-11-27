package com.sense.prolt_operations.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HolidayUploadResponse(
    boolean success,
    int count
) {
    public static HolidayUploadResponse ok(int count) {
        return new HolidayUploadResponse(true, count);
    }
}

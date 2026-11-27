package com.sense.prolt_operations.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationResponse(
    boolean success,
    String message
) {
    public static OperationResponse ok(String message) {
        return new OperationResponse(true, message);
    }

    public static OperationResponse error(String message) {
        return new OperationResponse(false, message);
    }
}

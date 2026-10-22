package com.sense.prolt_operations.exception;

public enum ErrorMessage {
    NOT_FOUND("Not found."),
    INTERNAL_SERVER_ERROR("Internal Server Error."),
    BAD_REQUEST("Bad Request."),
    INVALID_FILE("Invalid File."),
    SOMETHING_WENT_WRONG("Something went very wrong!");

    private final String message;

    ErrorMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}

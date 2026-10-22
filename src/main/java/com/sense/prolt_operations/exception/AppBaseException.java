package com.sense.prolt_operations.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR)
public class AppBaseException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final HttpStatus status;

    public AppBaseException() {
        super(ErrorMessage.SOMETHING_WENT_WRONG.getMessage());
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public AppBaseException(String message) {
        super(message);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public AppBaseException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

package com.sense.prolt_operations.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR)
public class SecurityStateUnavailableException extends AppBaseException {
    private static final long serialVersionUID = 1L;

    public SecurityStateUnavailableException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public SecurityStateUnavailableException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
        initCause(cause);
    }
}

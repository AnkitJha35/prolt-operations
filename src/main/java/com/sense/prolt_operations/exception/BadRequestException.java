package com.sense.prolt_operations.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.BAD_REQUEST)
public class BadRequestException extends AppBaseException {
    private static final long serialVersionUID = 1L;

    public BadRequestException() {
        super(ErrorMessage.BAD_REQUEST.getMessage(), HttpStatus.BAD_REQUEST);
    }

    public BadRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}

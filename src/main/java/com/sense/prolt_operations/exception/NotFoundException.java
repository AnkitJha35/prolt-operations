package com.sense.prolt_operations.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(code = HttpStatus.NOT_FOUND)
public class NotFoundException extends AppBaseException {
    private static final long serialVersionUID = 1L;

    public NotFoundException() {
        super(ErrorMessage.NOT_FOUND.getMessage(), HttpStatus.NOT_FOUND);
    }

    public NotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}

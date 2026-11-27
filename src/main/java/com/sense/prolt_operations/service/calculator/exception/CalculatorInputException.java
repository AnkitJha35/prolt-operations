package com.sense.prolt_operations.service.calculator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when calculator input parameters, state selection, consumption history,
 * or JSON payloads are invalid, missing, or malformed.
 */
@ResponseStatus(code = HttpStatus.BAD_REQUEST)
public class CalculatorInputException extends CalculatorException {

    private static final long serialVersionUID = 1L;

    public CalculatorInputException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }

    public CalculatorInputException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_REQUEST);
    }
}

package com.sense.prolt_operations.service.calculator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when mandatory regulatory or tariff data required for calculation
 * cannot be resolved from the database or configuration, and no audited fallback applies.
 */
@ResponseStatus(code = HttpStatus.UNPROCESSABLE_ENTITY)
public class CalculatorDataUnavailableException extends CalculatorException {

    private static final long serialVersionUID = 1L;

    public CalculatorDataUnavailableException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public CalculatorDataUnavailableException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}

package com.sense.prolt_operations.service.calculator.exception;

import org.springframework.http.HttpStatus;

import com.sense.prolt_operations.exception.AppBaseException;

/**
 * Base abstract exception for all Legacy Saving Calculator domain and calculation errors.
 * Extends {@link AppBaseException} to integrate seamlessly with {@code RestControllerAdvisor}.
 */
public abstract class CalculatorException extends AppBaseException {

    private static final long serialVersionUID = 1L;

    public CalculatorException(String message) {
        super(message);
    }

    public CalculatorException(String message, HttpStatus status) {
        super(message, status);
    }

    public CalculatorException(String message, Throwable cause, HttpStatus status) {
        super(message, status);
        if (cause != null) {
            initCause(cause);
        }
    }
}

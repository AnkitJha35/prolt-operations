package com.sense.prolt_operations.service.calculator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an error occurs during single-month or multi-month calculation execution.
 * Captures calculation context (calculation type, target month, client ID) while sanitizing messages
 * to avoid exposing database internals, SQL, or credentials.
 */
@ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR)
public class CalculatorExecutionException extends CalculatorException {

    private static final long serialVersionUID = 1L;

    private final String calculationType;
    private final String month;
    private final String clientId;

    public CalculatorExecutionException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
        this.calculationType = null;
        this.month = null;
        this.clientId = null;
    }

    public CalculatorExecutionException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR);
        this.calculationType = null;
        this.month = null;
        this.clientId = null;
    }

    public CalculatorExecutionException(String calculationType, String month, String clientId, String message, Throwable cause) {
        super(buildMessage(calculationType, month, clientId, message), cause, HttpStatus.INTERNAL_SERVER_ERROR);
        this.calculationType = calculationType;
        this.month = month;
        this.clientId = clientId;
    }

    private static String buildMessage(String calculationType, String month, String clientId, String detail) {
        StringBuilder sb = new StringBuilder();
        if (calculationType != null && !calculationType.isBlank()) {
            sb.append("[").append(calculationType).append("] ");
        }
        sb.append("Calculation failed");
        if (clientId != null && !clientId.isBlank()) {
            sb.append(" for client entry '").append(clientId).append("'");
        }
        if (month != null && !month.isBlank()) {
            sb.append(" in month '").append(month).append("'");
        }
        if (detail != null && !detail.isBlank()) {
            sb.append(": ").append(detail);
        }
        return sb.toString();
    }

    public String getCalculationType() {
        return calculationType;
    }

    public String getMonth() {
        return month;
    }

    public String getClientId() {
        return clientId;
    }
}

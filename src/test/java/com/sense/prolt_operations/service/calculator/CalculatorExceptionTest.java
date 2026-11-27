package com.sense.prolt_operations.service.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.sense.prolt_operations.service.calculator.exception.CalculatorDataUnavailableException;
import com.sense.prolt_operations.service.calculator.exception.CalculatorException;
import com.sense.prolt_operations.service.calculator.exception.CalculatorExecutionException;
import com.sense.prolt_operations.service.calculator.exception.CalculatorInputException;

class CalculatorExceptionTest {

    @Test
    @DisplayName("CalculatorInputException returns HTTP 400 BAD_REQUEST and preserves message and cause")
    void testCalculatorInputException() {
        IllegalArgumentException cause = new IllegalArgumentException("Invalid date range");
        CalculatorInputException ex = new CalculatorInputException("Malformed month format", cause);

        assertThat(ex).isInstanceOf(CalculatorException.class);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("Malformed month format");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("CalculatorExecutionException returns HTTP 500 and formats calculation context without leaking internals")
    void testCalculatorExecutionExceptionWithContext() {
        RuntimeException rootCause = new RuntimeException("Database timeout on slot table");
        CalculatorExecutionException ex = new CalculatorExecutionException(
                "HV-2 Savings",
                "2024-05",
                "client-1234",
                "Slot simulation failed",
                rootCause
        );

        assertThat(ex).isInstanceOf(CalculatorException.class);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ex.getCalculationType()).isEqualTo("HV-2 Savings");
        assertThat(ex.getMonth()).isEqualTo("2024-05");
        assertThat(ex.getClientId()).isEqualTo("client-1234");
        assertThat(ex.getCause()).isSameAs(rootCause);
        assertThat(ex.getMessage()).contains("[HV-2 Savings]");
        assertThat(ex.getMessage()).contains("client-1234");
        assertThat(ex.getMessage()).contains("2024-05");
        assertThat(ex.getMessage()).contains("Slot simulation failed");
    }

    @Test
    @DisplayName("CalculatorDataUnavailableException returns HTTP 422 UNPROCESSABLE_ENTITY")
    void testCalculatorDataUnavailableException() {
        CalculatorDataUnavailableException ex = new CalculatorDataUnavailableException(
                "Mandatory tariff schedule missing for state UP and category HV-2"
        );

        assertThat(ex).isInstanceOf(CalculatorException.class);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex.getMessage()).contains("Mandatory tariff schedule missing");
    }
}

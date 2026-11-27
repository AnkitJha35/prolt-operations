package com.sense.prolt_operations.service.calculator.model;

import java.time.LocalDate;

public record InternalRateSlot(
        LocalDate date,
        int timeblock,
        Double damMcp,
        Double rtmMcp,
        Double gdamMcp
) {}

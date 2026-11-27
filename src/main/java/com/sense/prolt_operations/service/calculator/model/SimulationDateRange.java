package com.sense.prolt_operations.service.calculator.model;

import java.time.LocalDate;
import java.util.List;

public record SimulationDateRange(
        LocalDate startDate,
        LocalDate endDate,
        int year,
        int month,
        int yyyymmMonth,
        int calendarMonth,
        int fppaQueryMonth,
        List<Integer> monthsInPlay
) {}

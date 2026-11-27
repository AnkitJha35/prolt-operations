package com.sense.prolt_operations.service.calculator.model;

public record OperationalOverheads(
        int totalDamDays,
        int totalGdamDays,
        int totalRtmDays,
        int totalDaysTraded,
        double nldcSchedulingCost,
        double sldcSchedulingCost,
        double dailyFixedOverhead,
        double bidApplicationFees
) {}

package com.sense.prolt_operations.dto.calculator;

import java.util.List;

public record DailyAnalysisRequestDTO(
    String consumerId,
    String startDate,
    String endDate,
    List<String> dates
) {}

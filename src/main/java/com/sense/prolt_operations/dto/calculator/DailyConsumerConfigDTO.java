package com.sense.prolt_operations.dto.calculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DailyConsumerConfigDTO(
    String id,
    String name,
    String discom,
    String stateCode,
    String consumerCategory,
    String voltageLevel,
    String meterNo,
    BigDecimal sanctionedLoadKw,
    BigDecimal powerFactor,
    BigDecimal proltMargin,
    BigDecimal traderMargin,
    Boolean applyElectricityDuty,
    BigDecimal electricityDutyPercent,
    BigDecimal fppaPercent,
    BigDecimal discomBaseTariff,
    List<Map<String, Object>> customTodSlots,
    Map<String, Object> tradeReports
) {}

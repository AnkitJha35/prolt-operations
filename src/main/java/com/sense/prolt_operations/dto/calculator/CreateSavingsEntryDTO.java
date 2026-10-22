package com.sense.prolt_operations.dto.calculator;

import java.math.BigDecimal;
import java.util.Map;

public record CreateSavingsEntryDTO(
    String clientName,
    String industryName,
    String address,
    BigDecimal sanctionedLoadKw,
    String stateCode,
    String discom,
    String consumerCategory,
    String voltageLevel,
    BigDecimal proltMargin,
    BigDecimal traderMargin,
    BigDecimal meteringCharges,
    BigDecimal consultancyFee,
    BigDecimal probusPlatformFee,
    Object todConsumptions,
    Boolean applyElectricityDuty,
    BigDecimal billedDemandKv,
    BigDecimal powerFactor,
    BigDecimal arrearAmount,
    BigDecimal currentLpsc,
    String billDate
) {}

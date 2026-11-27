package com.sense.prolt_operations.dto.calculator;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Spring Data JPA interface projection for 15-minute slot multi-market rates.
 * Maps columns from the combined FULL OUTER JOIN query across DAM, RTM, and GDAM tables.
 */
public interface ExchangeRateSlotProjection {

    LocalDate getDate();

    Integer getTimeblock();

    BigDecimal getDamMcp();

    BigDecimal getRtmMcp();

    BigDecimal getGdamMcp();
}

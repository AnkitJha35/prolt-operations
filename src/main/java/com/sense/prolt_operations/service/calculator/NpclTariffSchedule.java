package com.sense.prolt_operations.service.calculator;

import org.springframework.stereotype.Component;

/**
 * Encapsulates Noida Power Company Limited (NPCL) Heavy Industry Tariff Schedule (HV-2)
 * seasonal Time-of-Day (ToD) tariff resolution, peak surcharges (+15%), and off-peak rebates (-15%).
 *
 * <p>Regulatory Reference: UPERC (Uttar Pradesh Electricity Regulatory Commission) Tariff Orders.
 * Slabs are seasonal:
 * <ul>
 *   <li><b>Winter (September through March)</b>:
 *     <ul>
 *       <li>TOD1 [22:00 - 04:00]: Rate base * 0.85 (-15% off-peak rebate)</li>
 *       <li>TOD2 [04:00 - 06:00]: Rate base * 1.00 (Normal)</li>
 *       <li>TOD3 [06:00 - 10:00]: Rate base * 1.15 (+15% morning peak surcharge)</li>
 *       <li>TOD4 [10:00 - 17:00]: Rate base * 1.00 (Normal)</li>
 *       <li>TOD6 [17:00 - 19:00]: Rate base * 1.15 (+15% evening peak surcharge)</li>
 *       <li>TOD5 [19:00 - 22:00]: Rate base * 1.00 (Normal)</li>
 *     </ul>
 *   </li>
 *   <li><b>Summer (April through August)</b>:
 *     <ul>
 *       <li>TOD4 [07:00 - 16:00]: Rate base * 0.85 (-15% solar hours rebate)</li>
 *       <li>TOD1 [16:00 - 19:00]: Rate base * 1.00 (Normal)</li>
 *       <li>TOD2 [19:00 - 02:00]: Rate base * 1.15 (+15% night peak surcharge)</li>
 *       <li>TOD3 [02:00 - 07:00]: Rate base * 1.00 (Normal)</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@Component
public class NpclTariffSchedule {

    public static final double DEFAULT_BASE_RATE = 6.80;
    public static final double REBATE_MULTIPLIER = 0.85;
    public static final double NORMAL_MULTIPLIER = 1.00;
    public static final double SURCHARGE_MULTIPLIER = 1.15;

    public record NpclTodRate(String todName, double rate, double multiplier) {}

    /**
     * Determines whether the given 1-based calendar month belongs to the Winter tariff period.
     * September (9) through March (3) is Winter; April (4) through August (8) is Summer.
     *
     * @param month 1-12
     * @return true if Winter, false if Summer
     */
    public boolean isWinter(int month) {
        return month >= 9 || month <= 3;
    }

    /**
     * Resolves the applicable NPCL HV-2 ToD rate using the default base rate (Rs. 6.80/kWh).
     *
     * @param month 1-12 calendar month
     * @param hour 0-23 clock hour
     * @return NpclTodRate containing slab name, calculated rate, and applied multiplier
     */
    public NpclTodRate resolve(int month, int hour) {
        return resolve(month, hour, DEFAULT_BASE_RATE);
    }

    /**
     * Resolves the applicable NPCL HV-2 ToD rate for a specified base rate.
     *
     * @param month 1-12 calendar month
     * @param hour 0-23 clock hour
     * @param baseRate Base energy charge in Rs./kWh
     * @return NpclTodRate containing slab name, calculated rate, and applied multiplier
     */
    public NpclTodRate resolve(int month, int hour, double baseRate) {
        String todName;
        double multiplier;

        if (isWinter(month)) {
            if (hour >= 22 || hour < 4) {
                todName = "TOD1";
                multiplier = REBATE_MULTIPLIER;
            } else if (hour >= 4 && hour < 6) {
                todName = "TOD2";
                multiplier = NORMAL_MULTIPLIER;
            } else if (hour >= 6 && hour < 10) {
                todName = "TOD3";
                multiplier = SURCHARGE_MULTIPLIER;
            } else if (hour >= 10 && hour < 17) {
                todName = "TOD4";
                multiplier = NORMAL_MULTIPLIER;
            } else if (hour >= 17 && hour < 19) {
                todName = "TOD6";
                multiplier = SURCHARGE_MULTIPLIER;
            } else { // 19:00 - 22:00
                todName = "TOD5";
                multiplier = NORMAL_MULTIPLIER;
            }
        } else {
            if (hour >= 7 && hour < 16) {
                todName = "TOD4";
                multiplier = REBATE_MULTIPLIER;
            } else if (hour >= 16 && hour < 19) {
                todName = "TOD1";
                multiplier = NORMAL_MULTIPLIER;
            } else if (hour >= 19 || hour < 2) {
                todName = "TOD2";
                multiplier = SURCHARGE_MULTIPLIER;
            } else { // 02:00 - 07:00
                todName = "TOD3";
                multiplier = NORMAL_MULTIPLIER;
            }
        }

        double rate = baseRate * multiplier;
        return new NpclTodRate(todName, rate, multiplier);
    }
}

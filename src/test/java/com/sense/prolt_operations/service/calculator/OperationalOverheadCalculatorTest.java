package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.service.calculator.constant.OverheadFeeConstants;
import com.sense.prolt_operations.service.calculator.model.MarketSlotCalculation;
import com.sense.prolt_operations.service.calculator.model.OperationalOverheads;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OperationalOverheadCalculator Unit Tests")
class OperationalOverheadCalculatorTest {

    private final OperationalOverheadCalculator calculator = new OperationalOverheadCalculator();

    @Test
    @DisplayName("Should return all zeros for null or empty slots")
    void testNullOrEmptySlots() {
        OperationalOverheads overheads = calculator.calculate(null, 1000.0, 500.0);
        assertThat(overheads.totalDaysTraded()).isZero();
        assertThat(overheads.totalDamDays()).isZero();
        assertThat(overheads.nldcSchedulingCost()).isZero();
        assertThat(overheads.dailyFixedOverhead()).isZero();
    }

    @Test
    @DisplayName("Should correctly aggregate traded days across distinct products")
    void testTradedDaysAggregation() {
        List<MarketSlotCalculation> slots = new ArrayList<>();

        MarketSlotCalculation s1 = new MarketSlotCalculation();
        s1.date = "2026-06-01";
        s1.marketSource = "DAM";
        s1.marketEnergy = 250.0;
        slots.add(s1);

        MarketSlotCalculation s2 = new MarketSlotCalculation();
        s2.date = "2026-06-01";
        s2.marketSource = "GDAM";
        s2.marketEnergy = 500.0;
        slots.add(s2);

        MarketSlotCalculation s3 = new MarketSlotCalculation();
        s3.date = "2026-06-02";
        s3.marketSource = "DAM";
        s3.marketEnergy = 250.0;
        slots.add(s3);

        MarketSlotCalculation s4 = new MarketSlotCalculation();
        s4.date = "2026-06-03";
        s4.marketSource = "RTM";
        s4.marketEnergy = 250.0;
        slots.add(s4);

        double nldcFee = 1000.0;
        double sldcFee = 500.0;

        OperationalOverheads result = calculator.calculate(slots, nldcFee, sldcFee);

        assertThat(result.totalDaysTraded()).isEqualTo(3);
        assertThat(result.totalDamDays()).isEqualTo(2);
        assertThat(result.totalGdamDays()).isEqualTo(1);
        assertThat(result.totalRtmDays()).isEqualTo(1);

        int totalProductDays = 2 + 1 + 1; // 4
        assertThat(result.nldcSchedulingCost()).isEqualTo(3 * nldcFee);
        assertThat(result.sldcSchedulingCost()).isEqualTo(totalProductDays * sldcFee);
        assertThat(result.dailyFixedOverhead()).isEqualTo(3 * nldcFee + totalProductDays * sldcFee);
        assertThat(result.bidApplicationFees()).isEqualTo(totalProductDays * OverheadFeeConstants.NLDC_APPLICATION_FEE_PER_BID);
    }
}

package com.sense.prolt_operations.service.calculator;

import com.sense.prolt_operations.service.calculator.constant.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CalculatorConstantsTest {

    @Test
    @DisplayName("UnitConstants invariants")
    void testUnitConstants() {
        assertThat(UnitConstants.KW_PER_MW).isEqualTo(1000.0);
        assertThat(UnitConstants.KWH_PER_MWH).isEqualTo(1000.0);
        assertThat(UnitConstants.HOURS_PER_15MIN_SLOT).isEqualTo(0.25);
        assertThat(UnitConstants.KWH_PER_MW_SLOT).isEqualTo(250.0);
        assertThat(UnitConstants.KW_PER_MW * UnitConstants.HOURS_PER_15MIN_SLOT)
                .isEqualTo(UnitConstants.KWH_PER_MW_SLOT);
    }

    @Test
    @DisplayName("TimeblockConstants invariants")
    void testTimeblockConstants() {
        assertThat(TimeblockConstants.SLOTS_PER_DAY).isEqualTo(96);
        assertThat(TimeblockConstants.MINUTES_PER_SLOT).isEqualTo(15);
        assertThat(TimeblockConstants.HOURS_PER_DAY).isEqualTo(24);
        assertThat(TimeblockConstants.SLOTS_PER_DAY * TimeblockConstants.MINUTES_PER_SLOT)
                .isEqualTo(TimeblockConstants.HOURS_PER_DAY * 60);
    }

    @Test
    @DisplayName("RegulatoryConstants invariants")
    void testRegulatoryConstants() {
        assertThat(RegulatoryConstants.MIN_OA_ELIGIBILITY_KW).isEqualTo(1000.0);
        assertThat(RegulatoryConstants.MIN_OA_SCHEDULE_QUANTUM_MW).isEqualTo(0.1);
        assertThat(RegulatoryConstants.CONTRACT_DEMAND_PF_FACTOR).isEqualTo(0.9);
        assertThat(RegulatoryConstants.DEFAULT_POWER_FACTOR).isEqualTo(0.99);
        assertThat(RegulatoryConstants.MAX_TRANSMISSION_LOSS_CAP_PERCENT).isEqualTo(99.9);
        assertThat(RegulatoryConstants.NPCL_REBATE_DISCOUNT_FACTOR).isCloseTo(0.90 * 0.99, within(1e-9));
    }

    @Test
    @DisplayName("TaxConstants invariants")
    void testTaxConstants() {
        assertThat(TaxConstants.STATUTORY_GST_RATE).isEqualTo(0.18);
    }

    @Test
    @DisplayName("ExchangeFeeConstants invariants")
    void testExchangeFeeConstants() {
        assertThat(ExchangeFeeConstants.DEFAULT_EXCHANGE_FEE_PER_KWH).isEqualTo(0.02);
        assertThat(ExchangeFeeConstants.DEFAULT_OTHER_CHARGES_PER_KWH).isEqualTo(0.10);
        assertThat(ExchangeFeeConstants.DEFAULT_RPO_FLAT_RATE_PER_KWH).isEqualTo(0.25);
    }

    @Test
    @DisplayName("OverheadFeeConstants invariants")
    void testOverheadFeeConstants() {
        assertThat(OverheadFeeConstants.DEFAULT_DISCOM_NOC_FEE_MONTHLY).isEqualTo(7000.0);
        assertThat(OverheadFeeConstants.DEFAULT_SLDC_MONTHLY_FEE).isEqualTo(8333.0);
        assertThat(OverheadFeeConstants.DEFAULT_CONSULTANCY_FEE_MONTHLY).isEqualTo(20000.0);
        assertThat(OverheadFeeConstants.DEFAULT_PROBUS_PLATFORM_FEE_RATE).isEqualTo(0.02);
        assertThat(OverheadFeeConstants.NLDC_APPLICATION_FEE_PER_BID).isEqualTo(5.0);
        assertThat(OverheadFeeConstants.DEFAULT_NLDC_DAILY_SCHEDULING_FEE).isEqualTo(2500.0);
        assertThat(OverheadFeeConstants.DEFAULT_SLDC_DAILY_SCHEDULING_FEE).isEqualTo(1500.0);
    }

    @Test
    @DisplayName("RegulatoryFallbackConstants invariants")
    void testRegulatoryFallbackConstants() {
        assertThat(RegulatoryFallbackConstants.DEFAULT_CTU_PER_KWH).isEqualTo(0.496);
        assertThat(RegulatoryFallbackConstants.DEFAULT_STU_PER_KWH).isEqualTo(0.384);
        assertThat(RegulatoryFallbackConstants.DEFAULT_WHEELING_PER_KWH).isEqualTo(0.216);
        assertThat(RegulatoryFallbackConstants.DEFAULT_CROSS_SUBSIDY_PER_KWH).isEqualTo(0.68);
        assertThat(RegulatoryFallbackConstants.DEFAULT_ADDITIONAL_SURCHARGE_PER_KWH).isEqualTo(0.216);
        assertThat(RegulatoryFallbackConstants.DEFAULT_STU_LOSS_PERCENT).isEqualTo(3.5);
        assertThat(RegulatoryFallbackConstants.DEFAULT_WHEELING_LOSS_PERCENT).isEqualTo(4.0);
        assertThat(RegulatoryFallbackConstants.DEFAULT_DEMAND_FIXED_CHARGE_KVA).isEqualTo(330.0);
        assertThat(RegulatoryFallbackConstants.DEFAULT_BASE_DISCOM_RATE).isEqualTo(7.5);
    }

    @Test
    @DisplayName("SavingsCalculatorConstants invariants")
    void testSavingsCalculatorConstants() {
        assertThat(SavingsCalculatorConstants.DEMAND_SHIFT_MIN_SPREAD_RS).isEqualTo(0.01);
        assertThat(SavingsCalculatorConstants.EPSILON).isEqualTo(1e-9);
        assertThat(SavingsCalculatorConstants.DEFAULT_PROSPECT_CONSUMPTION_KWH).isEqualTo(1250000.0);
    }
}

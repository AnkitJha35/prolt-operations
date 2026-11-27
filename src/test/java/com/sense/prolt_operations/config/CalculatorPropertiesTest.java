package com.sense.prolt_operations.config;

import com.sense.prolt_operations.service.calculator.constant.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CalculatorPropertiesTest {

    private Validator validator;
    private CalculatorProperties properties;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        properties = new CalculatorProperties();
    }

    @Nested
    @DisplayName("Default Canonical Values Parity Tests")
    class DefaultValuesTests {

        @Test
        @DisplayName("Regulatory properties default values match canonical constants")
        void regulatoryDefaultsMatchConstants() {
            CalculatorProperties.Regulatory reg = properties.getRegulatory();
            assertThat(reg.getMinOaEligibilityKw()).isEqualTo(RegulatoryConstants.MIN_OA_ELIGIBILITY_KW);
            assertThat(reg.getNpclRebateDiscountFactor()).isEqualTo(RegulatoryConstants.NPCL_REBATE_DISCOUNT_FACTOR);
            assertThat(reg.getDefaultPowerFactor()).isEqualTo(RegulatoryConstants.DEFAULT_POWER_FACTOR);
            assertThat(reg.getContractDemandPfFactor()).isEqualTo(RegulatoryConstants.CONTRACT_DEMAND_PF_FACTOR);
        }

        @Test
        @DisplayName("Exchange properties default values match canonical constants")
        void exchangeDefaultsMatchConstants() {
            CalculatorProperties.Exchange ex = properties.getExchange();
            assertThat(ex.getDefaultExchangeFeePerKwh()).isEqualTo(ExchangeFeeConstants.DEFAULT_EXCHANGE_FEE_PER_KWH);
            assertThat(ex.getDefaultOtherChargesPerKwh()).isEqualTo(ExchangeFeeConstants.DEFAULT_OTHER_CHARGES_PER_KWH);
        }

        @Test
        @DisplayName("Tax properties default values match canonical constants")
        void taxDefaultsMatchConstants() {
            CalculatorProperties.Tax tax = properties.getTax();
            assertThat(tax.getGst()).isEqualTo(TaxConstants.STATUTORY_GST_RATE);
        }

        @Test
        @DisplayName("Fallback properties default values match canonical constants")
        void fallbackDefaultsMatchConstants() {
            CalculatorProperties.Fallback fb = properties.getFallback();
            assertThat(fb.getCtuPerKwh()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_CTU_PER_KWH);
            assertThat(fb.getStuPerKwh()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_STU_PER_KWH);
            assertThat(fb.getWheelingPerKwh()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_WHEELING_PER_KWH);
            assertThat(fb.getCrossSubsidyPerKwh()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_CROSS_SUBSIDY_PER_KWH);
            assertThat(fb.getAdditionalSurchargePerKwh()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_ADDITIONAL_SURCHARGE_PER_KWH);
            assertThat(fb.getStuLossPercent()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_STU_LOSS_PERCENT);
            assertThat(fb.getWheelingLossPercent()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_WHEELING_LOSS_PERCENT);
            assertThat(fb.getDemandFixedChargeKva()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_DEMAND_FIXED_CHARGE_KVA);
            assertThat(fb.getBaseDiscomRate()).isEqualTo(RegulatoryFallbackConstants.DEFAULT_BASE_DISCOM_RATE);
        }

        @Test
        @DisplayName("Fees properties default values match canonical constants")
        void feesDefaultsMatchConstants() {
            CalculatorProperties.Fees fees = properties.getFees();
            assertThat(fees.getNldcApplicationFeePerBid()).isEqualTo(OverheadFeeConstants.NLDC_APPLICATION_FEE_PER_BID);
            assertThat(fees.getNldcDailySchedulingFee()).isEqualTo(OverheadFeeConstants.DEFAULT_NLDC_DAILY_SCHEDULING_FEE);
            assertThat(fees.getSldcDailySchedulingFee()).isEqualTo(OverheadFeeConstants.DEFAULT_SLDC_DAILY_SCHEDULING_FEE);
            assertThat(fees.getDiscomNocMonthly()).isEqualTo(OverheadFeeConstants.DEFAULT_DISCOM_NOC_FEE_MONTHLY);
            assertThat(fees.getSldcMonthly()).isEqualTo(OverheadFeeConstants.DEFAULT_SLDC_MONTHLY_FEE);
            assertThat(fees.getConsultancyMonthly()).isEqualTo(OverheadFeeConstants.DEFAULT_CONSULTANCY_FEE_MONTHLY);
            assertThat(fees.getPlatformFeeRate()).isEqualTo(OverheadFeeConstants.DEFAULT_PROBUS_PLATFORM_FEE_RATE);
        }

        @Test
        @DisplayName("Business properties default values match canonical constants")
        void businessDefaultsMatchConstants() {
            CalculatorProperties.Business biz = properties.getBusiness();
            assertThat(biz.getDefaultProspectConsumptionKwh()).isEqualTo(SavingsCalculatorConstants.DEFAULT_PROSPECT_CONSUMPTION_KWH);
            assertThat(biz.getDemandShiftMinSpreadRs()).isEqualTo(SavingsCalculatorConstants.DEMAND_SHIFT_MIN_SPREAD_RS);
        }

        @Test
        @DisplayName("Default properties pass all Jakarta validation constraints")
        void defaultPropertiesPassValidation() {
            Set<ConstraintViolation<CalculatorProperties>> violations = validator.validate(properties);
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Jakarta Validation Constraints Enforcement")
    class ValidationConstraintsTests {

        @Test
        @DisplayName("Negative fees trigger validation violations")
        void negativeFeesFailValidation() {
            properties.getFees().setNldcDailySchedulingFee(-100.0);
            Set<ConstraintViolation<CalculatorProperties>> violations = validator.validate(properties);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("nldcDailySchedulingFee"));
        }

        @Test
        @DisplayName("Negative exchange fee triggers validation violations")
        void negativeExchangeFeeFailsValidation() {
            properties.getExchange().setDefaultExchangeFeePerKwh(-0.02);
            Set<ConstraintViolation<CalculatorProperties>> violations = validator.validate(properties);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("defaultExchangeFeePerKwh"));
        }

        @Test
        @DisplayName("Out of range GST (< 0 or > 1) triggers validation violations")
        void invalidGstFailsValidation() {
            properties.getTax().setGst(1.50);
            Set<ConstraintViolation<CalculatorProperties>> violations = validator.validate(properties);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("gst"));

            properties.getTax().setGst(-0.10);
            violations = validator.validate(properties);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("gst"));
        }

        @Test
        @DisplayName("Out of range power factor (<= 0 or > 1) triggers validation violations")
        void invalidPowerFactorFailsValidation() {
            properties.getRegulatory().setDefaultPowerFactor(0.0);
            Set<ConstraintViolation<CalculatorProperties>> violations = validator.validate(properties);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("defaultPowerFactor"));

            properties.getRegulatory().setDefaultPowerFactor(1.1);
            violations = validator.validate(properties);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("defaultPowerFactor"));
        }

        @Test
        @DisplayName("Negative min OA eligibility triggers validation violations")
        void negativeMinOaFailsValidation() {
            properties.getRegulatory().setMinOaEligibilityKw(-50.0);
            Set<ConstraintViolation<CalculatorProperties>> violations = validator.validate(properties);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("minOaEligibilityKw"));
        }

        @Test
        @DisplayName("Negative prospect consumption triggers validation violations")
        void negativeProspectConsumptionFailsValidation() {
            properties.getBusiness().setDefaultProspectConsumptionKwh(-500.0);
            Set<ConstraintViolation<CalculatorProperties>> violations = validator.validate(properties);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("defaultProspectConsumptionKwh"));
        }
    }

    @Nested
    @DisplayName("Spring Profile Configuration Binding Tests")
    class SpringProfileBindingTests {

        @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
        @org.springframework.boot.context.properties.EnableConfigurationProperties(CalculatorProperties.class)
        static class TestConfig {}

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class);

        @Test
        @DisplayName("Binds default configuration without overrides")
        void bindsDefaultConfiguration() {
            runner.run(context -> {
                assertThat(context).hasSingleBean(CalculatorProperties.class);
                CalculatorProperties props = context.getBean(CalculatorProperties.class);
                assertThat(props.getTax().getGst()).isEqualTo(0.18);
                assertThat(props.getFees().getConsultancyMonthly()).isEqualTo(20000.0);
                assertThat(props.getRegulatory().getMinOaEligibilityKw()).isEqualTo(1000.0);
            });
        }

        @Test
        @DisplayName("Binds custom property overrides successfully")
        void bindsPropertyOverrides() {
            runner.withPropertyValues(
                    "calculator.tax.gst=0.12",
                    "calculator.fees.consultancy-monthly=25000.0",
                    "calculator.regulatory.min-oa-eligibility-kw=250.0",
                    "calculator.business.demand-shift-min-spread-rs=0.75"
            ).run(context -> {
                assertThat(context).hasSingleBean(CalculatorProperties.class);
                CalculatorProperties props = context.getBean(CalculatorProperties.class);
                assertThat(props.getTax().getGst()).isEqualTo(0.12);
                assertThat(props.getFees().getConsultancyMonthly()).isEqualTo(25000.0);
                assertThat(props.getRegulatory().getMinOaEligibilityKw()).isEqualTo(250.0);
                assertThat(props.getBusiness().getDemandShiftMinSpreadRs()).isEqualTo(0.75);
            });
        }
    }
}

package com.sense.prolt_operations.config;

import com.sense.prolt_operations.service.calculator.constant.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed, profile-aware Spring Boot configuration properties for the
 * Legacy Saving Calculator.
 *
 * <p>Centralizes regulatory thresholds, market exchange fees, statutory taxes,
 * regulatory fallbacks, operational overheads, and business heuristic parameters.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "calculator")
public class CalculatorProperties {

    @Valid
    @NotNull
    private Regulatory regulatory = new Regulatory();

    @Valid
    @NotNull
    private Fallback fallback = new Fallback();

    @Valid
    @NotNull
    private Exchange exchange = new Exchange();

    @Valid
    @NotNull
    private Tax tax = new Tax();

    @Valid
    @NotNull
    private Fees fees = new Fees();

    @Valid
    @NotNull
    private Business business = new Business();

    public Regulatory getRegulatory() {
        return regulatory;
    }

    public void setRegulatory(Regulatory regulatory) {
        this.regulatory = regulatory;
    }

    public Fallback getFallback() {
        return fallback;
    }

    public void setFallback(Fallback fallback) {
        this.fallback = fallback;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    public Tax getTax() {
        return tax;
    }

    public void setTax(Tax tax) {
        this.tax = tax;
    }

    public Fees getFees() {
        return fees;
    }

    public void setFees(Fees fees) {
        this.fees = fees;
    }

    public Business getBusiness() {
        return business;
    }

    public void setBusiness(Business business) {
        this.business = business;
    }

    /**
     * Statutory regulatory thresholds and conversion rules.
     */
    public static class Regulatory {
        @Positive(message = "Minimum OA eligibility must be strictly positive")
        private double minOaEligibilityKw = RegulatoryConstants.MIN_OA_ELIGIBILITY_KW;

        @DecimalMin(value = "0.01", inclusive = true, message = "Minimum scheduling quantum must be at least 0.01 MW")
        @DecimalMax(value = "100.0", inclusive = true, message = "Minimum scheduling quantum must not exceed 100 MW")
        private double minOaScheduleQuantumMw = RegulatoryConstants.MIN_OA_SCHEDULE_QUANTUM_MW;

        @DecimalMin(value = "0.1", inclusive = true, message = "Contract demand PF factor must be at least 0.1")
        @DecimalMax(value = "1.0", inclusive = true, message = "Contract demand PF factor cannot exceed 1.0")
        private double contractDemandPfFactor = RegulatoryConstants.CONTRACT_DEMAND_PF_FACTOR;

        @DecimalMin(value = "0.5", inclusive = true, message = "Default power factor must be at least 0.5")
        @DecimalMax(value = "1.0", inclusive = true, message = "Default power factor cannot exceed 1.0")
        private double defaultPowerFactor = RegulatoryConstants.DEFAULT_POWER_FACTOR;

        @DecimalMin(value = "0.0", inclusive = true, message = "NPCL rebate discount factor must be non-negative")
        @DecimalMax(value = "1.0", inclusive = true, message = "NPCL rebate discount factor cannot exceed 1.0")
        private double npclRebateDiscountFactor = RegulatoryConstants.NPCL_REBATE_DISCOUNT_FACTOR;

        public double getMinOaEligibilityKw() {
            return minOaEligibilityKw;
        }

        public void setMinOaEligibilityKw(double minOaEligibilityKw) {
            this.minOaEligibilityKw = minOaEligibilityKw;
        }

        public double getMinOaScheduleQuantumMw() {
            return minOaScheduleQuantumMw;
        }

        public void setMinOaScheduleQuantumMw(double minOaScheduleQuantumMw) {
            this.minOaScheduleQuantumMw = minOaScheduleQuantumMw;
        }

        public double getContractDemandPfFactor() {
            return contractDemandPfFactor;
        }

        public void setContractDemandPfFactor(double contractDemandPfFactor) {
            this.contractDemandPfFactor = contractDemandPfFactor;
        }

        public double getDefaultPowerFactor() {
            return defaultPowerFactor;
        }

        public void setDefaultPowerFactor(double defaultPowerFactor) {
            this.defaultPowerFactor = defaultPowerFactor;
        }

        public double getNpclRebateDiscountFactor() {
            return npclRebateDiscountFactor;
        }

        public void setNpclRebateDiscountFactor(double npclRebateDiscountFactor) {
            this.npclRebateDiscountFactor = npclRebateDiscountFactor;
        }
    }

    /**
     * Power exchange transaction and flat statutory compliance fees.
     */
    public static class Exchange {
        @DecimalMin(value = "0.0", inclusive = true, message = "Default exchange fee must be non-negative")
        private double defaultExchangeFeePerKwh = ExchangeFeeConstants.DEFAULT_EXCHANGE_FEE_PER_KWH;

        @DecimalMin(value = "0.0", inclusive = true, message = "Default other charges must be non-negative")
        private double defaultOtherChargesPerKwh = ExchangeFeeConstants.DEFAULT_OTHER_CHARGES_PER_KWH;

        @DecimalMin(value = "0.0", inclusive = true, message = "Default RPO rate must be non-negative")
        private double defaultRpoRatePerKwh = ExchangeFeeConstants.DEFAULT_RPO_FLAT_RATE_PER_KWH;

        public double getDefaultExchangeFeePerKwh() {
            return defaultExchangeFeePerKwh;
        }

        public void setDefaultExchangeFeePerKwh(double defaultExchangeFeePerKwh) {
            this.defaultExchangeFeePerKwh = defaultExchangeFeePerKwh;
        }

        public double getDefaultOtherChargesPerKwh() {
            return defaultOtherChargesPerKwh;
        }

        public void setDefaultOtherChargesPerKwh(double defaultOtherChargesPerKwh) {
            this.defaultOtherChargesPerKwh = defaultOtherChargesPerKwh;
        }

        public double getDefaultRpoRatePerKwh() {
            return defaultRpoRatePerKwh;
        }

        public void setDefaultRpoRatePerKwh(double defaultRpoRatePerKwh) {
            this.defaultRpoRatePerKwh = defaultRpoRatePerKwh;
        }
    }

    /**
     * Statutory taxes applicable to advisory and trading operations.
     */
    public static class Tax {
        @DecimalMin(value = "0.0", inclusive = true, message = "GST rate must be non-negative")
        @DecimalMax(value = "1.0", inclusive = true, message = "GST rate cannot exceed 1.0 (100%)")
        private double gst = TaxConstants.STATUTORY_GST_RATE;

        public double getGst() {
            return gst;
        }

        public void setGst(double gst) {
            this.gst = gst;
        }
    }

    /**
     * Standard benchmark regulatory fallback tariffs and loss percentages.
     */
    public static class Fallback {
        @DecimalMin(value = "0.0", inclusive = true, message = "CTU charge fallback must be non-negative")
        private double ctuPerKwh = RegulatoryFallbackConstants.DEFAULT_CTU_PER_KWH;

        @DecimalMin(value = "0.0", inclusive = true, message = "STU charge fallback must be non-negative")
        private double stuPerKwh = RegulatoryFallbackConstants.DEFAULT_STU_PER_KWH;

        @DecimalMin(value = "0.0", inclusive = true, message = "Wheeling charge fallback must be non-negative")
        private double wheelingPerKwh = RegulatoryFallbackConstants.DEFAULT_WHEELING_PER_KWH;

        @DecimalMin(value = "0.0", inclusive = true, message = "Cross subsidy fallback must be non-negative")
        private double crossSubsidyPerKwh = RegulatoryFallbackConstants.DEFAULT_CROSS_SUBSIDY_PER_KWH;

        @DecimalMin(value = "0.0", inclusive = true, message = "Additional surcharge fallback must be non-negative")
        private double additionalSurchargePerKwh = RegulatoryFallbackConstants.DEFAULT_ADDITIONAL_SURCHARGE_PER_KWH;

        @DecimalMin(value = "0.0", inclusive = true, message = "STU loss percent must be non-negative")
        @DecimalMax(value = "100.0", inclusive = true, message = "STU loss percent cannot exceed 100%")
        private double stuLossPercent = RegulatoryFallbackConstants.DEFAULT_STU_LOSS_PERCENT;

        @DecimalMin(value = "0.0", inclusive = true, message = "Wheeling loss percent must be non-negative")
        @DecimalMax(value = "100.0", inclusive = true, message = "Wheeling loss percent cannot exceed 100%")
        private double wheelingLossPercent = RegulatoryFallbackConstants.DEFAULT_WHEELING_LOSS_PERCENT;

        @DecimalMin(value = "0.0", inclusive = true, message = "Demand fixed charge fallback must be non-negative")
        private double demandFixedChargeKva = RegulatoryFallbackConstants.DEFAULT_DEMAND_FIXED_CHARGE_KVA;

        @Positive(message = "Base DISCOM rate fallback must be strictly positive")
        private double baseDiscomRate = RegulatoryFallbackConstants.DEFAULT_BASE_DISCOM_RATE;

        public double getCtuPerKwh() {
            return ctuPerKwh;
        }

        public void setCtuPerKwh(double ctuPerKwh) {
            this.ctuPerKwh = ctuPerKwh;
        }

        public double getStuPerKwh() {
            return stuPerKwh;
        }

        public void setStuPerKwh(double stuPerKwh) {
            this.stuPerKwh = stuPerKwh;
        }

        public double getWheelingPerKwh() {
            return wheelingPerKwh;
        }

        public void setWheelingPerKwh(double wheelingPerKwh) {
            this.wheelingPerKwh = wheelingPerKwh;
        }

        public double getCrossSubsidyPerKwh() {
            return crossSubsidyPerKwh;
        }

        public void setCrossSubsidyPerKwh(double crossSubsidyPerKwh) {
            this.crossSubsidyPerKwh = crossSubsidyPerKwh;
        }

        public double getAdditionalSurchargePerKwh() {
            return additionalSurchargePerKwh;
        }

        public void setAdditionalSurchargePerKwh(double additionalSurchargePerKwh) {
            this.additionalSurchargePerKwh = additionalSurchargePerKwh;
        }

        public double getStuLossPercent() {
            return stuLossPercent;
        }

        public void setStuLossPercent(double stuLossPercent) {
            this.stuLossPercent = stuLossPercent;
        }

        public double getWheelingLossPercent() {
            return wheelingLossPercent;
        }

        public void setWheelingLossPercent(double wheelingLossPercent) {
            this.wheelingLossPercent = wheelingLossPercent;
        }

        public double getDemandFixedChargeKva() {
            return demandFixedChargeKva;
        }

        public void setDemandFixedChargeKva(double demandFixedChargeKva) {
            this.demandFixedChargeKva = demandFixedChargeKva;
        }

        public double getBaseDiscomRate() {
            return baseDiscomRate;
        }

        public void setBaseDiscomRate(double baseDiscomRate) {
            this.baseDiscomRate = baseDiscomRate;
        }
    }

    /**
     * Administrative, grid dispatch, and platform management overhead fees.
     */
    public static class Fees {
        @DecimalMin(value = "0.0", inclusive = true, message = "DISCOM NOC monthly fee must be non-negative")
        private double discomNocMonthly = OverheadFeeConstants.DEFAULT_DISCOM_NOC_FEE_MONTHLY;

        @DecimalMin(value = "0.0", inclusive = true, message = "SLDC monthly fee must be non-negative")
        private double sldcMonthly = OverheadFeeConstants.DEFAULT_SLDC_MONTHLY_FEE;

        @DecimalMin(value = "0.0", inclusive = true, message = "Consultancy monthly fee must be non-negative")
        private double consultancyMonthly = OverheadFeeConstants.DEFAULT_CONSULTANCY_FEE_MONTHLY;

        @DecimalMin(value = "0.0", inclusive = true, message = "Platform fee rate must be non-negative")
        private double platformFeeRate = OverheadFeeConstants.DEFAULT_PROBUS_PLATFORM_FEE_RATE;

        @DecimalMin(value = "0.0", inclusive = true, message = "NLDC application fee per bid must be non-negative")
        private double nldcApplicationFeePerBid = OverheadFeeConstants.NLDC_APPLICATION_FEE_PER_BID;

        @DecimalMin(value = "0.0", inclusive = true, message = "NLDC daily scheduling fee must be non-negative")
        private double nldcDailySchedulingFee = OverheadFeeConstants.DEFAULT_NLDC_DAILY_SCHEDULING_FEE;

        @DecimalMin(value = "0.0", inclusive = true, message = "SLDC daily scheduling fee must be non-negative")
        private double sldcDailySchedulingFee = OverheadFeeConstants.DEFAULT_SLDC_DAILY_SCHEDULING_FEE;

        public double getDiscomNocMonthly() {
            return discomNocMonthly;
        }

        public void setDiscomNocMonthly(double discomNocMonthly) {
            this.discomNocMonthly = discomNocMonthly;
        }

        public double getSldcMonthly() {
            return sldcMonthly;
        }

        public void setSldcMonthly(double sldcMonthly) {
            this.sldcMonthly = sldcMonthly;
        }

        public double getConsultancyMonthly() {
            return consultancyMonthly;
        }

        public void setConsultancyMonthly(double consultancyMonthly) {
            this.consultancyMonthly = consultancyMonthly;
        }

        public double getPlatformFeeRate() {
            return platformFeeRate;
        }

        public void setPlatformFeeRate(double platformFeeRate) {
            this.platformFeeRate = platformFeeRate;
        }

        public double getNldcApplicationFeePerBid() {
            return nldcApplicationFeePerBid;
        }

        public void setNldcApplicationFeePerBid(double nldcApplicationFeePerBid) {
            this.nldcApplicationFeePerBid = nldcApplicationFeePerBid;
        }

        public double getNldcDailySchedulingFee() {
            return nldcDailySchedulingFee;
        }

        public void setNldcDailySchedulingFee(double nldcDailySchedulingFee) {
            this.nldcDailySchedulingFee = nldcDailySchedulingFee;
        }

        public double getSldcDailySchedulingFee() {
            return sldcDailySchedulingFee;
        }

        public void setSldcDailySchedulingFee(double sldcDailySchedulingFee) {
            this.sldcDailySchedulingFee = sldcDailySchedulingFee;
        }
    }

    /**
     * Business heuristic limits and simulation defaults.
     */
    public static class Business {
        @DecimalMin(value = "0.0", inclusive = true, message = "Demand shift minimum spread must be non-negative")
        private double demandShiftMinSpreadRs = SavingsCalculatorConstants.DEMAND_SHIFT_MIN_SPREAD_RS;

        @Positive(message = "Default prospect consumption must be strictly positive")
        private double defaultProspectConsumptionKwh = SavingsCalculatorConstants.DEFAULT_PROSPECT_CONSUMPTION_KWH;

        public double getDemandShiftMinSpreadRs() {
            return demandShiftMinSpreadRs;
        }

        public void setDemandShiftMinSpreadRs(double demandShiftMinSpreadRs) {
            this.demandShiftMinSpreadRs = demandShiftMinSpreadRs;
        }

        public double getDefaultProspectConsumptionKwh() {
            return defaultProspectConsumptionKwh;
        }

        public void setDefaultProspectConsumptionKwh(double defaultProspectConsumptionKwh) {
            this.defaultProspectConsumptionKwh = defaultProspectConsumptionKwh;
        }
    }
}

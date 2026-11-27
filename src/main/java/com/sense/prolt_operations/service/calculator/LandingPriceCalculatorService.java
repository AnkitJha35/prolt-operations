package com.sense.prolt_operations.service.calculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.dto.calculator.LandingPriceCalculationRequest;
import com.sense.prolt_operations.dto.calculator.LandingPriceCalculationResponse;
import com.sense.prolt_operations.entity.CtuCharges;
import com.sense.prolt_operations.entity.IstsCharges;
import com.sense.prolt_operations.entity.StateCharges;
import com.sense.prolt_operations.entity.StateTariff;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.service.SavingsCalculatorDataAccessService;
import com.sense.prolt_operations.service.calculator.constant.ExchangeFeeConstants;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryConstants;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryFallbackConstants;
import com.sense.prolt_operations.service.calculator.constant.TaxConstants;
import com.sense.prolt_operations.service.calculator.model.MatchedTariffRate;
import com.sense.prolt_operations.service.calculator.model.ParsedCategory;

/**
 * Dedicated, independent Landing Price Calculator service.
 * <p>
 * Evaluates landed open-access electricity prices and DISCOM tariffs independently
 * of the Saving Calculator orchestration, adhering to the authoritative Node.js logic.
 */
@Service
public class LandingPriceCalculatorService {

    private static final Logger log = LoggerFactory.getLogger(LandingPriceCalculatorService.class);

    private static final double DEFAULT_DUTY_RATE = 0.075; // 7.5% statutory electricity duty

    private final SavingsCalculatorDataAccessService dataAccessService;
    private final RegulatoryChargeResolver regulatoryChargeResolver;
    private final LandedCostCalculator landedCostCalculator;
    private final ElectricalLossCalculator electricalLossCalculator;
    private final CalculatorProperties properties;

    public LandingPriceCalculatorService(
            SavingsCalculatorDataAccessService dataAccessService,
            RegulatoryChargeResolver regulatoryChargeResolver,
            LandedCostCalculator landedCostCalculator,
            ElectricalLossCalculator electricalLossCalculator,
            CalculatorProperties properties) {
        this.dataAccessService = dataAccessService;
        this.regulatoryChargeResolver = regulatoryChargeResolver;
        this.landedCostCalculator = landedCostCalculator;
        this.electricalLossCalculator = electricalLossCalculator;
        this.properties = properties != null ? properties : new CalculatorProperties();
    }

    /**
     * Executes an independent landed price calculation.
     *
     * @param req typed calculation request
     * @return typed calculation response
     */
    public LandingPriceCalculationResponse calculate(LandingPriceCalculationRequest req) {
        if (req == null) {
            throw new BadRequestException("Calculation request cannot be null.");
        }
        if (req.state() == null || req.state().trim().isEmpty()) {
            throw new BadRequestException("State is required to calculate landing price.");
        }
        if (req.consumerCategory() == null || req.consumerCategory().trim().isEmpty()) {
            throw new BadRequestException("Consumer category is required.");
        }
        if (req.voltageLevel() == null || req.voltageLevel().trim().isEmpty()) {
            throw new BadRequestException("Voltage level is required.");
        }

        // 1. Resolve date and timeblock defaults
        LocalDate deliveryDate = req.deliveryDate() != null ? req.deliveryDate() : LocalDate.now();
        int timeblock = (req.timeblock() != null && req.timeblock() >= 1 && req.timeblock() <= 96)
                ? req.timeblock() : 1;
        int hour = (timeblock - 1) * 15 / 60;
        int month = deliveryDate.getMonthValue();
        int yearMonth = deliveryDate.getYear() * 100 + month;

        // 2. Resolve client parameters & Open Access thresholds
        double minOaKw = properties.getRegulatory() != null
                ? properties.getRegulatory().getMinOaEligibilityKw()
                : RegulatoryConstants.MIN_OA_ELIGIBILITY_KW;
        double sanctionedLoadKw = req.sanctionedLoadKw() != null ? req.sanctionedLoadKw() : minOaKw;
        boolean isEligibleForOa = sanctionedLoadKw >= minOaKw;

        double defaultPf = properties.getRegulatory() != null
                ? properties.getRegulatory().getDefaultPowerFactor()
                : RegulatoryConstants.DEFAULT_POWER_FACTOR;
        double powerFactor = (req.powerFactor() != null && req.powerFactor() > 0)
                ? req.powerFactor() : defaultPf;

        boolean applyDuty = Boolean.TRUE.equals(req.applyElectricityDuty());
        double traderMargin = req.traderMarginRsPerKwh() != null
                ? req.traderMarginRsPerKwh() : 0.02;

        LandingPriceCalculationRequest.CalculationMode mode = req.calculationMode() != null
                ? req.calculationMode() : LandingPriceCalculationRequest.CalculationMode.OPEN_ACCESS_LANDED;

        // 3. Resolve state, DISCOM, and category formats
        String stateInput = req.state().trim();
        List<String> stateFormats = dataAccessService.resolveStateFormats(stateInput);
        String discom = req.discom() != null ? req.discom().trim() : "";
        boolean isNpcl = "NPCL".equalsIgnoreCase(discom);
        ParsedCategory parsedCat = CalculatorUtils.parseCategory(req.consumerCategory());
        boolean isNpclHv2 = isNpcl && "HV-2".equalsIgnoreCase(parsedCat.category());
        String parsedVoltage = CalculatorUtils.parseSupplyVoltageCategory(req.voltageLevel());

        // 4. Retrieve transmission & distribution losses
        List<IstsCharges> istsChargesList = dataAccessService.getIstsCharges(deliveryDate, deliveryDate);
        double istsLoss = regulatoryChargeResolver.resolveIstsLossPercent(deliveryDate, istsChargesList);

        Optional<StateCharges> stateChargesOpt = dataAccessService.getStateCharges(
                stateFormats, discom, parsedCat.category(), deliveryDate);

        double fallbackStuLoss = properties.getFallback() != null
                ? properties.getFallback().getStuLossPercent() : RegulatoryFallbackConstants.DEFAULT_STU_LOSS_PERCENT;
        double fallbackWheelingLoss = properties.getFallback() != null
                ? properties.getFallback().getWheelingLossPercent() : RegulatoryFallbackConstants.DEFAULT_WHEELING_LOSS_PERCENT;

        double stuLoss = stateChargesOpt
                .map(c -> c.getStuLossPercent() != null ? c.getStuLossPercent().doubleValue() : fallbackStuLoss)
                .orElse(fallbackStuLoss);
        double wheelingLoss = stateChargesOpt
                .map(c -> c.getWheelingLossPercent() != null ? c.getWheelingLossPercent().doubleValue() : fallbackWheelingLoss)
                .orElse(fallbackWheelingLoss);

        double lossCoefficient = electricalLossCalculator.calculateLossCoefficient(istsLoss, stuLoss, wheelingLoss);

        // 5. Retrieve regulatory adders and statutory charges
        Optional<CtuCharges> ctuChargesOpt = dataAccessService.getCtuCharges(stateFormats, yearMonth);
        double fallbackCtu = properties.getFallback() != null
                ? properties.getFallback().getCtuPerKwh() : RegulatoryFallbackConstants.DEFAULT_CTU_PER_KWH;
        double fallbackStu = properties.getFallback() != null
                ? properties.getFallback().getStuPerKwh() : RegulatoryFallbackConstants.DEFAULT_STU_PER_KWH;
        double fallbackWheeling = properties.getFallback() != null
                ? properties.getFallback().getWheelingPerKwh() : RegulatoryFallbackConstants.DEFAULT_WHEELING_PER_KWH;
        double fallbackCss = properties.getFallback() != null
                ? properties.getFallback().getCrossSubsidyPerKwh() : RegulatoryFallbackConstants.DEFAULT_CROSS_SUBSIDY_PER_KWH;
        double fallbackAs = properties.getFallback() != null
                ? properties.getFallback().getAdditionalSurchargePerKwh() : RegulatoryFallbackConstants.DEFAULT_ADDITIONAL_SURCHARGE_PER_KWH;

        double ctu = ctuChargesOpt
                .map(c -> c.getCtuChargesRsPerKwh() != null ? c.getCtuChargesRsPerKwh().doubleValue() : fallbackCtu)
                .orElse(fallbackCtu);
        double stu = stateChargesOpt
                .map(c -> c.getStuCharges() != null ? c.getStuCharges().doubleValue() : fallbackStu)
                .orElse(fallbackStu);
        double wheeling = stateChargesOpt
                .map(c -> c.getDistributionWheelingCharges() != null ? c.getDistributionWheelingCharges().doubleValue() : fallbackWheeling)
                .orElse(fallbackWheeling);
        double css = stateChargesOpt
                .map(c -> c.getCrossSubsidy() != null ? c.getCrossSubsidy().doubleValue() : fallbackCss)
                .orElse(fallbackCss);
        double as = stateChargesOpt
                .map(c -> c.getAdditionalCharge() != null ? c.getAdditionalCharge().doubleValue() : fallbackAs)
                .orElse(fallbackAs);

        double otherCharges = properties.getExchange() != null
                ? properties.getExchange().getDefaultOtherChargesPerKwh()
                : ExchangeFeeConstants.DEFAULT_OTHER_CHARGES_PER_KWH;
        double exchangeFees = properties.getExchange() != null
                ? properties.getExchange().getDefaultExchangeFeePerKwh()
                : ExchangeFeeConstants.DEFAULT_EXCHANGE_FEE_PER_KWH;
        double gstRate = properties.getTax() != null
                ? properties.getTax().getGst()
                : TaxConstants.STATUTORY_GST_RATE;
        double gstExchange = exchangeFees * gstRate;
        double gstTraderMargin = traderMargin * gstRate;

        double totalRegCharges = landedCostCalculator.calculateRegulatoryChargesSum(
                ctu, stu, wheeling, otherCharges, exchangeFees, gstExchange, traderMargin, gstTraderMargin, css, as);

        Double damMcpKwh = null;
        if (req.damMcpRsPerMwh() != null) {
            damMcpKwh = Double.valueOf(req.damMcpRsPerMwh() / 1000.0);
        } else if (req.mcpRsPerKwh() != null) {
            damMcpKwh = req.mcpRsPerKwh();
        }

        Double rtmMcpKwh = req.rtmMcpRsPerMwh() != null ? Double.valueOf(req.rtmMcpRsPerMwh() / 1000.0) : null;
        Double gdamMcpKwh = req.gdamMcpRsPerMwh() != null ? Double.valueOf(req.gdamMcpRsPerMwh() / 1000.0) : null;

        Map<String, LandingPriceCalculationResponse.MarketRateDetail> marketLandings = new LinkedHashMap<>();

        LandingPriceCalculationResponse.MarketRateDetail damDetail = computeMarketLanding(
                damMcpKwh, totalRegCharges, lossCoefficient, isEligibleForOa, mode);
        LandingPriceCalculationResponse.MarketRateDetail rtmDetail = computeMarketLanding(
                rtmMcpKwh, totalRegCharges, lossCoefficient, isEligibleForOa, mode);
        LandingPriceCalculationResponse.MarketRateDetail gdamDetail = computeMarketLanding(
                gdamMcpKwh, totalRegCharges, lossCoefficient, true, mode); // GDAM is open to all loads

        if (damMcpKwh != null) marketLandings.put("DAM", damDetail);
        if (rtmMcpKwh != null) marketLandings.put("RTM", rtmDetail);
        if (gdamMcpKwh != null) marketLandings.put("GDAM", gdamDetail);

        // Determine best market source
        String bestMarketSource = null;
        Double bestMarketLandedPrice = null;

        for (Map.Entry<String, LandingPriceCalculationResponse.MarketRateDetail> entry : marketLandings.entrySet()) {
            LandingPriceCalculationResponse.MarketRateDetail detail = entry.getValue();
            if (detail.eligibleForOa() && detail.landedPriceRsPerKwh() != null) {
                if (bestMarketLandedPrice == null || detail.landedPriceRsPerKwh() < bestMarketLandedPrice) {
                    bestMarketLandedPrice = detail.landedPriceRsPerKwh();
                    bestMarketSource = entry.getKey();
                }
            }
        }

        // 7. Resolve DISCOM baseline tariff
        List<StateTariff> tariffs = dataAccessService.getStateTariffs(
                stateFormats, discom, parsedCat.category(), parsedVoltage, parsedCat.subCategory(),
                List.of(month), month);

        MatchedTariffRate matchedTariff = regulatoryChargeResolver.resolveDiscomTariff(
                tariffs, hour, month, powerFactor, isNpclHv2);

        BigDecimal fppaDecimal = dataAccessService.getFppaPercent(stateFormats, discom, yearMonth);
        double fppaPercent = fppaDecimal != null ? fppaDecimal.doubleValue() : 0.0;
        double fppaAdder = matchedTariff.rate() * (fppaPercent / 100.0);

        double dutyAdder = applyDuty ? (matchedTariff.rate() + fppaAdder) * DEFAULT_DUTY_RATE : 0.0;

        double grossDiscom = matchedTariff.rate() + fppaAdder + dutyAdder;
        double effectiveDiscom = isNpcl ? landedCostCalculator.applyNpclDiscount(grossDiscom) : grossDiscom;
        double npclDiscount = grossDiscom - effectiveDiscom;

        // 8. Generate comparative summary
        String cheapestSource;
        double spreadVsDiscom;
        double potentialSavingsPercent;

        if (bestMarketLandedPrice != null) {
            spreadVsDiscom = effectiveDiscom - bestMarketLandedPrice;
            potentialSavingsPercent = effectiveDiscom > 0 ? (spreadVsDiscom * 100.0 / effectiveDiscom) : 0.0;
            cheapestSource = spreadVsDiscom > 0 ? bestMarketSource : "DISCOM";
        } else {
            spreadVsDiscom = 0.0;
            potentialSavingsPercent = 0.0;
            cheapestSource = "DISCOM";
        }

        return new LandingPriceCalculationResponse(
                stateInput,
                discom,
                req.consumerCategory(),
                req.voltageLevel(),
                deliveryDate,
                timeblock,
                mode.name(),
                new LandingPriceCalculationResponse.LossBreakdown(istsLoss, stuLoss, wheelingLoss, lossCoefficient),
                new LandingPriceCalculationResponse.RegulatoryChargesBreakdown(
                        ctu, stu, wheeling, otherCharges, exchangeFees, gstExchange,
                        traderMargin, gstTraderMargin, css, as, totalRegCharges),
                marketLandings,
                bestMarketSource,
                bestMarketLandedPrice,
                new LandingPriceCalculationResponse.DiscomTariffBreakdown(
                        matchedTariff.rate(), matchedTariff.todName(), fppaAdder, dutyAdder,
                        npclDiscount, effectiveDiscom),
                new LandingPriceCalculationResponse.ComparisonSummary(
                        cheapestSource,
                        bestMarketLandedPrice != null ? bestMarketLandedPrice : 0.0,
                        effectiveDiscom,
                        spreadVsDiscom,
                        potentialSavingsPercent)
        );
    }

    private LandingPriceCalculationResponse.MarketRateDetail computeMarketLanding(
            Double mcpKwh,
            double totalRegCharges,
            double lossCoeff,
            boolean eligible,
            LandingPriceCalculationRequest.CalculationMode mode) {
        if (mcpKwh == null) {
            return new LandingPriceCalculationResponse.MarketRateDetail(null, null, null, eligible);
        }

        if (mode == LandingPriceCalculationRequest.CalculationMode.BUSBAR_COMPARISON) {
            return new LandingPriceCalculationResponse.MarketRateDetail(mcpKwh, mcpKwh, mcpKwh, eligible);
        }

        double safeCoeff = lossCoeff > 0 ? lossCoeff : 1.0;
        double lossAdjustedMcp = mcpKwh / safeCoeff;
        Double landedPrice = landedCostCalculator.calculateExchangeLanding(mcpKwh, totalRegCharges, safeCoeff);

        return new LandingPriceCalculationResponse.MarketRateDetail(mcpKwh, lossAdjustedMcp, landedPrice, eligible);
    }
}

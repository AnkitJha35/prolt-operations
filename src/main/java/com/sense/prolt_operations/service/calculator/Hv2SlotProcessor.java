package com.sense.prolt_operations.service.calculator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.dto.calculator.ExchangeRateSlotProjection;
import com.sense.prolt_operations.entity.IstsCharges;
import com.sense.prolt_operations.entity.StateCharges;
import com.sense.prolt_operations.entity.StateTariff;
import com.sense.prolt_operations.service.SavingsCalculatorDataAccessService;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryConstants;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryFallbackConstants;
import com.sense.prolt_operations.service.calculator.constant.UnitConstants;
import com.sense.prolt_operations.service.calculator.model.EnergyMarketType;
import com.sense.prolt_operations.service.calculator.model.EnergySource;
import com.sense.prolt_operations.service.calculator.model.Hv2CalculationContext;
import com.sense.prolt_operations.service.calculator.model.Hv2SlotRecord;
import com.sense.prolt_operations.service.calculator.model.InternalRateSlot;
import com.sense.prolt_operations.service.calculator.model.MarketOption;
import com.sense.prolt_operations.service.calculator.model.MatchedTariffRate;
import com.sense.prolt_operations.service.calculator.model.SimulationDateRange;

/**
 * Spring component responsible for 96-slot dispatch simulation, landed cost resolution,
 * and market option selection for HV-2 savings calculations.
 */
@Component
public class Hv2SlotProcessor {

    private final SavingsCalculatorDataAccessService dataAccessService;
    private final RegulatoryChargeResolver regulatoryChargeResolver;
    private final LandedCostCalculator landedCostCalculator;
    private final CalculatorProperties properties;

    @Autowired
    public Hv2SlotProcessor(SavingsCalculatorDataAccessService dataAccessService,
            RegulatoryChargeResolver regulatoryChargeResolver,
            LandedCostCalculator landedCostCalculator,
            CalculatorProperties properties) {
        this.dataAccessService = dataAccessService;
        this.regulatoryChargeResolver = regulatoryChargeResolver != null ? regulatoryChargeResolver
                : new RegulatoryChargeResolver(properties);
        this.landedCostCalculator = landedCostCalculator != null ? landedCostCalculator
                : new LandedCostCalculator();
        this.properties = properties != null ? properties : new CalculatorProperties();
    }

    public Hv2SlotProcessor(SavingsCalculatorDataAccessService dataAccessService,
            RegulatoryChargeResolver regulatoryChargeResolver,
            LandedCostCalculator landedCostCalculator) {
        this(dataAccessService, regulatoryChargeResolver, landedCostCalculator, new CalculatorProperties());
    }

    private static final String[] SLOT_TIME_STRINGS = new String[97];
    static {
        for (int slot = 1; slot <= 96; slot++) {
            int startMinutes = (slot - 1) * 15;
            SLOT_TIME_STRINGS[slot] = String.format("%02d:%02d", startMinutes / 60, startMinutes % 60);
        }
    }

    /**
     * Simulates the 96 15-minute slot dispatches for a given month, resolving exchange prices,
     * DISCOM landed costs, transmission losses, and optimal energy market sourcing.
     *
     * @param ctx               the calculation context
     * @param yearMonth         the month string (e.g. "2024-05")
     * @param monthConsumptions the consumption data map for the month
     * @return list of 96 {@link Hv2SlotRecord} entries initialized with rates and selected sources
     */
    public List<Hv2SlotRecord> processMonthSlots(Hv2CalculationContext ctx, String yearMonth,
            Map<String, Object> monthConsumptions) {
        SimulationDateRange range = CalculatorUtils.resolveSimulationDateRange(yearMonth, monthConsumptions,
                ctx.entry().getDiscom());
        List<String> stateFormats = dataAccessService.resolveStateFormats(ctx.stateCode());

        Optional<StateCharges> stateChargesOpt = dataAccessService.getStateCharges(stateFormats, ctx.entry().getDiscom(),
                ctx.parsedCategory().category(), range.startDate());
        double stuLoss = stateChargesOpt
                .map(sc -> sc.getStuLossPercent() != null ? sc.getStuLossPercent().doubleValue() : 0.0).orElse(0.0);
        double wheelingLoss = stateChargesOpt
                .map(sc -> sc.getWheelingLossPercent() != null ? sc.getWheelingLossPercent().doubleValue() : 0.0)
                .orElse(0.0);

        List<IstsCharges> istsChargesList = dataAccessService.getIstsCharges(range.startDate(), range.endDate());
        List<StateTariff> tariffs = dataAccessService.getStateTariffs(stateFormats, ctx.entry().getDiscom(),
                ctx.parsedCategory().category(), ctx.parsedSupplyVoltage(), ctx.parsedCategory().subCategory(),
                range.monthsInPlay(), range.month());

        List<ExchangeRateSlotProjection> records = dataAccessService.getExchangeRateSlots(ctx.stateCode(),
                range.startDate(), range.endDate());
        List<InternalRateSlot> slotProjections = CalculatorUtils.toInternalSlots(records, range.startDate(),
                range.endDate());

        List<Hv2SlotRecord> slotsData = new ArrayList<>(slotProjections.size());
        boolean isNpcl = "NPCL".equalsIgnoreCase(ctx.entry().getDiscom());
        boolean isNpclHv2 = isNpcl && "HV-2".equalsIgnoreCase(ctx.parsedCategory().category());

        double baseDiscom = properties != null && properties.getFallback() != null
                ? properties.getFallback().getBaseDiscomRate()
                : RegulatoryFallbackConstants.DEFAULT_BASE_DISCOM_RATE;
        double minOaKw = properties != null && properties.getRegulatory() != null
                ? properties.getRegulatory().getMinOaEligibilityKw()
                : RegulatoryConstants.MIN_OA_ELIGIBILITY_KW;
        boolean isEligibleForOa = ctx.sanctionedLoad() >= minOaKw;

        // Calculation-scoped lookups and caches (Milestone V18N)
        RegulatoryChargeResolver.TariffIndex tariffIndex = regulatoryChargeResolver.createTariffIndex(tariffs);
        RegulatoryChargeResolver.TariffRateCache tariffRateCache = regulatoryChargeResolver.createTariffRateCache();
        RegulatoryChargeResolver.IstsLossCache istsLossCache = regulatoryChargeResolver.createIstsLossCache(istsChargesList);

        LocalDate prevDate = null;
        String dateStr = null;

        for (InternalRateSlot slotProjection : slotProjections) {
            LocalDate deliveryDate = slotProjection.date();
            int slot = slotProjection.timeblock();

            if (!deliveryDate.equals(prevDate)) {
                prevDate = deliveryDate;
                dateStr = deliveryDate.toString();
            }

            double istsLoss = regulatoryChargeResolver.resolveIstsLossPercentCached(deliveryDate, istsLossCache);

            int startMinutes = (slot - 1) * 15;
            int hour = startMinutes / 60;
            int minute = startMinutes % 60;
            String timeStr = (slot >= 1 && slot <= 96) ? SLOT_TIME_STRINGS[slot] : String.format("%02d:%02d", hour, minute);

            double damLandingPrice = slotProjection.damMcp() != null ? slotProjection.damMcp() / UnitConstants.KWH_PER_MWH : 0.0;
            double rtmLandingPrice = slotProjection.rtmMcp() != null ? slotProjection.rtmMcp() / UnitConstants.KWH_PER_MWH : 0.0;
            double gdamLandingPrice = slotProjection.gdamMcp() != null ? slotProjection.gdamMcp() / UnitConstants.KWH_PER_MWH : 0.0;

            MatchedTariffRate tariffRate = regulatoryChargeResolver.resolveHv2DiscomTariff(
                    tariffIndex, tariffRateCache, hour, deliveryDate.getMonthValue(), isNpclHv2, baseDiscom);
            double discomLandingPrice = tariffRate.rate();
            String matchedTariffName = tariffRate.todName();

            if (isNpcl) {
                discomLandingPrice = landedCostCalculator.applyNpclDiscount(discomLandingPrice);
            }

            // Zero-allocation scalar market selection with exact tie-breaking order (DAM -> RTM -> GDAM)
            double comparedLowestPrice = discomLandingPrice;
            String selectedSource = EnergySource.DISCOM.getValue();

            double bestMarketPrice = -1.0;
            String bestMarketSource = null;

            if (isEligibleForOa) {
                if (damLandingPrice > 0) {
                    bestMarketPrice = damLandingPrice;
                    bestMarketSource = EnergyMarketType.DAM.getValue();
                }
                if (rtmLandingPrice > 0 && (bestMarketPrice < 0 || rtmLandingPrice < bestMarketPrice)) {
                    bestMarketPrice = rtmLandingPrice;
                    bestMarketSource = EnergyMarketType.RTM.getValue();
                }
            }
            if (gdamLandingPrice > 0 && (bestMarketPrice < 0 || gdamLandingPrice < bestMarketPrice)) {
                bestMarketPrice = gdamLandingPrice;
                bestMarketSource = EnergyMarketType.GDAM.getValue();
            }

            if (bestMarketSource != null) {
                comparedLowestPrice = bestMarketPrice;
                selectedSource = bestMarketSource;
            }

            Hv2SlotRecord slotRecord = Hv2SlotRecord.initial(dateStr, slot, timeStr,
                    matchedTariffName, damLandingPrice, rtmLandingPrice, gdamLandingPrice, discomLandingPrice,
                    comparedLowestPrice, selectedSource, istsLoss, stuLoss, wheelingLoss);

            slotsData.add(slotRecord);
        }

        return slotsData;
    }
}

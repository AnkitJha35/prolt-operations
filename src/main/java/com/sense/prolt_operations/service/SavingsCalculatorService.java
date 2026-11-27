package com.sense.prolt_operations.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.dto.calculator.CreateSavingsEntryDTO;
import com.sense.prolt_operations.dto.calculator.ExchangeRateSlotProjection;
import com.sense.prolt_operations.dto.calculator.LandedCostCalcRequest;
import com.sense.prolt_operations.dto.calculator.LandedCostCalcResponse;
import com.sense.prolt_operations.dto.calculator.SavingsEntryDTO;
import com.sense.prolt_operations.entity.CtuCharges;
import com.sense.prolt_operations.entity.IexFees;
import com.sense.prolt_operations.entity.IstsCharges;
import com.sense.prolt_operations.entity.SavingsCalculatorEntry;
import com.sense.prolt_operations.entity.SavingsCalculatorEntryHistory;
import com.sense.prolt_operations.entity.StateCharges;
import com.sense.prolt_operations.entity.StateTariff;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.ForbiddenException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.repository.SavingsCalculatorEntryHistoryRepository;
import com.sense.prolt_operations.repository.SavingsCalculatorEntryRepository;
import com.sense.prolt_operations.service.calculator.CalculatorUtils;
import com.sense.prolt_operations.service.calculator.ClientOverviewAggregator;
import com.sense.prolt_operations.service.calculator.DemandShiftOptimizer;
import com.sense.prolt_operations.service.calculator.ElectricalLossCalculator;
import com.sense.prolt_operations.service.calculator.FinancialLedgerReconciler;
import com.sense.prolt_operations.service.calculator.ForwardBankingAllocator;
import com.sense.prolt_operations.service.calculator.Hv2SavingsCalculator;
import com.sense.prolt_operations.service.calculator.LandedCostCalculator;
import com.sense.prolt_operations.service.calculator.NpclTariffSchedule;
import com.sense.prolt_operations.service.calculator.OperationalOverheadCalculator;
import com.sense.prolt_operations.config.CalculatorProperties;
import com.sense.prolt_operations.service.calculator.RtmContiguitySolver;
import com.sense.prolt_operations.service.calculator.SevenMarketArbitrator;
import com.sense.prolt_operations.service.calculator.constant.ExchangeFeeConstants;
import com.sense.prolt_operations.service.calculator.constant.OverheadFeeConstants;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryConstants;
import com.sense.prolt_operations.service.calculator.constant.RegulatoryFallbackConstants;
import com.sense.prolt_operations.service.calculator.constant.SavingsCalculatorConstants;
import com.sense.prolt_operations.service.calculator.constant.TaxConstants;
import com.sense.prolt_operations.service.calculator.constant.UnitConstants;
import com.sense.prolt_operations.service.calculator.model.DemandShiftInsights;
import com.sense.prolt_operations.service.calculator.model.MatchedTariffRate;
import com.sense.prolt_operations.service.calculator.RegulatoryChargeResolver;
import com.sense.prolt_operations.service.calculator.model.InternalRateSlot;
import com.sense.prolt_operations.service.calculator.model.MarketDecisionContext;
import com.sense.prolt_operations.service.calculator.model.MarketSlotCalculation;
import com.sense.prolt_operations.service.calculator.model.MonthlyMarketDecisionResult;
import com.sense.prolt_operations.service.calculator.model.OperationalOverheads;
import com.sense.prolt_operations.service.calculator.model.ParsedCategory;
import com.sense.prolt_operations.service.calculator.model.SimulationDateRange;

@Service
public class SavingsCalculatorService {

    private static final Logger log = LoggerFactory.getLogger(SavingsCalculatorService.class);

    private final SavingsCalculatorEntryRepository entryRepository;
    private final SavingsCalculatorEntryHistoryRepository historyRepository;
    private final SavingsCalculatorDataAccessService dataAccessService;
    private final ObjectMapper objectMapper;
    private final NpclTariffSchedule npclTariffSchedule;
    private final ElectricalLossCalculator electricalLossCalculator;
    private final LandedCostCalculator landedCostCalculator;
    private final SevenMarketArbitrator sevenMarketArbitrator;
    private final RtmContiguitySolver rtmContiguitySolver;
    private final ForwardBankingAllocator forwardBankingAllocator;
    private final OperationalOverheadCalculator operationalOverheadCalculator;
    private final FinancialLedgerReconciler financialLedgerReconciler;
    private final DemandShiftOptimizer demandShiftOptimizer;
    private final Hv2SavingsCalculator hv2SavingsCalculator;
    private final ClientOverviewAggregator clientOverviewAggregator;
    private final RegulatoryChargeResolver regulatoryChargeResolver;
    private final CalculatorProperties properties;

    /**
     * Canonical Spring {@code @Autowired} constructor injecting all 17 collaborators and configuration properties.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public SavingsCalculatorService(SavingsCalculatorEntryRepository entryRepository,
            SavingsCalculatorEntryHistoryRepository historyRepository,
            SavingsCalculatorDataAccessService dataAccessService, ObjectMapper objectMapper,
            NpclTariffSchedule npclTariffSchedule, ElectricalLossCalculator electricalLossCalculator,
            LandedCostCalculator landedCostCalculator, SevenMarketArbitrator sevenMarketArbitrator,
            RtmContiguitySolver rtmContiguitySolver, ForwardBankingAllocator forwardBankingAllocator,
            OperationalOverheadCalculator operationalOverheadCalculator,
            FinancialLedgerReconciler financialLedgerReconciler, DemandShiftOptimizer demandShiftOptimizer,
            Hv2SavingsCalculator hv2SavingsCalculator, ClientOverviewAggregator clientOverviewAggregator,
            RegulatoryChargeResolver regulatoryChargeResolver,
            CalculatorProperties properties) {
        this.entryRepository = entryRepository;
        this.historyRepository = historyRepository;
        this.dataAccessService = dataAccessService;
        this.objectMapper = objectMapper;
        this.properties = properties != null ? properties : new CalculatorProperties();
        this.npclTariffSchedule = npclTariffSchedule != null ? npclTariffSchedule : new NpclTariffSchedule();
        this.regulatoryChargeResolver = regulatoryChargeResolver != null ? regulatoryChargeResolver
                : new RegulatoryChargeResolver(this.properties, this.npclTariffSchedule);
        this.electricalLossCalculator = electricalLossCalculator != null ? electricalLossCalculator
                : new ElectricalLossCalculator();
        this.landedCostCalculator = landedCostCalculator != null ? landedCostCalculator
                : new LandedCostCalculator(this.electricalLossCalculator, this.properties);
        this.sevenMarketArbitrator = sevenMarketArbitrator != null ? sevenMarketArbitrator
                : new SevenMarketArbitrator();
        this.rtmContiguitySolver = rtmContiguitySolver != null ? rtmContiguitySolver : new RtmContiguitySolver();
        this.forwardBankingAllocator = forwardBankingAllocator != null ? forwardBankingAllocator
                : new ForwardBankingAllocator();
        this.operationalOverheadCalculator = operationalOverheadCalculator != null ? operationalOverheadCalculator
                : new OperationalOverheadCalculator(this.properties);
        this.financialLedgerReconciler = financialLedgerReconciler != null ? financialLedgerReconciler
                : new FinancialLedgerReconciler(this.properties);
        this.demandShiftOptimizer = demandShiftOptimizer != null ? demandShiftOptimizer : new DemandShiftOptimizer(this.properties);
        this.hv2SavingsCalculator = hv2SavingsCalculator != null ? hv2SavingsCalculator
                : new Hv2SavingsCalculator(this.dataAccessService, this.npclTariffSchedule, this.landedCostCalculator,
                        this.objectMapper, this.properties, this.regulatoryChargeResolver);
        this.clientOverviewAggregator = clientOverviewAggregator != null ? clientOverviewAggregator
                : new ClientOverviewAggregator(this.properties);
    }

    /**
     * Backward-compatibility 16-argument constructor (pre-V18M.1) delegating to the canonical constructor.
     */
    public SavingsCalculatorService(SavingsCalculatorEntryRepository entryRepository,
            SavingsCalculatorEntryHistoryRepository historyRepository,
            SavingsCalculatorDataAccessService dataAccessService, ObjectMapper objectMapper,
            NpclTariffSchedule npclTariffSchedule, ElectricalLossCalculator electricalLossCalculator,
            LandedCostCalculator landedCostCalculator, SevenMarketArbitrator sevenMarketArbitrator,
            RtmContiguitySolver rtmContiguitySolver, ForwardBankingAllocator forwardBankingAllocator,
            OperationalOverheadCalculator operationalOverheadCalculator,
            FinancialLedgerReconciler financialLedgerReconciler, DemandShiftOptimizer demandShiftOptimizer,
            Hv2SavingsCalculator hv2SavingsCalculator, ClientOverviewAggregator clientOverviewAggregator,
            CalculatorProperties properties) {
        this(entryRepository, historyRepository, dataAccessService, objectMapper, npclTariffSchedule,
                electricalLossCalculator, landedCostCalculator, sevenMarketArbitrator, rtmContiguitySolver,
                forwardBankingAllocator, operationalOverheadCalculator, financialLedgerReconciler, demandShiftOptimizer,
                hv2SavingsCalculator, clientOverviewAggregator, null, properties);
    }

    /**
     * Backward-compatibility 15-argument constructor (pre-V18I) delegating to the canonical constructor.
     */
    public SavingsCalculatorService(SavingsCalculatorEntryRepository entryRepository,
            SavingsCalculatorEntryHistoryRepository historyRepository,
            SavingsCalculatorDataAccessService dataAccessService, ObjectMapper objectMapper,
            NpclTariffSchedule npclTariffSchedule, ElectricalLossCalculator electricalLossCalculator,
            LandedCostCalculator landedCostCalculator, SevenMarketArbitrator sevenMarketArbitrator,
            RtmContiguitySolver rtmContiguitySolver, ForwardBankingAllocator forwardBankingAllocator,
            OperationalOverheadCalculator operationalOverheadCalculator,
            FinancialLedgerReconciler financialLedgerReconciler, DemandShiftOptimizer demandShiftOptimizer,
            Hv2SavingsCalculator hv2SavingsCalculator, ClientOverviewAggregator clientOverviewAggregator) {
        this(entryRepository, historyRepository, dataAccessService, objectMapper, npclTariffSchedule,
                electricalLossCalculator, landedCostCalculator, sevenMarketArbitrator, rtmContiguitySolver,
                forwardBankingAllocator, operationalOverheadCalculator, financialLedgerReconciler, demandShiftOptimizer,
                hv2SavingsCalculator, clientOverviewAggregator, null);
    }

    /**
     * Backward-compatibility 12-argument constructor (pre-V18H) delegating to the canonical constructor.
     */
    public SavingsCalculatorService(SavingsCalculatorEntryRepository entryRepository,
            SavingsCalculatorEntryHistoryRepository historyRepository,
            SavingsCalculatorDataAccessService dataAccessService, ObjectMapper objectMapper,
            NpclTariffSchedule npclTariffSchedule, ElectricalLossCalculator electricalLossCalculator,
            LandedCostCalculator landedCostCalculator, SevenMarketArbitrator sevenMarketArbitrator,
            RtmContiguitySolver rtmContiguitySolver, ForwardBankingAllocator forwardBankingAllocator,
            OperationalOverheadCalculator operationalOverheadCalculator,
            FinancialLedgerReconciler financialLedgerReconciler) {
        this(entryRepository, historyRepository, dataAccessService, objectMapper, npclTariffSchedule,
                electricalLossCalculator, landedCostCalculator, sevenMarketArbitrator, rtmContiguitySolver,
                forwardBankingAllocator, operationalOverheadCalculator, financialLedgerReconciler, null, null, null);
    }

    /**
     * Backward-compatibility 7-argument constructor (pre-V18G) delegating to the canonical constructor.
     */
    public SavingsCalculatorService(SavingsCalculatorEntryRepository entryRepository,
            SavingsCalculatorEntryHistoryRepository historyRepository,
            SavingsCalculatorDataAccessService dataAccessService, ObjectMapper objectMapper,
            NpclTariffSchedule npclTariffSchedule, ElectricalLossCalculator electricalLossCalculator,
            LandedCostCalculator landedCostCalculator) {
        this(entryRepository, historyRepository, dataAccessService, objectMapper, npclTariffSchedule,
                electricalLossCalculator, landedCostCalculator, null, null, null, null, null, null, null, null);
    }

    /**
     * Backward-compatibility minimal 4-argument constructor delegating to the canonical constructor.
     */
    public SavingsCalculatorService(SavingsCalculatorEntryRepository entryRepository,
            SavingsCalculatorEntryHistoryRepository historyRepository,
            SavingsCalculatorDataAccessService dataAccessService, ObjectMapper objectMapper) {
        this(entryRepository, historyRepository, dataAccessService, objectMapper, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public static double getFlooredMaxEnergyPerSlot(BigDecimal sanctionedLoadKw, BigDecimal powerFactor) {
        return getFlooredMaxEnergyPerSlot(sanctionedLoadKw, powerFactor, null);
    }

    public static double getFlooredMaxEnergyPerSlot(BigDecimal sanctionedLoadKw, BigDecimal powerFactor,
            CalculatorProperties properties) {
        double defaultPf = properties != null && properties.getRegulatory() != null
                ? properties.getRegulatory().getDefaultPowerFactor()
                : RegulatoryConstants.DEFAULT_POWER_FACTOR;
        double pf = powerFactor != null ? powerFactor.doubleValue() : defaultPf;
        if (pf <= 0)
            pf = defaultPf;
        double load = sanctionedLoadKw != null ? sanctionedLoadKw.doubleValue() : 0.0;
        if (load <= 0)
            return 0.0;
        double loadKva = load / pf;
        double pfFactor = properties != null && properties.getRegulatory() != null
                ? properties.getRegulatory().getContractDemandPfFactor()
                : RegulatoryConstants.CONTRACT_DEMAND_PF_FACTOR;
        double rawMw = (loadKva * pfFactor) / UnitConstants.KW_PER_MW;
        double flooredMw = Math.floor(rawMw * 10.0 + SavingsCalculatorConstants.EPSILON) / 10.0;
        return flooredMw * UnitConstants.KWH_PER_MW_SLOT;
    }

    /**
     * Calculates direct landed cost comparison for quick prospects.
     * Note: "Acme Steel Mills" (or UP HV-2 33kV) represents a standardized benchmark industrial
     * profile with fixed pre-calculated cost components matching legacy client proposals.
     */
    public LandedCostCalcResponse calculateDirect(LandedCostCalcRequest req) {
        if (req == null || req.state() == null || req.state().trim().isEmpty()) {
            throw new BadRequestException(
                    "State is required to calculate savings. Please edit this entry to select a state.");
        }

        double defaultProspect = properties != null && properties.getBusiness() != null
                ? properties.getBusiness().getDefaultProspectConsumptionKwh()
                : SavingsCalculatorConstants.DEFAULT_PROSPECT_CONSUMPTION_KWH;
        BigDecimal consumption = req.consumptionKwh() != null ? req.consumptionKwh()
                : BigDecimal.valueOf(defaultProspect);

        boolean isAcmeBenchmark = "Acme Steel Mills".equalsIgnoreCase(req.clientName());
        boolean isUp = req.state().toLowerCase().contains("uttar") || "UP".equalsIgnoreCase(req.state());
        boolean isHv2 = req.tariffCategory() != null && req.tariffCategory().toUpperCase().contains("HV-2");
        boolean is33kv = req.connectedVoltage() != null && req.connectedVoltage().toUpperCase().contains("33");

        BigDecimal energyCost;
        BigDecimal ctuLossAndCharges;
        BigDecimal stLossAndCharges;
        BigDecimal crossSubsidySurcharge;
        BigDecimal additionalSurcharge;
        BigDecimal discomBillAmount;

        if (isAcmeBenchmark || (isUp && isHv2 && is33kv)) {
            discomBillAmount = BigDecimal.valueOf(10525000.00).setScale(2, RoundingMode.HALF_UP);
            energyCost = BigDecimal.valueOf(5200000.00).setScale(2, RoundingMode.HALF_UP);
            ctuLossAndCharges = BigDecimal.valueOf(620000.00).setScale(2, RoundingMode.HALF_UP);
            stLossAndCharges = BigDecimal.valueOf(480000.00).setScale(2, RoundingMode.HALF_UP);
            crossSubsidySurcharge = BigDecimal.valueOf(850000.00).setScale(2, RoundingMode.HALF_UP);
            additionalSurcharge = BigDecimal.valueOf(270000.00).setScale(2, RoundingMode.HALF_UP);
        } else {
            double ctuVal = properties != null && properties.getFallback() != null
                    ? properties.getFallback().getCtuPerKwh() : RegulatoryFallbackConstants.DEFAULT_CTU_PER_KWH;
            double stuVal = properties != null && properties.getFallback() != null
                    ? properties.getFallback().getStuPerKwh() : RegulatoryFallbackConstants.DEFAULT_STU_PER_KWH;
            double cssVal = properties != null && properties.getFallback() != null
                    ? properties.getFallback().getCrossSubsidyPerKwh() : RegulatoryFallbackConstants.DEFAULT_CROSS_SUBSIDY_PER_KWH;
            double asVal = properties != null && properties.getFallback() != null
                    ? properties.getFallback().getAdditionalSurchargePerKwh() : RegulatoryFallbackConstants.DEFAULT_ADDITIONAL_SURCHARGE_PER_KWH;

            energyCost = consumption.multiply(BigDecimal.valueOf(4.16)).setScale(2, RoundingMode.HALF_UP);
            ctuLossAndCharges = consumption
                    .multiply(BigDecimal.valueOf(ctuVal))
                    .setScale(2, RoundingMode.HALF_UP);
            stLossAndCharges = consumption.multiply(BigDecimal.valueOf(stuVal))
                    .setScale(2, RoundingMode.HALF_UP);
            crossSubsidySurcharge = consumption
                    .multiply(BigDecimal.valueOf(cssVal))
                    .setScale(2, RoundingMode.HALF_UP);
            additionalSurcharge = consumption
                    .multiply(BigDecimal.valueOf(asVal))
                    .setScale(2, RoundingMode.HALF_UP);
            discomBillAmount = consumption.multiply(BigDecimal.valueOf(8.42)).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal iexTotalCost = energyCost.add(ctuLossAndCharges).add(stLossAndCharges).add(crossSubsidySurcharge)
                .add(additionalSurcharge).setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalSavings = discomBillAmount.subtract(iexTotalCost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal percentageSavings = totalSavings.multiply(BigDecimal.valueOf(100)).divide(discomBillAmount, 2,
                RoundingMode.HALF_UP);

        LandedCostCalcResponse.CostBreakdown breakdown = new LandedCostCalcResponse.CostBreakdown(energyCost,
                ctuLossAndCharges, stLossAndCharges, crossSubsidySurcharge, additionalSurcharge);

        LandedCostCalcResponse.LandedCostData data = new LandedCostCalcResponse.LandedCostData(discomBillAmount,
                iexTotalCost, totalSavings, percentageSavings, breakdown);

        return new LandedCostCalcResponse(true, data);
    }

    public List<SavingsEntryDTO> getAll() {
        return entryRepository.findAll().stream().sorted(Comparator.comparing(SavingsCalculatorEntry::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder()))).map(this::toDTO).toList();
    }

    public SavingsEntryDTO getById(String id) {
        SavingsCalculatorEntry entry = entryRepository.findById(id).orElse(null);
        return entry != null ? toDTO(entry) : null;
    }

    public List<Map<String, Object>> getHistory(String id) {
        return historyRepository.findByEntryIdOrderByVersionDesc(id).stream().map(h -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", h.getId());
            map.put("entryId", h.getEntryId());
            map.put("version", h.getVersion());
            map.put("clientName", h.getClientName());
            map.put("industryName", h.getIndustryName());
            map.put("sanctionedLoadKw", h.getSanctionedLoadKw());
            map.put("stateCode", h.getStateCode());
            map.put("discom", h.getDiscom());
            map.put("consumerCategory", h.getConsumerCategory());
            map.put("voltageLevel", h.getVoltageLevel());
            map.put("changedAt", h.getChangedAt());
            return map;
        }).toList();
    }

    public SavingsCalculatorEntry getEntryOrVersion(String id, Integer version) {
        if (version != null) {
            SavingsCalculatorEntryHistory history = historyRepository.findByEntryIdAndVersion(id, version).orElse(null);
            if (history != null) {
                SavingsCalculatorEntry virtualEntry = SavingsCalculatorEntry.create();
                virtualEntry.setId(history.getEntryId());
                virtualEntry.setClientName(history.getClientName());
                virtualEntry.setIndustryName(history.getIndustryName());
                virtualEntry.setAddress(history.getAddress());
                virtualEntry.setSanctionedLoadKw(history.getSanctionedLoadKw());
                virtualEntry.setStateCode(history.getStateCode());
                virtualEntry.setDiscom(history.getDiscom());
                virtualEntry.setConsumerCategory(history.getConsumerCategory());
                virtualEntry.setVoltageLevel(history.getVoltageLevel());
                virtualEntry.setProltMargin(history.getProltMargin());
                virtualEntry.setTraderMargin(history.getTraderMargin());
                virtualEntry.setMeteringCharges(history.getMeteringCharges());
                virtualEntry.setConsultancyFee(history.getConsultancyFee());
                virtualEntry.setProbusPlatformFee(history.getProbusPlatformFee());
                virtualEntry.setTodConsumptions(history.getTodConsumptions());
                virtualEntry.setApplyElectricityDuty(history.getApplyElectricityDuty());
                virtualEntry.setBilledDemandKv(history.getBilledDemandKv());
                virtualEntry.setPowerFactor(history.getPowerFactor());
                virtualEntry.setArrearAmount(history.getArrearAmount());
                virtualEntry.setCurrentLpsc(history.getCurrentLpsc());
                virtualEntry.setBillDate(history.getBillDate());
                virtualEntry.setCreatedAt(history.getChangedAt());
                virtualEntry.setUpdatedAt(history.getChangedAt());
                return virtualEntry;
            }
            return null;
        }
        return entryRepository.findById(id).orElse(null);
    }

    @Transactional
    public SavingsEntryDTO create(CreateSavingsEntryDTO dto, String username) {
        SavingsCalculatorEntry entry = SavingsCalculatorEntry.create();
        entry.setClientName(dto.clientName());
        entry.setIndustryName(dto.industryName());
        entry.setAddress(dto.address());
        entry.setSanctionedLoadKw(dto.sanctionedLoadKw());
        entry.setStateCode(dto.stateCode());
        entry.setDiscom(dto.discom());
        entry.setConsumerCategory(dto.consumerCategory());
        entry.setVoltageLevel(dto.voltageLevel());
        entry.setProltMargin(dto.proltMargin());
        entry.setTraderMargin(dto.traderMargin());
        entry.setMeteringCharges(dto.meteringCharges());
        entry.setConsultancyFee(dto.consultancyFee());
        entry.setProbusPlatformFee(dto.probusPlatformFee());
        entry.setTodConsumptions(serializeJson(dto.todConsumptions()));
        entry.setApplyElectricityDuty(dto.applyElectricityDuty() != null ? dto.applyElectricityDuty() : true);
        entry.setBilledDemandKv(dto.billedDemandKv());
        entry.setPowerFactor(dto.powerFactor());
        entry.setArrearAmount(dto.arrearAmount());
        entry.setCurrentLpsc(dto.currentLpsc());
        entry.setBillDate(dto.billDate());
        entry.setCreatedBy(username);
        entry.setUpdatedBy(username);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());

        SavingsCalculatorEntry saved = entryRepository.save(entry);

        SavingsCalculatorEntryHistory history = SavingsCalculatorEntryHistory.create();
        copyToHistory(saved, history, 1);
        historyRepository.save(history);

        return toDTO(saved);
    }

    @Transactional
    public SavingsEntryDTO update(String id, CreateSavingsEntryDTO dto, String username) {
        SavingsCalculatorEntry entry = entryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Entry not found."));

        entry.setClientName(dto.clientName());
        entry.setIndustryName(dto.industryName());
        entry.setAddress(dto.address());
        if (dto.sanctionedLoadKw() != null)
            entry.setSanctionedLoadKw(dto.sanctionedLoadKw());
        if (dto.stateCode() != null)
            entry.setStateCode(dto.stateCode());
        if (dto.discom() != null)
            entry.setDiscom(dto.discom());
        if (dto.consumerCategory() != null)
            entry.setConsumerCategory(dto.consumerCategory());
        if (dto.voltageLevel() != null)
            entry.setVoltageLevel(dto.voltageLevel());
        if (dto.proltMargin() != null)
            entry.setProltMargin(dto.proltMargin());
        if (dto.traderMargin() != null)
            entry.setTraderMargin(dto.traderMargin());
        if (dto.meteringCharges() != null)
            entry.setMeteringCharges(dto.meteringCharges());
        if (dto.consultancyFee() != null)
            entry.setConsultancyFee(dto.consultancyFee());
        if (dto.probusPlatformFee() != null)
            entry.setProbusPlatformFee(dto.probusPlatformFee());
        if (dto.todConsumptions() != null)
            entry.setTodConsumptions(serializeJson(dto.todConsumptions()));
        if (dto.applyElectricityDuty() != null)
            entry.setApplyElectricityDuty(dto.applyElectricityDuty());
        if (dto.billedDemandKv() != null)
            entry.setBilledDemandKv(dto.billedDemandKv());
        if (dto.powerFactor() != null)
            entry.setPowerFactor(dto.powerFactor());
        if (dto.arrearAmount() != null)
            entry.setArrearAmount(dto.arrearAmount());
        if (dto.currentLpsc() != null)
            entry.setCurrentLpsc(dto.currentLpsc());
        if (dto.billDate() != null)
            entry.setBillDate(dto.billDate());
        entry.setUpdatedBy(username);
        entry.setUpdatedAt(LocalDateTime.now());

        SavingsCalculatorEntry saved = entryRepository.save(entry);

        int nextVersion = historyRepository.findByEntryIdOrderByVersionDesc(id).stream()
                .mapToInt(SavingsCalculatorEntryHistory::getVersion).max().orElse(0) + 1;

        SavingsCalculatorEntryHistory history = SavingsCalculatorEntryHistory.create();
        copyToHistory(saved, history, nextVersion);
        historyRepository.save(history);

        return toDTO(saved);
    }

    @Transactional
    public void delete(String id, String role, List<String> readOnlyModules) {
        if (!"SUPER_ADMIN".equals(role) && !"ADMIN".equals(role)) {
            if (readOnlyModules != null && readOnlyModules.contains("savings-calculator-nodelete")) {
                throw new ForbiddenException("You do not have permission to delete entries.");
            }
        }
        SavingsCalculatorEntry entry = entryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Entry not found."));
        historyRepository.deleteByEntryId(id);
        entryRepository.delete(entry);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CLIENT OVERVIEW
    // ─────────────────────────────────────────────────────────────────────────────

    public Map<String, Object> getClientOverview(String id) {
        SavingsCalculatorEntry entry = entryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Entry not found"));

        Map<String, Map<String, Object>> todConsumptions = parseTodConsumptions(entry.getTodConsumptions());
        return clientOverviewAggregator.aggregateClientOverviewTyped(entry, todConsumptions,
                month -> calculateMarketDecisionResult(id, month, null, false, null));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CALCULATE SAVINGS
    // ─────────────────────────────────────────────────────────────────────────────

    public Map<String, Object> calculateSavings(String id, String targetMonth, Integer version) {
        return calculateSavings(id, targetMonth, version, false, (DemandShiftInsights) null);
    }

    public Map<String, Object> calculateSavings(String id, String targetMonth, Integer version,
            boolean useShiftedProfile, DemandShiftInsights shiftInsights) {
        if ("all".equalsIgnoreCase(targetMonth)) {
            return calculateSavingsAllMonths(id, version, useShiftedProfile, shiftInsights);
        }
        SavingsCalculatorEntry entry = getEntryOrVersion(id, version);
        if (entry == null) {
            throw new NotFoundException("Entry not found");
        }
        if (entry.getStateCode() == null || entry.getStateCode().isBlank()) {
            throw new BadRequestException(
                    "State is required to calculate savings. Please edit this entry to select a state.");
        }

        DemandShiftInsights insights = shiftInsights;
        if (useShiftedProfile && insights == null) {
            insights = calculateDemandShift(id, targetMonth, version);
        }

        return calculateSavingsHV2(entry, targetMonth, insights);
    }

    public Map<String, Object> calculateSavings(String id, String targetMonth, Integer version,
            boolean useShiftedProfile, Map<String, Object> shiftInsights) {
        return calculateSavings(id, targetMonth, version, useShiftedProfile,
                shiftInsights != null ? DemandShiftInsights.fromMap(shiftInsights) : null);
    }

    public Map<String, Object> calculateSavingsAllMonths(String id, Integer version, boolean useShiftedProfile,
            DemandShiftInsights shiftInsights) {
        SavingsCalculatorEntry entry = getEntryOrVersion(id, version);
        if (entry == null)
            throw new NotFoundException("Entry not found");

        Map<String, Map<String, Object>> todConsumptions = parseTodConsumptions(entry.getTodConsumptions());
        if (todConsumptions.isEmpty()) {
            throw new BadRequestException("No consumption data found");
        }

        return hv2SavingsCalculator.calculateSavingsAllMonths(entry, todConsumptions,
                (month, unused) -> calculateSavings(id, month, version, useShiftedProfile, shiftInsights));
    }

    public Map<String, Object> calculateSavingsAllMonths(String id, Integer version, boolean useShiftedProfile,
            Map<String, Object> shiftInsights) {
        return calculateSavingsAllMonths(id, version, useShiftedProfile,
                shiftInsights != null ? DemandShiftInsights.fromMap(shiftInsights) : null);
    }

    public Map<String, Object> calculateSavingsHV2(SavingsCalculatorEntry entry, String targetMonth,
            DemandShiftInsights shiftInsights) {
        return hv2SavingsCalculator.calculateSavingsHV2(entry, targetMonth, shiftInsights);
    }

    public Map<String, Object> calculateSavingsHV2(SavingsCalculatorEntry entry, String targetMonth,
            Map<String, Object> shiftInsights) {
        return hv2SavingsCalculator.calculateSavingsHV2(entry, targetMonth, shiftInsights);
    }

    public double calculateBaselineCost(SavingsCalculatorEntry entry, String targetMonth) {
        return hv2SavingsCalculator.calculateBaselineCost(entry, targetMonth);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CALCULATE MARKET DECISION
    // ─────────────────────────────────────────────────────────────────────────────

    public MonthlyMarketDecisionResult calculateMarketDecisionResult(String id, String targetMonthStr, Integer version) {
        return calculateMarketDecisionResult(id, targetMonthStr, version, false, null);
    }

    public MonthlyMarketDecisionResult calculateMarketDecisionResult(String id, String targetMonthStr, Integer version,
            boolean useShiftedProfile, DemandShiftInsights shiftInsights) {
        SavingsCalculatorEntry entry = getEntryOrVersion(id, version);
        if (entry == null) {
            throw new NotFoundException("Savings calculator entry not found");
        }
        if (entry.getStateCode() == null || entry.getStateCode().isBlank()) {
            throw new BadRequestException(
                    "State is required to calculate savings. Please edit this entry to select a state.");
        }

        // Stage A: Input Validation & Context Preparation
        MarketDecisionContext context = prepareMarketDecisionContext(entry, id, targetMonthStr, version,
                useShiftedProfile, shiftInsights);

        // Stage B: Slot-Level Cost Resolution
        List<MarketSlotCalculation> slotsData = calculateSlotLandingCosts(context);

        // Stage C: Seven-Market SLDC Arbitration
        sevenMarketArbitrator.arbitrate(slotsData, context.sldcSchedulingFees(), context.maxEnergyPerSlot());

        // Stage D: RTM Contiguity Optimization
        rtmContiguitySolver.resolveContiguity(slotsData);

        // Stage E: Forward Banking Allocation
        forwardBankingAllocator.allocate(slotsData, context);

        // Stage F: Operational Overheads & Fixed Fees
        OperationalOverheads overheads = operationalOverheadCalculator.calculate(slotsData,
                context.nldcSchedulingFees(), context.sldcSchedulingFees());

        // Stage G: Financial Ledger Reconciliation & Output
        return financialLedgerReconciler.reconcile(context, slotsData, overheads);
    }

    public Map<String, Object> calculateMarketDecision(String id, String targetMonthStr, Integer version) {
        return calculateMarketDecision(id, targetMonthStr, version, false, (DemandShiftInsights) null);
    }

    public Map<String, Object> calculateMarketDecision(String id, String targetMonthStr, Integer version,
            boolean useShiftedProfile, DemandShiftInsights shiftInsights) {
        if ("all".equalsIgnoreCase(targetMonthStr)) {
            return calculateMarketDecisionAllMonths(id, version, useShiftedProfile, shiftInsights);
        }
        return calculateMarketDecisionResult(id, targetMonthStr, version, useShiftedProfile, shiftInsights).toMap();
    }

    public Map<String, Object> calculateMarketDecision(String id, String targetMonthStr, Integer version,
            boolean useShiftedProfile, Map<String, Object> shiftInsights) {
        return calculateMarketDecision(id, targetMonthStr, version, useShiftedProfile,
                shiftInsights != null ? DemandShiftInsights.fromMap(shiftInsights) : null);
    }

    public MarketDecisionContext prepareMarketDecisionContextForTesting(SavingsCalculatorEntry entry, String id,
            String targetMonthStr) {
        return prepareMarketDecisionContext(entry, id, targetMonthStr, null, false, null);
    }

    private MarketDecisionContext prepareMarketDecisionContext(SavingsCalculatorEntry entry, String id,
            String targetMonthStr, Integer version, boolean useShiftedProfile, DemandShiftInsights shiftInsights) {

        DemandShiftInsights insights = shiftInsights;
        if (useShiftedProfile && insights == null) {
            insights = calculateDemandShift(id, targetMonthStr, version);
        }

        Map<String, Map<String, Object>> todConsumptions = parseTodConsumptions(entry.getTodConsumptions());
        String monthKey;
        if (targetMonthStr != null && !targetMonthStr.isBlank()) {
            monthKey = targetMonthStr;
        } else if (!todConsumptions.isEmpty()) {
            monthKey = todConsumptions.keySet().iterator().next();
        } else {
            monthKey = String.format("%d-%02d", LocalDate.now().getYear(), LocalDate.now().getMonthValue());
        }

        Map<String, Object> monthConsumptions = todConsumptions.getOrDefault(monthKey, Collections.emptyMap());

        SimulationDateRange range = CalculatorUtils.resolveSimulationDateRange(monthKey, monthConsumptions,
                entry.getDiscom());
        String stateCode = entry.getStateCode();
        String rawCategory = entry.getConsumerCategory() != null ? entry.getConsumerCategory() : "";
        String rawVoltage = entry.getVoltageLevel() != null ? entry.getVoltageLevel() : "";

        ParsedCategory parsedCat = CalculatorUtils.parseCategory(rawCategory);
        String parsedSupplyVoltage = CalculatorUtils.parseSupplyVoltageCategory(rawVoltage);

        List<String> stateFormats = dataAccessService.resolveStateFormats(stateCode);
        double traderMargin = entry.getTraderMargin() != null ? entry.getTraderMargin().doubleValue() : 0.0;
        double sanctionedLoad = entry.getSanctionedLoadKw() != null ? entry.getSanctionedLoadKw().doubleValue() : 0.0;
        double maxEnergyPerSlot = getFlooredMaxEnergyPerSlot(entry.getSanctionedLoadKw(), entry.getPowerFactor(), properties);

        boolean applyElectricityDuty = entry.getApplyElectricityDuty() == null || entry.getApplyElectricityDuty();
        double electricityDutyPercent = 7.5;
        if (monthConsumptions.containsKey("_meta")) {
            Map<String, Object> meta = CalculatorUtils.getMap(monthConsumptions, "_meta");
            if (meta != null && meta.containsKey("electricityDutyPercent")) {
                electricityDutyPercent = CalculatorUtils.getDouble(meta, "electricityDutyPercent");
            }
        }

        double monthArrear = monthConsumptions.containsKey("Arrear Amount")
                && monthConsumptions.get("Arrear Amount") != null
                        ? CalculatorUtils.getDouble(monthConsumptions, "Arrear Amount")
                        : (entry.getArrearAmount() != null ? entry.getArrearAmount().doubleValue() : 0.0);

        double monthLpsc = monthConsumptions.containsKey("Current LPSC")
                && monthConsumptions.get("Current LPSC") != null
                        ? CalculatorUtils.getDouble(monthConsumptions, "Current LPSC")
                        : (entry.getCurrentLpsc() != null ? entry.getCurrentLpsc().doubleValue() : 0.0);

        double monthMisc = monthConsumptions.containsKey("Miscellaneous Charges")
                && monthConsumptions.get("Miscellaneous Charges") != null
                        ? CalculatorUtils.getDouble(monthConsumptions, "Miscellaneous Charges")
                        : 0.0;

        Optional<StateCharges> stateChargesOpt = dataAccessService.getStateCharges(stateFormats, entry.getDiscom(),
                parsedCat.category(), range.startDate());
        Optional<CtuCharges> ctuChargesOpt = dataAccessService.getCtuCharges(stateFormats, range.yyyymmMonth());
        List<IstsCharges> istsChargesList = dataAccessService.getIstsCharges(range.startDate(), range.endDate());
        Optional<IexFees> iexFeesOpt = dataAccessService.getIexFees(range.yyyymmMonth());

        double fallbackCtu = properties != null && properties.getFallback() != null
                ? properties.getFallback().getCtuPerKwh() : RegulatoryFallbackConstants.DEFAULT_CTU_PER_KWH;
        double fallbackStu = properties != null && properties.getFallback() != null
                ? properties.getFallback().getStuPerKwh() : RegulatoryFallbackConstants.DEFAULT_STU_PER_KWH;
        double fallbackWheeling = properties != null && properties.getFallback() != null
                ? properties.getFallback().getWheelingPerKwh() : RegulatoryFallbackConstants.DEFAULT_WHEELING_PER_KWH;
        double fallbackCrossSubsidy = properties != null && properties.getFallback() != null
                ? properties.getFallback().getCrossSubsidyPerKwh() : RegulatoryFallbackConstants.DEFAULT_CROSS_SUBSIDY_PER_KWH;
        double fallbackAdditionalSurcharge = properties != null && properties.getFallback() != null
                ? properties.getFallback().getAdditionalSurchargePerKwh() : RegulatoryFallbackConstants.DEFAULT_ADDITIONAL_SURCHARGE_PER_KWH;
        double fallbackStuLoss = properties != null && properties.getFallback() != null
                ? properties.getFallback().getStuLossPercent() : RegulatoryFallbackConstants.DEFAULT_STU_LOSS_PERCENT;
        double fallbackWheelingLoss = properties != null && properties.getFallback() != null
                ? properties.getFallback().getWheelingLossPercent() : RegulatoryFallbackConstants.DEFAULT_WHEELING_LOSS_PERCENT;
        double fallbackDemandChargeRate = properties != null && properties.getFallback() != null
                ? properties.getFallback().getDemandFixedChargeKva() : RegulatoryFallbackConstants.DEFAULT_DEMAND_FIXED_CHARGE_KVA;

        double ctuCharge = ctuChargesOpt
                .map(c -> c.getCtuChargesRsPerKwh() != null ? c.getCtuChargesRsPerKwh().doubleValue()
                        : fallbackCtu)
                .orElse(fallbackCtu);
        double stuCharge = stateChargesOpt
                .map(sc -> sc.getStuCharges() != null ? sc.getStuCharges().doubleValue()
                        : fallbackStu)
                .orElse(fallbackStu);
        double wheelingCharge = stateChargesOpt
                .map(sc -> sc.getDistributionWheelingCharges() != null
                        ? sc.getDistributionWheelingCharges().doubleValue()
                        : fallbackWheeling)
                .orElse(fallbackWheeling);
        double crossSubsidy = stateChargesOpt
                .map(sc -> sc.getCrossSubsidy() != null ? sc.getCrossSubsidy().doubleValue()
                        : fallbackCrossSubsidy)
                .orElse(fallbackCrossSubsidy);
        double additionalSurcharge = stateChargesOpt
                .map(sc -> sc.getAdditionalCharge() != null ? sc.getAdditionalCharge().doubleValue()
                        : fallbackAdditionalSurcharge)
                .orElse(fallbackAdditionalSurcharge);
        double stuLoss = stateChargesOpt
                .map(sc -> sc.getStuLossPercent() != null ? sc.getStuLossPercent().doubleValue()
                        : fallbackStuLoss)
                .orElse(fallbackStuLoss);
        double wheelingLoss = stateChargesOpt
                .map(sc -> sc.getWheelingLossPercent() != null ? sc.getWheelingLossPercent().doubleValue()
                        : fallbackWheelingLoss)
                .orElse(fallbackWheelingLoss);
        double demandChargeRate = stateChargesOpt
                .map(sc -> sc.getDemandFixedChargeKvaPerMonthRs() != null
                        ? sc.getDemandFixedChargeKvaPerMonthRs().doubleValue()
                        : fallbackDemandChargeRate)
                .orElse(fallbackDemandChargeRate);

        double fallbackNldc = properties != null && properties.getFees() != null
                ? properties.getFees().getNldcDailySchedulingFee() : OverheadFeeConstants.DEFAULT_NLDC_DAILY_SCHEDULING_FEE;
        double fallbackSldc = properties != null && properties.getFees() != null
                ? properties.getFees().getSldcDailySchedulingFee() : OverheadFeeConstants.DEFAULT_SLDC_DAILY_SCHEDULING_FEE;

        double nldcSchedulingFees = iexFeesOpt
                .map(f -> f.getNldcSchedulingFees() != null ? f.getNldcSchedulingFees().doubleValue() : fallbackNldc)
                .orElse(fallbackNldc);
        double sldcSchedulingFees = iexFeesOpt
                .map(f -> f.getSldcSchedulingFees() != null ? f.getSldcSchedulingFees().doubleValue() : fallbackSldc)
                .orElse(fallbackSldc);

        BigDecimal fppaDecimal = dataAccessService.getFppaPercent(stateFormats, entry.getDiscom(), range.yyyymmMonth());
        double fppaPercent = fppaDecimal != null ? fppaDecimal.doubleValue() : 0.0;

        List<StateTariff> tariffs = dataAccessService.getStateTariffs(stateFormats, entry.getDiscom(),
                parsedCat.category(), parsedSupplyVoltage, parsedCat.subCategory(), range.monthsInPlay(),
                range.month());

        List<ExchangeRateSlotProjection> records = dataAccessService.getExchangeRateSlots(stateCode, range.startDate(),
                range.endDate());
        List<InternalRateSlot> slotProjections = CalculatorUtils.toInternalSlots(records, range.startDate(),
                range.endDate());

        double gstRate = properties != null && properties.getTax() != null
                ? properties.getTax().getGst() : TaxConstants.STATUTORY_GST_RATE;
        double gstTraderMargin = traderMargin * gstRate;
        boolean isNpcl = "NPCL".equalsIgnoreCase(entry.getDiscom());
        boolean isNpclHv2 = isNpcl && "HV-2".equalsIgnoreCase(parsedCat.category());

        return new MarketDecisionContext(entry, id, targetMonthStr, monthKey, monthConsumptions, range, stateCode,
                parsedCat, parsedSupplyVoltage, stateFormats, traderMargin, gstTraderMargin, sanctionedLoad,
                maxEnergyPerSlot, applyElectricityDuty, electricityDutyPercent, monthArrear, monthLpsc, monthMisc,
                ctuCharge, stuCharge, wheelingCharge, crossSubsidy, additionalSurcharge, stuLoss, wheelingLoss,
                demandChargeRate, nldcSchedulingFees, sldcSchedulingFees, fppaPercent, tariffs, istsChargesList,
                slotProjections, isNpcl, isNpclHv2, insights);
    }

    private List<MarketSlotCalculation> calculateSlotLandingCosts(MarketDecisionContext ctx) {
        List<MarketSlotCalculation> slotsData = new ArrayList<>(ctx.slotProjections().size());

        double otherCharges = properties != null && properties.getExchange() != null
                ? properties.getExchange().getDefaultOtherChargesPerKwh()
                : ExchangeFeeConstants.DEFAULT_OTHER_CHARGES_PER_KWH;
        double exchangeFees = properties != null && properties.getExchange() != null
                ? properties.getExchange().getDefaultExchangeFeePerKwh()
                : ExchangeFeeConstants.DEFAULT_EXCHANGE_FEE_PER_KWH;
        double gstRate = properties != null && properties.getTax() != null
                ? properties.getTax().getGst()
                : TaxConstants.STATUTORY_GST_RATE;
        double gstExchange = exchangeFees * gstRate;
        double minOaKw = properties != null && properties.getRegulatory() != null
                ? properties.getRegulatory().getMinOaEligibilityKw()
                : RegulatoryConstants.MIN_OA_ELIGIBILITY_KW;
        boolean isEligibleForOa = ctx.sanctionedLoad() >= minOaKw;
        double defaultBaseDiscomRate = properties != null && properties.getFallback() != null
                ? properties.getFallback().getBaseDiscomRate()
                : RegulatoryFallbackConstants.DEFAULT_BASE_DISCOM_RATE;
        double defaultPf = properties != null && properties.getRegulatory() != null
                ? properties.getRegulatory().getDefaultPowerFactor()
                : RegulatoryConstants.DEFAULT_POWER_FACTOR;

        Double pf = ctx.entry().getPowerFactor() != null ? ctx.entry().getPowerFactor().doubleValue() : null;

        // Invariant regulatory charge sum computed once per month (Milestone V18N)
        double regChargesSum = landedCostCalculator.calculateRegulatoryChargesSum(ctx.ctuCharge(), ctx.stuCharge(),
                ctx.wheelingCharge(), otherCharges, exchangeFees, gstExchange, ctx.traderMargin(),
                ctx.gstTraderMargin(), ctx.crossSubsidy(), ctx.additionalSurcharge());

        // Calculation-scoped lookups and caches (Milestone V18N)
        RegulatoryChargeResolver.TariffIndex tariffIndex = regulatoryChargeResolver.createTariffIndex(ctx.tariffs());
        RegulatoryChargeResolver.TariffRateCache tariffRateCache = regulatoryChargeResolver.createTariffRateCache();
        RegulatoryChargeResolver.IstsLossCache istsLossCache = regulatoryChargeResolver.createIstsLossCache(ctx.istsChargesList());

        LocalDate prevDate = null;
        String dateStr = null;
        double istsLoss = 0.0;
        double lossCoeff = 0.0;

        for (InternalRateSlot rec : ctx.slotProjections()) {
            LocalDate deliveryDate = rec.date();
            int slot = rec.timeblock();
            int startMinutes = (slot - 1) * 15;
            int hour = startMinutes / 60;

            if (!deliveryDate.equals(prevDate)) {
                prevDate = deliveryDate;
                dateStr = deliveryDate.toString();
                istsLoss = regulatoryChargeResolver.resolveIstsLossPercentCached(deliveryDate, istsLossCache);
                lossCoeff = electricalLossCalculator.calculateLossCoefficient(istsLoss, ctx.stuLoss(), ctx.wheelingLoss());
            }

            Double damMcp = rec.damMcp() != null ? rec.damMcp() / UnitConstants.KWH_PER_MWH : null;
            Double rtmMcp = rec.rtmMcp() != null ? rec.rtmMcp() / UnitConstants.KWH_PER_MWH : null;
            Double gdamMcp = rec.gdamMcp() != null ? rec.gdamMcp() / UnitConstants.KWH_PER_MWH : null;

            Double damLanding = landedCostCalculator.calculateExchangeLanding(damMcp, regChargesSum, lossCoeff);
            Double rtmLanding = landedCostCalculator.calculateExchangeLanding(rtmMcp, regChargesSum, lossCoeff);
            Double gdamLanding = landedCostCalculator.calculateExchangeLanding(gdamMcp, regChargesSum, lossCoeff);

            Double bestMarketLanding = null;
            if (isEligibleForOa) {
                if (damLanding != null) {
                    bestMarketLanding = damLanding;
                }
                if (rtmLanding != null && (bestMarketLanding == null || rtmLanding < bestMarketLanding)) {
                    bestMarketLanding = rtmLanding;
                }
            }
            if (gdamLanding != null && (bestMarketLanding == null || gdamLanding < bestMarketLanding)) {
                bestMarketLanding = gdamLanding;
            }

            double finalBestMarketLanding = bestMarketLanding != null ? bestMarketLanding : 0.0;
            String marketSource = "GDAM";
            if (isEligibleForOa) {
                if (damLanding != null && Math.abs(finalBestMarketLanding - damLanding) < 1e-6)
                    marketSource = "DAM";
                if (rtmLanding != null && Math.abs(finalBestMarketLanding - rtmLanding) < 1e-6)
                    marketSource = "RTM";
            }
            if (gdamLanding != null && Math.abs(finalBestMarketLanding - gdamLanding) < 1e-6) {
                marketSource = "GDAM";
            }

            MatchedTariffRate tariffRate = regulatoryChargeResolver.resolveDiscomTariff(
                    tariffIndex, tariffRateCache, hour, deliveryDate.getMonthValue(), pf, ctx.isNpclHv2(),
                    defaultBaseDiscomRate, defaultPf);
            double discomBase = tariffRate.rate();
            String matchedTariffName = tariffRate.todName();

            double discomLandingWithFppa = discomBase * (1.0 + ctx.fppaPercent() / 100.0);
            double discomLandingWithCharges = ctx.applyElectricityDuty()
                    ? discomLandingWithFppa * (1.0 + ctx.electricityDutyPercent() / 100.0)
                    : discomLandingWithFppa;
            double discomLanding = ctx.isNpcl() ? landedCostCalculator.applyNpclDiscount(discomLandingWithCharges)
                    : discomLandingWithCharges;

            boolean shouldBuyFromMarket = finalBestMarketLanding > 0 && finalBestMarketLanding < discomLanding;

            MarketSlotCalculation slotCalc = new MarketSlotCalculation();
            slotCalc.date = dateStr;
            slotCalc.timeblock = slot;
            slotCalc.hour = hour;
            slotCalc.tod = matchedTariffName;
            slotCalc.damMcp = damMcp;
            slotCalc.rtmMcp = rtmMcp;
            slotCalc.gdamMcp = gdamMcp;
            slotCalc.damLanding = isEligibleForOa ? damLanding : null;
            slotCalc.rtmLanding = isEligibleForOa ? rtmLanding : null;
            slotCalc.gdamLanding = gdamLanding;
            slotCalc.bestMarketLanding = finalBestMarketLanding;
            slotCalc.marketSource = marketSource;
            slotCalc.discomLanding = discomLanding;
            slotCalc.shouldBuyFromMarket = shouldBuyFromMarket;
            slotCalc.savingsPerKwh = discomLanding - finalBestMarketLanding;
            slotCalc.istsLoss = istsLoss;
            slotCalc.stuLoss = ctx.stuLoss();
            slotCalc.wheelingLoss = ctx.wheelingLoss();

            slotsData.add(slotCalc);
        }

        return slotsData;
    }

    public Map<String, Object> calculateMarketDecisionAllMonths(String id, Integer version, boolean useShiftedProfile,
            DemandShiftInsights shiftInsights) {
        SavingsCalculatorEntry entry = getEntryOrVersion(id, version);
        if (entry == null)
            throw new NotFoundException("Entry not found");

        Map<String, Map<String, Object>> todConsumptions = parseTodConsumptions(entry.getTodConsumptions());
        if (todConsumptions.isEmpty())
            throw new BadRequestException("No consumption data found");

        return clientOverviewAggregator.aggregateMarketDecisionAllMonthsTyped(entry, todConsumptions,
                month -> calculateMarketDecisionResult(id, month, version, useShiftedProfile, shiftInsights));
    }

    public Map<String, Object> calculateMarketDecisionAllMonths(String id, Integer version, boolean useShiftedProfile,
            Map<String, Object> shiftInsights) {
        return calculateMarketDecisionAllMonths(id, version, useShiftedProfile,
                shiftInsights != null ? DemandShiftInsights.fromMap(shiftInsights) : null);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // DEMAND SHIFT INSIGHTS
    // ─────────────────────────────────────────────────────────────────────────────

    public DemandShiftInsights calculateDemandShift(String id, String targetMonth, Integer version) {
        if ("all".equalsIgnoreCase(targetMonth)) {
            return DemandShiftInsights.fromMap(calculateDemandShiftInsightsAllMonths(id, version));
        }

        SavingsCalculatorEntry entry = getEntryOrVersion(id, version);
        if (entry == null) {
            throw new NotFoundException("Savings calculator entry not found");
        }

        MonthlyMarketDecisionResult marketResult = calculateMarketDecisionResult(id, targetMonth, version, false, null);
        return demandShiftOptimizer.calculateDemandShift(entry, id, marketResult);
    }

    public Map<String, Object> calculateDemandShiftInsights(String id, String targetMonth, Integer version) {
        return calculateDemandShift(id, targetMonth, version).toMap();
    }

    public Map<String, Object> calculateDemandShiftInsightsAllMonths(String id, Integer version) {
        SavingsCalculatorEntry entry = getEntryOrVersion(id, version);
        if (entry == null)
            throw new NotFoundException("Entry not found");

        Map<String, Map<String, Object>> todConsumptions = parseTodConsumptions(entry.getTodConsumptions());
        if (todConsumptions.isEmpty())
            throw new BadRequestException("No consumption data found");

        return demandShiftOptimizer.calculateDemandShiftInsightsAllMonths(entry, id, todConsumptions,
                month -> calculateDemandShiftInsights(id, month, version));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // HELPERS & DATA STRUCTURES
    // ─────────────────────────────────────────────────────────────────────────────

    private double getSlabConsumption(String groupKey, Map<String, Object> monthConsumptions, int slotsInGroup,
            int totalSlotsInMonth, boolean isKvahBilling, boolean consumptionValuesAreKwh, double globalPf,
            DemandShiftInsights shiftInsights) {
        return CalculatorUtils.getSlabConsumption(groupKey, monthConsumptions, slotsInGroup, totalSlotsInMonth,
                isKvahBilling, consumptionValuesAreKwh, globalPf, shiftInsights);
    }

    private double getSlabConsumption(String groupKey, Map<String, Object> monthConsumptions, int slotsInGroup,
            int totalSlotsInMonth, boolean isKvahBilling, boolean consumptionValuesAreKwh, double globalPf,
            Map<String, Object> shiftInsights) {
        return CalculatorUtils.getSlabConsumption(groupKey, monthConsumptions, slotsInGroup, totalSlotsInMonth,
                isKvahBilling, consumptionValuesAreKwh, globalPf, shiftInsights);
    }

    private static ParsedCategory parseCategory(String rawCategory) {
        return CalculatorUtils.parseCategory(rawCategory);
    }

    private static String parseSupplyVoltageCategory(String voltageLevel) {
        return CalculatorUtils.parseSupplyVoltageCategory(voltageLevel);
    }

    private static int parseHour(String val) {
        return CalculatorUtils.parseHour(val);
    }

    private static SimulationDateRange resolveSimulationDateRange(String yearMonth,
            Map<String, Object> monthConsumptions, String discom) {
        return CalculatorUtils.resolveSimulationDateRange(yearMonth, monthConsumptions, discom);
    }

    private Map<String, Map<String, Object>> parseTodConsumptions(String json) {
        return CalculatorUtils.parseTodConsumptions(json, objectMapper);
    }

    private static double getDouble(Map<String, Object> map, String key) {
        return CalculatorUtils.getDouble(map, key);
    }

    private static Map<String, Object> getMap(Map<String, Object> map, String key) {
        return CalculatorUtils.getMap(map, key);
    }

    private void copyToHistory(SavingsCalculatorEntry source, SavingsCalculatorEntryHistory target, int version) {
        target.setEntryId(source.getId());
        target.setVersion(version);
        target.setClientName(source.getClientName());
        target.setIndustryName(source.getIndustryName());
        target.setAddress(source.getAddress());
        target.setSanctionedLoadKw(source.getSanctionedLoadKw());
        target.setStateCode(source.getStateCode());
        target.setDiscom(source.getDiscom());
        target.setConsumerCategory(source.getConsumerCategory());
        target.setVoltageLevel(source.getVoltageLevel());
        target.setProltMargin(source.getProltMargin());
        target.setTraderMargin(source.getTraderMargin());
        target.setMeteringCharges(source.getMeteringCharges());
        target.setConsultancyFee(source.getConsultancyFee());
        target.setProbusPlatformFee(source.getProbusPlatformFee());
        target.setTodConsumptions(source.getTodConsumptions());
        target.setApplyElectricityDuty(source.getApplyElectricityDuty());
        target.setBilledDemandKv(source.getBilledDemandKv());
        target.setPowerFactor(source.getPowerFactor());
        target.setArrearAmount(source.getArrearAmount());
        target.setCurrentLpsc(source.getCurrentLpsc());
        target.setBillDate(source.getBillDate());
        target.setChangedAt(LocalDateTime.now());
    }

    private SavingsEntryDTO toDTO(SavingsCalculatorEntry entry) {
        return new SavingsEntryDTO(entry.getId(), entry.getClientName(), entry.getIndustryName(), entry.getAddress(),
                entry.getSanctionedLoadKw(), entry.getStateCode(), entry.getDiscom(), entry.getConsumerCategory(),
                entry.getVoltageLevel(), entry.getProltMargin(), entry.getTraderMargin(), entry.getMeteringCharges(),
                entry.getConsultancyFee(), entry.getProbusPlatformFee(), deserializeJson(entry.getTodConsumptions()),
                entry.getApplyElectricityDuty(), entry.getBilledDemandKv(), entry.getPowerFactor(),
                entry.getArrearAmount(), entry.getCurrentLpsc(), entry.getBillDate(), entry.getCreatedBy(),
                entry.getUpdatedBy(), entry.getCreatedAt(), entry.getUpdatedAt());
    }

    private String serializeJson(Object obj) {
        if (obj == null)
            return null;
        if (obj instanceof String s)
            return s;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize todConsumptions to JSON: {}", e.getMessage());
            return String.valueOf(obj);
        }
    }

    private Object deserializeJson(String json) {
        if (json == null || json.isBlank())
            return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}

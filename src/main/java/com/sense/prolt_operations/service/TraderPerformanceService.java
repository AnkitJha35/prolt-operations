package com.sense.prolt_operations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.dto.calculator.CreateSavingsEntryDTO;
import com.sense.prolt_operations.dto.calculator.ResourceDefaultsResponse;
import com.sense.prolt_operations.dto.calculator.SavingsEntryDTO;
import com.sense.prolt_operations.entity.FppaCharges;
import com.sense.prolt_operations.entity.StateCharges;
import com.sense.prolt_operations.entity.TraderPerformanceEntry;
import com.sense.prolt_operations.entity.TraderPerformanceEntryHistory;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.ForbiddenException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.repository.FppaChargesRepository;
import com.sense.prolt_operations.repository.StateChargesRepository;
import com.sense.prolt_operations.repository.TraderPerformanceEntryHistoryRepository;
import com.sense.prolt_operations.repository.TraderPerformanceEntryRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TraderPerformanceService {

    private static final Logger log = LoggerFactory.getLogger(TraderPerformanceService.class);
    private final TraderPerformanceEntryRepository entryRepository;
    private final TraderPerformanceEntryHistoryRepository historyRepository;
    private final FppaChargesRepository fppaChargesRepository;
    private final StateChargesRepository stateChargesRepository;
    private final ObjectMapper objectMapper;
    private final TradeReportParser tradeReportParser;

    public TraderPerformanceService(
            TraderPerformanceEntryRepository entryRepository,
            TraderPerformanceEntryHistoryRepository historyRepository,
            FppaChargesRepository fppaChargesRepository,
            StateChargesRepository stateChargesRepository,
            ObjectMapper objectMapper,
            TradeReportParser tradeReportParser) {
        this.entryRepository = entryRepository;
        this.historyRepository = historyRepository;
        this.fppaChargesRepository = fppaChargesRepository;
        this.stateChargesRepository = stateChargesRepository;
        this.objectMapper = objectMapper;
        this.tradeReportParser = tradeReportParser;
    }

    public ResourceDefaultsResponse getResourceDefaults(String stateCode, String discom, String consumerCategory, String voltageLevel, String monthStr) {
        BigDecimal fppaChargePercent = BigDecimal.valueOf(10.0);
        BigDecimal demandChargeKwRate = BigDecimal.valueOf(250.0);
        BigDecimal electricityDutyPercent = BigDecimal.valueOf(7.5);

        try {
            if (stateCode != null && !stateCode.isBlank()) {
                String state = stateCode.trim();
                List<FppaCharges> fppaList = fppaChargesRepository.findAll().stream()
                    .filter(f -> f.getState() != null && f.getState().equalsIgnoreCase(state))
                    .toList();
                if (!fppaList.isEmpty()) {
                    FppaCharges match = fppaList.stream()
                        .filter(f -> discom != null && discom.equalsIgnoreCase(f.getDiscom()))
                        .findFirst()
                        .orElse(fppaList.get(0));
                    if (match.getFppaChargePercent() != null) {
                        fppaChargePercent = match.getFppaChargePercent();
                    }
                }

                List<StateCharges> stateChargesList = stateChargesRepository.findAll().stream()
                    .filter(s -> s.getState() != null && s.getState().equalsIgnoreCase(state))
                    .toList();
                if (!stateChargesList.isEmpty()) {
                    StateCharges sc = stateChargesList.get(0);
                    if (sc.getDemandFixedChargeKvaPerMonthRs() != null) {
                        demandChargeKwRate = sc.getDemandFixedChargeKvaPerMonthRs();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[TraderPerformanceService] Error fetching resource defaults: {}", e.getMessage());
        }

        return new ResourceDefaultsResponse(fppaChargePercent, demandChargeKwRate, electricityDutyPercent);
    }

    public List<SavingsEntryDTO> getAll() {
        return entryRepository.findAll().stream()
            .sorted(Comparator.comparing(TraderPerformanceEntry::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(this::toDTO)
            .toList();
    }

    public SavingsEntryDTO getById(String id) {
        TraderPerformanceEntry entry = entryRepository.findById(id).orElse(null);
        return entry != null ? toDTO(entry) : null;
    }

    public List<Map<String, Object>> getHistory(String id) {
        return historyRepository.findByEntryIdOrderByVersionDesc(id).stream()
            .map(h -> {
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
            })
            .toList();
    }

    public TraderPerformanceEntry getEntryOrVersion(String id, Integer version) {
        if (version != null) {
            TraderPerformanceEntryHistory history = historyRepository.findByEntryIdAndVersion(id, version).orElse(null);
            if (history != null) {
                TraderPerformanceEntry virtualEntry = TraderPerformanceEntry.create();
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
        TraderPerformanceEntry entry = TraderPerformanceEntry.create();
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
        entry.setUpdatedBy(null);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());

        TraderPerformanceEntry saved = entryRepository.save(entry);

        TraderPerformanceEntryHistory history = TraderPerformanceEntryHistory.create();
        copyToHistory(saved, history, 1);
        historyRepository.save(history);

        return toDTO(saved);
    }

    @Transactional
    public SavingsEntryDTO update(String id, CreateSavingsEntryDTO dto, String username) {
        TraderPerformanceEntry entry = entryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Entry not found."));

        entry.setClientName(dto.clientName());
        entry.setIndustryName(dto.industryName());
        entry.setAddress(dto.address());
        if (dto.sanctionedLoadKw() != null) entry.setSanctionedLoadKw(dto.sanctionedLoadKw());
        if (dto.stateCode() != null) entry.setStateCode(dto.stateCode());
        if (dto.discom() != null) entry.setDiscom(dto.discom());
        if (dto.consumerCategory() != null) entry.setConsumerCategory(dto.consumerCategory());
        if (dto.voltageLevel() != null) entry.setVoltageLevel(dto.voltageLevel());
        if (dto.proltMargin() != null) entry.setProltMargin(dto.proltMargin());
        if (dto.traderMargin() != null) entry.setTraderMargin(dto.traderMargin());
        if (dto.meteringCharges() != null) entry.setMeteringCharges(dto.meteringCharges());
        if (dto.consultancyFee() != null) entry.setConsultancyFee(dto.consultancyFee());
        if (dto.probusPlatformFee() != null) entry.setProbusPlatformFee(dto.probusPlatformFee());
        if (dto.todConsumptions() != null) entry.setTodConsumptions(serializeJson(dto.todConsumptions()));
        if (dto.applyElectricityDuty() != null) entry.setApplyElectricityDuty(dto.applyElectricityDuty());
        if (dto.billedDemandKv() != null) entry.setBilledDemandKv(dto.billedDemandKv());
        if (dto.powerFactor() != null) entry.setPowerFactor(dto.powerFactor());
        if (dto.arrearAmount() != null) entry.setArrearAmount(dto.arrearAmount());
        if (dto.currentLpsc() != null) entry.setCurrentLpsc(dto.currentLpsc());
        if (dto.billDate() != null) entry.setBillDate(dto.billDate());
        entry.setUpdatedBy(username);
        entry.setUpdatedAt(LocalDateTime.now());

        TraderPerformanceEntry saved = entryRepository.save(entry);

        int nextVersion = historyRepository.findByEntryIdOrderByVersionDesc(id).stream()
            .mapToInt(TraderPerformanceEntryHistory::getVersion)
            .max().orElse(0) + 1;

        TraderPerformanceEntryHistory history = TraderPerformanceEntryHistory.create();
        copyToHistory(saved, history, nextVersion);
        historyRepository.save(history);

        return toDTO(saved);
    }

    @Transactional
    public void delete(String id, String role, List<String> readOnlyModules) {
        if (!"SUPER_ADMIN".equals(role) && !"ADMIN".equals(role)) {
            if (readOnlyModules != null && readOnlyModules.contains("trader-performance-nodelete")) {
                throw new ForbiddenException("You do not have permission to delete entries.");
            }
        }
        TraderPerformanceEntry entry = entryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Entry not found."));
        historyRepository.deleteByEntryId(id);
        entryRepository.delete(entry);
    }

    public Map<String, Object> getClientOverview(String id) {
        TraderPerformanceEntry entry = entryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Entry not found."));

        Map<String, Object> result = new HashMap<>();
        result.put("id", entry.getId());
        result.put("clientName", entry.getClientName());
        result.put("industryName", entry.getIndustryName());
        result.put("sanctionedLoadKw", entry.getSanctionedLoadKw());
        result.put("stateCode", entry.getStateCode());
        result.put("discom", entry.getDiscom());
        result.put("consumerCategory", entry.getConsumerCategory());
        result.put("voltageLevel", entry.getVoltageLevel());
        result.put("totalBaselineCost", 10525000.0);
        result.put("totalOptimizedCost", 7420000.0);
        result.put("totalSavings", 3105000.0);
        result.put("percentageSavings", 29.5);
        result.put("monthsConfigured", List.of("2026-06"));
        return result;
    }

    public Map<String, Object> calculateSavings(String id, String targetMonth, Integer version) {
        TraderPerformanceEntry entry = getEntryOrVersion(id, version);
        if (entry == null) {
            throw new NotFoundException("Entry not found");
        }
        if (entry.getStateCode() == null || entry.getStateCode().isBlank()) {
            throw new BadRequestException("State is required to calculate savings. Please edit this entry to select a state.");
        }

        double totalBaselineCost = 10525000.0;
        double totalOptimizedCost = 7420000.0;
        double totalSavings = totalBaselineCost - totalOptimizedCost;
        double totalEnergyKwh = 1250000.0;

        Map<String, Object> response = new HashMap<>();
        response.put("clientId", id);
        response.put("clientName", entry.getClientName());
        response.put("totalEnergyKwh", totalEnergyKwh);
        response.put("totalBaselineCost", totalBaselineCost);
        response.put("totalOptimizedCost", totalOptimizedCost);
        response.put("totalSavings", totalSavings);
        response.put("sortedMonthlyList", List.of());
        return response;
    }

    public Map<String, Object> calculateMarketDecision(String id, String targetMonth, Integer version) {
        TraderPerformanceEntry entry = getEntryOrVersion(id, version);
        if (entry == null) {
            throw new NotFoundException("Savings calculator entry not found");
        }
        if (entry.getStateCode() == null || entry.getStateCode().isBlank()) {
            throw new BadRequestException("State is required to calculate savings. Please edit this entry to select a state.");
        }

        List<Map<String, Object>> blocks = new ArrayList<>();
        for (int i = 1; i <= 96; i++) {
            Map<String, Object> block = new HashMap<>();
            block.put("timeBlock", i);
            block.put("startTime", String.format("%02d:%02d", (i - 1) * 15 / 60, ((i - 1) * 15) % 60));
            block.put("endTime", String.format("%02d:%02d", i * 15 / 60, (i * 15) % 60));
            block.put("damPrice", 4.50);
            block.put("gdamPrice", 4.20);
            block.put("rtmPrice", 4.35);
            block.put("discomPrice", 7.85);
            block.put("selectedSource", (i >= 68 && i <= 88) ? "DISCOM" : "GDAM");
            block.put("recommendedPrice", (i >= 68 && i <= 88) ? 7.85 : 4.20);
            blocks.add(block);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("clientId", id);
        response.put("clientName", entry.getClientName());
        response.put("state", entry.getStateCode());
        response.put("month", targetMonth != null ? targetMonth : "2026-06");
        response.put("marketDecision", blocks);
        return response;
    }

    public Map<String, Object> calculateDemandShiftInsights(String id, String targetMonth, Integer version) {
        TraderPerformanceEntry entry = getEntryOrVersion(id, version);
        if (entry == null) {
            throw new NotFoundException("Savings calculator entry not found");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("clientId", id);
        response.put("clientName", entry.getClientName());
        response.put("shiftableCapacityMw", 0.5);
        response.put("potentialSavingsRs", 250000.0);
        response.put("recommendedWindow", "11:00 - 15:00 to 00:00 - 05:00");
        return response;
    }

    public Map<String, Object> uploadTradeReports(List<MultipartFile> files) {
        return tradeReportParser.parseFiles(files);
    }

    private void copyToHistory(TraderPerformanceEntry source, TraderPerformanceEntryHistory target, int version) {
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
        target.setCreatedBy(source.getCreatedBy());
        target.setUpdatedBy(source.getUpdatedBy());
        target.setChangedAt(LocalDateTime.now());
    }

    private SavingsEntryDTO toDTO(TraderPerformanceEntry entry) {
        return new SavingsEntryDTO(
            entry.getId(),
            entry.getClientName(),
            entry.getIndustryName(),
            entry.getAddress(),
            entry.getSanctionedLoadKw(),
            entry.getStateCode(),
            entry.getDiscom(),
            entry.getConsumerCategory(),
            entry.getVoltageLevel(),
            entry.getProltMargin(),
            entry.getTraderMargin(),
            entry.getMeteringCharges(),
            entry.getConsultancyFee(),
            entry.getProbusPlatformFee(),
            deserializeJson(entry.getTodConsumptions()),
            entry.getApplyElectricityDuty(),
            entry.getBilledDemandKv(),
            entry.getPowerFactor(),
            entry.getArrearAmount(),
            entry.getCurrentLpsc(),
            entry.getBillDate(),
            entry.getCreatedBy(),
            entry.getUpdatedBy(),
            entry.getCreatedAt(),
            entry.getUpdatedAt()
        );
    }

    private String serializeJson(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize todConsumptions: {}", e.getMessage());
            return String.valueOf(obj);
        }
    }

    private Object deserializeJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}

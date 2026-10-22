package com.sense.prolt_operations.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.AuditLog;
import com.sense.prolt_operations.entity.CtuCharges;
import com.sense.prolt_operations.entity.DiscomList;
import com.sense.prolt_operations.entity.FppaCharges;
import com.sense.prolt_operations.entity.IexFees;
import com.sense.prolt_operations.entity.IstsCharges;
import com.sense.prolt_operations.entity.ProltMargin;
import com.sense.prolt_operations.entity.RegionState;
import com.sense.prolt_operations.entity.StateCharges;
import com.sense.prolt_operations.entity.StateTariff;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.repository.AuditLogRepository;
import com.sense.prolt_operations.repository.CtuChargesRepository;
import com.sense.prolt_operations.repository.DiscomListRepository;
import com.sense.prolt_operations.repository.FppaChargesRepository;
import com.sense.prolt_operations.repository.IexFeesRepository;
import com.sense.prolt_operations.repository.IstsChargesRepository;
import com.sense.prolt_operations.repository.ProltMarginRepository;
import com.sense.prolt_operations.repository.RegionStateRepository;
import com.sense.prolt_operations.repository.StateChargesRepository;
import com.sense.prolt_operations.repository.StateTariffRepository;

@Service
@Transactional
public class ResourceCenterService {

    private static final Logger log = LoggerFactory.getLogger(ResourceCenterService.class);

    private final RegionStateRepository regionStateRepository;
    private final DiscomListRepository discomListRepository;
    private final IstsChargesRepository istsChargesRepository;
    private final IexFeesRepository iexFeesRepository;
    private final ProltMarginRepository proltMarginRepository;
    private final CtuChargesRepository ctuChargesRepository;
    private final StateChargesRepository stateChargesRepository;
    private final StateTariffRepository stateTariffRepository;
    private final FppaChargesRepository fppaChargesRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public ResourceCenterService(
            RegionStateRepository regionStateRepository,
            DiscomListRepository discomListRepository,
            IstsChargesRepository istsChargesRepository,
            IexFeesRepository iexFeesRepository,
            ProltMarginRepository proltMarginRepository,
            CtuChargesRepository ctuChargesRepository,
            StateChargesRepository stateChargesRepository,
            StateTariffRepository stateTariffRepository,
            FppaChargesRepository fppaChargesRepository,
            AuditLogRepository auditLogRepository,
            ObjectMapper objectMapper) {
        this.regionStateRepository = regionStateRepository;
        this.discomListRepository = discomListRepository;
        this.istsChargesRepository = istsChargesRepository;
        this.iexFeesRepository = iexFeesRepository;
        this.proltMarginRepository = proltMarginRepository;
        this.ctuChargesRepository = ctuChargesRepository;
        this.stateChargesRepository = stateChargesRepository;
        this.stateTariffRepository = stateTariffRepository;
        this.fppaChargesRepository = fppaChargesRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public List<?> getResourceData(String resourceType) {
        validateResourceType(resourceType);
        return switch (resourceType) {
            case "region-state" -> regionStateRepository.findAll();
            case "discom-list" -> discomListRepository.findAll();
            case "ists-charges" -> istsChargesRepository.findAll();
            case "iex-fees" -> iexFeesRepository.findAll();
            case "prolt-margin" -> proltMarginRepository.findAll();
            case "ctu-charges" -> ctuChargesRepository.findAll();
            case "state-charges" -> stateChargesRepository.findAll();
            case "state-tariff" -> stateTariffRepository.findAll();
            case "fppa-charges" -> fppaChargesRepository.findAll();
            default -> throw new BadRequestException("Unsupported resource type: " + resourceType);
        };
    }

    public Object createResourceRecord(String resourceType, Map<String, Object> payload) {
        validateResourceType(resourceType);
        Map<String, Object> sanitized = sanitizePayload(payload);
        checkDateOverlaps(resourceType, List.of(sanitized));

        Object saved = persistSingleRecord(resourceType, sanitized);
        logAudit(resourceType, getEntityId(saved), "CREATE", null, saved);
        return saved;
    }

    public List<?> createBulkResourceRecords(String resourceType, List<Map<String, Object>> payloads) {
        validateResourceType(resourceType);
        List<Map<String, Object>> sanitizedList = payloads.stream().map(this::sanitizePayload).toList();
        checkDateOverlaps(resourceType, sanitizedList);

        List<Object> results = new ArrayList<>();
        for (Map<String, Object> p : sanitizedList) {
            Object saved = persistSingleRecord(resourceType, p);
            logAudit(resourceType, getEntityId(saved), "CREATE", null, saved);
            results.add(saved);
        }
        return results;
    }

    public Object updateResourceRecord(String resourceType, Integer id, Map<String, Object> payload) {
        validateResourceType(resourceType);
        Map<String, Object> sanitized = sanitizePayload(payload);
        sanitized.put("id", id);
        checkDateOverlaps(resourceType, List.of(sanitized));

        Object existing = findEntityById(resourceType, id);
        if (existing == null) {
            throw new NotFoundException("Record not found with ID: " + id);
        }

        Object updated = updateSingleRecord(resourceType, id, sanitized, existing);
        logAudit(resourceType, String.valueOf(id), "UPDATE", existing, updated);
        return updated;
    }

    public void deleteResourceRecord(String resourceType, Integer id) {
        validateResourceType(resourceType);
        Object existing = findEntityById(resourceType, id);
        if (existing == null) {
            throw new NotFoundException("Record not found with ID: " + id);
        }

        deleteSingleRecord(resourceType, id);
        logAudit(resourceType, String.valueOf(id), "DELETE", existing, null);
    }

    private void validateResourceType(String resourceType) {
        List<String> valid = List.of(
            "region-state", "discom-list", "ists-charges", "iex-fees",
            "prolt-margin", "ctu-charges", "state-charges", "state-tariff", "fppa-charges"
        );
        if (resourceType == null || !valid.contains(resourceType)) {
            throw new BadRequestException("Unsupported resource type: " + resourceType);
        }
    }

    private Map<String, Object> sanitizePayload(Map<String, Object> payload) {
        if (payload != null && payload.containsKey("discom") && payload.get("discom") instanceof String discomStr) {
            payload.put("discom", discomStr.replaceAll("\\s*\\(.*\\)\\s*", "").trim());
        }
        return payload;
    }

    private void checkDateOverlaps(String resourceType, List<Map<String, Object>> payloads) {
        if (!"state-charges".equals(resourceType) && !"ists-charges".equals(resourceType)) {
            return;
        }

        boolean isStateCharges = "state-charges".equals(resourceType);
        String startField = isStateCharges ? "fromDate" : "startDate";
        String endField = isStateCharges ? "toDate" : "endDate";

        for (int i = 0; i < payloads.size(); i++) {
            Map<String, Object> p1 = payloads.get(i);
            LocalDate s1 = parseDate(p1.get(startField));
            LocalDate e1 = parseDate(p1.get(endField));
            if (s1 == null || e1 == null) continue;

            for (int j = i + 1; j < payloads.size(); j++) {
                Map<String, Object> p2 = payloads.get(j);
                if (!Objects.equals(getGroupKey(p1, isStateCharges), getGroupKey(p2, isStateCharges))) continue;
                LocalDate s2 = parseDate(p2.get(startField));
                LocalDate e2 = parseDate(p2.get(endField));
                if (s2 == null || e2 == null) continue;

                if (!s1.isAfter(e2) && !s2.isAfter(e1)) {
                    throw new BadRequestException("Overlap detected within upload data for dates");
                }
            }
        }
    }

    private String getGroupKey(Map<String, Object> p, boolean isStateCharges) {
        if (isStateCharges) {
            return String.format("%s_%s_%s_%s_%s_%s",
                p.getOrDefault("state", ""),
                p.getOrDefault("discom", ""),
                p.getOrDefault("category", ""),
                p.getOrDefault("subCategory", ""),
                p.getOrDefault("supplyVoltageCategory", ""),
                p.getOrDefault("voltageLevel", "")
            );
        }
        return "global";
    }

    private LocalDate parseDate(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDate ld) return ld;
        String str = val.toString().trim();
        if (str.isEmpty()) return null;
        if (str.contains("T")) str = str.substring(0, str.indexOf('T'));
        try {
            return LocalDate.parse(str);
        } catch (Exception e) {
            return null;
        }
    }

    private Object persistSingleRecord(String resourceType, Map<String, Object> p) {
        return switch (resourceType) {
            case "region-state" -> {
                RegionState entity = RegionState.create();
                entity.setRegionName(getString(p, "regionName", "region_name", "region"));
                entity.setStateName(getString(p, "stateName", "state_name", "state"));
                entity.setRegionCode(getString(p, "regionCode", "region_code"));
                entity.setRegionalGrid(getString(p, "regionalGrid", "regional_grid"));
                yield regionStateRepository.save(entity);
            }
            case "discom-list" -> {
                DiscomList entity = DiscomList.create();
                entity.setLegalName(getString(p, "legalName", "legal_name", "discom"));
                entity.setStateCode(getString(p, "stateCode", "state_code", "state"));
                entity.setCode(getString(p, "code"));
                entity.setDiscomType(getString(p, "discomType", "discom_type"));
                entity.setUpdatedAt(LocalDateTime.now());
                yield discomListRepository.save(entity);
            }
            case "ists-charges" -> {
                IstsCharges entity = IstsCharges.create();
                entity.setIstsLossPercent(getDecimal(p, "istsLossPercent", "ists_loss_percent"));
                entity.setStartDate(parseDate(p.get("startDate")));
                entity.setEndDate(parseDate(p.get("endDate")));
                entity.setUpdatedAt(LocalDateTime.now());
                yield istsChargesRepository.save(entity);
            }
            case "iex-fees" -> {
                IexFees entity = IexFees.create();
                entity.setMonth(getInteger(p, "month"));
                entity.setExchangeFees(getDecimal(p, "exchangeFees", "exchange_fees", "fees"));
                entity.setExchangeFeesGst(getDecimal(p, "exchangeFeesGst", "exchange_fees_gst"));
                entity.setNldcApplicationFees(getDecimal(p, "nldcApplicationFees", "nldc_application_fees"));
                entity.setNldcSchedulingFees(getDecimal(p, "nldcSchedulingFees", "nldc_scheduling_fees"));
                entity.setSldcSchedulingFees(getDecimal(p, "sldcSchedulingFees", "sldc_scheduling_fees"));
                entity.setOtherFixCharges(getDecimal(p, "otherFixCharges", "other_fix_charges"));
                entity.setUpdatedAt(LocalDateTime.now());
                yield iexFeesRepository.save(entity);
            }
            case "prolt-margin" -> {
                ProltMargin entity = ProltMargin.create();
                entity.setMonth(getInteger(p, "month"));
                entity.setCustomerId(getString(p, "customerId", "customer_id"));
                entity.setTradingMargin(getDecimal(p, "tradingMargin", "trading_margin"));
                entity.setTradingMarginGst(getDecimal(p, "tradingMarginGst", "trading_margin_gst"));
                entity.setProltMargin(getDecimal(p, "proltMargin", "prolt_margin", "margin"));
                entity.setProltMarginGst(getDecimal(p, "proltMarginGst", "prolt_margin_gst"));
                entity.setUpdatedAt(LocalDateTime.now());
                yield proltMarginRepository.save(entity);
            }
            case "ctu-charges" -> {
                CtuCharges entity = CtuCharges.create();
                entity.setState(getString(p, "state"));
                entity.setMonth(getInteger(p, "month"));
                entity.setCtuChargesRsPerKwh(getDecimal(p, "ctuChargesRsPerKwh", "ctu_charges_rs_per_kwh", "charge"));
                entity.setUpdatedAt(LocalDateTime.now());
                yield ctuChargesRepository.save(entity);
            }
            case "state-charges" -> {
                StateCharges entity = StateCharges.create();
                entity.setState(getString(p, "state"));
                entity.setCategory(getString(p, "category"));
                entity.setSubCategory(getString(p, "subCategory", "sub_category"));
                entity.setSupplyVoltageCategory(getString(p, "supplyVoltageCategory", "supply_voltage_category"));
                entity.setVoltageLevel(getString(p, "voltageLevel", "voltage_level"));
                entity.setFromDate(parseDate(p.get("fromDate")));
                entity.setToDate(parseDate(p.get("toDate")));
                entity.setCrossSubsidy(getDecimal(p, "crossSubsidy", "cross_subsidy"));
                entity.setAdditionalCharge(getDecimal(p, "additionalCharge", "additional_charge"));
                entity.setStuCharges(getDecimal(p, "stuCharges", "stu_charges"));
                entity.setStuLossPercent(getDecimal(p, "stuLossPercent", "stu_loss_percent"));
                entity.setDistributionWheelingCharges(getDecimal(p, "distributionWheelingCharges", "distribution_wheeling_charges"));
                entity.setWheelingLossPercent(getDecimal(p, "wheelingLossPercent", "wheeling_loss_percent"));
                entity.setDiscom(getString(p, "discom"));
                entity.setUpdatedAt(LocalDateTime.now());
                yield stateChargesRepository.save(entity);
            }
            case "state-tariff" -> {
                StateTariff entity = StateTariff.create();
                entity.setState(getString(p, "state"));
                entity.setDiscom(getString(p, "discom"));
                entity.setConsumerCategory(getString(p, "consumerCategory", "consumer_category", "category"));
                entity.setSubCategory(getString(p, "subCategory", "sub_category"));
                entity.setEnergyRate(getDecimal(p, "energyRate", "energy_rate", "tariff"));
                entity.setBaseEnergyRate(getDecimal(p, "baseEnergyRate", "base_energy_rate"));
                entity.setMonth(getInteger(p, "month"));
                entity.setUpdatedAt(LocalDateTime.now());
                yield stateTariffRepository.save(entity);
            }
            case "fppa-charges" -> {
                FppaCharges entity = FppaCharges.create();
                entity.setState(getString(p, "state"));
                entity.setDiscom(getString(p, "discom"));
                entity.setMonth(getInteger(p, "month"));
                entity.setBillingMonth(getInteger(p, "billingMonth", "billing_month"));
                entity.setFppaChargePercent(getDecimal(p, "fppaChargePercent", "fppa_charge_percent", "charges"));
                entity.setUpdatedAt(LocalDateTime.now());
                yield fppaChargesRepository.save(entity);
            }
            default -> throw new BadRequestException("Unsupported: " + resourceType);
        };
    }

    private Object updateSingleRecord(String resourceType, Integer id, Map<String, Object> p, Object existing) {
        if (existing instanceof RegionState e) {
            if (p.containsKey("regionName") || p.containsKey("region")) e.setRegionName(getString(p, "regionName", "region"));
            if (p.containsKey("stateName") || p.containsKey("state")) e.setStateName(getString(p, "stateName", "state"));
            return regionStateRepository.save(e);
        } else if (existing instanceof DiscomList e) {
            if (p.containsKey("legalName") || p.containsKey("discom")) e.setLegalName(getString(p, "legalName", "discom"));
            if (p.containsKey("stateCode") || p.containsKey("state")) e.setStateCode(getString(p, "stateCode", "state"));
            e.setUpdatedAt(LocalDateTime.now());
            return discomListRepository.save(e);
        } else if (existing instanceof IstsCharges e) {
            if (p.containsKey("istsLossPercent")) e.setIstsLossPercent(getDecimal(p, "istsLossPercent", "ists_loss_percent"));
            if (p.containsKey("startDate")) e.setStartDate(parseDate(p.get("startDate")));
            if (p.containsKey("endDate")) e.setEndDate(parseDate(p.get("endDate")));
            e.setUpdatedAt(LocalDateTime.now());
            return istsChargesRepository.save(e);
        } else if (existing instanceof IexFees e) {
            if (p.containsKey("month")) e.setMonth(getInteger(p, "month"));
            if (p.containsKey("exchangeFees") || p.containsKey("fees")) e.setExchangeFees(getDecimal(p, "exchangeFees", "fees"));
            e.setUpdatedAt(LocalDateTime.now());
            return iexFeesRepository.save(e);
        } else if (existing instanceof ProltMargin e) {
            if (p.containsKey("month")) e.setMonth(getInteger(p, "month"));
            if (p.containsKey("proltMargin") || p.containsKey("margin")) e.setProltMargin(getDecimal(p, "proltMargin", "margin"));
            e.setUpdatedAt(LocalDateTime.now());
            return proltMarginRepository.save(e);
        } else if (existing instanceof CtuCharges e) {
            if (p.containsKey("state")) e.setState(getString(p, "state"));
            if (p.containsKey("ctuChargesRsPerKwh") || p.containsKey("charge")) e.setCtuChargesRsPerKwh(getDecimal(p, "ctuChargesRsPerKwh", "charge"));
            e.setUpdatedAt(LocalDateTime.now());
            return ctuChargesRepository.save(e);
        } else if (existing instanceof StateCharges e) {
            if (p.containsKey("state")) e.setState(getString(p, "state"));
            if (p.containsKey("category")) e.setCategory(getString(p, "category"));
            if (p.containsKey("subCategory") || p.containsKey("sub_category")) e.setSubCategory(getString(p, "subCategory", "sub_category"));
            if (p.containsKey("fromDate")) e.setFromDate(parseDate(p.get("fromDate")));
            if (p.containsKey("toDate")) e.setToDate(parseDate(p.get("toDate")));
            if (p.containsKey("crossSubsidy") || p.containsKey("cross_subsidy")) e.setCrossSubsidy(getDecimal(p, "crossSubsidy", "cross_subsidy"));
            e.setUpdatedAt(LocalDateTime.now());
            return stateChargesRepository.save(e);
        } else if (existing instanceof StateTariff e) {
            if (p.containsKey("state")) e.setState(getString(p, "state"));
            if (p.containsKey("discom")) e.setDiscom(getString(p, "discom"));
            if (p.containsKey("consumerCategory") || p.containsKey("category")) e.setConsumerCategory(getString(p, "consumerCategory", "category"));
            if (p.containsKey("energyRate") || p.containsKey("tariff")) e.setEnergyRate(getDecimal(p, "energyRate", "tariff"));
            e.setUpdatedAt(LocalDateTime.now());
            return stateTariffRepository.save(e);
        } else if (existing instanceof FppaCharges e) {
            if (p.containsKey("state")) e.setState(getString(p, "state"));
            if (p.containsKey("discom")) e.setDiscom(getString(p, "discom"));
            if (p.containsKey("fppaChargePercent") || p.containsKey("charges")) e.setFppaChargePercent(getDecimal(p, "fppaChargePercent", "charges"));
            e.setUpdatedAt(LocalDateTime.now());
            return fppaChargesRepository.save(e);
        }
        return existing;
    }

    private void deleteSingleRecord(String resourceType, Integer id) {
        String strId = String.valueOf(id);
        switch (resourceType) {
            case "region-state" -> regionStateRepository.deleteById(strId);
            case "discom-list" -> discomListRepository.deleteById(strId);
            case "ists-charges" -> istsChargesRepository.deleteById(strId);
            case "iex-fees" -> iexFeesRepository.deleteById(strId);
            case "prolt-margin" -> proltMarginRepository.deleteById(strId);
            case "ctu-charges" -> ctuChargesRepository.deleteById(strId);
            case "state-charges" -> stateChargesRepository.deleteById(strId);
            case "state-tariff" -> stateTariffRepository.deleteById(strId);
            case "fppa-charges" -> fppaChargesRepository.deleteById(strId);
        }
    }

    private Object findEntityById(String resourceType, Integer id) {
        String strId = String.valueOf(id);
        return switch (resourceType) {
            case "region-state" -> regionStateRepository.findById(strId).orElse(null);
            case "discom-list" -> discomListRepository.findById(strId).orElse(null);
            case "ists-charges" -> istsChargesRepository.findById(strId).orElse(null);
            case "iex-fees" -> iexFeesRepository.findById(strId).orElse(null);
            case "prolt-margin" -> proltMarginRepository.findById(strId).orElse(null);
            case "ctu-charges" -> ctuChargesRepository.findById(strId).orElse(null);
            case "state-charges" -> stateChargesRepository.findById(strId).orElse(null);
            case "state-tariff" -> stateTariffRepository.findById(strId).orElse(null);
            case "fppa-charges" -> fppaChargesRepository.findById(strId).orElse(null);
            default -> null;
        };
    }

    private String getEntityId(Object entity) {
        try {
            var m = entity.getClass().getMethod("getId");
            return String.valueOf(m.invoke(entity));
        } catch (Exception e) {
            return "0";
        }
    }

    private void logAudit(String resourceType, String recordId, String action, Object oldData, Object newData) {
        try {
            AuditLog logEntry = AuditLog.create();
            logEntry.setTableName(resourceType);
            logEntry.setRecordId(recordId);
            logEntry.setAction(action);
            logEntry.setOldData(oldData != null ? objectMapper.writeValueAsString(oldData) : null);
            logEntry.setNewData(newData != null ? objectMapper.writeValueAsString(newData) : null);
            logEntry.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    private String getString(Map<String, Object> p, String... keys) {
        for (String k : keys) {
            if (p.containsKey(k) && p.get(k) != null) {
                return String.valueOf(p.get(k)).trim();
            }
        }
        return null;
    }

    private Integer getInteger(Map<String, Object> p, String... keys) {
        for (String k : keys) {
            if (p.containsKey(k) && p.get(k) != null) {
                try {
                    return Integer.parseInt(String.valueOf(p.get(k)).trim());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private BigDecimal getDecimal(Map<String, Object> p, String... keys) {
        for (String k : keys) {
            if (p.containsKey(k) && p.get(k) != null) {
                String str = String.valueOf(p.get(k)).trim().replace(",", "");
                try {
                    return new BigDecimal(str);
                } catch (Exception ignored) {}
            }
        }
        return BigDecimal.ZERO;
    }
}

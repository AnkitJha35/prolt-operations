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
import com.sense.prolt_operations.entity.StateUtEnum;
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
        assertSupportedResourceType(resourceType);
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
            default -> throw new BadRequestException("Unsupported resource type");
        };
    }

    public Object createResourceRecord(String resourceType, Map<String, Object> payload) {
        assertSupportedResourceType(resourceType);
        if (payload == null || payload.isEmpty()) {
            throw new BadRequestException("Payload cannot be empty");
        }
        Map<String, Object> sanitized = sanitizePayload(payload);
        validateRequiredFields(resourceType, sanitized);
        checkDateOverlaps(resourceType, List.of(sanitized));

        Object saved = persistSingleRecord(resourceType, sanitized);
        logAudit(resourceType, getEntityId(saved), "CREATE", null, saved);
        return saved;
    }

    public List<?> createBulkResourceRecords(String resourceType, List<Map<String, Object>> payloads) {
        assertSupportedResourceType(resourceType);
        List<Map<String, Object>> sanitizedList = payloads.stream().map(this::sanitizePayload).toList();
        for (Map<String, Object> p : sanitizedList) {
            if (!p.containsKey("id") || p.get("id") == null) {
                validateRequiredFields(resourceType, p);
            }
        }
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
        assertSupportedResourceType(resourceType);
        Map<String, Object> sanitized = sanitizePayload(payload);
        sanitized.put("id", id);
        checkDateOverlaps(resourceType, List.of(sanitized));

        Object existing = findEntityById(resourceType, id);
        if (existing == null) {
            throw new NotFoundException("Record not found");
        }

        Object updated = updateSingleRecord(resourceType, id, sanitized, existing);
        logAudit(resourceType, String.valueOf(id), "UPDATE", existing, updated);
        return updated;
    }

    public void deleteResourceRecord(String resourceType, Integer id) {
        assertSupportedResourceType(resourceType);
        Object existing = findEntityById(resourceType, id);
        if (existing == null) {
            throw new NotFoundException("Record not found");
        }

        deleteSingleRecord(resourceType, id);
        logAudit(resourceType, String.valueOf(id), "DELETE", existing, null);
    }

    public void assertSupportedResourceType(String resourceType) {
        List<String> valid = List.of(
            "region-state", "discom-list", "ists-charges", "iex-fees",
            "prolt-margin", "ctu-charges", "state-charges", "state-tariff", "fppa-charges"
        );
        if (resourceType == null || !valid.contains(resourceType)) {
            throw new BadRequestException("Unsupported resource type");
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
        String startFieldAlt = isStateCharges ? "from_date" : "start_date";
        String endFieldAlt = isStateCharges ? "to_date" : "end_date";

        for (int i = 0; i < payloads.size(); i++) {
            Map<String, Object> p1 = payloads.get(i);
            LocalDate s1 = parseDate(getValue(p1, startField, startFieldAlt));
            LocalDate e1 = parseDate(getValue(p1, endField, endFieldAlt));
            if (s1 == null || e1 == null) continue;

            for (int j = i + 1; j < payloads.size(); j++) {
                Map<String, Object> p2 = payloads.get(j);
                if (!Objects.equals(getGroupKey(p1, isStateCharges), getGroupKey(p2, isStateCharges))) continue;
                LocalDate s2 = parseDate(getValue(p2, startField, startFieldAlt));
                LocalDate e2 = parseDate(getValue(p2, endField, endFieldAlt));
                if (s2 == null || e2 == null) continue;

                if (!s1.isAfter(e2) && !s2.isAfter(e1)) {
                    throw new BadRequestException("Overlap detected within upload data for dates");
                }
            }
        }

        List<?> existingRecords = isStateCharges ? stateChargesRepository.findAll() : istsChargesRepository.findAll();
        for (Map<String, Object> p1 : payloads) {
            LocalDate s1 = parseDate(getValue(p1, startField, startFieldAlt));
            LocalDate e1 = parseDate(getValue(p1, endField, endFieldAlt));
            if (s1 == null || e1 == null) continue;
            Integer p1Id = getInteger(p1, "id");

            for (Object rec : existingRecords) {
                if (rec instanceof StateCharges sc) {
                    if (p1Id != null && p1Id.equals(sc.getId())) continue;
                    if (!Objects.equals(getGroupKey(p1, true), getEntityGroupKey(sc))) continue;
                    LocalDate s2 = sc.getFromDate();
                    LocalDate e2 = sc.getToDate();
                    if (s2 == null || e2 == null) continue;
                    if (!s1.isAfter(e2) && !s2.isAfter(e1)) {
                        throw new BadRequestException("Overlap detected with existing record for dates");
                    }
                } else if (rec instanceof IstsCharges ic) {
                    if (p1Id != null && p1Id.equals(ic.getId())) continue;
                    LocalDate s2 = ic.getStartDate();
                    LocalDate e2 = ic.getEndDate();
                    if (s2 == null || e2 == null) continue;
                    if (!s1.isAfter(e2) && !s2.isAfter(e1)) {
                        throw new BadRequestException("Overlap detected with existing record for dates");
                    }
                }
            }
        }
    }

    private String getEntityGroupKey(StateCharges sc) {
        return String.format("%s_%s_%s_%s_%s_%s",
            sc.getState() != null ? sc.getState() : "",
            sc.getDiscom() != null ? sc.getDiscom() : "",
            sc.getCategory() != null ? sc.getCategory() : "",
            sc.getSubCategory() != null ? sc.getSubCategory() : "",
            sc.getSupplyVoltageCategory() != null ? sc.getSupplyVoltageCategory() : "",
            sc.getVoltageLevel() != null ? sc.getVoltageLevel() : ""
        );
    }

    private String getGroupKey(Map<String, Object> p, boolean isStateCharges) {
        if (isStateCharges) {
            return String.format("%s_%s_%s_%s_%s_%s",
                getString(p, "state") != null ? getString(p, "state") : "",
                getString(p, "discom") != null ? getString(p, "discom") : "",
                getString(p, "category") != null ? getString(p, "category") : "",
                getString(p, "subCategory", "sub_category") != null ? getString(p, "subCategory", "sub_category") : "",
                getString(p, "supplyVoltageCategory", "supply_voltage_category") != null ? getString(p, "supplyVoltageCategory", "supply_voltage_category") : "",
                getString(p, "voltageLevel", "voltage_level") != null ? getString(p, "voltageLevel", "voltage_level") : ""
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
                entity.setRegionalGrid(getString(p, "regionalGrid", "regional_grid"));
                entity.setRegionCode(getString(p, "regionCode", "region_code"));
                entity.setRegionName(getString(p, "regionName", "region_name", "region"));
                entity.setStateName(getString(p, "stateName", "state_name", "state"));
                entity.setStateCode(getString(p, "stateCode", "state_code"));
                entity.setStateOrUt(parseStateOrUt(getValue(p, "stateOrUt", "state_or_ut")));
                entity.setUpdatedAt(LocalDateTime.now());
                yield regionStateRepository.save(entity);
            }
            case "discom-list" -> {
                DiscomList entity = DiscomList.create();
                entity.setCode(getString(p, "code"));
                entity.setLegalName(getString(p, "legalName", "legal_name", "discom"));
                entity.setStateCode(getString(p, "stateCode", "state_code", "state"));
                entity.setDiscomType(getString(p, "discomType", "discom_type"));
                entity.setUpdatedAt(LocalDateTime.now());
                yield discomListRepository.save(entity);
            }
            case "ists-charges" -> {
                IstsCharges entity = IstsCharges.create();
                entity.setIstsLossPercent(getDecimal(p, BigDecimal.ZERO, "istsLossPercent", "ists_loss_percent"));
                entity.setStartDate(parseDate(getValue(p, "startDate", "start_date")));
                entity.setEndDate(parseDate(getValue(p, "endDate", "end_date")));
                entity.setUpdatedAt(LocalDateTime.now());
                yield istsChargesRepository.save(entity);
            }
            case "iex-fees" -> {
                IexFees entity = IexFees.create();
                entity.setMonth(parseMonth(getValue(p, "month", "Month", "billMonth", "bill_month"), "month"));
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
                entity.setMonth(parseMonth(getValue(p, "month", "Month", "billMonth", "bill_month"), "month"));
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
                entity.setMonth(parseMonth(getValue(p, "month", "Month", "billMonth", "bill_month"), "month"));
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
                entity.setFromDate(parseDate(getValue(p, "fromDate", "from_date")));
                entity.setToDate(parseDate(getValue(p, "toDate", "to_date")));
                entity.setDemandFixedChargeKvaPerMonthRs(getDecimal(p, "demandFixedChargeKvaPerMonthRs", "demand_fixed_charge_kva_per_month_rs"));
                entity.setCrossSubsidy(getDecimal(p, "crossSubsidy", "cross_subsidy"));
                entity.setDistributionWheelingCharges(getDecimal(p, "distributionWheelingCharges", "distribution_wheeling_charges"));
                entity.setStuCharges(getDecimal(p, "stuCharges", "stu_charges"));
                entity.setStuLossPercent(getDecimal(p, "stuLossPercent", "stu_loss_percent"));
                entity.setWheelingLossPercent(getDecimal(p, "wheelingLossPercent", "wheeling_loss_percent"));
                entity.setAdditionalCharge(getDecimal(p, "additionalCharge", "additional_charge"));
                entity.setDiscom(getString(p, "discom"));
                entity.setUpdatedAt(LocalDateTime.now());
                yield stateChargesRepository.save(entity);
            }
            case "state-tariff" -> {
                StateTariff entity = StateTariff.create();
                entity.setState(getString(p, "state"));
                entity.setDiscom(getString(p, "discom"));
                entity.setConsumerCategory(getString(p, "consumerCategory", "consumer_category", "category"));
                entity.setSubCategory(getString(p, "subCategory", "sub_category") != null ? getString(p, "subCategory", "sub_category") : "");
                entity.setSupplyVoltageCategory(getString(p, "supplyVoltageCategory", "supply_voltage_category") != null ? getString(p, "supplyVoltageCategory", "supply_voltage_category") : "");
                entity.setSupplyVoltage(getString(p, "supplyVoltage", "supply_voltage", "voltageLevel", "voltage_level") != null ? getString(p, "supplyVoltage", "supply_voltage", "voltageLevel", "voltage_level") : "");
                entity.setMonth(parseMonth(getValue(p, "month", "Month", "billMonth", "bill_month"), "month"));
                entity.setTodStartTime(parseTodTime(getValue(p, "todStartTime", "tod_start_time", "todStartHour"), "00:00"));
                entity.setTodEndTime(parseTodTime(getValue(p, "todEndTime", "tod_end_time", "todEndHour"), "24:00"));
                entity.setBaseEnergyRate(getDecimal(p, BigDecimal.ZERO, "baseEnergyRate", "base_energy_rate", "baseEnergyCharges"));
                entity.setBaseEnergyUnit(getString(p, "baseEnergyUnit", "base_energy_unit") != null ? getString(p, "baseEnergyUnit", "base_energy_unit") : "kWh");
                entity.setTodChargePercent(getInteger(p, 0, "todChargePercent", "tod_charge_percent"));
                entity.setEnergyRate(getDecimal(p, BigDecimal.ZERO, "energyRate", "energy_rate", "energyCharges", "tariff"));
                entity.setConsumptionMonth(parseMonth(getValue(p, "consumptionMonth", "consumption_month"), "consumptionMonth"));
                entity.setUpdatedAt(LocalDateTime.now());
                yield stateTariffRepository.save(entity);
            }
            case "fppa-charges" -> {
                FppaCharges entity = FppaCharges.create();
                entity.setState(getString(p, "state"));
                entity.setDiscom(getString(p, "discom"));
                entity.setMonth(parseMonth(getValue(p, "month", "Month", "billMonth", "bill_month"), "month"));
                entity.setBillingMonth(parseMonth(getValue(p, "billingMonth", "billing_month"), "billingMonth"));
                entity.setFppaChargePercent(getDecimal(p, "fppaChargePercent", "fppa_charge_percent", "charges"));
                entity.setUpdatedAt(LocalDateTime.now());
                yield fppaChargesRepository.save(entity);
            }
            default -> throw new BadRequestException("Unsupported resource type: " + resourceType);
        };
    }

    private Object updateSingleRecord(String resourceType, Integer id, Map<String, Object> p, Object existing) {
        if (existing instanceof RegionState e) {
            if (hasKey(p, "regionalGrid", "regional_grid")) e.setRegionalGrid(getString(p, "regionalGrid", "regional_grid"));
            if (hasKey(p, "regionCode", "region_code")) e.setRegionCode(getString(p, "regionCode", "region_code"));
            if (hasKey(p, "regionName", "region_name", "region")) e.setRegionName(getString(p, "regionName", "region_name", "region"));
            if (hasKey(p, "stateName", "state_name", "state")) e.setStateName(getString(p, "stateName", "state_name", "state"));
            if (hasKey(p, "stateCode", "state_code")) e.setStateCode(getString(p, "stateCode", "state_code"));
            if (hasKey(p, "stateOrUt", "state_or_ut")) e.setStateOrUt(parseStateOrUt(getValue(p, "stateOrUt", "state_or_ut")));
            e.setUpdatedAt(LocalDateTime.now());
            return regionStateRepository.save(e);
        } else if (existing instanceof DiscomList e) {
            if (hasKey(p, "code")) e.setCode(getString(p, "code"));
            if (hasKey(p, "legalName", "legal_name", "discom")) e.setLegalName(getString(p, "legalName", "legal_name", "discom"));
            if (hasKey(p, "stateCode", "state_code", "state")) e.setStateCode(getString(p, "stateCode", "state_code", "state"));
            if (hasKey(p, "discomType", "discom_type")) e.setDiscomType(getString(p, "discomType", "discom_type"));
            e.setUpdatedAt(LocalDateTime.now());
            return discomListRepository.save(e);
        } else if (existing instanceof IstsCharges e) {
            if (hasKey(p, "istsLossPercent", "ists_loss_percent")) e.setIstsLossPercent(getDecimal(p, BigDecimal.ZERO, "istsLossPercent", "ists_loss_percent"));
            if (hasKey(p, "startDate", "start_date")) e.setStartDate(parseDate(getValue(p, "startDate", "start_date")));
            if (hasKey(p, "endDate", "end_date")) e.setEndDate(parseDate(getValue(p, "endDate", "end_date")));
            e.setUpdatedAt(LocalDateTime.now());
            return istsChargesRepository.save(e);
        } else if (existing instanceof IexFees e) {
            if (hasKey(p, "month", "Month", "billMonth", "bill_month")) e.setMonth(parseMonth(getValue(p, "month", "Month", "billMonth", "bill_month"), "month"));
            if (hasKey(p, "exchangeFees", "exchange_fees", "fees")) e.setExchangeFees(getDecimal(p, "exchangeFees", "exchange_fees", "fees"));
            if (hasKey(p, "exchangeFeesGst", "exchange_fees_gst")) e.setExchangeFeesGst(getDecimal(p, "exchangeFeesGst", "exchange_fees_gst"));
            if (hasKey(p, "nldcApplicationFees", "nldc_application_fees")) e.setNldcApplicationFees(getDecimal(p, "nldcApplicationFees", "nldc_application_fees"));
            if (hasKey(p, "nldcSchedulingFees", "nldc_scheduling_fees")) e.setNldcSchedulingFees(getDecimal(p, "nldcSchedulingFees", "nldc_scheduling_fees"));
            if (hasKey(p, "sldcSchedulingFees", "sldc_scheduling_fees")) e.setSldcSchedulingFees(getDecimal(p, "sldcSchedulingFees", "sldc_scheduling_fees"));
            if (hasKey(p, "otherFixCharges", "other_fix_charges")) e.setOtherFixCharges(getDecimal(p, "otherFixCharges", "other_fix_charges"));
            e.setUpdatedAt(LocalDateTime.now());
            return iexFeesRepository.save(e);
        } else if (existing instanceof ProltMargin e) {
            if (hasKey(p, "month", "Month", "billMonth", "bill_month")) e.setMonth(parseMonth(getValue(p, "month", "Month", "billMonth", "bill_month"), "month"));
            if (hasKey(p, "customerId", "customer_id")) e.setCustomerId(getString(p, "customerId", "customer_id"));
            if (hasKey(p, "tradingMargin", "trading_margin")) e.setTradingMargin(getDecimal(p, "tradingMargin", "trading_margin"));
            if (hasKey(p, "tradingMarginGst", "trading_margin_gst")) e.setTradingMarginGst(getDecimal(p, "tradingMarginGst", "trading_margin_gst"));
            if (hasKey(p, "proltMargin", "prolt_margin", "margin")) e.setProltMargin(getDecimal(p, "proltMargin", "prolt_margin", "margin"));
            if (hasKey(p, "proltMarginGst", "prolt_margin_gst")) e.setProltMarginGst(getDecimal(p, "proltMarginGst", "prolt_margin_gst"));
            e.setUpdatedAt(LocalDateTime.now());
            return proltMarginRepository.save(e);
        } else if (existing instanceof CtuCharges e) {
            if (hasKey(p, "state")) e.setState(getString(p, "state"));
            if (hasKey(p, "month", "Month", "billMonth", "bill_month")) e.setMonth(parseMonth(getValue(p, "month", "Month", "billMonth", "bill_month"), "month"));
            if (hasKey(p, "ctuChargesRsPerKwh", "ctu_charges_rs_per_kwh", "charge")) e.setCtuChargesRsPerKwh(getDecimal(p, "ctuChargesRsPerKwh", "ctu_charges_rs_per_kwh", "charge"));
            e.setUpdatedAt(LocalDateTime.now());
            return ctuChargesRepository.save(e);
        } else if (existing instanceof StateCharges e) {
            if (hasKey(p, "state")) e.setState(getString(p, "state"));
            if (hasKey(p, "category")) e.setCategory(getString(p, "category"));
            if (hasKey(p, "subCategory", "sub_category")) e.setSubCategory(getString(p, "subCategory", "sub_category"));
            if (hasKey(p, "supplyVoltageCategory", "supply_voltage_category")) e.setSupplyVoltageCategory(getString(p, "supplyVoltageCategory", "supply_voltage_category"));
            if (hasKey(p, "voltageLevel", "voltage_level")) e.setVoltageLevel(getString(p, "voltageLevel", "voltage_level"));
            if (hasKey(p, "fromDate", "from_date")) e.setFromDate(parseDate(getValue(p, "fromDate", "from_date")));
            if (hasKey(p, "toDate", "to_date")) e.setToDate(parseDate(getValue(p, "toDate", "to_date")));
            if (hasKey(p, "demandFixedChargeKvaPerMonthRs", "demand_fixed_charge_kva_per_month_rs")) e.setDemandFixedChargeKvaPerMonthRs(getDecimal(p, "demandFixedChargeKvaPerMonthRs", "demand_fixed_charge_kva_per_month_rs"));
            if (hasKey(p, "crossSubsidy", "cross_subsidy")) e.setCrossSubsidy(getDecimal(p, "crossSubsidy", "cross_subsidy"));
            if (hasKey(p, "distributionWheelingCharges", "distribution_wheeling_charges")) e.setDistributionWheelingCharges(getDecimal(p, "distributionWheelingCharges", "distribution_wheeling_charges"));
            if (hasKey(p, "stuCharges", "stu_charges")) e.setStuCharges(getDecimal(p, "stuCharges", "stu_charges"));
            if (hasKey(p, "stuLossPercent", "stu_loss_percent")) e.setStuLossPercent(getDecimal(p, "stuLossPercent", "stu_loss_percent"));
            if (hasKey(p, "wheelingLossPercent", "wheeling_loss_percent")) e.setWheelingLossPercent(getDecimal(p, "wheelingLossPercent", "wheeling_loss_percent"));
            if (hasKey(p, "additionalCharge", "additional_charge")) e.setAdditionalCharge(getDecimal(p, "additionalCharge", "additional_charge"));
            if (hasKey(p, "discom")) e.setDiscom(getString(p, "discom"));
            e.setUpdatedAt(LocalDateTime.now());
            return stateChargesRepository.save(e);
        } else if (existing instanceof StateTariff e) {
            if (hasKey(p, "state")) e.setState(getString(p, "state"));
            if (hasKey(p, "discom")) e.setDiscom(getString(p, "discom"));
            if (hasKey(p, "consumerCategory", "consumer_category", "category")) e.setConsumerCategory(getString(p, "consumerCategory", "consumer_category", "category"));
            if (hasKey(p, "subCategory", "sub_category")) e.setSubCategory(getString(p, "subCategory", "sub_category"));
            if (hasKey(p, "supplyVoltageCategory", "supply_voltage_category")) e.setSupplyVoltageCategory(getString(p, "supplyVoltageCategory", "supply_voltage_category"));
            if (hasKey(p, "supplyVoltage", "supply_voltage", "voltageLevel", "voltage_level")) e.setSupplyVoltage(getString(p, "supplyVoltage", "supply_voltage", "voltageLevel", "voltage_level"));
            if (hasKey(p, "month", "Month", "billMonth", "bill_month")) e.setMonth(parseMonth(getValue(p, "month", "Month", "billMonth", "bill_month"), "month"));
            if (hasKey(p, "todStartTime", "tod_start_time", "todStartHour")) e.setTodStartTime(parseTodTime(getValue(p, "todStartTime", "tod_start_time", "todStartHour"), "00:00"));
            if (hasKey(p, "todEndTime", "tod_end_time", "todEndHour")) e.setTodEndTime(parseTodTime(getValue(p, "todEndTime", "tod_end_time", "todEndHour"), "24:00"));
            if (hasKey(p, "baseEnergyRate", "base_energy_rate", "baseEnergyCharges")) e.setBaseEnergyRate(getDecimal(p, BigDecimal.ZERO, "baseEnergyRate", "base_energy_rate", "baseEnergyCharges"));
            if (hasKey(p, "baseEnergyUnit", "base_energy_unit")) e.setBaseEnergyUnit(getString(p, "baseEnergyUnit", "base_energy_unit"));
            if (hasKey(p, "todChargePercent", "tod_charge_percent")) e.setTodChargePercent(getInteger(p, 0, "todChargePercent", "tod_charge_percent"));
            if (hasKey(p, "energyRate", "energy_rate", "energyCharges", "tariff")) e.setEnergyRate(getDecimal(p, BigDecimal.ZERO, "energyRate", "energy_rate", "energyCharges", "tariff"));
            if (hasKey(p, "consumptionMonth", "consumption_month")) e.setConsumptionMonth(parseMonth(getValue(p, "consumptionMonth", "consumption_month"), "consumptionMonth"));
            e.setUpdatedAt(LocalDateTime.now());
            return stateTariffRepository.save(e);
        } else if (existing instanceof FppaCharges e) {
            if (hasKey(p, "state")) e.setState(getString(p, "state"));
            if (hasKey(p, "discom")) e.setDiscom(getString(p, "discom"));
            if (hasKey(p, "month", "Month", "billMonth", "bill_month")) e.setMonth(parseMonth(getValue(p, "month", "Month", "billMonth", "bill_month"), "month"));
            if (hasKey(p, "billingMonth", "billing_month")) e.setBillingMonth(parseMonth(getValue(p, "billingMonth", "billing_month"), "billingMonth"));
            if (hasKey(p, "fppaChargePercent", "fppa_charge_percent", "charges")) e.setFppaChargePercent(getDecimal(p, "fppaChargePercent", "fppa_charge_percent", "charges"));
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

    private String getModelName(String resourceType) {
        return switch (resourceType) {
            case "region-state" -> "regionState";
            case "discom-list" -> "discomList";
            case "ists-charges" -> "istsCharges";
            case "iex-fees" -> "iexFees";
            case "prolt-margin" -> "proltMargin";
            case "ctu-charges" -> "ctuCharges";
            case "state-charges" -> "stateCharges";
            case "state-tariff" -> "stateTariff";
            case "fppa-charges" -> "fppaCharges";
            default -> resourceType;
        };
    }

    private void logAudit(String resourceType, String recordId, String action, Object oldData, Object newData) {
        try {
            AuditLog logEntry = AuditLog.create();
            logEntry.setTableName(getModelName(resourceType));
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

    private boolean hasKey(Map<String, Object> p, String... keys) {
        for (String k : keys) {
            if (p.containsKey(k)) return true;
        }
        return false;
    }

    private Object getValue(Map<String, Object> p, String... keys) {
        for (String k : keys) {
            if (p.containsKey(k) && p.get(k) != null) {
                return p.get(k);
            }
        }
        return null;
    }

    private String getString(Map<String, Object> p, String... keys) {
        for (String k : keys) {
            if (p.containsKey(k) && p.get(k) != null) {
                String str = String.valueOf(p.get(k)).trim();
                return str.isEmpty() ? null : str;
            }
        }
        return null;
    }

    private void validateRequiredFields(String resourceType, Map<String, Object> p) {
        List<String> required = switch (resourceType) {
            case "region-state" -> List.of("stateName");
            case "discom-list" -> List.of("legalName");
            case "ists-charges" -> List.of("startDate", "endDate", "istsLossPercent");
            case "iex-fees" -> List.of("month");
            case "prolt-margin" -> List.of("month", "customerId");
            case "ctu-charges" -> List.of("state", "month");
            case "state-charges" -> List.of("state", "fromDate", "toDate");
            case "state-tariff" -> List.of("state", "month");
            case "fppa-charges" -> List.of("state", "month");
            default -> List.of();
        };

        for (String field : required) {
            Object val = switch (field) {
                case "stateName" -> getValue(p, "stateName", "state_name", "state");
                case "legalName" -> getValue(p, "legalName", "legal_name", "discom");
                case "startDate" -> getValue(p, "startDate", "start_date");
                case "endDate" -> getValue(p, "endDate", "end_date");
                case "istsLossPercent" -> getValue(p, "istsLossPercent", "ists_loss_percent");
                case "month" -> getValue(p, "month", "Month", "billMonth", "bill_month");
                case "customerId" -> getValue(p, "customerId", "customer_id");
                case "state" -> getValue(p, "state");
                case "fromDate" -> getValue(p, "fromDate", "from_date");
                case "toDate" -> getValue(p, "toDate", "to_date");
                default -> p.get(field);
            };

            if (val == null || (val instanceof String s && s.trim().isEmpty())) {
                throw new BadRequestException("Validation error: " + field + " is required");
            }
        }
    }

    private Integer getInteger(Map<String, Object> p, String... keys) {
        return getInteger(p, null, keys);
    }

    private Integer getInteger(Map<String, Object> p, Integer defaultValue, String... keys) {
        String fieldName = keys != null && keys.length > 0 ? keys[0] : "value";
        for (String k : keys) {
            if (p.containsKey(k) && p.get(k) != null) {
                String str = cleanNumericString(p.get(k));
                if (str != null) {
                    int val;
                    try {
                        val = Integer.parseInt(str);
                    } catch (Exception e) {
                        throw new BadRequestException("Validation error: " + fieldName + " must be a finite number");
                    }
                    if (val < 0) {
                        throw new BadRequestException("Validation error: " + fieldName + " cannot be negative");
                    }
                    return val;
                }
            }
        }
        return defaultValue;
    }

    private BigDecimal getDecimal(Map<String, Object> p, String... keys) {
        return getDecimal(p, null, keys);
    }

    private BigDecimal getDecimal(Map<String, Object> p, BigDecimal defaultValue, String... keys) {
        String fieldName = keys != null && keys.length > 0 ? keys[0] : "value";
        for (String k : keys) {
            if (p.containsKey(k) && p.get(k) != null) {
                String str = cleanNumericString(p.get(k));
                if (str != null) {
                    BigDecimal bd;
                    try {
                        bd = new BigDecimal(str);
                    } catch (Exception e) {
                        throw new BadRequestException("Validation error: " + fieldName + " must be a finite number");
                    }
                    if (bd.compareTo(BigDecimal.ZERO) < 0 && !"fppaChargePercent".equals(fieldName) && !"fppa_charge_percent".equals(fieldName) && !"charges".equals(fieldName)) {
                        throw new BadRequestException("Validation error: " + fieldName + " cannot be negative");
                    }
                    if (("istsLossPercent".equals(fieldName) || "ists_loss_percent".equals(fieldName)
                            || "stuLossPercent".equals(fieldName) || "stu_loss_percent".equals(fieldName)
                            || "wheelingLossPercent".equals(fieldName) || "wheeling_loss_percent".equals(fieldName))
                            && bd.compareTo(BigDecimal.valueOf(100)) > 0) {
                        throw new BadRequestException("Validation error: " + fieldName + " cannot exceed 100");
                    }
                    return bd;
                }
            }
        }
        return defaultValue;
    }

    private String cleanNumericString(Object val) {
        if (val == null) return null;
        String s = String.valueOf(val).replaceAll("(?i)[%₹,\\s]|rs\\.?", "");
        return s.isEmpty() ? null : s;
    }

    private StateUtEnum parseStateOrUt(Object val) {
        if (val == null) return null;
        String s = String.valueOf(val).trim().toLowerCase();
        if (s.isEmpty()) return null;
        if ("state".equals(s)) return StateUtEnum.state;
        if ("ut".equals(s)) return StateUtEnum.ut;
        throw new BadRequestException("Validation error: stateOrUt must be 'state' or 'ut'");
    }

    private String parseTodTime(Object val, String defaultValue) {
        if (val == null) return defaultValue;
        if (val instanceof Number n) {
            return String.format("%02d:00", n.intValue());
        }
        String s = String.valueOf(val).trim();
        if (s.isEmpty()) return defaultValue;
        if (s.matches("^\\d+$")) {
            try {
                return String.format("%02d:00", Integer.parseInt(s));
            } catch (Exception ignored) {}
        }
        return s;
    }

    private Integer parseMonth(Object val, String fieldName) {
        if (val == null) return null;
        if (val instanceof Number num) {
            int n = num.intValue();
            return validateMonthNumber(n, fieldName);
        }
        String str = val.toString().toLowerCase().trim();
        if (str.isEmpty()) return null;

        try {
            int n = Integer.parseInt(str);
            return validateMonthNumber(n, fieldName);
        } catch (NumberFormatException ignored) {}

        var dashMatcher = java.util.regex.Pattern.compile("^(\\d{4})-(\\d{2})$").matcher(str);
        if (dashMatcher.matches()) {
            int y = Integer.parseInt(dashMatcher.group(1));
            int m = Integer.parseInt(dashMatcher.group(2));
            return validateMonthNumber(y * 100 + m, fieldName);
        }

        var slashMatcher = java.util.regex.Pattern.compile("^(\\d{2})/(\\d{4})$").matcher(str);
        if (slashMatcher.matches()) {
            int m = Integer.parseInt(slashMatcher.group(1));
            int y = Integer.parseInt(slashMatcher.group(2));
            return validateMonthNumber(y * 100 + m, fieldName);
        }

        Integer year = null;
        var yearMatcher = java.util.regex.Pattern.compile("\\b(20\\d{2})\\b").matcher(str);
        if (yearMatcher.find()) {
            year = Integer.parseInt(yearMatcher.group(1));
        }

        Map<String, Integer> monthMap = Map.ofEntries(
            Map.entry("january", 1), Map.entry("jan", 1),
            Map.entry("february", 2), Map.entry("feb", 2),
            Map.entry("march", 3), Map.entry("mar", 3),
            Map.entry("april", 4), Map.entry("apr", 4),
            Map.entry("may", 5),
            Map.entry("june", 6), Map.entry("jun", 6),
            Map.entry("july", 7), Map.entry("jul", 7),
            Map.entry("august", 8), Map.entry("aug", 8),
            Map.entry("september", 9), Map.entry("sep", 9),
            Map.entry("october", 10), Map.entry("oct", 10),
            Map.entry("november", 11), Map.entry("nov", 11),
            Map.entry("december", 12), Map.entry("dec", 12)
        );

        for (var entry : monthMap.entrySet()) {
            if (str.contains(entry.getKey())) {
                int mNum = entry.getValue();
                if (year != null) {
                    return validateMonthNumber(year * 100 + mNum, fieldName);
                }
                return validateMonthNumber(mNum, fieldName);
            }
        }

        throw new BadRequestException("Validation error: " + fieldName + " must be a number or a valid month name");
    }

    private Integer validateMonthNumber(int val, String fieldName) {
        boolean isCyclic = val >= 1 && val <= 12;
        boolean isYyyyMm = String.valueOf(val).length() == 6 && val >= 200001 && val <= 210012;
        if (!isCyclic && !isYyyyMm) {
            throw new BadRequestException("Validation error: " + fieldName + " must be between 1 and 12, or in YYYYMM format");
        }
        return val;
    }
}

package com.sense.prolt_operations.service;

import com.sense.prolt_operations.dto.calculator.ExchangeRateSlotProjection;
import com.sense.prolt_operations.entity.*;
import com.sense.prolt_operations.repository.*;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Repository and data-access aggregation service for the Legacy Saving Calculator.
 * Replicates the data-retrieval patterns of the reference Node.js savings calculator service.
 */
@Service
@Transactional(readOnly = true)
public class SavingsCalculatorDataAccessService {

    private static final Logger log = LoggerFactory.getLogger(SavingsCalculatorDataAccessService.class);

    private final ExchangeDamRateRepository exchangeDamRateRepository;
    private final RegionStateRepository regionStateRepository;
    private final StateTariffRepository stateTariffRepository;
    private final StateChargesRepository stateChargesRepository;
    private final IstsChargesRepository istsChargesRepository;
    private final CtuChargesRepository ctuChargesRepository;
    private final IexFeesRepository iexFeesRepository;
    private final FppaChargesRepository fppaChargesRepository;

    public SavingsCalculatorDataAccessService(
            ExchangeDamRateRepository exchangeDamRateRepository,
            RegionStateRepository regionStateRepository,
            StateTariffRepository stateTariffRepository,
            StateChargesRepository stateChargesRepository,
            IstsChargesRepository istsChargesRepository,
            CtuChargesRepository ctuChargesRepository,
            IexFeesRepository iexFeesRepository,
            FppaChargesRepository fppaChargesRepository) {
        this.exchangeDamRateRepository = exchangeDamRateRepository;
        this.regionStateRepository = regionStateRepository;
        this.stateTariffRepository = stateTariffRepository;
        this.stateChargesRepository = stateChargesRepository;
        this.istsChargesRepository = istsChargesRepository;
        this.ctuChargesRepository = ctuChargesRepository;
        this.iexFeesRepository = iexFeesRepository;
        this.fppaChargesRepository = fppaChargesRepository;
    }

    /**
     * Resolves user input state (e.g. "UP" or "Uttar Pradesh") into all known state formats
     * [stateCode, stateName] for cross-table querying parity with Node.js.
     */
    public List<String> resolveStateFormats(String stateInput) {
        if (stateInput == null || stateInput.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String trimmed = stateInput.trim();
        Optional<RegionState> regionOpt = regionStateRepository
                .findFirstByStateCodeIgnoreCaseOrStateNameIgnoreCase(trimmed, trimmed);

        if (regionOpt.isPresent()) {
            RegionState reg = regionOpt.get();
            Set<String> formats = new LinkedHashSet<>();
            if (reg.getStateCode() != null && !reg.getStateCode().trim().isEmpty()) {
                formats.add(reg.getStateCode().trim());
            }
            if (reg.getStateName() != null && !reg.getStateName().trim().isEmpty()) {
                formats.add(reg.getStateName().trim());
            }
            formats.add(trimmed);
            return new ArrayList<>(formats);
        }
        return List.of(trimmed);
    }

    /**
     * Fetches 96-slot multi-market rates combining DAM, RTM, and GDAM via FULL OUTER JOIN.
     */
    public List<ExchangeRateSlotProjection> getExchangeRateSlots(String stateCode, LocalDate startDate, LocalDate endDate) {
        log.debug("Fetching exchange rate slots for state={}, start={}, end={}", stateCode, startDate, endDate);
        return exchangeDamRateRepository.findCombinedExchangeRates(stateCode, startDate, endDate);
    }

    /**
     * Retrieves matching StateTariff slabs. If no exact match is found for the specified months,
     * falls back to the latest tariff for the same month or latest available tariff.
     */
    public List<StateTariff> getStateTariffs(
            List<String> stateFormats,
            String discom,
            String consumerCategory,
            String supplyVoltageCategory,
            String subCategory,
            List<Integer> monthsInPlay,
            int targetMonth) {

        Specification<StateTariff> matchSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (stateFormats != null && !stateFormats.isEmpty()) {
                predicates.add(root.get("state").in(stateFormats));
            }
            if (discom != null && discom.trim().equalsIgnoreCase("NPCL")) {
                predicates.add(cb.equal(cb.upper(root.get("discom")), "NPCL"));
            } else {
                predicates.add(cb.or(cb.isNull(root.get("discom")), cb.equal(root.get("discom"), "")));
            }
            if (consumerCategory != null && !consumerCategory.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("consumerCategory")), consumerCategory.trim().toUpperCase()));
            }
            if (supplyVoltageCategory != null && !supplyVoltageCategory.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("supplyVoltageCategory")), supplyVoltageCategory.trim().toUpperCase()));
            }
            if (subCategory != null && !subCategory.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("subCategory")), "%" + subCategory.trim().toLowerCase() + "%"));
            }
            if (monthsInPlay != null && !monthsInPlay.isEmpty()) {
                Predicate consumptionMonthMatch = root.get("consumptionMonth").in(monthsInPlay);
                Predicate monthFallbackMatch = cb.and(
                        cb.isNull(root.get("consumptionMonth")),
                        root.get("month").in(monthsInPlay)
                );
                predicates.add(cb.or(consumptionMonthMatch, monthFallbackMatch));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<StateTariff> tariffs = stateTariffRepository.findAll(matchSpec);
        if (!tariffs.isEmpty()) {
            return tariffs;
        }

        // Fallback resolution matching Node.js
        log.info("No direct tariff match for stateFormats={}, months={}. Querying fallback tariffs.", stateFormats, monthsInPlay);
        Specification<StateTariff> fallbackSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (stateFormats != null && !stateFormats.isEmpty()) {
                predicates.add(root.get("state").in(stateFormats));
            }
            if (discom != null && discom.trim().equalsIgnoreCase("NPCL")) {
                predicates.add(cb.equal(cb.upper(root.get("discom")), "NPCL"));
            } else {
                predicates.add(cb.or(cb.isNull(root.get("discom")), cb.equal(root.get("discom"), "")));
            }
            if (consumerCategory != null && !consumerCategory.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("consumerCategory")), consumerCategory.trim().toUpperCase()));
            }
            if (supplyVoltageCategory != null && !supplyVoltageCategory.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("supplyVoltageCategory")), supplyVoltageCategory.trim().toUpperCase()));
            }
            if (subCategory != null && !subCategory.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("subCategory")), "%" + subCategory.trim().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<StateTariff> allTariffs = stateTariffRepository.findAll(fallbackSpec, Sort.by(Sort.Direction.DESC, "month"));
        if (allTariffs.isEmpty()) {
            return Collections.emptyList();
        }

        StateTariff sameMonthTariff = allTariffs.stream()
                .filter(t -> {
                    Integer m = t.getConsumptionMonth() != null ? t.getConsumptionMonth() : t.getMonth();
                    return m != null && (m % 100) == targetMonth;
                })
                .findFirst()
                .orElse(null);

        StateTariff selected = sameMonthTariff != null ? sameMonthTariff : allTariffs.get(0);
        Integer chosenMonth = selected.getConsumptionMonth() != null ? selected.getConsumptionMonth() : selected.getMonth();

        return allTariffs.stream()
                .filter(t -> {
                    Integer m = t.getConsumptionMonth() != null ? t.getConsumptionMonth() : t.getMonth();
                    return Objects.equals(m, chosenMonth);
                })
                .toList();
    }

    /**
     * Retrieves StateCharges record containing losses and open access surcharges.
     */
    public Optional<StateCharges> getStateCharges(
            List<String> stateFormats,
            String discom,
            String category,
            LocalDate targetDate) {

        Specification<StateCharges> matchSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (stateFormats != null && !stateFormats.isEmpty()) {
                predicates.add(root.get("state").in(stateFormats));
            }
            if (discom != null && discom.trim().equalsIgnoreCase("NPCL")) {
                predicates.add(cb.equal(cb.upper(root.get("discom")), "NPCL"));
            } else {
                predicates.add(cb.or(cb.isNull(root.get("discom")), cb.equal(root.get("discom"), "")));
            }
            if (category != null && !category.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("category")), category.trim().toUpperCase()));
            }
            if (targetDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fromDate"), targetDate));
                predicates.add(cb.greaterThanOrEqualTo(root.get("toDate"), targetDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<StateCharges> charges = stateChargesRepository.findAll(matchSpec, Sort.by(Sort.Direction.ASC, "id"));
        if (!charges.isEmpty()) {
            return Optional.of(charges.get(0));
        }

        // Fallback without strict date matching
        Specification<StateCharges> fallbackSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (stateFormats != null && !stateFormats.isEmpty()) {
                predicates.add(root.get("state").in(stateFormats));
            }
            if (discom != null && discom.trim().equalsIgnoreCase("NPCL")) {
                predicates.add(cb.equal(cb.upper(root.get("discom")), "NPCL"));
            } else {
                predicates.add(cb.or(cb.isNull(root.get("discom")), cb.equal(root.get("discom"), "")));
            }
            if (category != null && !category.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("category")), category.trim().toUpperCase()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<StateCharges> fallback = stateChargesRepository.findAll(fallbackSpec, Sort.by(Sort.Direction.DESC, "toDate", "id"));
        return fallback.isEmpty() ? Optional.empty() : Optional.of(fallback.get(0));
    }

    /**
     * Retrieves ISTS transmission losses active for the simulation period.
     */
    public List<IstsCharges> getIstsCharges(LocalDate startDate, LocalDate endDate) {
        return istsChargesRepository.findActiveChargesBetween(startDate, endDate);
    }

    /**
     * Retrieves CTU charges for a given month or fallback month.
     */
    public Optional<CtuCharges> getCtuCharges(List<String> stateFormats, Integer month) {
        Optional<CtuCharges> ctu = ctuChargesRepository.findFirstByStateInAndMonth(stateFormats, month);
        if (ctu.isPresent()) {
            return ctu;
        }
        ctu = ctuChargesRepository.findFirstByMonth(month);
        if (ctu.isPresent()) {
            return ctu;
        }
        return ctuChargesRepository.findFirstByOrderByMonthDesc();
    }

    /**
     * Retrieves IEX application and scheduling fees for a given month or fallback month.
     */
    public Optional<IexFees> getIexFees(Integer month) {
        Optional<IexFees> fees = iexFeesRepository.findFirstByMonth(month);
        if (fees.isPresent()) {
            return fees;
        }
        return iexFeesRepository.findFirstByOrderByMonthDesc();
    }

    /**
     * Retrieves FPPA surcharge percent matching the state, discom, and billing month.
     */
    public BigDecimal getFppaPercent(List<String> stateFormats, String discom, Integer month) {
        List<FppaCharges> list = fppaChargesRepository.findByStateInAndMonth(stateFormats, month);
        if (list.isEmpty()) {
            list = fppaChargesRepository.findByStateInOrderByMonthDesc(stateFormats);
        }
        if (list.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Match by discom first
        if (discom != null && !discom.trim().isEmpty()) {
            for (FppaCharges f : list) {
                if (discom.equalsIgnoreCase(f.getDiscom())) {
                    return f.getFppaChargePercent() != null ? f.getFppaChargePercent() : BigDecimal.ZERO;
                }
            }
        }

        // Fallback to record with blank or null discom
        for (FppaCharges f : list) {
            if (f.getDiscom() == null || f.getDiscom().trim().isEmpty()) {
                return f.getFppaChargePercent() != null ? f.getFppaChargePercent() : BigDecimal.ZERO;
            }
        }

        // Return first available
        return list.get(0).getFppaChargePercent() != null ? list.get(0).getFppaChargePercent() : BigDecimal.ZERO;
    }
}

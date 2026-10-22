package com.sense.prolt_operations.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sense.prolt_operations.entity.DamRecord;
import com.sense.prolt_operations.entity.Dataset;
import com.sense.prolt_operations.entity.DatasetStatus;
import com.sense.prolt_operations.entity.GdamNewRecord;
import com.sense.prolt_operations.entity.GdamRecord;
import com.sense.prolt_operations.entity.MarketType;
import com.sense.prolt_operations.entity.RtmRecord;
import com.sense.prolt_operations.entity.UploadHistory;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.repository.DamRecordRepository;
import com.sense.prolt_operations.repository.DatasetRepository;
import com.sense.prolt_operations.repository.GdamNewRecordRepository;
import com.sense.prolt_operations.repository.GdamRecordRepository;
import com.sense.prolt_operations.repository.RtmRecordRepository;
import com.sense.prolt_operations.repository.UploadHistoryRepository;

import jakarta.persistence.criteria.Predicate;

@Service
@Transactional(readOnly = true)
public class DatasetService {

    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);
    private static final LocalDate GDAM_TRANSITION_DATE = LocalDate.of(2026, 7, 13);

    private final DatasetRepository datasetRepository;
    private final UploadHistoryRepository uploadHistoryRepository;
    private final DamRecordRepository damRecordRepository;
    private final GdamRecordRepository gdamRecordRepository;
    private final GdamNewRecordRepository gdamNewRecordRepository;
    private final RtmRecordRepository rtmRecordRepository;

    public DatasetService(DatasetRepository datasetRepository,
                          UploadHistoryRepository uploadHistoryRepository,
                          DamRecordRepository damRecordRepository,
                          GdamRecordRepository gdamRecordRepository,
                          GdamNewRecordRepository gdamNewRecordRepository,
                          RtmRecordRepository rtmRecordRepository) {
        this.datasetRepository = datasetRepository;
        this.uploadHistoryRepository = uploadHistoryRepository;
        this.damRecordRepository = damRecordRepository;
        this.gdamRecordRepository = gdamRecordRepository;
        this.gdamNewRecordRepository = gdamNewRecordRepository;
        this.rtmRecordRepository = rtmRecordRepository;
    }

    public Map<String, Object> getDatasets(Map<String, String> query) {
        int page = parseInt(query.get("page"), 1);
        int limit = parseInt(query.get("limit"), 20);
        String sortBy = query.getOrDefault("sortBy", "deliveryDate");
        String sortOrder = query.getOrDefault("sortOrder", "desc");

        Sort sort = Sort.by("asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page - 1, limit, sort);

        Specification<Dataset> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.containsKey("market") && !query.get("market").isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("market"), MarketType.valueOf(query.get("market").toUpperCase())));
                } catch (Exception ignored) {}
            }
            if (query.containsKey("status") && !query.get("status").isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("status"), DatasetStatus.valueOf(query.get("status").toUpperCase())));
                } catch (Exception ignored) {}
            }
            if (query.containsKey("fileName") && !query.get("fileName").isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("fileName")), "%" + query.get("fileName").toLowerCase() + "%"));
            }
            if (query.containsKey("deliveryDate") && !query.get("deliveryDate").isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("deliveryDate"), LocalDate.parse(query.get("deliveryDate").trim())));
                } catch (Exception ignored) {}
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Dataset> resultPage = datasetRepository.findAll(spec, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("data", resultPage.getContent());
        response.put("meta", Map.of(
            "page", page,
            "limit", limit,
            "total", resultPage.getTotalElements(),
            "totalPages", resultPage.getTotalPages()
        ));
        return response;
    }

    public Map<String, Object> getSummary() {
        long total = datasetRepository.count();
        long active = datasetRepository.countByStatus(DatasetStatus.ACTIVE);
        long replaced = datasetRepository.countByStatus(DatasetStatus.REPLACED);
        long deleted = datasetRepository.countByStatus(DatasetStatus.DELETED);
        long historyTotal = uploadHistoryRepository.count();
        var latest = uploadHistoryRepository.findFirstByOrderByTimestampDesc();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalDatasets", total);
        summary.put("activeDatasets", active);
        summary.put("replacedDatasets", replaced);
        summary.put("deletedDatasets", deleted);
        summary.put("totalUploadHistoryRecords", historyTotal);
        summary.put("latestUploadTimestamp", latest.map(UploadHistory::getTimestamp).orElse(null));
        return summary;
    }

    public Dataset getDatasetById(String id) {
        return datasetRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Dataset not found"));
    }

    public Map<String, Object> getUploadHistory(Map<String, String> query) {
        int page = parseInt(query.get("page"), 1);
        int limit = parseInt(query.get("limit"), 20);
        String sortBy = query.getOrDefault("sortBy", "timestamp");
        String sortOrder = query.getOrDefault("sortOrder", "desc");

        Sort sort = Sort.by("asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(page - 1, limit, sort);

        Specification<UploadHistory> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.containsKey("market") && !query.get("market").isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("market"), MarketType.valueOf(query.get("market").toUpperCase())));
                } catch (Exception ignored) {}
            }
            if (query.containsKey("datasetId") && !query.get("datasetId").isBlank()) {
                predicates.add(cb.equal(root.get("datasetId"), query.get("datasetId").trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<UploadHistory> resultPage = uploadHistoryRepository.findAll(spec, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("data", resultPage.getContent());
        response.put("meta", Map.of(
            "page", page,
            "limit", limit,
            "total", resultPage.getTotalElements(),
            "totalPages", resultPage.getTotalPages()
        ));
        return response;
    }

    public List<UploadHistory> getDatasetHistory(String datasetId) {
        return uploadHistoryRepository.findByDatasetIdOrderByTimestampAsc(datasetId);
    }

    public Map<String, Object> getTimeseries(String dateStr, String marketStr) {
        if (marketStr == null || (!marketStr.equalsIgnoreCase("DAM") &&
                                  !marketStr.equalsIgnoreCase("GDAM") &&
                                  !marketStr.equalsIgnoreCase("RTM"))) {
            throw new BadRequestException("Invalid market specified. Must be DAM, GDAM, or RTM");
        }

        if (dateStr == null || dateStr.isBlank()) {
            throw new BadRequestException("Date is required");
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateStr.trim());
        } catch (Exception e) {
            throw new BadRequestException("Invalid date format. Expected YYYY-MM-DD");
        }

        MarketType market = MarketType.valueOf(marketStr.trim().toUpperCase());
        var datasetOpt = datasetRepository.findFirstByMarketAndDeliveryDateAndStatus(market, date, DatasetStatus.ACTIVE);

        if (datasetOpt.isEmpty()) {
            return Map.of("success", true, "data", List.of(), "count", 0);
        }

        Dataset dataset = datasetOpt.get();
        List<Map<String, Object>> records = new ArrayList<>();

        if (market == MarketType.DAM) {
            List<DamRecord> list = damRecordRepository.findByDatasetIdOrderByIntervalNumberAsc(dataset.getId());
            for (DamRecord r : list) {
                records.add(Map.of(
                    "timeBlock", r.getIntervalNumber() != null ? r.getIntervalNumber() : 0,
                    "time", r.getIntervalTime() != null ? r.getIntervalTime() : "",
                    "mcp", r.getMcp() != null ? r.getMcp() : 0,
                    "mcv", r.getMcv() != null ? r.getMcv() : 0,
                    "purchaseBid", r.getPurchaseBid() != null ? r.getPurchaseBid() : 0,
                    "sellBid", r.getSellBid() != null ? r.getSellBid() : 0
                ));
            }
        } else if (market == MarketType.GDAM) {
            if (date.isBefore(GDAM_TRANSITION_DATE)) {
                List<GdamRecord> list = gdamRecordRepository.findByDatasetIdOrderByIntervalNumberAsc(dataset.getId());
                for (GdamRecord r : list) {
                    records.add(Map.of(
                        "timeBlock", r.getIntervalNumber() != null ? r.getIntervalNumber() : 0,
                        "time", r.getIntervalTime() != null ? r.getIntervalTime() : "",
                        "mcp", r.getMcp() != null ? r.getMcp() : 0,
                        "mcv", r.getMcvTotal() != null ? r.getMcvTotal() : 0,
                        "purchaseBid", r.getPurchaseBid() != null ? r.getPurchaseBid() : 0,
                        "sellBid", r.getSellBidTotal() != null ? r.getSellBidTotal() : 0
                    ));
                }
            } else {
                List<GdamNewRecord> list = gdamNewRecordRepository.findByDatasetIdOrderByIntervalNumberAsc(dataset.getId());
                for (GdamNewRecord r : list) {
                    records.add(Map.of(
                        "timeBlock", r.getIntervalNumber() != null ? r.getIntervalNumber() : 0,
                        "time", r.getIntervalTime() != null ? r.getIntervalTime() : "",
                        "mcp", r.getMcp() != null ? r.getMcp() : 0,
                        "mcv", r.getMcvTotal() != null ? r.getMcvTotal() : 0,
                        "purchaseBid", r.getPurchaseBid() != null ? r.getPurchaseBid() : 0,
                        "sellBid", r.getSellBidTotal() != null ? r.getSellBidTotal() : 0
                    ));
                }
            }
        } else if (market == MarketType.RTM) {
            List<RtmRecord> list = rtmRecordRepository.findByDatasetIdOrderByIntervalNumberAsc(dataset.getId());
            for (RtmRecord r : list) {
                records.add(Map.of(
                    "timeBlock", r.getIntervalNumber() != null ? r.getIntervalNumber() : 0,
                    "time", r.getIntervalTime() != null ? r.getIntervalTime() : "",
                    "mcp", r.getMcp() != null ? r.getMcp() : 0,
                    "mcv", r.getMcv() != null ? r.getMcv() : 0,
                    "purchaseBid", r.getPurchaseBid() != null ? r.getPurchaseBid() : 0,
                    "sellBid", r.getSellBid() != null ? r.getSellBid() : 0
                ));
            }
        }

        return Map.of("success", true, "data", records, "count", records.size());
    }

    private int parseInt(String val, int fallback) {
        if (val == null || val.isBlank()) return fallback;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

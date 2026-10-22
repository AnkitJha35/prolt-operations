package com.sense.prolt_operations.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sense.prolt_operations.dto.dataset.PersistDatasetDTO;
import com.sense.prolt_operations.entity.DamRecord;
import com.sense.prolt_operations.entity.Dataset;
import com.sense.prolt_operations.entity.DatasetStatus;
import com.sense.prolt_operations.entity.GdamNewRecord;
import com.sense.prolt_operations.entity.GdamRecord;
import com.sense.prolt_operations.entity.MarketType;
import com.sense.prolt_operations.entity.RecRecord;
import com.sense.prolt_operations.entity.RtmRecord;
import com.sense.prolt_operations.entity.UploadAction;
import com.sense.prolt_operations.entity.UploadHistory;
import com.sense.prolt_operations.exception.AppBaseException;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.ConflictException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.repository.DamRecordRepository;
import com.sense.prolt_operations.repository.DatasetRepository;
import com.sense.prolt_operations.repository.GdamNewRecordRepository;
import com.sense.prolt_operations.repository.GdamRecordRepository;
import com.sense.prolt_operations.repository.RecRecordRepository;
import com.sense.prolt_operations.repository.RtmRecordRepository;
import com.sense.prolt_operations.repository.UploadHistoryRepository;

@Service
@Transactional
public class PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(PersistenceService.class);
    private static final LocalDate GDAM_TRANSITION_DATE = LocalDate.of(2026, 7, 13);

    private final DatasetRepository datasetRepository;
    private final UploadHistoryRepository uploadHistoryRepository;
    private final DamRecordRepository damRecordRepository;
    private final GdamRecordRepository gdamRecordRepository;
    private final GdamNewRecordRepository gdamNewRecordRepository;
    private final RtmRecordRepository rtmRecordRepository;
    private final RecRecordRepository recRecordRepository;

    public PersistenceService(DatasetRepository datasetRepository,
                              UploadHistoryRepository uploadHistoryRepository,
                              DamRecordRepository damRecordRepository,
                              GdamRecordRepository gdamRecordRepository,
                              GdamNewRecordRepository gdamNewRecordRepository,
                              RtmRecordRepository rtmRecordRepository,
                              RecRecordRepository recRecordRepository) {
        this.datasetRepository = datasetRepository;
        this.uploadHistoryRepository = uploadHistoryRepository;
        this.damRecordRepository = damRecordRepository;
        this.gdamRecordRepository = gdamRecordRepository;
        this.gdamNewRecordRepository = gdamNewRecordRepository;
        this.rtmRecordRepository = rtmRecordRepository;
        this.recRecordRepository = recRecordRepository;
    }

    public Dataset persistDataset(PersistDatasetDTO params) {
        if (params == null || params.market() == null || params.deliveryDate() == null) {
            throw new BadRequestException("Market and deliveryDate are required");
        }

        MarketType market;
        try {
            market = MarketType.valueOf(params.market().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid market specified: " + params.market());
        }

        LocalDate deliveryDate = params.deliveryDate();
        List<Map<String, Object>> records = params.records() != null ? params.records() : List.of();
        boolean isReplacement = "replace".equalsIgnoreCase(params.action());

        // Pre-flight check: exactly 96 records (except REC and RTM)
        if (market != MarketType.REC && market != MarketType.RTM && !Boolean.TRUE.equals(params.isWebhookImport())) {
            if (records.size() != 96) {
                throw new BadRequestException("Persistence rejected: Expected exactly 96 records, got " + records.size());
            }
        }

        List<Dataset> oldDatasets = datasetRepository.findByMarketAndDeliveryDateAndStatus(market, deliveryDate, DatasetStatus.ACTIVE);
        if (oldDatasets.size() > 1) {
            throw new AppBaseException("Operational Error: Multiple ACTIVE datasets exist. Please contact support.");
        }

        Dataset oldDatasetToReplace = null;
        if (oldDatasets.size() == 1) {
            if (!isReplacement) {
                log.warn("Duplicate Check Failed: Active dataset already exists for {} on {}", market, deliveryDate);
                throw new ConflictException("Dataset already exists. Use ?action=replace to overwrite.");
            }
            oldDatasetToReplace = oldDatasets.get(0);
            log.info("Replacement Workflow Initiated: Replacing Dataset {}", oldDatasetToReplace.getId());
        } else {
            if (isReplacement) {
                log.warn("Replacement Failed: No ACTIVE dataset found for {} on {}", market, deliveryDate);
                throw new NotFoundException("Cannot replace: No ACTIVE dataset found for this market and date.");
            }
        }

        // Clean up old dataset if replacing
        if (oldDatasetToReplace != null) {
            oldDatasetToReplace.setStatus(DatasetStatus.REPLACED);
            datasetRepository.save(oldDatasetToReplace);
            deleteMarketRecords(oldDatasetToReplace.getId(), market);
            log.info("Old dataset {} marked REPLACED and child records deleted", oldDatasetToReplace.getId());
        }

        // Create new dataset
        Dataset dataset = Dataset.create();
        dataset.setMarket(market);
        dataset.setDeliveryDate(deliveryDate);
        String fileName = params.fileName() != null && !params.fileName().isBlank()
                ? params.fileName().trim()
                : String.format("scraped_%s_%s.csv", market.name().toLowerCase(), deliveryDate);
        dataset.setFileName(fileName);
        dataset.setStatus(DatasetStatus.ACTIVE);
        dataset = datasetRepository.save(dataset);

        // Create upload history
        UploadHistory history = UploadHistory.create();
        history.setDatasetId(dataset.getId());
        history.setMarket(market);
        history.setDeliveryDate(deliveryDate);
        history.setAction(isReplacement ? UploadAction.REPLACE : UploadAction.UPLOAD);
        uploadHistoryRepository.save(history);

        // Bulk insert records
        insertMarketRecords(dataset.getId(), market, deliveryDate, records);
        log.info("Successfully persisted dataset {} with {} records", dataset.getId(), records.size());

        return dataset;
    }

    public void deleteDataset(String datasetId) {
        Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new NotFoundException("Dataset not found"));

        if (dataset.getStatus() == DatasetStatus.DELETED) {
            throw new ConflictException("Dataset is already deleted");
        }
        if (dataset.getStatus() == DatasetStatus.REPLACED) {
            throw new ConflictException("Cannot delete a REPLACED historical dataset");
        }
        if (dataset.getStatus() != DatasetStatus.ACTIVE) {
            throw new ConflictException("Cannot delete dataset with status " + dataset.getStatus());
        }

        dataset.setStatus(DatasetStatus.DELETED);
        datasetRepository.save(dataset);

        deleteMarketRecords(dataset.getId(), dataset.getMarket());

        UploadHistory history = UploadHistory.create();
        history.setDatasetId(dataset.getId());
        history.setMarket(dataset.getMarket());
        history.setDeliveryDate(dataset.getDeliveryDate());
        history.setAction(UploadAction.DELETE);
        uploadHistoryRepository.save(history);

        log.info("Dataset {} deleted successfully", datasetId);
    }

    private void deleteMarketRecords(String datasetId, MarketType market) {
        if (market == MarketType.DAM) {
            damRecordRepository.deleteByDatasetId(datasetId);
        } else if (market == MarketType.GDAM) {
            gdamRecordRepository.deleteByDatasetId(datasetId);
            gdamNewRecordRepository.deleteByDatasetId(datasetId);
        } else if (market == MarketType.RTM) {
            rtmRecordRepository.deleteByDatasetId(datasetId);
        } else if (market == MarketType.REC) {
            recRecordRepository.deleteByDatasetId(datasetId);
        }
    }

    private void insertMarketRecords(String datasetId, MarketType market, LocalDate deliveryDate, List<Map<String, Object>> records) {
        if (market == MarketType.DAM) {
            List<DamRecord> list = new ArrayList<>();
            for (int i = 0; i < records.size(); i++) {
                Map<String, Object> r = records.get(i);
                DamRecord entity = DamRecord.create();
                entity.setDatasetId(datasetId);
                entity.setIntervalNumber(toShort(r.getOrDefault("intervalNumber", r.get("timeBlock")), (short) (i + 1)));
                entity.setIntervalTime(toStr(r.getOrDefault("intervalTime", r.get("time")), ""));
                entity.setPurchaseBid(toBigDecimal(r.get("purchaseBid")));
                entity.setSellBid(toBigDecimal(r.get("sellBid")));
                entity.setMcv(toBigDecimal(r.get("mcv")));
                entity.setFsv(toBigDecimal(r.getOrDefault("fsv", r.get("mcv"))));
                entity.setMcp(toBigDecimal(r.get("mcp")));
                entity.setDate(toStr(r.get("date"), deliveryDate.toString()));
                list.add(entity);
            }
            damRecordRepository.saveAll(list);
        } else if (market == MarketType.GDAM) {
            if (deliveryDate.isBefore(GDAM_TRANSITION_DATE)) {
                List<GdamRecord> list = new ArrayList<>();
                for (int i = 0; i < records.size(); i++) {
                    Map<String, Object> r = records.get(i);
                    GdamRecord entity = GdamRecord.create();
                    entity.setDatasetId(datasetId);
                    entity.setIntervalNumber(toShort(r.getOrDefault("intervalNumber", r.get("timeBlock")), (short) (i + 1)));
                    entity.setIntervalTime(toStr(r.getOrDefault("intervalTime", r.get("time")), ""));
                    entity.setPurchaseBid(toBigDecimal(r.get("purchaseBid")));
                    entity.setSellBidTotal(toBigDecimal(r.getOrDefault("sellBidTotal", r.get("sellBid"))));
                    entity.setMcvTotal(toBigDecimal(r.getOrDefault("mcvTotal", r.get("mcv"))));
                    entity.setMcp(toBigDecimal(r.get("mcp")));
                    entity.setDate(toStr(r.get("date"), deliveryDate.toString()));
                    list.add(entity);
                }
                gdamRecordRepository.saveAll(list);
            } else {
                List<GdamNewRecord> list = new ArrayList<>();
                for (int i = 0; i < records.size(); i++) {
                    Map<String, Object> r = records.get(i);
                    GdamNewRecord entity = GdamNewRecord.create();
                    entity.setDatasetId(datasetId);
                    entity.setIntervalNumber(toShort(r.getOrDefault("intervalNumber", r.get("timeBlock")), (short) (i + 1)));
                    entity.setIntervalTime(toStr(r.getOrDefault("intervalTime", r.get("time")), ""));
                    entity.setPurchaseBid(toBigDecimal(r.getOrDefault("purchaseBid", r.get("purchaseBidTotal"))));
                    entity.setSellBidTotal(toBigDecimal(r.getOrDefault("sellBidTotal", r.get("sellBid"))));
                    entity.setMcvTotal(toBigDecimal(r.getOrDefault("mcvTotal", r.get("mcv"))));
                    entity.setMcp(toBigDecimal(r.get("mcp")));
                    entity.setDate(toStr(r.get("date"), deliveryDate.toString()));
                    list.add(entity);
                }
                gdamNewRecordRepository.saveAll(list);
            }
        } else if (market == MarketType.RTM) {
            List<RtmRecord> list = new ArrayList<>();
            for (int i = 0; i < records.size(); i++) {
                Map<String, Object> r = records.get(i);
                RtmRecord entity = RtmRecord.create();
                entity.setDatasetId(datasetId);
                entity.setIntervalNumber(toShort(r.getOrDefault("intervalNumber", r.get("timeBlock")), (short) (i + 1)));
                entity.setIntervalTime(toStr(r.getOrDefault("intervalTime", r.get("time")), ""));
                entity.setSessionId(toStr(r.get("sessionId"), "S" + ((i / 2) + 1)));
                entity.setPurchaseBid(toBigDecimal(r.get("purchaseBid")));
                entity.setSellBid(toBigDecimal(r.get("sellBid")));
                entity.setMcv(toBigDecimal(r.get("mcv")));
                entity.setFsv(toBigDecimal(r.getOrDefault("fsv", r.get("mcv"))));
                entity.setMcp(toBigDecimal(r.get("mcp")));
                entity.setDate(toStr(r.get("date"), deliveryDate.toString()));
                list.add(entity);
            }
            rtmRecordRepository.saveAll(list);
        }
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number num) return BigDecimal.valueOf(num.doubleValue());
        try {
            return new BigDecimal(obj.toString().trim().replace(",", ""));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Short toShort(Object obj, short fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Number num) return num.shortValue();
        try {
            return Short.parseShort(obj.toString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private String toStr(Object obj, String fallback) {
        if (obj == null) return fallback;
        return obj.toString();
    }
}

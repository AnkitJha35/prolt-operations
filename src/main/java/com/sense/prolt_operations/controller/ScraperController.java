package com.sense.prolt_operations.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.config.AppProperties;
import com.sense.prolt_operations.dto.dataset.PersistDatasetDTO;
import com.sense.prolt_operations.dto.response.ScraperImportResponse;
import com.sense.prolt_operations.dto.response.ScraperTriggerResponse;
import com.sense.prolt_operations.entity.Dataset;
import com.sense.prolt_operations.entity.DatasetStatus;
import com.sense.prolt_operations.entity.MarketType;
import com.sense.prolt_operations.entity.StateDemandData;
import com.sense.prolt_operations.entity.StateMarketRecord;
import com.sense.prolt_operations.exception.AppBaseException;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.repository.DatasetRepository;
import com.sense.prolt_operations.repository.StateDemandDataRepository;
import com.sense.prolt_operations.repository.StateMarketRecordRepository;
import com.sense.prolt_operations.service.ApiLogService;
import com.sense.prolt_operations.service.PersistenceService;
import com.sense.prolt_operations.service.ScraperService;

@RestController
@RequestMapping("/api/scraper")
public class ScraperController {

    private static final Logger log = LoggerFactory.getLogger(ScraperController.class);

    private final ScraperService scraperService;
    private final PersistenceService persistenceService;
    private final ApiLogService apiLogService;
    private final AppProperties appProperties;
    private final DatasetRepository datasetRepository;
    private final StateDemandDataRepository stateDemandDataRepository;
    private final StateMarketRecordRepository stateMarketRecordRepository;

    public ScraperController(
            ScraperService scraperService,
            PersistenceService persistenceService,
            ApiLogService apiLogService,
            AppProperties appProperties,
            DatasetRepository datasetRepository,
            StateDemandDataRepository stateDemandDataRepository,
            StateMarketRecordRepository stateMarketRecordRepository) {
        this.scraperService = scraperService;
        this.persistenceService = persistenceService;
        this.apiLogService = apiLogService;
        this.appProperties = appProperties;
        this.datasetRepository = datasetRepository;
        this.stateDemandDataRepository = stateDemandDataRepository;
        this.stateMarketRecordRepository = stateMarketRecordRepository;
    }

    @PostMapping("/sync")
    public ResponseEntity<ScraperTriggerResponse> triggerScraper(@RequestBody Map<String, Object> body) {
        String market = body != null && body.get("market") != null ? body.get("market").toString().trim().toUpperCase() : null;
        if (market == null || (!market.equals("DAM") && !market.equals("GDAM") && !market.equals("RTM") && !market.equals("REC"))) {
            throw new BadRequestException("Invalid market specified. Must be DAM, GDAM, RTM, or REC.");
        }

        if ("REC".equals(market)) {
            apiLogService.createLog("IEX REC Scraper", "manual-trigger", "ERROR", "REC persistence not yet ported");
            throw new AppBaseException("REC persistence not yet ported", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        log.info("Manual scraper triggered for market: {}", market);
        List<Map<String, Object>> records;
        try {
            records = switch (market) {
                case "DAM" -> scraperService.scrapeDam();
                case "GDAM" -> scraperService.scrapeGdam();
                case "RTM" -> scraperService.scrapeRtm();
                case "REC" -> scraperService.scrapeRec();
                default -> List.of();
            };

            if (records == null || records.isEmpty()) {
                throw new AppBaseException("Scraper returned 0 records", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            LocalDate deliveryDate = LocalDate.now(ZoneId.of("Asia/Kolkata"));
            MarketType marketType = MarketType.valueOf(market);
            var existing = datasetRepository.findFirstByMarketAndDeliveryDateAndStatus(marketType, deliveryDate, DatasetStatus.ACTIVE);

            PersistDatasetDTO dto = new PersistDatasetDTO(
                market,
                deliveryDate,
                String.format("scraped_%s_%s.csv", market.toLowerCase(), deliveryDate),
                records,
                existing.isPresent() ? "replace" : null,
                false
            );

            Dataset dataset = persistenceService.persistDataset(dto);

            apiLogService.createLog("IEX " + market + " Scraper",
                "https://www.iexindia.com/market-data/" + market.toLowerCase(),
                "SUCCESS",
                "Successfully scraped and saved " + records.size() + " records for " + market + " (manual trigger)");

            return ResponseEntity.ok(ScraperTriggerResponse.ok(
                "Successfully scraped and saved " + records.size() + " records for " + market,
                dataset.getId()
            ));
        } catch (Exception e) {
            log.error("Scraper Controller Error: {}", e.getMessage(), e);
            apiLogService.createLog("IEX " + market + " Scraper", "manual-trigger", "ERROR", e.getMessage());
            if (e instanceof AppBaseException abe) {
                throw abe;
            }
            throw new AppBaseException(e.getMessage() != null ? e.getMessage() : "Scraper failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/import")
    public ResponseEntity<ScraperImportResponse> importScrapedData(
            @RequestHeader(value = "x-webhook-token", required = false) String webhookToken,
            @RequestHeader(value = "x-webhook-secret", required = false) String webhookSecret,
            @RequestBody(required = false) Map<String, Object> body) {

        String providedToken = webhookToken != null && !webhookToken.isBlank() ? webhookToken : webhookSecret;
        if (providedToken == null || !isSecretValid(providedToken, appProperties.getWebhookSecret())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ScraperImportResponse.error("Unauthorized"));
        }

        if (body == null || body.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ScraperImportResponse.error("Missing type or payload"));
        }

        // Direct SCRAPE-01 format: { "market": "DAM", "deliveryDate": "2026-06-21", "records": [...] }
        if (body.containsKey("market") && body.containsKey("deliveryDate") && body.containsKey("records")) {
            return handleDirectDatasetImport(body);
        }

        String type = (String) body.get("type");
        Object payloadObj = body.get("payload");

        if (type == null || payloadObj == null) {
            return ResponseEntity.badRequest()
                    .body(ScraperImportResponse.error("Missing type or payload"));
        }

        log.info("[Webhook Import] Received event: {}", type);

        try {
            if ("dataset".equalsIgnoreCase(type)) {
                if (payloadObj instanceof Map<?, ?> payloadMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) payloadMap;
                    return handleDirectDatasetImport(payload);
                }
                return ResponseEntity.badRequest().body(ScraperImportResponse.error("Invalid dataset payload"));
            } else if ("state-demand".equalsIgnoreCase(type)) {
                return handleStateDemandImport(payloadObj);
            } else if ("state-market-records".equalsIgnoreCase(type)) {
                return handleStateMarketRecordsImport(payloadObj);
            } else {
                return ResponseEntity.badRequest()
                        .body(ScraperImportResponse.error("Unknown import event type: " + type));
            }
        } catch (Exception e) {
            log.error("[Webhook Import Error]: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ScraperImportResponse.error(e.getMessage() != null ? e.getMessage() : "Import failed"));
        }
    }

    private ResponseEntity<ScraperImportResponse> handleDirectDatasetImport(Map<String, Object> payload) {
        String marketStr = (String) payload.get("market");
        String dateStr = (String) payload.get("deliveryDate");
        String fileName = (String) payload.getOrDefault("fileName", "webhook_" + marketStr + "_" + dateStr + ".csv");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) payload.get("records");
        String action = (String) payload.get("action");

        MarketType market = MarketType.valueOf(marketStr.trim().toUpperCase());
        LocalDate deliveryDate = LocalDate.parse(dateStr.trim().substring(0, 10));

        var existing = datasetRepository.findFirstByMarketAndDeliveryDateAndStatus(market, deliveryDate, DatasetStatus.ACTIVE);
        if (existing.isPresent()) {
            action = "replace";
        }

        PersistDatasetDTO dto = new PersistDatasetDTO(
            marketStr,
            deliveryDate,
            fileName,
            records != null ? records : List.of(),
            action,
            true
        );

        Dataset dataset = persistenceService.persistDataset(dto);
        log.info("[Webhook Import] Successfully imported dataset for {} on {}", market, deliveryDate);

        return ResponseEntity.ok(ScraperImportResponse.ok(
            String.format("Successfully imported %d records for %s %s",
                records != null ? records.size() : 0, marketStr, dateStr),
            dataset.getId()
        ));
    }

    private ResponseEntity<ScraperImportResponse> handleStateDemandImport(Object payloadObj) {
        if (payloadObj instanceof Map<?, ?> payloadMap) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) payloadMap.get("records");
            if (records != null && !records.isEmpty()) {
                List<StateDemandData> list = new ArrayList<>();
                for (Map<String, Object> r : records) {
                    StateDemandData entity = StateDemandData.create();
                    entity.setStateName((String) r.get("stateName"));
                    entity.setDate((String) r.get("date"));
                    entity.setTimeStr((String) r.get("timeStr"));
                    if (r.get("demand") != null) {
                        entity.setDemand(((Number) r.get("demand")).intValue());
                    }
                    entity.setUnit((String) r.get("unit"));
                    entity.setRegion((String) r.get("region"));
                    if (r.get("price") != null) {
                        entity.setPrice(((Number) r.get("price")).doubleValue());
                    }
                    entity.setUpdatedAt(LocalDateTime.now());
                    list.add(entity);
                }
                stateDemandDataRepository.saveAll(list);
                log.info("[Webhook Import] Successfully imported {} state-demand records", list.size());
                return ResponseEntity.ok(ScraperImportResponse.ok(list.size()));
            }
        }
        return ResponseEntity.ok(ScraperImportResponse.ok(0));
    }

    private ResponseEntity<ScraperImportResponse> handleStateMarketRecordsImport(Object payloadObj) {
        if (payloadObj instanceof Map<?, ?> payloadMap) {
            String marketStr = (String) payloadMap.get("market");
            String area = (String) payloadMap.get("area");
            String dateStr = (String) payloadMap.get("deliveryDate");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = (List<Map<String, Object>>) payloadMap.get("records");

            MarketType market = MarketType.valueOf(marketStr.trim().toUpperCase());
            LocalDate deliveryDate = LocalDate.parse(dateStr.trim().substring(0, 10));

            int successCount = 0;
            if (records != null) {
                for (int i = 0; i < records.size(); i++) {
                    Map<String, Object> row = records.get(i);
                    if (row == null) continue;
                    short intervalNumber = (short) (i + 1);

                    StateMarketRecord record = stateMarketRecordRepository
                        .findByMarketAndAreaAndDeliveryDateAndIntervalNumber(market, area, deliveryDate, intervalNumber)
                        .orElseGet(StateMarketRecord::create);

                    record.setMarket(market);
                    record.setArea(area);
                    record.setDeliveryDate(deliveryDate);
                    record.setIntervalNumber(intervalNumber);

                    String intervalTime = (String) row.getOrDefault("intervalTime", row.get("timePeriod"));
                    if (intervalTime != null && intervalTime.contains("-")) {
                        intervalTime = intervalTime.split("-")[0].trim();
                    }
                    record.setIntervalTime(intervalTime);

                    if (row.get("purchaseBid") != null) {
                        record.setPurchaseBid(new BigDecimal(row.get("purchaseBid").toString()));
                    }
                    if (row.get("sellBid") != null) {
                        record.setSellBid(new BigDecimal(row.get("sellBid").toString()));
                    }
                    if (row.get("mcv") != null) {
                        record.setClearedVolume(new BigDecimal(row.get("mcv").toString()));
                    }
                    if (row.get("mcp") != null) {
                        record.setPrice(new BigDecimal(row.get("mcp").toString()));
                    }
                    record.setUpdatedAt(LocalDateTime.now());
                    stateMarketRecordRepository.save(record);
                    successCount++;
                }
            }
            log.info("[Webhook Import] Successfully imported {} state-market-records for {} ({})", successCount, area, market);
            return ResponseEntity.ok(ScraperImportResponse.ok(successCount));
        }
        return ResponseEntity.badRequest().body(ScraperImportResponse.error("Invalid payload"));
    }

    private boolean isSecretValid(String providedToken, String expectedSecret) {
        if (providedToken == null || expectedSecret == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                providedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expectedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}

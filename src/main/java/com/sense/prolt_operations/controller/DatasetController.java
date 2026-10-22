package com.sense.prolt_operations.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.service.DatasetService;
import com.sense.prolt_operations.service.PersistenceService;

@RestController
@RequestMapping(value = {"/api/datasets", "/api/dataset"})
public class DatasetController {

    private static final Logger log = LoggerFactory.getLogger(DatasetController.class);

    private final DatasetService datasetService;
    private final PersistenceService persistenceService;

    public DatasetController(DatasetService datasetService, PersistenceService persistenceService) {
        this.datasetService = datasetService;
        this.persistenceService = persistenceService;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getDatasetSummary() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", datasetService.getSummary()
        ));
    }

    @GetMapping("/timeseries")
    public ResponseEntity<Map<String, Object>> getTimeseries(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String market) {
        return ResponseEntity.ok(datasetService.getTimeseries(date, market));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDatasets(@RequestParam Map<String, String> query) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.putAll(datasetService.getDatasets(query));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{datasetId}")
    public ResponseEntity<Map<String, Object>> getDatasetById(@PathVariable String datasetId) {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", datasetService.getDatasetById(datasetId)
        ));
    }

    @GetMapping("/{datasetId}/history")
    public ResponseEntity<Map<String, Object>> getDatasetHistory(@PathVariable String datasetId) {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", datasetService.getDatasetHistory(datasetId)
        ));
    }

    @DeleteMapping("/{datasetId}")
    public ResponseEntity<Map<String, Object>> deleteDataset(@PathVariable String datasetId) {
        log.info("Received API request to delete dataset {}", datasetId);
        persistenceService.deleteDataset(datasetId);
        log.info("Successfully processed deletion for dataset {}", datasetId);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Dataset deleted successfully"
        ));
    }
}

package com.sense.prolt_operations.controller;

import java.util.LinkedHashMap;
import java.util.List;
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

import com.sense.prolt_operations.dto.response.ApiResponse;
import com.sense.prolt_operations.dto.response.DatasetDTO;
import com.sense.prolt_operations.dto.response.DatasetSummaryResponse;
import com.sense.prolt_operations.dto.response.OperationResponse;
import com.sense.prolt_operations.dto.response.PaginatedResponse;
import com.sense.prolt_operations.dto.response.TimeseriesResponse;
import com.sense.prolt_operations.dto.response.UploadHistoryDTO;
import com.sense.prolt_operations.service.DatasetService;
import com.sense.prolt_operations.service.PersistenceService;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private static final Logger log = LoggerFactory.getLogger(DatasetController.class);

    private final DatasetService datasetService;
    private final PersistenceService persistenceService;

    public DatasetController(DatasetService datasetService, PersistenceService persistenceService) {
        this.datasetService = datasetService;
        this.persistenceService = persistenceService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DatasetSummaryResponse>> getDatasetSummary() {
        return ResponseEntity.ok(ApiResponse.ok(datasetService.getSummary()));
    }

    @GetMapping("/timeseries")
    public ResponseEntity<TimeseriesResponse> getTimeseries(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String market) {
        return ResponseEntity.ok(datasetService.getTimeseries(date, market));
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<DatasetDTO>> getDatasets(@RequestParam Map<String, String> query) {
        return ResponseEntity.ok(datasetService.getDatasets(query));
    }

    @GetMapping("/{datasetId}")
    public ResponseEntity<ApiResponse<DatasetDTO>> getDatasetById(@PathVariable String datasetId) {
        return ResponseEntity.ok(ApiResponse.ok(datasetService.getDatasetById(datasetId)));
    }

    @GetMapping("/{datasetId}/history")
    public ResponseEntity<ApiResponse<List<UploadHistoryDTO>>> getDatasetHistory(@PathVariable String datasetId) {
        return ResponseEntity.ok(ApiResponse.ok(datasetService.getDatasetHistory(datasetId)));
    }

    @DeleteMapping("/{datasetId}")
    public ResponseEntity<OperationResponse> deleteDataset(@PathVariable String datasetId) {
        log.info("Received API request to delete dataset {}", datasetId);
        persistenceService.deleteDataset(datasetId);
        log.info("Successfully processed deletion for dataset {}", datasetId);
        return ResponseEntity.ok(OperationResponse.ok("Dataset deleted successfully"));
    }
}

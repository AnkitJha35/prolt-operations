package com.sense.prolt_operations.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.service.DatasetService;

@RestController
@RequestMapping("/api/upload-history")
public class UploadHistoryController {

    private final DatasetService datasetService;

    public UploadHistoryController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getUploadHistory(@RequestParam Map<String, String> query) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.putAll(datasetService.getUploadHistory(query));
        return ResponseEntity.ok(result);
    }
}

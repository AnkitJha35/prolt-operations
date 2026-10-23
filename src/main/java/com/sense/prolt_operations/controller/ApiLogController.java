package com.sense.prolt_operations.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.service.ApiLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/logs")
@Tag(name = "API Logs", description = "System and integration API telemetry logs")
public class ApiLogController {

    private final ApiLogService apiLogService;

    public ApiLogController(ApiLogService apiLogService) {
        this.apiLogService = apiLogService;
    }

    @GetMapping
    @Operation(summary = "Get API Logs", description = "Returns paginated list of API logs with optional date range and apiName filters")
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "apiName", required = false) String apiName) {

        return ResponseEntity.ok(apiLogService.getLogs(page, limit, startDate, endDate, apiName));
    }

    @GetMapping("/api-names")
    @Operation(summary = "Get Unique API Names", description = "Retrieves all distinct registered API log names")
    public ResponseEntity<List<String>> getUniqueApiNames() {
        return ResponseEntity.ok(apiLogService.getUniqueApiNames());
    }

    @GetMapping("/stats")
    @Operation(summary = "Get API Log Statistics", description = "Computes total, success, error counts, and accuracy percentage")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "apiName", required = false) String apiName) {

        return ResponseEntity.ok(apiLogService.getStats(startDate, endDate, apiName));
    }
}

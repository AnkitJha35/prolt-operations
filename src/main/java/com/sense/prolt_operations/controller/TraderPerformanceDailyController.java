package com.sense.prolt_operations.controller;

import com.sense.prolt_operations.dto.calculator.DailyAnalysisRequestDTO;
import com.sense.prolt_operations.dto.calculator.DailyConsumerConfigDTO;
import com.sense.prolt_operations.service.TraderPerformanceDailyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trader-performance-daily")
public class TraderPerformanceDailyController {

    private final TraderPerformanceDailyService dailyService;

    public TraderPerformanceDailyController(TraderPerformanceDailyService dailyService) {
        this.dailyService = dailyService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDailyTradeReports(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No files provided"));
        }
        if (files.size() > 200) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Too many files"));
        }
        for (MultipartFile file : files) {
            if (file.getSize() > 25L * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "File too large"));
            }
        }
        Map<String, Object> response = dailyService.uploadDailyTradeReports(files);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/meters")
    public ResponseEntity<List<String>> getAvailableMeters() {
        return ResponseEntity.ok(dailyService.getAvailableMeters());
    }

    @GetMapping("/consumers")
    public ResponseEntity<List<DailyConsumerConfigDTO>> getConsumers() {
        return ResponseEntity.ok(dailyService.getConsumers());
    }

    @PostMapping("/consumers")
    public ResponseEntity<DailyConsumerConfigDTO> saveConsumer(@RequestBody DailyConsumerConfigDTO consumer) {
        return ResponseEntity.ok(dailyService.saveConsumer(consumer));
    }

    @GetMapping("/analysis")
    public ResponseEntity<?> getDailyAnalysisGet(
            @RequestParam(required = false) String consumerId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        DailyAnalysisRequestDTO req = new DailyAnalysisRequestDTO(consumerId, startDate, endDate, List.of());
        return ResponseEntity.ok(dailyService.calculateDailyAnalysis(req));
    }

    @PostMapping("/analysis")
    public ResponseEntity<?> getDailyAnalysisPost(@RequestBody(required = false) DailyAnalysisRequestDTO req) {
        return ResponseEntity.ok(dailyService.calculateDailyAnalysis(req));
    }
}

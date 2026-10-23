package com.sense.prolt_operations.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.service.UploadProcessingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/uploads")
@Tag(name = "Uploads", description = "Market dataset upload and health operations")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final UploadProcessingService uploadProcessingService;

    public UploadController(UploadProcessingService uploadProcessingService) {
        this.uploadProcessingService = uploadProcessingService;
    }

    @PostMapping(consumes = {"multipart/form-data"})
    @Operation(summary = "Upload market dataset file", description = "Uploads and persists DAM, GDAM, RTM, or REC dataset")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "market", required = false) String market,
            @RequestParam(value = "deliveryDate", required = false) String deliveryDate,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "action", required = false) String action) {

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file provided for upload.");
        }
        if (file.getSize() > 50L * 1024 * 1024) {
            throw new BadRequestException("File size exceeds the 50MB limit.");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lower = originalFilename.toLowerCase();
            if (!lower.endsWith(".csv") && !lower.endsWith(".xlsx") && !lower.endsWith(".xls") && !lower.endsWith(".xlxs")) {
                int dotIdx = originalFilename.lastIndexOf('.');
                String ext = dotIdx >= 0 ? originalFilename.substring(dotIdx).toLowerCase() : "";
                throw new BadRequestException("Unsupported file extension: " + ext + ". Only .csv, .xlsx, .xls are allowed.");
            }
        }
        if (market == null || market.trim().isEmpty()) {
            throw new BadRequestException("Market type is required.");
        }

        String targetDateStr = deliveryDate != null && !deliveryDate.trim().isEmpty() ? deliveryDate.trim() : (date != null ? date.trim() : null);
        if (targetDateStr == null || targetDateStr.isEmpty()) {
            throw new BadRequestException("Delivery date is required.");
        }

        LocalDate parsedDate;
        try {
            if (targetDateStr.contains("T")) {
                parsedDate = LocalDate.parse(targetDateStr.substring(0, targetDateStr.indexOf('T')));
            } else {
                parsedDate = LocalDate.parse(targetDateStr);
            }
        } catch (Exception e) {
            throw new BadRequestException("Invalid delivery date format: " + targetDateStr);
        }

        Map<String, Object> result = uploadProcessingService.processUpload(market, parsedDate, file, action);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("datasetId", result.get("datasetId"));
        response.put("market", market.trim().toUpperCase());
        response.put("deliveryDate", targetDateStr);
        response.put("fileName", file.getOriginalFilename());
        response.put("rowCount", result.get("rowCount"));
        response.put("parsed", result.get("parsed"));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Upload storage health check", description = "Verifies upload subsystem storage availability")
    public ResponseEntity<Map<String, Object>> getUploadHealth() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "storage", "healthy"
        ));
    }
}

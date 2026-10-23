package com.sense.prolt_operations.controller;

import com.sense.prolt_operations.dto.calculator.CreateSavingsEntryDTO;
import com.sense.prolt_operations.dto.calculator.ResourceDefaultsResponse;
import com.sense.prolt_operations.dto.calculator.SavingsEntryDTO;
import com.sense.prolt_operations.security.ProltUserPrincipal;
import com.sense.prolt_operations.service.ExcelExportService;
import com.sense.prolt_operations.service.TraderPerformanceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trader-performance")
public class TraderPerformanceController {

    private final TraderPerformanceService traderPerformanceService;
    private final ExcelExportService excelExportService;

    public TraderPerformanceController(
            TraderPerformanceService traderPerformanceService,
            ExcelExportService excelExportService) {
        this.traderPerformanceService = traderPerformanceService;
        this.excelExportService = excelExportService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadTradeReports(@RequestParam("files") List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No files provided"));
        }
        if (files.size() > 200) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Too many files"));
        }
        for (MultipartFile file : files) {
            if (file.getSize() > 10L * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "File too large"));
            }
        }
        Map<String, Object> response = traderPerformanceService.uploadTradeReports(files);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SavingsEntryDTO>> getAll() {
        return ResponseEntity.ok(traderPerformanceService.getAll());
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody CreateSavingsEntryDTO dto,
            @AuthenticationPrincipal ProltUserPrincipal principal) {
        if (dto.clientName() == null || dto.industryName() == null || dto.address() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Missing required fields: clientName, industryName, address."));
        }
        String username = (principal != null) ? principal.getUsername() : null;
        SavingsEntryDTO entry = traderPerformanceService.create(dto, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @GetMapping("/resource-defaults")
    public ResponseEntity<ResourceDefaultsResponse> getResourceDefaults(
            @RequestParam(required = false) String stateCode,
            @RequestParam(required = false) String discom,
            @RequestParam(required = false) String consumerCategory,
            @RequestParam(required = false) String voltageLevel,
            @RequestParam(required = false) String monthStr) {
        return ResponseEntity.ok(traderPerformanceService.getResourceDefaults(stateCode, discom, consumerCategory, voltageLevel, monthStr));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        SavingsEntryDTO entry = traderPerformanceService.getById(id);
        if (entry == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Entry not found."));
        }
        return ResponseEntity.ok(entry);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestBody CreateSavingsEntryDTO dto,
            @AuthenticationPrincipal ProltUserPrincipal principal) {
        if (dto.clientName() == null || dto.industryName() == null || dto.address() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Missing required fields: clientName, industryName, address."));
        }
        String username = (principal != null) ? principal.getUsername() : null;
        SavingsEntryDTO entry = traderPerformanceService.update(id, dto, username);
        return ResponseEntity.ok(entry);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable String id,
            @AuthenticationPrincipal ProltUserPrincipal principal) {
        String role = principal != null ? principal.getRole() : "USER";
        List<String> readOnly = principal != null ? principal.getReadOnlyModules() : List.of();
        traderPerformanceService.delete(id, role, readOnly);
        return ResponseEntity.ok(Map.of("message", "Entry deleted successfully."));
    }

    @GetMapping("/{id}/client-overview")
    public ResponseEntity<?> getClientOverview(@PathVariable String id) {
        return ResponseEntity.ok(traderPerformanceService.getClientOverview(id));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> getHistory(@PathVariable String id) {
        return ResponseEntity.ok(traderPerformanceService.getHistory(id));
    }

    @GetMapping("/{id}/calculate")
    public ResponseEntity<?> calculateSavings(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(traderPerformanceService.calculateSavings(id, month, version));
    }

    @GetMapping("/{id}/market-decision")
    public ResponseEntity<?> calculateMarketDecision(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(traderPerformanceService.calculateMarketDecision(id, month, version));
    }

    @GetMapping("/{id}/demand-shift-insights")
    public ResponseEntity<?> getDemandShiftInsights(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(traderPerformanceService.calculateDemandShiftInsights(id, month, version));
    }

    @GetMapping("/{id}/export-excel")
    public ResponseEntity<byte[]> exportExcel(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        SavingsEntryDTO entry = traderPerformanceService.getById(id);
        String clientName = entry != null ? entry.clientName() : id;
        Map<String, Object> calcResult = traderPerformanceService.calculateSavings(id, month, version);
        byte[] excelBytes = excelExportService.exportSavingsAnalysis(clientName, calcResult);

        String sanitized = clientName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Savings_Analysis_" + sanitized + ".xlsx")
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private")
            .body(excelBytes);
    }

    @GetMapping("/{id}/actual-trader/export-excel")
    public ResponseEntity<byte[]> exportActualTraderExcel(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        SavingsEntryDTO entry = traderPerformanceService.getById(id);
        String clientName = entry != null ? entry.clientName() : id;
        Map<String, Object> calcResult = traderPerformanceService.calculateSavings(id, month, version);
        byte[] excelBytes = excelExportService.exportSavingsAnalysis(clientName + "_Actual", calcResult);

        String sanitized = clientName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Actual_Trader_Analysis_" + sanitized + ".xlsx")
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private")
            .body(excelBytes);
    }

    @GetMapping("/{id}/demand-shift-insights/export-excel")
    public ResponseEntity<byte[]> exportDemandShiftExcel(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        SavingsEntryDTO entry = traderPerformanceService.getById(id);
        String clientName = entry != null ? entry.clientName() : id;
        Map<String, Object> insights = traderPerformanceService.calculateDemandShiftInsights(id, month, version);
        byte[] excelBytes = excelExportService.exportDemandShift(clientName, insights);

        String sanitized = clientName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Demand_Shift_Insights_" + sanitized + ".xlsx")
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private")
            .body(excelBytes);
    }
}

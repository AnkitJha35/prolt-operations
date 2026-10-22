package com.sense.prolt_operations.controller;

import com.sense.prolt_operations.dto.calculator.CreateSavingsEntryDTO;
import com.sense.prolt_operations.dto.calculator.LandedCostCalcRequest;
import com.sense.prolt_operations.dto.calculator.LandedCostCalcResponse;
import com.sense.prolt_operations.dto.calculator.SavingsEntryDTO;
import com.sense.prolt_operations.security.ProltUserPrincipal;
import com.sense.prolt_operations.service.ExcelExportService;
import com.sense.prolt_operations.service.SavingsCalculatorService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/savings-calculator")
public class SavingsCalculatorController {

    private final SavingsCalculatorService savingsCalculatorService;
    private final ExcelExportService excelExportService;

    public SavingsCalculatorController(
            SavingsCalculatorService savingsCalculatorService,
            ExcelExportService excelExportService) {
        this.savingsCalculatorService = savingsCalculatorService;
        this.excelExportService = excelExportService;
    }

    // Direct Landed Cost Calculation (Scenario CALC-01 / CALC-01a)
    @PostMapping("/calculate")
    public ResponseEntity<LandedCostCalcResponse> calculateDirect(@RequestBody LandedCostCalcRequest request) {
        LandedCostCalcResponse response = savingsCalculatorService.calculateDirect(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SavingsEntryDTO>> getAll() {
        return ResponseEntity.ok(savingsCalculatorService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        SavingsEntryDTO entry = savingsCalculatorService.getById(id);
        if (entry == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Entry not found."));
        }
        return ResponseEntity.ok(entry);
    }

    @GetMapping("/{id}/overview")
    public ResponseEntity<?> getClientOverview(@PathVariable String id) {
        return ResponseEntity.ok(savingsCalculatorService.getClientOverview(id));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> getHistory(@PathVariable String id) {
        return ResponseEntity.ok(savingsCalculatorService.getHistory(id));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody CreateSavingsEntryDTO dto,
            @AuthenticationPrincipal ProltUserPrincipal principal) {
        if (dto.clientName() == null || dto.industryName() == null || dto.address() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Missing required fields: clientName, industryName, address."));
        }
        String username = principal != null ? principal.getUsername() : "system";
        SavingsEntryDTO entry = savingsCalculatorService.create(dto, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
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
        String username = principal != null ? principal.getUsername() : "system";
        SavingsEntryDTO entry = savingsCalculatorService.update(id, dto, username);
        return ResponseEntity.ok(entry);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable String id,
            @AuthenticationPrincipal ProltUserPrincipal principal) {
        String role = principal != null ? principal.getRole() : "USER";
        List<String> readOnly = principal != null ? principal.getReadOnlyModules() : List.of();
        savingsCalculatorService.delete(id, role, readOnly);
        return ResponseEntity.ok(Map.of("message", "Entry deleted successfully."));
    }

    @PostMapping("/{id}/calculate")
    public ResponseEntity<?> calculate(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(savingsCalculatorService.calculateSavings(id, month, version));
    }

    @PostMapping("/{id}/calculate-market-decision")
    public ResponseEntity<?> calculateMarketDecision(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(savingsCalculatorService.calculateMarketDecision(id, month, version));
    }

    @PostMapping("/{id}/demand-shift-insights")
    public ResponseEntity<?> getDemandShiftInsights(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(savingsCalculatorService.calculateDemandShiftInsights(id, month, version));
    }

    @GetMapping("/{id}/export-excel")
    public ResponseEntity<byte[]> exportExcel(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        SavingsEntryDTO entry = savingsCalculatorService.getById(id);
        String clientName = entry != null ? entry.clientName() : id;
        Map<String, Object> calcResult = savingsCalculatorService.calculateSavings(id, month, version);
        byte[] excelBytes = excelExportService.exportSavingsAnalysis(clientName, calcResult);

        String sanitized = clientName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Savings_Analysis_" + sanitized + ".xlsx")
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private")
            .body(excelBytes);
    }

    @GetMapping("/{id}/demand-shift-insights/export-excel")
    public ResponseEntity<byte[]> exportDemandShiftExcel(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        SavingsEntryDTO entry = savingsCalculatorService.getById(id);
        String clientName = entry != null ? entry.clientName() : id;
        Map<String, Object> insights = savingsCalculatorService.calculateDemandShiftInsights(id, month, version);
        byte[] excelBytes = excelExportService.exportDemandShift(clientName, insights);

        String sanitized = clientName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Demand_Shift_Insights_" + sanitized + ".xlsx")
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private")
            .body(excelBytes);
    }
}

package com.sense.prolt_operations.controller;

import com.sense.prolt_operations.dto.calculator.CreateSavingsEntryDTO;
import com.sense.prolt_operations.dto.calculator.ResourceDefaultsResponse;
import com.sense.prolt_operations.dto.calculator.SavingsEntryDTO;
import com.sense.prolt_operations.security.ProltUserPrincipal;
import com.sense.prolt_operations.service.ExcelExportService;
import com.sense.prolt_operations.service.SavingsCalculatorNewService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/savings-calculator-new")
public class SavingsCalculatorNewController {

    private final SavingsCalculatorNewService savingsCalculatorNewService;
    private final ExcelExportService excelExportService;

    public SavingsCalculatorNewController(
            SavingsCalculatorNewService savingsCalculatorNewService,
            ExcelExportService excelExportService) {
        this.savingsCalculatorNewService = savingsCalculatorNewService;
        this.excelExportService = excelExportService;
    }

    @GetMapping("/entries")
    public ResponseEntity<List<SavingsEntryDTO>> getAll() {
        return ResponseEntity.ok(savingsCalculatorNewService.getAll());
    }

    @GetMapping("/entries/resource-defaults")
    public ResponseEntity<ResourceDefaultsResponse> getResourceDefaults(
            @RequestParam(required = false) String stateCode,
            @RequestParam(required = false) String discom,
            @RequestParam(required = false) String consumerCategory,
            @RequestParam(required = false) String voltageLevel,
            @RequestParam(required = false) String monthStr) {
        return ResponseEntity.ok(savingsCalculatorNewService.getResourceDefaults(stateCode, discom, consumerCategory, voltageLevel, monthStr));
    }

    @PostMapping("/entries")
    public ResponseEntity<?> create(
            @RequestBody CreateSavingsEntryDTO dto,
            @AuthenticationPrincipal ProltUserPrincipal principal) {
        if (dto.clientName() == null || dto.industryName() == null || dto.address() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Missing required fields: clientName, industryName, address."));
        }
        String username = principal != null ? principal.getUsername() : "system";
        SavingsEntryDTO entry = savingsCalculatorNewService.create(dto, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(entry);
    }

    @GetMapping("/entries/{id}")
    public ResponseEntity<?> getById(@PathVariable String id) {
        SavingsEntryDTO entry = savingsCalculatorNewService.getById(id);
        if (entry == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Entry not found."));
        }
        return ResponseEntity.ok(entry);
    }

    @PutMapping("/entries/{id}")
    public ResponseEntity<?> update(
            @PathVariable String id,
            @RequestBody CreateSavingsEntryDTO dto,
            @AuthenticationPrincipal ProltUserPrincipal principal) {
        if (dto.clientName() == null || dto.industryName() == null || dto.address() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Missing required fields: clientName, industryName, address."));
        }
        String username = principal != null ? principal.getUsername() : "system";
        SavingsEntryDTO entry = savingsCalculatorNewService.update(id, dto, username);
        return ResponseEntity.ok(entry);
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<?> delete(
            @PathVariable String id,
            @AuthenticationPrincipal ProltUserPrincipal principal) {
        String role = principal != null ? principal.getRole() : "USER";
        List<String> readOnly = principal != null ? principal.getReadOnlyModules() : List.of();
        savingsCalculatorNewService.delete(id, role, readOnly);
        return ResponseEntity.ok(Map.of("message", "Entry deleted successfully."));
    }

    @GetMapping("/entries/{id}/history")
    public ResponseEntity<?> getHistory(@PathVariable String id) {
        return ResponseEntity.ok(savingsCalculatorNewService.getHistory(id));
    }

    @GetMapping("/entries/{id}/client-overview")
    public ResponseEntity<?> getClientOverview(@PathVariable String id) {
        return ResponseEntity.ok(savingsCalculatorNewService.getClientOverview(id));
    }

    @GetMapping("/entries/{id}/calculate")
    public ResponseEntity<?> calculateSavings(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(savingsCalculatorNewService.calculateSavings(id, month, version));
    }

    @GetMapping("/entries/{id}/market-decision")
    public ResponseEntity<?> calculateMarketDecision(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(savingsCalculatorNewService.calculateMarketDecision(id, month, version));
    }

    @PostMapping("/entries/{id}/demand-shift-insights")
    public ResponseEntity<?> getDemandShiftInsights(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        return ResponseEntity.ok(savingsCalculatorNewService.calculateDemandShiftInsights(id, month, version));
    }

    @GetMapping("/entries/{id}/export-excel")
    public ResponseEntity<byte[]> exportExcel(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        SavingsEntryDTO entry = savingsCalculatorNewService.getById(id);
        String clientName = entry != null ? entry.clientName() : id;
        Map<String, Object> calcResult = savingsCalculatorNewService.calculateSavings(id, month, version);
        byte[] excelBytes = excelExportService.exportSavingsAnalysis(clientName, calcResult);

        String sanitized = clientName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Savings_Analysis_" + sanitized + ".xlsx")
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private")
            .body(excelBytes);
    }

    @GetMapping("/entries/{id}/demand-shift-insights/export-excel")
    public ResponseEntity<byte[]> exportDemandShiftExcel(
            @PathVariable String id,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Integer version) {
        SavingsEntryDTO entry = savingsCalculatorNewService.getById(id);
        String clientName = entry != null ? entry.clientName() : id;
        Map<String, Object> insights = savingsCalculatorNewService.calculateDemandShiftInsights(id, month, version);
        byte[] excelBytes = excelExportService.exportDemandShift(clientName, insights);

        String sanitized = clientName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Demand_Shift_Insights_" + sanitized + ".xlsx")
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private")
            .body(excelBytes);
    }
}

package com.sense.prolt_operations.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.service.MarketOperationsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/market-operations")
@Tag(name = "Market Operations", description = "Multi-market operational MCP comparison queries")
public class MarketOperationsController {

    private final MarketOperationsService marketOperationsService;

    public MarketOperationsController(MarketOperationsService marketOperationsService) {
        this.marketOperationsService = marketOperationsService;
    }

    @GetMapping
    @Operation(summary = "Get Market Operations Records", description = "Retrieves aligned interval MCP comparison across DAM, RTM, and GDAM")
    public ResponseEntity<Map<String, Object>> getRecords(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {

        List<Map<String, Object>> data = marketOperationsService.getRecords(startDate, endDate);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", data
        ));
    }
}

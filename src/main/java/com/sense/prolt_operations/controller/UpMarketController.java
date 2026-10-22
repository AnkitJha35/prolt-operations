package com.sense.prolt_operations.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.service.UpMarketService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/up-market")
@Tag(name = "UP Market", description = "Uttar Pradesh specific regional power market data")
public class UpMarketController {

    private final UpMarketService upMarketService;

    public UpMarketController(UpMarketService upMarketService) {
        this.upMarketService = upMarketService;
    }

    @GetMapping("/{market}")
    @Operation(summary = "Get UP regional market data", description = "Fetches interval market data for DAM, RTM, or GDAM with regional analytics")
    public ResponseEntity<Map<String, Object>> getMarketData(
            @PathVariable("market") String market,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "state", required = false) String state) {

        String start = startDate != null ? startDate : date;
        String end = endDate != null ? endDate : date;

        if (start == null || end == null) {
            throw new BadRequestException("startDate and endDate are required");
        }

        String lowerMarket = market != null ? market.trim().toLowerCase() : "";
        if (!List.of("dam", "rtm", "gdam").contains(lowerMarket)) {
            throw new BadRequestException("Invalid market type");
        }

        Map<String, Object> data = upMarketService.getMarketData(market, start, end, state);
        @SuppressWarnings("unchecked")
        List<?> intervals = (List<?>) data.get("intervals");
        if (intervals == null || intervals.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false,
                "message", "No data found"
            ));
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", data
        ));
    }
}

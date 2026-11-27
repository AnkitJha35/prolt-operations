package com.sense.prolt_operations.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.dto.response.ApiResponse;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.service.ForecastService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping({"/api/forecast", "/forecast"})
@Tag(name = "Forecast", description = "Price, demand, generation, and market selection forecast endpoints")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping("/price")
    @Operation(summary = "Get price forecast", description = "Retrieves forecasted and actual MCP across intervals")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPriceForecast(
            @RequestParam(value = "market", defaultValue = "dam") String market,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "interval", defaultValue = "15min") String interval,
            @RequestParam(value = "model", defaultValue = "Model1") String model) {

        if (startDate == null || endDate == null) {
            throw new BadRequestException("Both startDate and endDate are required parameters.");
        }

        List<Map<String, Object>> result = forecastService.getPriceForecast(market, startDate, endDate, interval, model);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/demand")
    @Operation(summary = "Get demand forecast", description = "Retrieves forecasted demand for all-india, state, or consumer meter")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDemandForecast(
            @RequestParam(value = "type", defaultValue = "all-india") String type,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "interval", defaultValue = "15min") String interval,
            @RequestParam(value = "meterNo", required = false) String meterNo) {

        if (startDate == null || endDate == null) {
            throw new BadRequestException("Both startDate and endDate are required parameters.");
        }

        List<Map<String, Object>> result = forecastService.getDemandForecast(type, startDate, endDate, interval, meterNo);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/generation")
    @Operation(summary = "Get generation forecast", description = "Retrieves generation forecast by source")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getGenerationForecast(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "interval", defaultValue = "15min") String interval,
            @RequestParam(value = "model", defaultValue = "Model1") String model) {

        if (startDate == null || endDate == null) {
            throw new BadRequestException("Both startDate and endDate are required parameters.");
        }

        List<Map<String, Object>> result = forecastService.getGenerationForecast(startDate, endDate, interval, model);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/dates")
    @Operation(summary = "Get available forecast dates", description = "Retrieves list of distinct dates with forecast data")
    public ResponseEntity<ApiResponse<List<String>>> getForecastDates(@RequestParam(value = "market", defaultValue = "dam") String market) {
        List<String> result = forecastService.getForecastDates(market);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/meters")
    @Operation(summary = "Get available meters", description = "Retrieves list of available smart meters for consumer demand forecast")
    public ResponseEntity<ApiResponse<List<String>>> getMeters() {
        List<String> result = forecastService.getMeters();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/market-selection")
    @Operation(summary = "Get market selection forecast", description = "Retrieves recommended market allocations across time blocks")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMarketSelectionForecast(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {

        if (startDate == null || endDate == null) {
            throw new BadRequestException("Both startDate and endDate are required parameters.");
        }

        List<Map<String, Object>> result = forecastService.getMarketSelectionForecast(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}

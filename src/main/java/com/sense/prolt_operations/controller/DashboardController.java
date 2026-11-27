package com.sense.prolt_operations.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.dto.response.ApiResponse;
import com.sense.prolt_operations.service.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOverviewData() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getOverview()));
    }

    @GetMapping("/dam")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDamData(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String interval) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDashboardData("DAM", startDate, endDate, interval)));
    }

    @GetMapping("/gdam")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGdamData(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String interval) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDashboardData("GDAM", startDate, endDate, interval)));
    }

    @GetMapping("/rtm")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRtmData(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String interval) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDashboardData("RTM", startDate, endDate, interval)));
    }

    @GetMapping("/rec")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRecData(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String interval) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDashboardData("REC", startDate, endDate, interval)));
    }

    @GetMapping("/dam/analytics")
    public ResponseEntity<ApiResponse<Object>> getDamAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String interval) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getAnalytics("DAM", startDate, endDate, interval)));
    }

    @GetMapping("/gdam/analytics")
    public ResponseEntity<ApiResponse<Object>> getGdamAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String interval) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getAnalytics("GDAM", startDate, endDate, interval)));
    }

    @GetMapping("/rtm/analytics")
    public ResponseEntity<ApiResponse<Object>> getRtmAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String interval) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getAnalytics("RTM", startDate, endDate, interval)));
    }

    @GetMapping("/rec/analytics")
    public ResponseEntity<ApiResponse<Object>> getRecAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String interval) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getAnalytics("REC", startDate, endDate, interval)));
    }
}

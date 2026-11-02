package com.sense.prolt_operations.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.service.DatabaseService;
import com.sense.prolt_operations.service.HolidayService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/database")
@Tag(name = "Database", description = "Demand, generation, weather, holidays, and city-state data")
public class DatabaseController {

    private final DatabaseService databaseService;
    private final HolidayService holidayService;

    public DatabaseController(DatabaseService databaseService, HolidayService holidayService) {
        this.databaseService = databaseService;
        this.holidayService = holidayService;
    }

    @GetMapping("/demand")
    @Operation(summary = "Get All-India and State Demand", description = "Retrieves NPP demand and state-wise demand")
    public ResponseEntity<Map<String, Object>> getDemandData(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "time", required = false) String time) {

        String start = startDate != null ? startDate : date;
        String end = endDate != null ? endDate : date;

        Map<String, Object> allIndia = databaseService.getAllIndiaDemand(start, end);
        Map<String, Object> stateWise = databaseService.getStateWiseDemand(date, time);

        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", Map.of(
                "allIndiaDemand", allIndia,
                "stateWiseDemand", stateWise
            )
        ));
    }

    @GetMapping("/generation")
    @Operation(summary = "Get Generation Data", description = "Retrieves fuel-wise generation data")
    public ResponseEntity<Map<String, Object>> getGenerationData(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {

        String start = startDate != null ? startDate : date;
        String end = endDate != null ? endDate : date;

        Object data = databaseService.getGenerationData(start, end);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", data
        ));
    }

    @GetMapping("/weather")
    @Operation(summary = "Get Weather Data", description = "Retrieves historical or forecast weather data")
    public ResponseEntity<Map<String, Object>> getWeatherData(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "latitude", required = false) String latitude,
            @RequestParam(value = "longitude", required = false) String longitude) {

        Object data = databaseService.getWeatherData(startDate, endDate, type, latitude, longitude);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", data
        ));
    }

    @GetMapping("/export/csv")
    @Operation(summary = "Export data as CSV", description = "Streams CSV export for npp, generation, state, or weather datasets")
    public void exportCsv(
            @RequestParam(value = "dataset", required = false) String dataset,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            HttpServletResponse response) throws IOException {

        if (dataset == null || dataset.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Missing dataset parameter\"}");
            return;
        }

        if ("city_state".equalsIgnoreCase(dataset)) {
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=\"state_and_city_export.csv\"");
            databaseService.exportCityStateAsCsvStream(response.getWriter());
            return;
        }

        if (startDate == null || endDate == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"message\":\"Missing startDate or endDate\"}");
            return;
        }

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", String.format("attachment; filename=\"%s_export_%s_to_%s.csv\"", dataset, startDate, endDate));
        databaseService.exportDataAsCsvStream(dataset, startDate, endDate, response.getWriter());
    }

    @GetMapping("/holidays")
    @Operation(summary = "Get Holiday Calendar", description = "Retrieves all holidays")
    public ResponseEntity<Map<String, Object>> getHolidays() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", holidayService.getHolidays()
        ));
    }

    @PostMapping("/holidays")
    @Operation(summary = "Create Holiday", description = "Creates a single holiday entry")
    public ResponseEntity<Map<String, Object>> createHoliday(@RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "data", holidayService.createHoliday(body)
        ));
    }

    @PostMapping(value = "/holidays/upload", consumes = {"multipart/form-data"})
    @Operation(summary = "Upload Holidays", description = "Bulk uploads holiday calendar via CSV or Excel")
    public ResponseEntity<Map<String, Object>> uploadHolidays(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = holidayService.uploadHolidays(file);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/holidays/{id}")
    @Operation(summary = "Update Holiday", description = "Updates a holiday entry")
    public ResponseEntity<Map<String, Object>> updateHoliday(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", holidayService.updateHoliday(id, body)
        ));
    }

    @PatchMapping("/holidays/{id}/toggle")
    @Operation(summary = "Toggle Holiday Active State", description = "Toggles isActive flag for a holiday")
    public ResponseEntity<Map<String, Object>> toggleHoliday(@PathVariable("id") String id) {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", holidayService.toggleHoliday(id)
        ));
    }

    @GetMapping("/city-state")
    @Operation(summary = "Get City State Registry", description = "Retrieves city-state population and coordinate mappings")
    public ResponseEntity<Map<String, Object>> getCityStateData() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", databaseService.getCityStateData()
        ));
    }

    @PostMapping("/city-state")
    @Operation(summary = "Create City State Entry", description = "Creates a city state entry")
    public ResponseEntity<Map<String, Object>> createCityStateData(@RequestBody Map<String, Object> body) {
        if (body.get("cityName") == null || body.get("cityName").toString().isEmpty() ||
            body.get("stateName") == null || body.get("stateName").toString().isEmpty() ||
            body.get("population") == null || body.get("latitude") == null || body.get("longitude") == null) {
            throw new BadRequestException("Missing required fields");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "data", databaseService.createCityStateData(body)
        ));
    }
}

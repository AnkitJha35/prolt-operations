package com.sense.prolt_operations.controller;

import java.io.IOException;
import java.util.List;
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

import com.sense.prolt_operations.dto.response.ApiResponse;
import com.sense.prolt_operations.dto.response.CityStateDTO;
import com.sense.prolt_operations.dto.response.HolidayCalendarDTO;
import com.sense.prolt_operations.dto.response.HolidayUploadResponse;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.service.DatabaseService;
import com.sense.prolt_operations.service.HolidayService;

import org.springframework.security.access.prepost.PreAuthorize;

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
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDemandData(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "time", required = false) String time) {

        String start = startDate != null ? startDate : date;
        String end = endDate != null ? endDate : date;

        Map<String, Object> allIndia = databaseService.getAllIndiaDemand(start, end);
        Map<String, Object> stateWise = databaseService.getStateWiseDemand(date, time);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "allIndiaDemand", allIndia,
            "stateWiseDemand", stateWise
        )));
    }

    @GetMapping("/generation")
    @Operation(summary = "Get Generation Data", description = "Retrieves fuel-wise generation data")
    public ResponseEntity<ApiResponse<Object>> getGenerationData(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {

        String start = startDate != null ? startDate : date;
        String end = endDate != null ? endDate : date;

        Object data = databaseService.getGenerationData(start, end);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/weather")
    @Operation(summary = "Get Weather Data", description = "Retrieves historical or forecast weather data")
    public ResponseEntity<ApiResponse<Object>> getWeatherData(
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "latitude", required = false) String latitude,
            @RequestParam(value = "longitude", required = false) String longitude) {

        Object data = databaseService.getWeatherData(startDate, endDate, type, latitude, longitude);
        return ResponseEntity.ok(ApiResponse.ok(data));
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
    public ResponseEntity<ApiResponse<List<HolidayCalendarDTO>>> getHolidays() {
        List<HolidayCalendarDTO> dtos = holidayService.getHolidays().stream()
                .map(HolidayCalendarDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @PostMapping("/holidays")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create Holiday", description = "Creates a single holiday entry")
    public ResponseEntity<ApiResponse<HolidayCalendarDTO>> createHoliday(@RequestBody Map<String, Object> body) {
        var created = holidayService.createHoliday(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(HolidayCalendarDTO.fromEntity(created)));
    }

    @PostMapping(value = "/holidays/upload", consumes = {"multipart/form-data"})
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Upload Holidays", description = "Bulk uploads holiday calendar via CSV or Excel")
    public ResponseEntity<HolidayUploadResponse> uploadHolidays(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = holidayService.uploadHolidays(file);
        int count = result.get("count") instanceof Number n ? n.intValue() : 0;
        return ResponseEntity.ok(HolidayUploadResponse.ok(count));
    }

    @PutMapping("/holidays/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update Holiday", description = "Updates a holiday entry")
    public ResponseEntity<ApiResponse<HolidayCalendarDTO>> updateHoliday(
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> body) {
        var updated = holidayService.updateHoliday(id, body);
        return ResponseEntity.ok(ApiResponse.ok(HolidayCalendarDTO.fromEntity(updated)));
    }

    @PatchMapping("/holidays/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Toggle Holiday Active State", description = "Toggles isActive flag for a holiday")
    public ResponseEntity<ApiResponse<HolidayCalendarDTO>> toggleHoliday(@PathVariable("id") String id) {
        var toggled = holidayService.toggleHoliday(id);
        return ResponseEntity.ok(ApiResponse.ok(HolidayCalendarDTO.fromEntity(toggled)));
    }

    @GetMapping("/city-state")
    @Operation(summary = "Get City State Registry", description = "Retrieves city-state population and coordinate mappings")
    public ResponseEntity<ApiResponse<List<CityStateDTO>>> getCityStateData() {
        List<CityStateDTO> dtos = databaseService.getCityStateData().stream()
                .map(CityStateDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(dtos));
    }

    @PostMapping("/city-state")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create City State Entry", description = "Creates a city state entry")
    public ResponseEntity<ApiResponse<CityStateDTO>> createCityStateData(@RequestBody Map<String, Object> body) {
        if (body.get("cityName") == null || body.get("cityName").toString().isEmpty() ||
            body.get("stateName") == null || body.get("stateName").toString().isEmpty() ||
            body.get("population") == null || body.get("latitude") == null || body.get("longitude") == null) {
            throw new BadRequestException("Missing required fields");
        }
        var created = databaseService.createCityStateData(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(CityStateDTO.fromEntity(created)));
    }
}

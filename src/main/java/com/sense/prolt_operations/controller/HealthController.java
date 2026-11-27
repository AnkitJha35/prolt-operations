package com.sense.prolt_operations.controller;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Health", description = "System and database health checks")
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    @Operation(summary = "Liveness probe", description = "Returns server health status")
    public ResponseEntity<com.sense.prolt_operations.dto.response.HealthResponse> health() {
        return ResponseEntity.ok(new com.sense.prolt_operations.dto.response.HealthResponse(true, "healthy"));
    }

    @GetMapping("/health/database")
    @Operation(summary = "Database readiness probe", description = "Checks database connectivity")
    public ResponseEntity<com.sense.prolt_operations.dto.response.DatabaseHealthResponse> healthDatabase() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            return ResponseEntity.ok(new com.sense.prolt_operations.dto.response.DatabaseHealthResponse(true, "connected"));
        }
    }
}

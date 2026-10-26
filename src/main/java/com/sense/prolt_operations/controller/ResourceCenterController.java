package com.sense.prolt_operations.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.service.ResourceCenterService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/resource-center")
@Tag(name = "Resource Center", description = "Master data, tariffs, charges, and region settings")
public class ResourceCenterController {

    private final ResourceCenterService resourceCenterService;
    private final ObjectMapper objectMapper;

    public ResourceCenterController(ResourceCenterService resourceCenterService, ObjectMapper objectMapper) {
        this.resourceCenterService = resourceCenterService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{resourceType}")
    @Operation(summary = "Get resource center records", description = "Retrieves all records for given resourceType")
    public ResponseEntity<Map<String, Object>> getResourceData(@PathVariable("resourceType") String resourceType) {
        List<?> data = resourceCenterService.getResourceData(resourceType);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", data
        ));
    }

    @PostMapping("/{resourceType}")
    @Operation(summary = "Create resource center record", description = "Creates a single record in the specified resource table")
    public ResponseEntity<Map<String, Object>> createResourceRecord(
            @PathVariable("resourceType") String resourceType,
            @RequestBody(required = false) Map<String, Object> payload) {

        if (payload == null || payload.isEmpty()) {
            throw new BadRequestException("Payload cannot be empty");
        }

        Object data = resourceCenterService.createResourceRecord(resourceType, payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "message", "Record created successfully",
            "data", data
        ));
    }

    @PostMapping("/{resourceType}/bulk")
    @Operation(summary = "Bulk create resource center records", description = "Creates or upserts multiple records in the specified table")
    public ResponseEntity<Map<String, Object>> createBulkResourceRecords(
            @PathVariable("resourceType") String resourceType,
            @RequestBody(required = false) JsonNode payload) {

        // Match Node: validate the resource type before examining the body.
        resourceCenterService.assertSupportedResourceType(resourceType);
        if (payload == null || !payload.isArray()) {
            throw new BadRequestException("Payload must be an array of records");
        }

        List<Map<String, Object>> validPayload = new ArrayList<>();
        for (JsonNode record : payload) {
            if (!record.isObject()) {
                throw new BadRequestException("Invalid payload format");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> values = objectMapper.convertValue(record, Map.class);
            if (!isBlank(values)) {
                validPayload.add(values);
            }
        }

        List<?> results = resourceCenterService.createBulkResourceRecords(resourceType, validPayload);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "success", true,
            "message", String.format("Bulk uploaded %d records successfully", validPayload.size()),
            "data", results
        ));
    }

    private boolean isBlank(Map<String, Object> payload) {
        return payload.values().stream()
            .allMatch(value -> value == null || value.toString().trim().isEmpty());
    }

    @PutMapping("/{resourceType}/{id}")
    @Operation(summary = "Update resource center record", description = "Updates a record by ID")
    public ResponseEntity<Map<String, Object>> updateResourceRecord(
            @PathVariable("resourceType") String resourceType,
            @PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> payload) {

        Integer numId = parseId(id);
        if (payload == null || payload.isEmpty()) {
            throw new BadRequestException("Payload cannot be empty");
        }

        Object updated = resourceCenterService.updateResourceRecord(resourceType, numId, payload);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Record updated successfully",
            "data", updated
        ));
    }

    @DeleteMapping("/{resourceType}/{id}")
    @Operation(summary = "Delete resource center record", description = "Deletes a record by ID")
    public ResponseEntity<Map<String, Object>> deleteResourceRecord(
            @PathVariable("resourceType") String resourceType,
            @PathVariable("id") String id) {

        Integer numId = parseId(id);
        resourceCenterService.deleteResourceRecord(resourceType, numId);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Record deleted successfully"
        ));
    }

    private Integer parseId(String id) {
        if (id == null) {
            throw new BadRequestException("Invalid ID format");
        }
        try {
            int num = Integer.parseInt(id.trim());
            if (num <= 0) {
                throw new BadRequestException("Invalid ID format");
            }
            return num;
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid ID format");
        }
    }
}

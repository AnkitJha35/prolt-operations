package com.sense.prolt_operations.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.dto.response.MessageResponse;
import com.sense.prolt_operations.service.LandingPriceService;

@RestController
@RequestMapping("/api/landing-price-calc")
public class LandingPriceCalcController {

    private final LandingPriceService landingPriceService;

    public LandingPriceCalcController(LandingPriceService landingPriceService) {
        this.landingPriceService = landingPriceService;
    }

    @GetMapping("/calculate")
    public ResponseEntity<Object> calculate(@RequestParam Map<String, String> query) {
        try {
            return ResponseEntity.ok(landingPriceService.calculate(query));
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse(error.getMessage() != null ? error.getMessage() : "Landing price calculation failed."));
        }
    }

    @GetMapping("/consumer-category")
    public ResponseEntity<Object> getConsumerCategories(@RequestParam(required = false, defaultValue = "") String state) {
        try {
            return ResponseEntity.ok(landingPriceService.getConsumerCategories(state));
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse(error.getMessage() != null ? error.getMessage() : "Failed to fetch consumer categories"));
        }
    }

    @GetMapping("/voltage")
    public ResponseEntity<Object> getVoltages(
            @RequestParam(required = false, defaultValue = "") String state,
            @RequestParam(required = false, defaultValue = "") String consumerCategory) {
        try {
            return ResponseEntity.ok(landingPriceService.getVoltages(state, consumerCategory));
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse(error.getMessage() != null ? error.getMessage() : "Failed to fetch voltages"));
        }
    }

    @GetMapping("/tod-months")
    public ResponseEntity<Object> getTodMonths(
            @RequestParam(required = false, defaultValue = "") String state,
            @RequestParam(required = false, defaultValue = "") String consumerCategory,
            @RequestParam(required = false, defaultValue = "") String voltage) {
        try {
            return ResponseEntity.ok(landingPriceService.getTodMonths(state, consumerCategory, voltage));
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse(error.getMessage() != null ? error.getMessage() : "Failed to fetch TOD months"));
        }
    }

    @GetMapping("/tod-slots")
    public ResponseEntity<Object> getTodSlots(
            @RequestParam(required = false, defaultValue = "") String state,
            @RequestParam(required = false, defaultValue = "") String consumerCategory,
            @RequestParam(required = false, defaultValue = "") String voltage,
            @RequestParam(required = false, defaultValue = "") String month) {
        try {
            return ResponseEntity.ok(landingPriceService.getTodSlots(state, consumerCategory, voltage, month));
        } catch (Exception error) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse(error.getMessage() != null ? error.getMessage() : "Failed to fetch TOD slots"));
        }
    }
}

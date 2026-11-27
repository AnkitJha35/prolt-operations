package com.sense.prolt_operations.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.dto.calculator.LandingPriceCalculationRequest;
import com.sense.prolt_operations.dto.calculator.LandingPriceCalculationResponse;
import com.sense.prolt_operations.service.calculator.LandingPriceCalculatorService;

import jakarta.validation.Valid;

/**
 * REST API controller for the independent Landing Price Calculator capability.
 */
@RestController
@RequestMapping("/api/landing-price-calculator")
public class LandingPriceCalculatorController {

    private final LandingPriceCalculatorService landingPriceCalculatorService;

    public LandingPriceCalculatorController(LandingPriceCalculatorService landingPriceCalculatorService) {
        this.landingPriceCalculatorService = landingPriceCalculatorService;
    }

    /**
     * Executes landed price calculation via JSON POST body.
     */
    @PostMapping("/calculate")
    public ResponseEntity<LandingPriceCalculationResponse> calculatePost(
            @Valid @RequestBody LandingPriceCalculationRequest request) {
        LandingPriceCalculationResponse response = landingPriceCalculatorService.calculate(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Executes landed price calculation via HTTP GET query parameters (matching Node.js style).
     */
    @GetMapping("/calculate")
    public ResponseEntity<LandingPriceCalculationResponse> calculateGet(
            @Valid @ModelAttribute LandingPriceCalculationRequest request) {
        LandingPriceCalculationResponse response = landingPriceCalculatorService.calculate(request);
        return ResponseEntity.ok(response);
    }
}

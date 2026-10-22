package com.sense.prolt_operations.controller;

import com.sense.prolt_operations.service.LandingPriceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/landing-price-calc")
public class LandingPriceCalcController {

    private final LandingPriceService landingPriceService;

    public LandingPriceCalcController(LandingPriceService landingPriceService) {
        this.landingPriceService = landingPriceService;
    }

    @GetMapping("/calculate")
    public ResponseEntity<?> calculate(@RequestParam Map<String, String> query) {
        return ResponseEntity.ok(landingPriceService.calculate(query));
    }

    @GetMapping("/consumer-category")
    public ResponseEntity<?> getConsumerCategories(@RequestParam(required = false, defaultValue = "") String state) {
        return ResponseEntity.ok(landingPriceService.getConsumerCategories(state));
    }

    @GetMapping("/voltage")
    public ResponseEntity<?> getVoltages(
            @RequestParam(required = false, defaultValue = "") String state,
            @RequestParam(required = false, defaultValue = "") String consumerCategory) {
        return ResponseEntity.ok(landingPriceService.getVoltages(state, consumerCategory));
    }

    @GetMapping("/tod-months")
    public ResponseEntity<?> getTodMonths(
            @RequestParam(required = false, defaultValue = "") String state,
            @RequestParam(required = false, defaultValue = "") String consumerCategory,
            @RequestParam(required = false, defaultValue = "") String voltage) {
        return ResponseEntity.ok(landingPriceService.getTodMonths(state, consumerCategory, voltage));
    }

    @GetMapping("/tod-slots")
    public ResponseEntity<?> getTodSlots(
            @RequestParam(required = false, defaultValue = "") String state,
            @RequestParam(required = false, defaultValue = "") String consumerCategory,
            @RequestParam(required = false, defaultValue = "") String voltage,
            @RequestParam(required = false, defaultValue = "") String month) {
        return ResponseEntity.ok(landingPriceService.getTodSlots(state, consumerCategory, voltage, month));
    }
}

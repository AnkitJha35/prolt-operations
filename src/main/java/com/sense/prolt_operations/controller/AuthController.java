package com.sense.prolt_operations.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.dto.auth.AuthResponse;
import com.sense.prolt_operations.dto.auth.LoginDTO;
import com.sense.prolt_operations.dto.auth.SendOtpDTO;
import com.sense.prolt_operations.dto.auth.SuccessResponse;
import com.sense.prolt_operations.dto.auth.VerifyOtpDTO;
import com.sense.prolt_operations.security.ProltUserPrincipal;
import com.sense.prolt_operations.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentication and session management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user using username/email and password")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginDTO dto) {
        AuthResponse response = authService.login(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = {"/send-otp", "/otp/generate"})
    @Operation(summary = "Send OTP", description = "Send OTP to user email")
    public ResponseEntity<SuccessResponse> sendOtp(@RequestBody SendOtpDTO dto) {
        SuccessResponse response = authService.sendOtp(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = {"/verify-otp", "/otp/verify"})
    @Operation(summary = "Verify OTP", description = "Verify OTP and obtain JWT")
    public ResponseEntity<AuthResponse> verifyOtp(@RequestBody VerifyOtpDTO dto) {
        AuthResponse response = authService.verifyOtp(dto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Retrieve current authenticated user details")
    public ResponseEntity<Map<String, Object>> getMe(@AuthenticationPrincipal ProltUserPrincipal principal) {
        Map<String, Object> response = authService.getMe(principal);
        return ResponseEntity.ok(response);
    }
}

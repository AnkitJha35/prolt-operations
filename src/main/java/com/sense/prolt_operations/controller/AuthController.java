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

    @PostMapping("/send-otp")
    @Operation(summary = "Send OTP", description = "Send OTP to user email")
    public ResponseEntity<SuccessResponse> sendOtp(@RequestBody SendOtpDTO dto) {
        SuccessResponse response = authService.sendOtp(dto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verify OTP and obtain JWT with refresh token cookie")
    public ResponseEntity<AuthResponse> verifyOtp(@RequestBody VerifyOtpDTO dto,
                                                  jakarta.servlet.http.HttpServletRequest request,
                                                  jakarta.servlet.http.HttpServletResponse response) {
        String clientIp = com.sense.prolt_operations.security.ClientIpResolver.resolveClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        com.sense.prolt_operations.dto.auth.RefreshResult result = authService.verifyOtp(dto, clientIp, userAgent);
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, result.refreshCookie().toString());
        return ResponseEntity.ok(result.authResponse());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Rotate refresh token and issue a new access JWT")
    public ResponseEntity<AuthResponse> refresh(
            @org.springframework.web.bind.annotation.CookieValue(name = "${app.auth.refresh-token.cookie-name:iex_refresh_token}", required = false) String refreshToken,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new com.sense.prolt_operations.exception.UnauthorizedException("Refresh token cookie is required");
        }
        String clientIp = com.sense.prolt_operations.security.ClientIpResolver.resolveClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        com.sense.prolt_operations.dto.auth.RefreshResult result = authService.refresh(refreshToken, clientIp, userAgent);
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, result.refreshCookie().toString());
        return ResponseEntity.ok(result.authResponse());
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revoke current refresh session and clear refresh cookie")
    public ResponseEntity<SuccessResponse> logout(
            @org.springframework.web.bind.annotation.CookieValue(name = "${app.auth.refresh-token.cookie-name:iex_refresh_token}", required = false) String refreshToken,
            jakarta.servlet.http.HttpServletResponse response) {
        org.springframework.http.ResponseCookie logoutCookie = authService.logout(refreshToken);
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, logoutCookie.toString());
        return ResponseEntity.ok(new SuccessResponse(true, "Logged out successfully"));
    }

    @GetMapping("/sessions")
    @Operation(summary = "Get user sessions", description = "List all active refresh sessions for the current user")
    public ResponseEntity<java.util.List<com.sense.prolt_operations.dto.auth.UserSessionDTO>> getSessions(
            @AuthenticationPrincipal ProltUserPrincipal principal,
            @org.springframework.web.bind.annotation.CookieValue(name = "${app.auth.refresh-token.cookie-name:iex_refresh_token}", required = false) String refreshToken) {
        if (principal == null) {
            throw new com.sense.prolt_operations.exception.UnauthorizedException("Unauthorized");
        }
        java.util.List<com.sense.prolt_operations.dto.auth.UserSessionDTO> sessions =
                authService.getSessions(principal.getId(), refreshToken);
        return ResponseEntity.ok(sessions);
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    @Operation(summary = "Revoke session", description = "Revoke a specific active session belonging to the current user")
    public ResponseEntity<SuccessResponse> revokeSession(
            @AuthenticationPrincipal ProltUserPrincipal principal,
            @org.springframework.web.bind.annotation.PathVariable String sessionId) {
        if (principal == null) {
            throw new com.sense.prolt_operations.exception.UnauthorizedException("Unauthorized");
        }
        authService.revokeSession(principal.getId(), sessionId);
        return ResponseEntity.ok(new SuccessResponse(true, "Session successfully revoked"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Retrieve current authenticated user details")
    public ResponseEntity<com.sense.prolt_operations.dto.response.UserMeResponse> getMe(@AuthenticationPrincipal ProltUserPrincipal principal) {
        com.sense.prolt_operations.dto.response.UserMeResponse response = authService.getMe(principal);
        return ResponseEntity.ok(response);
    }
}

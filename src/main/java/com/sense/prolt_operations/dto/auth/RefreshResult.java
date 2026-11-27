package com.sense.prolt_operations.dto.auth;

import org.springframework.http.ResponseCookie;

public record RefreshResult(
    AuthResponse authResponse,
    ResponseCookie refreshCookie
) {
}

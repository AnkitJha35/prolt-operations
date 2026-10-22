package com.sense.prolt_operations.dto.auth;

public record AuthResponse(
    boolean success,
    String token,
    AuthUserResponse user
) {
}

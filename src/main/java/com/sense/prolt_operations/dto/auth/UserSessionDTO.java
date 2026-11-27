package com.sense.prolt_operations.dto.auth;

import java.time.LocalDateTime;

public record UserSessionDTO(
    String sessionId,
    LocalDateTime createdAt,
    LocalDateTime lastUsedAt,
    LocalDateTime expiresAt,
    String createdIp,
    String userAgent,
    boolean current
) {
}

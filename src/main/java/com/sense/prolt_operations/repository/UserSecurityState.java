package com.sense.prolt_operations.repository;

import com.sense.prolt_operations.entity.Role;

/**
 * Lightweight scalar projection for rapid user authentication state lookup in JwtAuthenticationFilter.
 */
public record UserSecurityState(
        String id,
        String username,
        Role role,
        boolean enabled,
        int tokenVersion
) {}

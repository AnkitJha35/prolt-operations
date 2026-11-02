package com.sense.prolt_operations.dto.user;

import java.time.LocalDateTime;
import java.util.List;

public record UserDTO(
    String id,
    String username,
    String email,
    String fullName,
    String role,
    List<String> hiddenModules,
    List<String> readOnlyModules,
    LocalDateTime createdAt
) {
    public UserDTO(String id, String username, String email, String role, List<String> hiddenModules, List<String> readOnlyModules, LocalDateTime createdAt) {
        this(id, username, email, null, role, hiddenModules, readOnlyModules, createdAt);
    }
}

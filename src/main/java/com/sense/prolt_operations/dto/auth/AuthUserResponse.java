package com.sense.prolt_operations.dto.auth;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthUserResponse(
    String id,
    String username,
    String email,
    String fullName,
    String role,
    boolean enabled,
    List<String> hiddenModules,
    List<String> readOnlyModules
) {
    public AuthUserResponse(String id, String username, String email, String fullName, String role, List<String> hiddenModules, List<String> readOnlyModules) {
        this(id, username, email, fullName, role, true, hiddenModules, readOnlyModules);
    }

    public AuthUserResponse(String id, String username, String email, String role, List<String> hiddenModules, List<String> readOnlyModules) {
        this(id, username, email, null, role, true, hiddenModules, readOnlyModules);
    }

    @JsonProperty("name")
    public String name() {
        return (fullName != null && !fullName.isBlank()) ? fullName : username;
    }
}

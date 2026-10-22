package com.sense.prolt_operations.dto.auth;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthUserResponse(
    String id,
    String username,
    String email,
    String role,
    List<String> hiddenModules,
    List<String> readOnlyModules
) {
    @JsonProperty("name")
    public String name() {
        return username;
    }
}

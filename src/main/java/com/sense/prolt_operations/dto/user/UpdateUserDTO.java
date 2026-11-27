package com.sense.prolt_operations.dto.user;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateUserDTO(
    String email,
    String fullName,
    String role,
    List<String> hiddenModules,
    List<String> readOnlyModules
) {
    public UpdateUserDTO(String email, String role, List<String> hiddenModules, List<String> readOnlyModules) {
        this(email, null, role, hiddenModules, readOnlyModules);
    }
}

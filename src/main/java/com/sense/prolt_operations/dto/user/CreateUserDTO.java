package com.sense.prolt_operations.dto.user;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateUserDTO(
    String username,
    String email,
    String fullName,
    String password,
    String role,
    List<String> hiddenModules,
    List<String> readOnlyModules
) {
    public CreateUserDTO(String username, String email, String password, String role, List<String> hiddenModules, List<String> readOnlyModules) {
        this(username, email, null, password, role, hiddenModules, readOnlyModules);
    }
}

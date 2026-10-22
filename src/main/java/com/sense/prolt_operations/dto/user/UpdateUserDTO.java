package com.sense.prolt_operations.dto.user;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateUserDTO(
    String username,
    String email,
    String password,
    String role,
    List<String> hiddenModules,
    List<String> readOnlyModules
) {
}

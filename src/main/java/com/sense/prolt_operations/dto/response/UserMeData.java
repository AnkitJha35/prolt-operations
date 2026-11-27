package com.sense.prolt_operations.dto.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserMeData(
    String id,
    String username,
    String fullName,
    String role,
    boolean enabled,
    List<String> hiddenModules,
    List<String> readOnlyModules,
    Long iat,
    Long exp
) {
}

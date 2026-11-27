package com.sense.prolt_operations.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateUserStatusDTO(
    @NotNull(message = "enabled field is required")
    Boolean enabled
) {}

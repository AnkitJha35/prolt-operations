package com.sense.prolt_operations.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VerifyOtpDTO(
    String email,
    String otp
) {
}

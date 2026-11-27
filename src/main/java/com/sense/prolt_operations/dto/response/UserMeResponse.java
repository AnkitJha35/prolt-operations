package com.sense.prolt_operations.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserMeResponse(
    boolean success,
    UserMeData user
) {
    public static UserMeResponse of(UserMeData user) {
        return new UserMeResponse(true, user);
    }
}

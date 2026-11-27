package com.sense.prolt_operations.security;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String customMsg = (String) request.getAttribute("auth_error_message");
        if (customMsg != null && !customMsg.isBlank()) {
            response.getWriter().write("{\"success\":false,\"message\":\"" + customMsg + "\"}");
            return;
        }
        response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\"}");
    }
}

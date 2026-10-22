package com.sense.prolt_operations.security;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/users")) {
            response.getWriter().write("{\"success\":false,\"message\":\"Forbidden: Super Admin access required\"}");
        } else {
            response.getWriter().write("{\"success\":false,\"message\":\"Forbidden: Access denied\"}");
        }
    }
}

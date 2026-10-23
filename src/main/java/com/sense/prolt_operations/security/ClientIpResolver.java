package com.sense.prolt_operations.security;

import java.util.Enumeration;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * Resolves the client IP address matching Node's app.set('trust proxy', 1).
     * The last entry in X-Forwarded-For is the client IP as observed by the immediate reverse proxy (Nginx).
     * The first entries can be spoofed by an external client.
     */
    public static String resolveClientIp(HttpServletRequest request) {
        Enumeration<String> headers = request.getHeaders("X-Forwarded-For");
        String lastIp = null;
        if (headers != null) {
            while (headers.hasMoreElements()) {
                String headerVal = headers.nextElement();
                if (headerVal != null && !headerVal.isBlank()) {
                    String[] parts = headerVal.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) {
                            lastIp = trimmed;
                        }
                    }
                }
            }
        }
        if (lastIp != null) {
            return lastIp;
        }
        return request.getRemoteAddr();
    }
}

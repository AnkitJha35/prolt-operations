package com.sense.prolt_operations.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class UploadRateLimiterFilter extends OncePerRequestFilter {

    private final FixedWindowRateLimiter limiter;

    @Value("${app.rate-limit.legacy-headers:false}")
    private boolean legacyHeaders;

    public UploadRateLimiterFilter(Clock clock) {
        this.limiter = new FixedWindowRateLimiter(Duration.ofHours(1), 30, clock);
    }

    public FixedWindowRateLimiter getLimiter() {
        return limiter;
    }

    public void setClock(Clock clock) {
        this.limiter.setClock(clock);
    }

    public void setLegacyHeaders(boolean legacyHeaders) {
        this.legacyHeaders = legacyHeaders;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isRateLimitedUploadRoute(request)) {
            // In Node: upload routes are protected by authMiddleware first before uploadLimiter.
            // If the user is unauthenticated, skip rate limiter so request is rejected with 401 by AuthorizationFilter
            // without consuming upload rate limit quota.
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                filterChain.doFilter(request, response);
                return;
            }

            String clientIp = ClientIpResolver.resolveClientIp(request);
            FixedWindowRateLimiter.RateLimitResult result = limiter.tryAcquire(clientIp);

            // Set standard headers matching express-rate-limit (standardHeaders: true)
            response.setHeader("RateLimit-Limit", String.valueOf(result.limit()));
            response.setHeader("RateLimit-Remaining", String.valueOf(result.remaining()));
            response.setHeader("RateLimit-Reset", String.valueOf(result.resetSeconds()));

            if (legacyHeaders) {
                response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
                response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));
            }

            if (!result.allowed()) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
                response.getWriter().write("{\"success\":false,\"message\":\"Upload limit exceeded. Please try again after an hour.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimitedUploadRoute(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null) return false;
        return uri.equals("/api/uploads") || uri.equals("/api/uploads/");
    }
}

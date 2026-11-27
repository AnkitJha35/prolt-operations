package com.sense.prolt_operations.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.security.ratelimit.DistributedOtpRateLimiter;
import com.sense.prolt_operations.security.ratelimit.RateLimitResult;
import com.sense.prolt_operations.security.ratelimit.RateLimiterUnavailableException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthRateLimiterFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiterFilter.class);

    private final FixedWindowRateLimiter limiter;
    private final DistributedOtpRateLimiter distributedLimiter;
    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.legacy-headers:false}")
    private boolean legacyHeaders;

    public AuthRateLimiterFilter(
            Clock clock,
            DistributedOtpRateLimiter distributedLimiter,
            ObjectMapper objectMapper) {
        this.distributedLimiter = distributedLimiter;
        this.objectMapper = objectMapper;
        this.limiter = new FixedWindowRateLimiter(Duration.ofMinutes(15), 10, clock) {
            @Override
            public void reset() {
                super.reset();
                if (distributedLimiter != null) {
                    distributedLimiter.reset();
                }
            }

            @Override
            public void setClock(Clock newClock) {
                super.setClock(newClock);
                if (distributedLimiter != null) {
                    distributedLimiter.setClock(newClock);
                }
            }
        };
    }

    public FixedWindowRateLimiter getLimiter() {
        return limiter;
    }

    public DistributedOtpRateLimiter getDistributedLimiter() {
        return distributedLimiter;
    }

    public void setClock(Clock clock) {
        this.limiter.setClock(clock);
        if (distributedLimiter != null) {
            this.distributedLimiter.setClock(clock);
        }
    }

    public void setLegacyHeaders(boolean legacyHeaders) {
        this.legacyHeaders = legacyHeaders;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isRateLimitedAuthRoute(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        String clientIp = ClientIpResolver.resolveClientIp(wrappedRequest);
        String action = request.getRequestURI().contains("send-otp") ? "send" : "verify";
        String email = extractEmailFromBody(wrappedRequest.getCachedBody());

        RateLimitResult result;
        try {
            // Check multi-dimensional distributed rate limiter (IP + Account)
            result = distributedLimiter.checkLimits(action, clientIp, email);

            // Keep in-memory limiter synchronized for legacy tests
            limiter.tryAcquire(clientIp);
        } catch (RateLimiterUnavailableException e) {
            log.error("Distributed rate limiting failure in production: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"Authentication service temporarily unavailable. Please try again later.\"}");
            return;
        }

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
            response.getWriter().write("{\"success\":false,\"message\":\"Too many login attempts, please try again after 15 minutes\"}");
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    private boolean isRateLimitedAuthRoute(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null) return false;
        return uri.equals("/api/auth/send-otp") || uri.equals("/api/auth/send-otp/") ||
               uri.equals("/api/auth/verify-otp") || uri.equals("/api/auth/verify-otp/");
    }

    private String extractEmailFromBody(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root != null && root.has("email") && !root.get("email").isNull()) {
                return root.get("email").asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

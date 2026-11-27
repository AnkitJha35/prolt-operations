package com.sense.prolt_operations.security.ratelimit;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.sense.prolt_operations.security.cors.CorsConfigValidator;

@Component
public class DistributedOtpRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(DistributedOtpRateLimiter.class);

    private final RateLimitProperties properties;
    private final Environment environment;
    private final StringRedisTemplate redisTemplate;
    private final RedisRateLimiter redisRateLimiter;
    private final InMemoryRateLimiter fallbackRateLimiter;
    private final boolean isProduction;

    public DistributedOtpRateLimiter(
            RateLimitProperties properties,
            Environment environment,
            @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.environment = environment;
        this.redisTemplate = redisTemplate;
        this.isProduction = CorsConfigValidator.determineIfProduction(environment);
        this.fallbackRateLimiter = new InMemoryRateLimiter();
        this.redisRateLimiter = (redisTemplate != null) ? new RedisRateLimiter(redisTemplate) : null;
    }

    @PostConstruct
    public void validateConfiguration() {
        if (isProduction) {
            String key = properties != null ? properties.getAccountHmacKey() : null;
            if (key == null || key.isBlank()) {
                throw new IllegalStateException(
                    "FATAL: Production rate limiter misconfiguration: 'RATE_LIMIT_HMAC_KEY' (app.rate-limit.account-hmac-key) must be configured with a non-blank secret key!"
                );
            }
        }
    }

    public void setClock(Clock clock) {
        this.fallbackRateLimiter.setClock(clock);
    }

    public InMemoryRateLimiter getFallbackRateLimiter() {
        return fallbackRateLimiter;
    }

    public void reset() {
        fallbackRateLimiter.reset();
    }

    /**
     * Privacy-preserving HMAC-SHA256 representation of account email for Redis keys.
     */
    public String hashAccount(String email) {
        if (email == null || email.isBlank()) {
            return "anonymous";
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String key = properties != null ? properties.getAccountHmacKey() : null;
        if (key == null || key.isBlank()) {
            if (isProduction) {
                log.error("FATAL: Rate limit HMAC key is not configured in production environment!");
                throw new IllegalStateException("FATAL: Production rate limiter misconfiguration: 'RATE_LIMIT_HMAC_KEY' is missing or blank!");
            }
            log.warn("RATE_LIMIT_HMAC_KEY is not configured in non-production; using fallback digest for account rate-limiting");
            return org.springframework.util.DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hmacBytes.length * 2);
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            if (isProduction) {
                log.error("FATAL: HMAC hashing failed in production environment: {}", e.getMessage(), e);
                throw new IllegalStateException("FATAL: Failed to compute account HMAC in production: " + e.getMessage(), e);
            }
            log.warn("HMAC hashing failed in non-production, fallback to digest: {}", e.getMessage());
            return org.springframework.util.DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Evaluates multi-dimensional rate limits for an auth request.
     *
     * @param action "send" or "verify"
     * @param clientIp client IP address
     * @param email account email if present (may be null/empty)
     * @return RateLimitResult indicating allow/reject, quota limits, and remaining time
     */
    public RateLimitResult checkLimits(String action, String clientIp, String email) {
        // 1. IP-level rate limit (Shared across auth endpoints)
        String ipKey = "rl:auth:ip:" + (clientIp != null ? clientIp : "unknown");
        RateLimitResult ipResult = executeCheck(
                ipKey,
                properties.getAuthIpLimit(),
                properties.getAuthIpWindowSeconds()
        );

        if (!ipResult.allowed()) {
            log.warn("Rate limit exceeded for IP: {} on action: {}", clientIp, action);
            return ipResult;
        }

        // 2. Account-level rate limit (if email is provided)
        if (email != null && !email.isBlank()) {
            String hashedAcc = hashAccount(email);
            String accKey = "rl:auth:" + action + ":acc:" + hashedAcc;
            int accLimit = "send".equalsIgnoreCase(action)
                    ? properties.getOtpSendAccountLimit()
                    : properties.getOtpVerifyAccountLimit();
            long accWindow = "send".equalsIgnoreCase(action)
                    ? properties.getOtpSendAccountWindowSeconds()
                    : properties.getOtpVerifyAccountWindowSeconds();

            RateLimitResult accResult = executeCheck(accKey, accLimit, accWindow);
            if (!accResult.allowed()) {
                log.warn("Rate limit exceeded for account hash: {} on action: {}", hashedAcc, action);
                return accResult;
            }
        }

        return ipResult;
    }

    private RateLimitResult executeCheck(String key, int limit, long windowSeconds) {
        if (redisRateLimiter != null) {
            try {
                return redisRateLimiter.checkAndIncrement(key, limit, windowSeconds);
            } catch (RateLimiterUnavailableException e) {
                if (isProduction) {
                    log.error("FATAL: Distributed Redis rate limiter failure in production! Failing closed: {}", e.getMessage());
                    throw e;
                }
                log.warn("Redis rate limiter unavailable in local/dev; falling back to in-memory store: {}", e.getMessage());
                return fallbackRateLimiter.checkAndIncrement(key, limit, windowSeconds);
            }
        }

        // No RedisTemplate bean present
        if (isProduction) {
            log.error("FATAL: Redis is mandatory for distributed rate limiting in production, but RedisTemplate bean is not configured!");
            throw new RateLimiterUnavailableException("Distributed rate limiter unavailable in production");
        }

        return fallbackRateLimiter.checkAndIncrement(key, limit, windowSeconds);
    }

    public RateLimitProperties getProperties() {
        return properties;
    }

    public boolean isProduction() {
        return isProduction;
    }
}

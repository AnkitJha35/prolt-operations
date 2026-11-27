package com.sense.prolt_operations.security.ratelimit;

public record RateLimitResult(
        boolean allowed,
        int limit,
        int remaining,
        long resetSeconds,
        long retryAfterSeconds,
        String key
) {
    public static RateLimitResult allowed(int limit, int remaining, long resetSeconds, String key) {
        return new RateLimitResult(true, limit, remaining, resetSeconds, 0, key);
    }

    public static RateLimitResult rejected(int limit, long resetSeconds, long retryAfterSeconds, String key) {
        return new RateLimitResult(false, limit, 0, resetSeconds, retryAfterSeconds, key);
    }
}

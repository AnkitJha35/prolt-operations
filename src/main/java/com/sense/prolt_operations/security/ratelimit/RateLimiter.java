package com.sense.prolt_operations.security.ratelimit;

public interface RateLimiter {

    /**
     * Atomically checks and increments the counter for the specified key within a time window.
     *
     * @param key unique counter key
     * @param limit maximum allowed requests within the window
     * @param windowSeconds duration of the rate limit window in seconds
     * @return RateLimitResult indicating whether the request is allowed and remaining quotas
     */
    RateLimitResult checkAndIncrement(String key, int limit, long windowSeconds);

    /**
     * Resets or clears counters (used primarily for test isolation).
     */
    void reset();
}

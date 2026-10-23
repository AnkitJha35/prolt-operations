package com.sense.prolt_operations.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter matching express-rate-limit semantics.
 * Window starts on the first request and resets when windowDuration expires.
 * Hits do not refill continuously/linearly like a token bucket.
 */
public class FixedWindowRateLimiter {

    public record WindowRecord(long resetEpochMillis, int hits) {}

    public record RateLimitResult(
            boolean allowed,
            int limit,
            int remaining,
            long resetSeconds,
            long retryAfterSeconds
    ) {}

    private final Duration windowDuration;
    private final int maxRequests;
    private volatile Clock clock;
    private final ConcurrentHashMap<String, WindowRecord> store = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(Duration windowDuration, int maxRequests) {
        this(windowDuration, maxRequests, Clock.systemUTC());
    }

    public FixedWindowRateLimiter(Duration windowDuration, int maxRequests, Clock clock) {
        this.windowDuration = windowDuration;
        this.maxRequests = maxRequests;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public Clock getClock() {
        return this.clock;
    }

    public void reset() {
        this.store.clear();
    }

    public RateLimitResult tryAcquire(String key) {
        long windowDurationMs = windowDuration.toMillis();
        long now = clock.millis();

        WindowRecord record = store.compute(key, (k, existing) -> {
            if (existing == null || existing.resetEpochMillis() <= now) {
                return new WindowRecord(now + windowDurationMs, 1);
            }
            return new WindowRecord(existing.resetEpochMillis(), existing.hits() + 1);
        });

        long resetSeconds = Math.max(0, (long) Math.ceil((record.resetEpochMillis() - now) / 1000.0));
        boolean allowed = record.hits() <= maxRequests;
        int remaining = Math.max(0, maxRequests - record.hits());

        return new RateLimitResult(allowed, maxRequests, remaining, resetSeconds, resetSeconds);
    }
}

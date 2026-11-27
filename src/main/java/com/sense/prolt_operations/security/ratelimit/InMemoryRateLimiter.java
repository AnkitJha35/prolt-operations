package com.sense.prolt_operations.security.ratelimit;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRateLimiter implements RateLimiter {

    public record WindowRecord(long resetEpochMillis, int hits) {}

    private final ConcurrentHashMap<String, WindowRecord> store = new ConcurrentHashMap<>();
    private volatile Clock clock;

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public Clock getClock() {
        return clock;
    }

    @Override
    public RateLimitResult checkAndIncrement(String key, int limit, long windowSeconds) {
        long now = clock.millis();
        long windowDurationMs = windowSeconds * 1000L;

        WindowRecord record = store.compute(key, (k, existing) -> {
            if (existing == null || existing.resetEpochMillis() <= now) {
                return new WindowRecord(now + windowDurationMs, 1);
            }
            return new WindowRecord(existing.resetEpochMillis(), existing.hits() + 1);
        });

        long resetSeconds = Math.max(0, (long) Math.ceil((record.resetEpochMillis() - now) / 1000.0));
        boolean allowed = record.hits() <= limit;
        int remaining = Math.max(0, limit - record.hits());

        if (allowed) {
            return RateLimitResult.allowed(limit, remaining, resetSeconds, key);
        } else {
            return RateLimitResult.rejected(limit, resetSeconds, resetSeconds, key);
        }
    }

    @Override
    public void reset() {
        store.clear();
    }
}

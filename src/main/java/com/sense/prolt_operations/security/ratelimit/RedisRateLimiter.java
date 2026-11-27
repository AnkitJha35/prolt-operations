package com.sense.prolt_operations.security.ratelimit;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final String ATOMIC_RATE_LIMIT_LUA = """
        local key = KEYS[1]
        local limit = tonumber(ARGV[1])
        local window = tonumber(ARGV[2])

        local current = redis.call('INCR', key)
        if current == 1 then
            redis.call('EXPIRE', key, window)
        end
        local ttl = redis.call('TTL', key)
        if ttl < 0 then
            redis.call('EXPIRE', key, window)
            ttl = window
        end
        return { current, ttl }
        """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> script;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(ATOMIC_RATE_LIMIT_LUA, List.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitResult checkAndIncrement(String key, int limit, long windowSeconds) {
        try {
            List<Long> result = (List<Long>) redisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    String.valueOf(limit),
                    String.valueOf(windowSeconds)
            );

            if (result == null || result.size() < 2) {
                throw new IllegalStateException("Redis rate limiting script returned null or incomplete result");
            }

            long currentCount = result.get(0);
            long ttl = Math.max(0, result.get(1));

            boolean allowed = currentCount <= limit;
            int remaining = Math.max(0, limit - (int) currentCount);

            if (allowed) {
                return RateLimitResult.allowed(limit, remaining, ttl, key);
            } else {
                return RateLimitResult.rejected(limit, ttl, ttl, key);
            }
        } catch (Exception e) {
            log.warn("Redis rate limiter execution failure for key '{}': {}", key, e.getMessage());
            throw new RateLimiterUnavailableException("Redis rate limiter unavailable", e);
        }
    }

    @Override
    public void reset() {
        // No-op or pattern delete in tests if needed
    }
}

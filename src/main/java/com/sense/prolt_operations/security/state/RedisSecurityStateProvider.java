package com.sense.prolt_operations.security.state;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.exception.SecurityStateUnavailableException;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.repository.UserSecurityState;

public class RedisSecurityStateProvider implements UserSecurityStateProvider {

    private static final Logger log = LoggerFactory.getLogger(RedisSecurityStateProvider.class);
    private static final String KEY_PREFIX = "user:security-state:";
    private static final long DEFAULT_TTL_SECONDS = 15L * 60; // 15 minutes matching JWT access token expiration

    private static final String ATOMIC_VERSION_SET_LUA = """
        local current = redis.call('GET', KEYS[1])
        if not current then
            redis.call('SETEX', KEYS[1], ARGV[1], ARGV[2])
            return 1
        end
        local ok, obj = pcall(cjson.decode, current)
        if ok and obj and obj.tokenVersion then
            if tonumber(ARGV[3]) >= tonumber(obj.tokenVersion) then
                redis.call('SETEX', KEYS[1], ARGV[1], ARGV[2])
                return 1
            else
                return 0
            end
        else
            redis.call('SETEX', KEYS[1], ARGV[1], ARGV[2])
            return 1
        end
        """;

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<Long> atomicSetScript;

    public RedisSecurityStateProvider(StringRedisTemplate redisTemplate,
                                      UserRepository userRepository,
                                      ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.atomicSetScript = new DefaultRedisScript<>(ATOMIC_VERSION_SET_LUA, Long.class);
    }

    private String keyFor(String userId) {
        return KEY_PREFIX + userId;
    }

    @Override
    public Optional<UserSecurityState> getSecurityState(String userId) {
        String key = keyFor(userId);
        String cachedJson;

        // 1. Attempt reading from Redis
        try {
            cachedJson = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            // STRICT FAIL-CLOSED: Do not silently fall back to PostgreSQL in production!
            log.error("Redis security state provider failed while reading key '{}': {}", key, e.getMessage(), e);
            throw new SecurityStateUnavailableException("Redis security state cache is unavailable", e);
        }

        // 2. Cache Hit
        if (cachedJson != null && !cachedJson.isBlank()) {
            try {
                UserSecurityState state = objectMapper.readValue(cachedJson, UserSecurityState.class);
                log.trace("Redis cache hit for user '{}' (enabled={}, tokenVersion={})",
                        userId, state.enabled(), state.tokenVersion());
                return Optional.of(state);
            } catch (Exception e) {
                log.warn("Corrupt JSON in Redis for key '{}', evicting and reloading from DB: {}", key, e.getMessage());
                evictSecurityState(userId);
            }
        }

        // 3. Cache Miss: Read from PostgreSQL
        log.debug("Redis cache miss for user '{}' - querying PostgreSQL", userId);
        Optional<UserSecurityState> stateOpt = userRepository.findSecurityStateById(userId);

        // 4. Populate Redis on Cache Miss
        if (stateOpt.isPresent()) {
            UserSecurityState state = stateOpt.get();
            writeStateToRedis(state);
        }

        return stateOpt;
    }

    @Override
    public void updateSecurityState(String userId, boolean enabled, int tokenVersion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncStateToRedis(userId);
                }
            });
        } else {
            syncStateToRedis(userId);
        }
    }

    @Override
    public void evictSecurityState(String userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doEvict(userId);
                }
            });
        } else {
            doEvict(userId);
        }
    }

    private void doEvict(String userId) {
        try {
            redisTemplate.delete(keyFor(userId));
        } catch (Exception e) {
            log.error("Failed to evict Redis security state for userId='{}': {}", userId, e.getMessage());
            throw new SecurityStateUnavailableException("Failed to evict Redis security state", e);
        }
    }

    private void syncStateToRedis(String userId) {
        Optional<UserSecurityState> stateOpt = userRepository.findSecurityStateById(userId);
        if (stateOpt.isPresent()) {
            writeStateToRedis(stateOpt.get());
        } else {
            evictSecurityState(userId);
        }
    }

    private void writeStateToRedis(UserSecurityState state) {
        String key = keyFor(state.id());
        try {
            String json = objectMapper.writeValueAsString(state);
            redisTemplate.execute(
                    atomicSetScript,
                    Collections.singletonList(key),
                    String.valueOf(DEFAULT_TTL_SECONDS),
                    json,
                    String.valueOf(state.tokenVersion())
            );
            log.debug("Synchronized user security state to Redis for user '{}' (tokenVersion={})",
                    state.id(), state.tokenVersion());
        } catch (Exception e) {
            log.error("Failed to write user security state to Redis for key '{}': {}", key, e.getMessage());
            throw new SecurityStateUnavailableException("Failed to update Redis security state", e);
        }
    }
}

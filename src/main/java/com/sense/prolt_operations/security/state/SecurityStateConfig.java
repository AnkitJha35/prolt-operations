package com.sense.prolt_operations.security.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.repository.UserRepository;

import jakarta.annotation.PostConstruct;

@Configuration
public class SecurityStateConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityStateConfig.class);

    @Value("${app.security-state.provider:database}")
    private String provider;

    private final Environment environment;

    public SecurityStateConfig(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateConfiguration() {
        String cleanProvider = provider != null ? provider.trim().toLowerCase() : "";
        if (!cleanProvider.equals("database") && !cleanProvider.equals("redis")) {
            throw new IllegalStateException(
                "Invalid configuration: 'app.security-state.provider' must be either 'database' or 'redis', but was '" + provider + "'"
            );
        }

        if (cleanProvider.equals("redis")) {
            String redisUrl = environment.getProperty("spring.data.redis.url");
            String appRedisUrl = environment.getProperty("app.redis-url");
            if ((redisUrl == null || redisUrl.isBlank()) && (appRedisUrl == null || appRedisUrl.isBlank())) {
                throw new IllegalStateException(
                    "FATAL: 'app.security-state.provider=redis' requires a valid Redis URL configured via 'spring.data.redis.url' or 'app.redis-url'!"
                );
            }
            log.info("Security State Provider configured: REDIS (fail-closed, version-aware)");
        } else {
            log.info("Security State Provider configured: DATABASE (direct PostgreSQL, Redis not required)");
        }
    }

    @Bean
    @ConditionalOnProperty(name = "app.security-state.provider", havingValue = "database", matchIfMissing = true)
    public UserSecurityStateProvider databaseSecurityStateProvider(UserRepository userRepository) {
        log.info("Instantiating DatabaseSecurityStateProvider bean");
        return new DatabaseSecurityStateProvider(userRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "app.security-state.provider", havingValue = "redis")
    public UserSecurityStateProvider redisSecurityStateProvider(
            StringRedisTemplate redisTemplate,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        log.info("Instantiating RedisSecurityStateProvider bean");
        return new RedisSecurityStateProvider(redisTemplate, userRepository, objectMapper);
    }
}

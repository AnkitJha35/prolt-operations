package com.sense.prolt_operations;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
public abstract class BaseIntegrationTest {

    public static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("Prolt_Operations")
                .withUsername("postgres")
                .withPassword("password");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?") + "stringtype=unspecified");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.hibernate.naming.physical-strategy", () -> "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        registry.add("spring.jpa.properties.hibernate.physical_naming_strategy", () -> "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        registry.add("app.auth.superadmin-otp-bypass.enabled", () -> "true");
        registry.add("app.auth.superadmin-otp-bypass.otp-value", () -> "749261");
        registry.add("app.auth.superadmin-otp-bypass.email", () -> "super.admin@probus.io");
        registry.add("app.auth.otp.allow-memory-fallback", () -> "true");
        registry.add("app.run-scraper", () -> "false");
        registry.add("management.health.redis.enabled", () -> "false");
        registry.add("management.health.mail.enabled", () -> "false");
        registry.add("app.jwt.secret", () -> "super_secret_jwt_key_for_testing_purposes_at_least_32_bytes!");
        registry.add("management.endpoints.web.exposure.include", () -> "health,info,prometheus");
        registry.add("management.prometheus.metrics.export.enabled", () -> "true");
    }
}

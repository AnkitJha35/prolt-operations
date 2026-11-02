package com.sense.prolt_operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.JwtService;
import com.sense.prolt_operations.service.OtpService;

class ProfileSecurityDefaultsTest {

    @Test
    @DisplayName("SEC-01: With no profile set, bypass is disabled and OTP verification is rejected")
    void testNoProfileBypassRejected() {
        MockEnvironment env = new MockEnvironment(); // No active profiles
        UserRepository userRepo = mock(UserRepository.class);
        org.springframework.data.redis.core.StringRedisTemplate redisTemplate = mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        org.mockito.Mockito.when(valueOps.get("otp:super.admin@probus.io")).thenReturn(null); // No OTP stored in redis

        OtpService otpService = new OtpService(userRepo, redisTemplate, null);

        ReflectionTestUtils.setField(otpService, "environment", env);
        ReflectionTestUtils.setField(otpService, "superadminBypassEnabled", false); // fail-closed default from application.properties
        ReflectionTestUtils.setField(otpService, "superadminEmail", "super.admin@probus.io");
        ReflectionTestUtils.setField(otpService, "allowMemoryFallback", false);
        otpService.validateStartupConfiguration();

        // Verification fails closed (not bypassed)
        boolean result = otpService.verifyOtp("super.admin@probus.io", "749261");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("SEC-02: With dev profile, bypass is enabled and OTP verification succeeds")
    void testDevProfileBypassWorks() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        UserRepository userRepo = mock(UserRepository.class);
        OtpService otpService = new OtpService(userRepo, null, null);

        ReflectionTestUtils.setField(otpService, "environment", env);
        ReflectionTestUtils.setField(otpService, "superadminBypassEnabled", true); // enabled in application-dev.properties
        ReflectionTestUtils.setField(otpService, "superadminFixedOtp", "749261");
        ReflectionTestUtils.setField(otpService, "superadminEmail", "super.admin@probus.io");
        ReflectionTestUtils.setField(otpService, "allowMemoryFallback", true);
        otpService.validateStartupConfiguration();

        boolean result = otpService.verifyOtp("super.admin@probus.io", "749261");
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("SEC-03: Startup validation fails if bypass is enabled without OTP value")
    void testStartupFailsWhenBypassEnabledWithoutOtp() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        UserRepository userRepo = mock(UserRepository.class);
        OtpService otpService = new OtpService(userRepo, null, null);

        ReflectionTestUtils.setField(otpService, "environment", env);
        ReflectionTestUtils.setField(otpService, "superadminBypassEnabled", true);
        ReflectionTestUtils.setField(otpService, "superadminFixedOtp", ""); // empty/missing

        assertThatThrownBy(otpService::validateStartupConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Superadmin OTP bypass is enabled, but app.auth.superadmin-otp-bypass.otp-value is not configured");
    }

    @Test
    @DisplayName("SEC-04: Startup validation fails if bypass is enabled in prod profile")
    void testStartupFailsWhenBypassEnabledInProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        UserRepository userRepo = mock(UserRepository.class);
        OtpService otpService = new OtpService(userRepo, null, null);

        ReflectionTestUtils.setField(otpService, "environment", env);
        ReflectionTestUtils.setField(otpService, "superadminBypassEnabled", true);
        ReflectionTestUtils.setField(otpService, "superadminFixedOtp", "749261");

        assertThatThrownBy(otpService::validateStartupConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Superadmin OTP bypass cannot be enabled in the 'prod' profile");
    }

    @Test
    @DisplayName("SEC-05: JWT startup fails in prod if secret is missing or shorter than 32 bytes")
    void testJwtStartupFailsInProdForShortSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        JwtService jwtService = new JwtService();

        ReflectionTestUtils.setField(jwtService, "environment", env);
        ReflectionTestUtils.setField(jwtService, "secret", "short-secret-less-than-32-b");

        assertThatThrownBy(jwtService::init)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("In production, app.jwt.secret must be provided and must be at least 32 bytes long");
    }

    @Test
    @DisplayName("SEC-06: Prod profile disables Swagger UI, API docs, and health details")
    void testProdProfileSwaggerAndHealthSettings() throws Exception {
        java.util.Properties prodProps = new java.util.Properties();
        try (var is = getClass().getResourceAsStream("/application-prod.properties")) {
            prodProps.load(is);
        }
        assertThat(prodProps.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
        assertThat(prodProps.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(prodProps.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }

    @Test
    @DisplayName("SEC-07: Dev profile enables health details for authorized callers")
    void testDevProfileHealthSettings() throws Exception {
        java.util.Properties devProps = new java.util.Properties();
        try (var is = getClass().getResourceAsStream("/application-dev.properties")) {
            devProps.load(is);
        }
        assertThat(devProps.getProperty("management.endpoint.health.show-details")).isEqualTo("when-authorized");
    }

    @Test
    @DisplayName("SEC-08: Production datasource validator fails startup if spring.datasource.url is blank")
    void testProdDatasourceValidatorFailsIfUrlBlank() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.url", "");
        env.setProperty("spring.datasource.username", "postgres");
        env.setProperty("spring.datasource.password", "secret");

        com.sense.prolt_operations.config.ProductionDatasourceValidator validator = new com.sense.prolt_operations.config.ProductionDatasourceValidator();
        validator.setEnvironment(env);

        assertThatThrownBy(() -> validator.postProcessBeanFactory(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Production datasource URL (spring.datasource.url / DATABASE_URL) must not be blank!");
    }

    @Test
    @DisplayName("SEC-09: Production datasource validator fails startup if spring.datasource.username is blank")
    void testProdDatasourceValidatorFailsIfUsernameBlank() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/db");
        env.setProperty("spring.datasource.username", "");
        env.setProperty("spring.datasource.password", "secret");

        com.sense.prolt_operations.config.ProductionDatasourceValidator validator = new com.sense.prolt_operations.config.ProductionDatasourceValidator();
        validator.setEnvironment(env);

        assertThatThrownBy(() -> validator.postProcessBeanFactory(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Production datasource username (spring.datasource.username / POSTGRES_USER) must not be blank!");
    }

    @Test
    @DisplayName("SEC-10: Production datasource validator fails startup if spring.datasource.password is blank")
    void testProdDatasourceValidatorFailsIfPasswordBlank() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/db");
        env.setProperty("spring.datasource.username", "postgres");
        env.setProperty("spring.datasource.password", "");

        com.sense.prolt_operations.config.ProductionDatasourceValidator validator = new com.sense.prolt_operations.config.ProductionDatasourceValidator();
        validator.setEnvironment(env);

        assertThatThrownBy(() -> validator.postProcessBeanFactory(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Production datasource password (spring.datasource.password / POSTGRES_PASSWORD) must not be blank!");
    }

    @Test
    @DisplayName("SEC-11: StarrocksService with blank host skips connection and returns empty list and map")
    void testStarrocksServiceBlankHostSkipsConnection() {
        com.sense.prolt_operations.service.StarrocksService starrocksService = new com.sense.prolt_operations.service.StarrocksService();
        ReflectionTestUtils.setField(starrocksService, "host", "");

        assertThat(starrocksService.getAvailableMeters()).isEmpty();
        assertThat(starrocksService.getConsumerActualDemandMap(java.util.List.of("2026-06-01"), "M123")).isEmpty();
    }

    @Test
    @DisplayName("SEC-12: Verify Redis defaults and profile-specific overrides")
    void testRedisProfileConfigurations() throws Exception {
        java.util.Properties defaultProps = new java.util.Properties();
        try (var is = getClass().getResourceAsStream("/application.properties")) {
            defaultProps.load(is);
        }
        assertThat(defaultProps.getProperty("app.auth.otp.allow-memory-fallback"))
            .isEqualTo("${OTP_ALLOW_MEMORY_FALLBACK:true}");
        assertThat(defaultProps.getProperty("management.health.redis.enabled"))
            .isEqualTo("${MANAGEMENT_HEALTH_REDIS_ENABLED:false}");
        assertThat(defaultProps.getProperty("spring.data.redis.url"))
            .isEqualTo("${REDIS_URL:redis://localhost:6379}");

        java.util.Properties prodProps = new java.util.Properties();
        try (var is = getClass().getResourceAsStream("/application-prod.properties")) {
            prodProps.load(is);
        }
        assertThat(prodProps.getProperty("app.auth.otp.allow-memory-fallback")).isEqualTo("false");
        assertThat(prodProps.getProperty("management.health.redis.enabled")).isEqualTo("true");

        java.util.Properties stagingProps = new java.util.Properties();
        try (var is = getClass().getResourceAsStream("/application-staging.properties")) {
            stagingProps.load(is);
        }
        assertThat(stagingProps.getProperty("app.auth.otp.allow-memory-fallback")).isEqualTo("true");
        assertThat(stagingProps.getProperty("management.health.redis.enabled")).isEqualTo("true");
    }
}

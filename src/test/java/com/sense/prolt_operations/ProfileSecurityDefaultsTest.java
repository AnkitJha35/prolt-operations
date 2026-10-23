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
}

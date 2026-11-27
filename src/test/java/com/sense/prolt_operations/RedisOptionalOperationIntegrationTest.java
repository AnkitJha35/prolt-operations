package com.sense.prolt_operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.data.redis.RedisHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.MockMvc;

import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.exception.AppBaseException;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.service.AuthService;
import com.sense.prolt_operations.service.OtpService;
import com.sense.prolt_operations.testutil.TestUserHelper;

@AutoConfigureMockMvc
class RedisOptionalOperationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private OtpService otpService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("REDIS-OPT-01: OTP verification via superadmin bypass succeeds without any Redis interaction")
    void testSuperadminOtpBypassWithoutRedis() {
        TestUserHelper.TestUser superAdmin = testUserHelper.createOrUpdateSuperAdmin("super.admin@probus.io");
        try {
            otpService.setSuperadminBypassEnabled(true);
            var verifyDto = new com.sense.prolt_operations.dto.auth.VerifyOtpDTO("super.admin@probus.io", "749261");
            var authResponse = authService.verifyOtp(verifyDto);
            assertThat(authResponse).isNotNull();
            assertThat(authResponse.success()).isTrue();
            assertThat(authResponse.token()).isNotBlank();
            assertThat(authResponse.user().email()).isEqualTo("super.admin@probus.io");
        } finally {
            deleteTestUser(superAdmin);
        }
    }

    @Test
    @DisplayName("REDIS-OPT-02: OTP generation and verification work without Redis via in-memory fallback")
    void testOtpGenerationAndVerificationWithoutRedis() {
        TestUserHelper.TestUser testUser = createTestUser(Role.CLIENT);
        String email = testUser.getUser().getEmail();
        try {
            otpService.setAllowMemoryFallback(true);
            otpService.setSuperadminBypassEnabled(false);

            otpService.generateAndSendOtp(email);

            Map<String, OtpService.StoredOtp> inMemoryStore = otpService.getInMemoryStore();
            assertThat(inMemoryStore).containsKey(email);

            OtpService.StoredOtp storedOtp = inMemoryStore.get(email);
            assertThat(storedOtp.otp()).matches("\\d{6}");
            assertThat(storedOtp.expiresAt()).isAfter(Instant.now());
            assertThat(storedOtp.expiresAt()).isBeforeOrEqualTo(Instant.now().plus(5, ChronoUnit.MINUTES).plusSeconds(2));

            // Verify wrong OTP fails
            boolean wrongResult = otpService.verifyOtp(email, "999999");
            assertThat(wrongResult).isFalse();
            assertThat(inMemoryStore).containsKey(email);

            // Verify correct OTP succeeds and removes from memory
            boolean correctResult = otpService.verifyOtp(email, storedOtp.otp());
            assertThat(correctResult).isTrue();
            assertThat(inMemoryStore).doesNotContainKey(email);
        } finally {
            deleteTestUser(testUser);
        }
    }

    @Test
    @DisplayName("REDIS-OPT-03: OTP in-memory fallback respects 5-minute expiration")
    void testOtpInMemoryFallbackExpiration() {
        TestUserHelper.TestUser testUser = createTestUser(Role.CLIENT);
        String email = testUser.getUser().getEmail();
        try {
            otpService.setAllowMemoryFallback(true);
            otpService.setSuperadminBypassEnabled(false);

            otpService.generateAndSendOtp(email);
            Map<String, OtpService.StoredOtp> inMemoryStore = otpService.getInMemoryStore();
            assertThat(inMemoryStore).containsKey(email);

            // Simulate expired OTP (expired 1 second ago)
            String otpValue = inMemoryStore.get(email).otp();
            inMemoryStore.put(email, new OtpService.StoredOtp(otpValue, Instant.now().minusSeconds(1)));

            boolean result = otpService.verifyOtp(email, otpValue);
            assertThat(result).isFalse();
            assertThat(inMemoryStore).doesNotContainKey(email);
        } finally {
            deleteTestUser(testUser);
        }
    }

    @Test
    @DisplayName("REDIS-OPT-04: OTP works with Redis when configured and reachable")
    @SuppressWarnings("unchecked")
    void testOtpWithReachableRedis() {
        TestUserHelper.TestUser testUser = createTestUser(Role.CLIENT);
        String email = testUser.getUser().getEmail();
        StringRedisTemplate mockRedis = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> mockOps = Mockito.mock(ValueOperations.class);
        Mockito.when(mockRedis.opsForValue()).thenReturn(mockOps);

        OtpService customOtpService = new OtpService(userRepository, mockRedis, null);
        customOtpService.setAllowMemoryFallback(true);
        customOtpService.setSuperadminBypassEnabled(false);

        try {
            customOtpService.generateAndSendOtp(email);

            // Verify redis set was called with 5 minutes TTL
            Mockito.verify(mockOps).set(Mockito.eq("otp:" + email), Mockito.matches("\\d{6}"), Mockito.eq(5L), Mockito.eq(TimeUnit.MINUTES));

            // Verify verification with redis
            String generatedOtp = customOtpService.getInMemoryStore().get(email).otp();
            Mockito.when(mockOps.get("otp:" + email)).thenReturn(generatedOtp);

            boolean verified = customOtpService.verifyOtp(email, generatedOtp);
            assertThat(verified).isTrue();
            Mockito.verify(mockRedis).delete("otp:" + email);
        } finally {
            deleteTestUser(testUser);
        }
    }

    @Test
    @DisplayName("REDIS-OPT-05: In production profile (allowMemoryFallback=false), Redis failure fails closed")
    void testProductionProfileRedisFailureFailsClosed() {
        TestUserHelper.TestUser testUser = createTestUser(Role.CLIENT);
        String email = testUser.getUser().getEmail();
        try {
            otpService.setAllowMemoryFallback(false);
            otpService.setSuperadminBypassEnabled(false);

            // Generation must throw AppBaseException with 500 when Redis is unavailable
            assertThatThrownBy(() -> otpService.generateAndSendOtp(email))
                .isInstanceOf(AppBaseException.class)
                .hasMessage("Redis connection error");

            // Verification must also throw AppBaseException with 500 when Redis is unavailable
            assertThatThrownBy(() -> otpService.verifyOtp(email, "123456"))
                .isInstanceOf(AppBaseException.class)
                .hasMessage("Redis connection error");
        } finally {
            otpService.setAllowMemoryFallback(true);
            deleteTestUser(testUser);
        }
    }

    @Test
    @DisplayName("REDIS-OPT-06: Actuator /health reports UP when Redis is disabled/unavailable")
    void testActuatorHealthWithoutRedis() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("REDIS-OPT-07: Production Redis health indicator reports DOWN when connection fails")
    void testProductionRedisHealthIndicatorReportsDownOnFailure() {
        RedisConnectionFactory mockConnectionFactory = Mockito.mock(RedisConnectionFactory.class);
        Mockito.when(mockConnectionFactory.getConnection()).thenThrow(new RuntimeException("Connection refused"));

        RedisHealthIndicator healthIndicator = new RedisHealthIndicator(mockConnectionFactory);
        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}

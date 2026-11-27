package com.sense.prolt_operations.security.ratelimit;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.BaseIntegrationTest;
import com.sense.prolt_operations.security.AuthRateLimiterFilter;
import com.sense.prolt_operations.security.ClientIpResolver;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DistributedOtpRateLimitingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRateLimiterFilter authRateLimiterFilter;

    @Autowired
    private DistributedOtpRateLimiter distributedLimiter;

    @Autowired
    private RateLimitProperties rateLimitProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        distributedLimiter.reset();
        authRateLimiterFilter.getLimiter().reset();
    }

    // =========================================================================
    // 1. IP-Based Distributed Rate Limiting Tests
    // =========================================================================

    @Test
    @DisplayName("RL-01: Requests below IP threshold succeed with RateLimit headers")
    void testRequestsBelowIpThresholdSucceed() throws Exception {
        String testIp = "198.51.100.1";

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/auth/send-otp")
                    .with(req -> {
                        req.setRemoteAddr(testIp);
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"user" + i + "@probus.io\"}"))
                    .andExpect(header().string("RateLimit-Limit", "10"))
                    .andExpect(header().string("RateLimit-Remaining", String.valueOf(10 - i)))
                    .andExpect(header().exists("RateLimit-Reset"));
        }
    }

    @Test
    @DisplayName("RL-02: IP threshold exceeded returns HTTP 429 and Retry-After header")
    void testIpThresholdExceededReturns429() throws Exception {
        String testIp = "198.51.100.2";

        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/auth/send-otp")
                    .with(req -> {
                        req.setRemoteAddr(testIp);
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"different" + i + "@probus.io\"}"));
        }

        // 11th request from same IP must be rejected
        mockMvc.perform(post("/api/auth/send-otp")
                .with(req -> {
                    req.setRemoteAddr(testIp);
                    return req;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"different11@probus.io\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("RateLimit-Limit", "10"))
                .andExpect(header().string("RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Too many login attempts, please try again after 15 minutes")));
    }

    // =========================================================================
    // 2. Account-Based Rate Limiting Across Multiple IPs
    // =========================================================================

    @Test
    @DisplayName("RL-03: Account threshold enforced when different IPs target the same account")
    void testAccountThresholdEnforcedAcrossDifferentIps() throws Exception {
        String targetEmail = "victim_account@probus.io";

        // Save original and set smaller account limit for test
        int originalAccountLimit = rateLimitProperties.getOtpSendAccountLimit();
        rateLimitProperties.setOtpSendAccountLimit(3);

        try {
            // 3 requests from 3 different IPs targeting same email
            for (int i = 1; i <= 3; i++) {
                String rotatingIp = "198.51.100." + (10 + i);
                mockMvc.perform(post("/api/auth/send-otp")
                        .with(req -> {
                            req.setRemoteAddr(rotatingIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + targetEmail + "\"}"));
            }

            // 4th request from a completely fresh 4th IP targeting the SAME account must be rejected with 429
            String freshIp = "198.51.100.99";
            mockMvc.perform(post("/api/auth/send-otp")
                    .with(req -> {
                        req.setRemoteAddr(freshIp);
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + targetEmail + "\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("Too many login attempts, please try again after 15 minutes")));
        } finally {
            rateLimitProperties.setOtpSendAccountLimit(originalAccountLimit);
        }
    }

    @Test
    @DisplayName("RL-04: OTP verification account threshold enforced across multiple IPs")
    void testOtpVerifyAccountThresholdEnforced() throws Exception {
        String targetEmail = "verify_target@probus.io";

        int originalVerifyLimit = rateLimitProperties.getOtpVerifyAccountLimit();
        rateLimitProperties.setOtpVerifyAccountLimit(3);

        try {
            // 3 verify attempts from 3 different IPs
            for (int i = 1; i <= 3; i++) {
                String rotatingIp = "198.51.100." + (30 + i);
                mockMvc.perform(post("/api/auth/verify-otp")
                        .with(req -> {
                            req.setRemoteAddr(rotatingIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + targetEmail + "\",\"otp\":\"12345" + i + "\"}"));
            }

            // 4th verify attempt from 4th IP must be blocked by account limit
            String freshIp = "198.51.100.88";
            mockMvc.perform(post("/api/auth/verify-otp")
                    .with(req -> {
                        req.setRemoteAddr(freshIp);
                        return req;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + targetEmail + "\",\"otp\":\"999999\"}"))
                    .andExpect(status().isTooManyRequests());
        } finally {
            rateLimitProperties.setOtpVerifyAccountLimit(originalVerifyLimit);
        }
    }

    // =========================================================================
    // 3. Privacy-Preserving Account Identifier Tests
    // =========================================================================

    @Test
    @DisplayName("RL-05: Account email is transformed via HMAC-SHA256 into privacy-preserving hash")
    void testAccountPrivacyHashing() {
        String email = "Sensitive.User@example.com";
        String hash = distributedLimiter.hashAccount(email);

        // 1. Raw email must not appear in the hash
        assertThat(hash).doesNotContain("Sensitive");
        assertThat(hash).doesNotContain("@");
        assertThat(hash).doesNotContain("example.com");

        // 2. Hash must be 64-character hex string (SHA-256 output length)
        assertThat(hash).matches("^[a-f0-9]{64}$");

        // 3. Normalized matching (case-insensitive & trimmed)
        String hashLower = distributedLimiter.hashAccount("sensitive.user@example.com");
        String hashTrimmed = distributedLimiter.hashAccount("  Sensitive.User@example.com  ");
        assertThat(hash).isEqualTo(hashLower);
        assertThat(hash).isEqualTo(hashTrimmed);
    }

    // =========================================================================
    // 4. Reverse Proxy & Header Spoofing Protection Tests
    // =========================================================================

    @Test
    @DisplayName("RL-06: Direct connection from untrusted IP ignores spoofed X-Forwarded-For")
    void testDirectUntrustedConnectionIgnoresForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50"); // Public/untrusted direct IP
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");

        String resolvedIp = ClientIpResolver.resolveClientIp(request);

        // Crucial security check: X-Forwarded-For MUST BE IGNORED because remoteAddr is untrusted
        assertThat(resolvedIp).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("RL-07: Trusted proxy connection resolves client IP from X-Forwarded-For")
    void testTrustedProxyResolvesClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // Trusted loopback reverse proxy
        request.addHeader("X-Forwarded-For", "10.0.0.1, 198.51.100.99");

        String resolvedIp = ClientIpResolver.resolveClientIp(request);

        // Immediate reverse proxy appended the client IP at the rightmost position
        assertThat(resolvedIp).isEqualTo("198.51.100.99");
    }

    // =========================================================================
    // 5. Fail-Closed in Production vs Fallback in Local/Dev
    // =========================================================================

    @Test
    @DisplayName("RL-08: Production environment fails closed with HTTP 503 on Redis failure")
    void testProductionRedisFailureFailsClosedWith503() throws Exception {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        RateLimitProperties props = new RateLimitProperties();
        props.setAccountHmacKey("test-production-rate-limit-hmac-key");
        DistributedOtpRateLimiter prodLimiter = new DistributedOtpRateLimiter(props, prodEnv, null);

        AuthRateLimiterFilter filter = new AuthRateLimiterFilter(
                java.time.Clock.systemUTC(),
                prodLimiter,
                objectMapper
        );

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/send-otp");
        request.setRemoteAddr("127.0.0.1");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"email\":\"user@example.com\"}".getBytes());

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("Authentication service temporarily unavailable");
    }

    @Test
    @DisplayName("RL-09: Local/Dev environment falls back to in-memory limiter without Redis")
    void testLocalDevFallbackToInMemory() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("local");

        RateLimitProperties props = new RateLimitProperties();
        DistributedOtpRateLimiter localLimiter = new DistributedOtpRateLimiter(props, devEnv, null);

        // Must not throw; must successfully use in-memory store
        RateLimitResult result = localLimiter.checkLimits("send", "127.0.0.1", "dev_user@probus.io");
        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isGreaterThanOrEqualTo(0);
    }

    // =========================================================================
    // 6. Multi-Instance Distributed Redis Mock Test
    // =========================================================================

    @Test
    @DisplayName("RL-10: Multi-instance rate limiting via atomic Lua script")
    @SuppressWarnings("unchecked")
    void testMultiInstanceRateLimitingViaLuaScript() {
        StringRedisTemplate mockRedis = Mockito.mock(StringRedisTemplate.class);

        // Simulate atomic Lua script returning [count=1, ttl=900]
        when(mockRedis.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(List.of(1L, 900L));

        RedisRateLimiter redisLimiter = new RedisRateLimiter(mockRedis);
        RateLimitResult result = redisLimiter.checkAndIncrement("rl:auth:ip:10.0.0.1", 10, 900);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(9);
        assertThat(result.resetSeconds()).isEqualTo(900);

        // Simulate 11th request exceeding limit [count=11, ttl=850]
        when(mockRedis.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(List.of(11L, 850L));

        RateLimitResult rejectedResult = redisLimiter.checkAndIncrement("rl:auth:ip:10.0.0.1", 10, 900);
        assertThat(rejectedResult.allowed()).isFalse();
        assertThat(rejectedResult.remaining()).isEqualTo(0);
        assertThat(rejectedResult.retryAfterSeconds()).isEqualTo(850);
    }
}

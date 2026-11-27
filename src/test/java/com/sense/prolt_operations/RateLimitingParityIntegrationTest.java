package com.sense.prolt_operations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.sense.prolt_operations.security.AuthRateLimiterFilter;
import com.sense.prolt_operations.security.JwtService;
import com.sense.prolt_operations.security.UploadRateLimiterFilter;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimitingParityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRateLimiterFilter authRateLimiterFilter;

    @Autowired
    private UploadRateLimiterFilter uploadRateLimiterFilter;

    @Autowired
    private JwtService jwtService;

    private MutableTestClock testClock;

    public static class MutableTestClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        public MutableTestClock(Instant start) {
            this(start, ZoneOffset.UTC);
        }

        public MutableTestClock(Instant start, ZoneId zone) {
            this.instant = start;
            this.zone = zone;
        }

        public void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableTestClock(this.instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @BeforeEach
    void setUp() {
        testClock = new MutableTestClock(Instant.parse("2026-10-23T12:00:00Z"));
        authRateLimiterFilter.setClock(testClock);
        authRateLimiterFilter.getLimiter().reset();
        uploadRateLimiterFilter.setClock(testClock);
        uploadRateLimiterFilter.getLimiter().reset();
    }

    @Test
    @DisplayName("Auth limiter: 10 requests succeed, 11th returns 429 with standard headers and message")
    void tenRequestsSucceed_eleventhReturns429() throws Exception {
        String testIp = "203.0.113.10";

        for (int i = 1; i <= 10; i++) {
            int expectedRemaining = 10 - i;
            mockMvc.perform(post("/api/auth/send-otp")
                            .with(request -> {
                                request.setRemoteAddr(testIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"wrong@probus.io\"}"))
                    .andExpect(header().string("RateLimit-Limit", "10"))
                    .andExpect(header().string("RateLimit-Remaining", String.valueOf(expectedRemaining)))
                    .andExpect(header().exists("RateLimit-Reset"));
        }

        // 11th request must return 429
        mockMvc.perform(post("/api/auth/send-otp")
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrong@probus.io\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("RateLimit-Limit", "10"))
                .andExpect(header().string("RateLimit-Remaining", "0"))
                .andExpect(header().string("RateLimit-Reset", "900"))
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Too many login attempts, please try again after 15 minutes")));
    }

    @Test
    @DisplayName("Auth limiter: Advancing clock by 15 minutes resets fixed-window counter")
    void advancingClockBy15MinutesResetsCounter() throws Exception {
        String testIp = "203.0.113.20";

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/send-otp")
                    .with(request -> {
                        request.setRemoteAddr(testIp);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"wrong@probus.io\"}"));
        }

        // Exceeded
        mockMvc.perform(post("/api/auth/send-otp")
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrong@probus.io\"}"))
                .andExpect(status().isTooManyRequests());

        // Advance clock by 15 minutes and 1 second
        testClock.advance(Duration.ofMinutes(15).plusSeconds(1));

        // Window should have reset: request succeeds (not 429) and remaining is 9
        mockMvc.perform(post("/api/auth/send-otp")
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrong@probus.io\"}"))
                .andExpect(header().string("RateLimit-Limit", "10"))
                .andExpect(header().string("RateLimit-Remaining", "9"))
                .andExpect(status().isNotFound()); // User not found, but rate limiter allowed it through!
    }

    @Test
    @DisplayName("Auth limiter: Shared counter across POST /send-otp and /verify-otp")
    void sharedCounterAcrossAllAuthEndpoints() throws Exception {
        String testIp = "203.0.113.30";

        // 5 requests to /send-otp
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/send-otp")
                    .with(request -> {
                        request.setRemoteAddr(testIp);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"u@p.io\"}"));
        }

        // 5 requests to /verify-otp
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/verify-otp")
                    .with(request -> {
                        request.setRemoteAddr(testIp);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"u@p.io\",\"otp\":\"123456\"}"));
        }

        // Total 10 requests consumed across both routes. 11th request to /send-otp must return 429
        mockMvc.perform(post("/api/auth/send-otp")
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"u@p.io\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", is("Too many login attempts, please try again after 15 minutes")));
    }

    @Test
    @DisplayName("Auth limiter: Two distinct IPs have independent counters")
    void twoDistinctIpsHaveIndependentCounters() throws Exception {
        String ip1 = "203.0.113.41";
        String ip2 = "203.0.113.42";

        // Exhaust IP 1
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/auth/send-otp")
                    .with(request -> {
                        request.setRemoteAddr(ip1);
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"u@p.io\"}"));
        }

        mockMvc.perform(post("/api/auth/send-otp")
                        .with(request -> {
                            request.setRemoteAddr(ip1);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"u@p.io\"}"))
                .andExpect(status().isTooManyRequests());

        // IP 2 is completely unaffected
        mockMvc.perform(post("/api/auth/send-otp")
                        .with(request -> {
                            request.setRemoteAddr(ip2);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"u@p.io\"}"))
                .andExpect(header().string("RateLimit-Remaining", "9"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Auth limiter: Spoofed first X-Forwarded-For does not trick limiter (trust proxy 1 parity)")
    void spoofedFirstXForwardedForDoesNotTrickLimiter() throws Exception {
        String realClientIp = "198.51.100.77";

        // Attacker rotates spoofed IP in the first position of X-Forwarded-For
        for (int i = 1; i <= 10; i++) {
            String spoofedHeader = "10.0.0." + i + ", " + realClientIp;
            mockMvc.perform(post("/api/auth/send-otp")
                    .header("X-Forwarded-For", spoofedHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"u@p.io\"}"));
        }

        // 11th request with yet another spoofed IP in first position
        String spoofedHeader11 = "10.0.0.99, " + realClientIp;
        mockMvc.perform(post("/api/auth/send-otp")
                        .header("X-Forwarded-For", spoofedHeader11)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"u@p.io\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", is("Too many login attempts, please try again after 15 minutes")));
    }

    @Test
    @DisplayName("Upload limiter: 30 requests succeed, 31st returns 429; GET /health is not limited")
    void uploadLimiterParity() throws Exception {
        String testIp = "203.0.113.88";
        var uploader = createTestUser(com.sense.prolt_operations.entity.Role.ADMIN, "uploader_rl", "uploader_rl@probus.io");
        try {
            String token = uploader.getBearerToken();

        for (int i = 1; i <= 30; i++) {
            mockMvc.perform(post("/api/uploads")
                            .header("Authorization", token)
                            .with(request -> {
                                request.setRemoteAddr(testIp);
                                return request;
                            }))
                    .andExpect(header().string("RateLimit-Limit", "30"))
                    .andExpect(header().string("RateLimit-Remaining", String.valueOf(30 - i)));
        }

        // 31st request returns 429
        mockMvc.perform(post("/api/uploads")
                        .header("Authorization", token)
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("RateLimit-Limit", "30"))
                .andExpect(header().string("RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Upload limit exceeded. Please try again after an hour.")));

        // GET /api/uploads/health is protected by JWT but NOT rate limited
        mockMvc.perform(get("/api/uploads/health")
                        .header("Authorization", token)
                        .with(request -> {
                            request.setRemoteAddr(testIp);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("RateLimit-Limit"));
        } finally {
            deleteTestUser(uploader);
        }
    }
}

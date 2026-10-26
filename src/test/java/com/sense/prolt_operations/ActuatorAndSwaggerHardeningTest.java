package com.sense.prolt_operations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.security.JwtService;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorAndSwaggerHardeningTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Security headers: exact parity with Node Helmet configuration")
    void securityHeaders_matchNodeHelmetParity() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline' https:; img-src 'self' data: validator.swagger.io https:"))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-XSS-Protection", "0"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(header().string("Origin-Agent-Cluster", "?1"))
                .andExpect(header().string("X-DNS-Prefetch-Control", "off"))
                .andExpect(header().string("X-Download-Options", "noopen"))
                .andExpect(header().string("X-Permitted-Cross-Domain-Policies", "none"));
    }

    @Test
    @DisplayName("Actuator: sensitive endpoints (env, heapdump, loggers, threaddump) are not exposed")
    void actuatorEndpoints_sensitiveAreDisabled() throws Exception {
        // Health is exposed and public
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));

        // Info is exposed and public
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());

        // Prometheus requires authentication
        com.sense.prolt_operations.testutil.TestUserHelper.TestUser testUser = createTestUser(Role.ADMIN);
        try {
            String token = testUser.getBearerToken();
            mockMvc.perform(get("/actuator/prometheus").header("Authorization", token))
                    .andExpect(status().isOk());

            // Without auth -> rejected with 401 Unauthorized
            mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());

            // Even when authenticated as ADMIN, sensitive endpoints return 404 (disabled and excluded from web exposure)
            mockMvc.perform(get("/actuator/env").header("Authorization", token)).andExpect(status().isNotFound());
            mockMvc.perform(get("/actuator/heapdump").header("Authorization", token)).andExpect(status().isNotFound());
            mockMvc.perform(get("/actuator/loggers").header("Authorization", token)).andExpect(status().isNotFound());
            mockMvc.perform(get("/actuator/threaddump").header("Authorization", token)).andExpect(status().isNotFound());
        } finally {
            deleteTestUser(testUser);
        }
    }
}

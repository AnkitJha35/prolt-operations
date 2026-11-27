package com.sense.prolt_operations.security.cors;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import com.sense.prolt_operations.BaseIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class EnvironmentCorsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CorsConfigValidator corsValidator;

    // =========================================================================
    // 1. Non-Production (Local / Dev) Unrestricted CORS Tests
    // =========================================================================

    @Test
    @DisplayName("CORS-01: Non-production environment allows arbitrary origin with credentials")
    void testNonProductionAllowsArbitraryOrigin() throws Exception {
        mockMvc.perform(options("/api/datasets/summary")
                .header("Origin", "https://untrusted-attacker.io")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://untrusted-attacker.io"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("CORS-02: Non-production environment actual request includes CORS headers")
    void testNonProductionActualRequestIncludesCorsHeaders() throws Exception {
        mockMvc.perform(get("/health")
                .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    // =========================================================================
    // 2. Production Profile Validation & Startup Guard Tests
    // =========================================================================

    @Test
    @DisplayName("CORS-03: Production with valid explicit origins passes validation")
    void testProductionWithValidExplicitOriginsPasses() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        CorsConfigValidator validator = new CorsConfigValidator(env);
        org.springframework.test.util.ReflectionTestUtils.setField(
                validator,
                "corsOriginsRaw",
                "https://dashboard.example.com, https://admin.example.com:8443"
        );

        validator.validateConfiguration();

        assertThat(validator.isProduction()).isTrue();
        assertThat(validator.getParsedAllowedOrigins()).containsExactly(
                "https://dashboard.example.com",
                "https://admin.example.com:8443"
        );
    }

    @Test
    @DisplayName("CORS-04: Production startup fails if app.cors-origins is missing or empty")
    void testProductionStartupFailsOnMissingAllowlist() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        CorsConfigValidator validator = new CorsConfigValidator(env);
        org.springframework.test.util.ReflectionTestUtils.setField(validator, "corsOriginsRaw", "   ");

        assertThatThrownBy(validator::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production CORS misconfiguration")
                .hasMessageContaining("must be configured with explicit trusted origins");
    }

    @Test
    @DisplayName("CORS-05: Production startup fails if allowlist contains wildcard ('*')")
    void testProductionStartupFailsOnWildcardAllowlist() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("production");

        CorsConfigValidator validator = new CorsConfigValidator(env);
        org.springframework.test.util.ReflectionTestUtils.setField(
                validator,
                "corsOriginsRaw",
                "https://dashboard.example.com, *"
        );

        assertThatThrownBy(validator::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wildcard origins ('*') are strictly prohibited in production");
    }

    @Test
    @DisplayName("CORS-06: Production startup fails on malformed origin (missing scheme)")
    void testProductionStartupFailsOnMissingScheme() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        CorsConfigValidator validator = new CorsConfigValidator(env);
        org.springframework.test.util.ReflectionTestUtils.setField(
                validator,
                "corsOriginsRaw",
                "dashboard.example.com"
        );

        assertThatThrownBy(validator::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Scheme must be http or https");
    }

    @Test
    @DisplayName("CORS-07: Production startup fails on malformed origin with path")
    void testProductionStartupFailsOnOriginWithPath() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        CorsConfigValidator validator = new CorsConfigValidator(env);
        org.springframework.test.util.ReflectionTestUtils.setField(
                validator,
                "corsOriginsRaw",
                "https://dashboard.example.com/api"
        );

        assertThatThrownBy(validator::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Path not permitted");
    }

    // =========================================================================
    // 3. Multi-Profile Precedence Tests
    // =========================================================================

    @Test
    @DisplayName("CORS-08: When both 'dev' and 'prod' profiles are active, Production policy wins")
    void testProductionPrecedenceOverDev() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "prod");

        assertThat(CorsConfigValidator.determineIfProduction(env)).isTrue();
    }

    @Test
    @DisplayName("CORS-09: When both 'local' and 'production' profiles are active, Production policy wins")
    void testProductionPrecedenceOverLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local", "production");

        assertThat(CorsConfigValidator.determineIfProduction(env)).isTrue();
    }

    @Test
    @DisplayName("CORS-10: When only 'local' or 'dev' profile is active, non-production policy applies")
    void testNonProductionProfilesOnly() {
        MockEnvironment env1 = new MockEnvironment();
        env1.setActiveProfiles("local");
        assertThat(CorsConfigValidator.determineIfProduction(env1)).isFalse();

        MockEnvironment env2 = new MockEnvironment();
        env2.setActiveProfiles("dev");
        assertThat(CorsConfigValidator.determineIfProduction(env2)).isFalse();

        MockEnvironment env3 = new MockEnvironment();
        env3.setActiveProfiles("development");
        assertThat(CorsConfigValidator.determineIfProduction(env3)).isFalse();
    }
}

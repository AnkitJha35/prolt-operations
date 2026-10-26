package com.sense.prolt_operations;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
class LandingPriceParityIntegrationTest extends BaseIntegrationTest {

    private static final WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void overrideIexProperties(DynamicPropertyRegistry registry) {
        registry.add("app.iex-calculator-api-url", () -> wireMockServer.baseUrl());
        registry.add("app.iex-calculator.connect-timeout", () -> 500);
        registry.add("app.iex-calculator.read-timeout", () -> 500);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String token;
    private com.sense.prolt_operations.testutil.TestUserHelper.TestUser testUser;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        testUser = createTestUser(Role.ADMIN);
        token = testUser.getBearerToken();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        deleteTestUser(testUser);
    }

    @Test
    @DisplayName("Upstream 200: returns parsed JSON response from IEX API")
    void calculate_upstream200_returnsParsedResult() throws Exception {
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/calculator"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("User-Agent", WireMock.equalTo("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"landedCost\":6.85,\"success\":true,\"state\":\"UP\"}")));

        mockMvc.perform(get("/api/landing-price-calc/calculate?state=UP&voltage=33kV")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landedCost", is(6.85)))
                .andExpect(jsonPath("$.state", is("UP")));
    }

    @Test
    @DisplayName("Upstream 500: maps to HTTP 500 { message: 'IEX API Error: 500 - ...' } without success field")
    void calculate_upstream500_returnsMappedErrorMessage() throws Exception {
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/calculator"))
                .willReturn(WireMock.aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\"}")));

        mockMvc.perform(get("/api/landing-price-calc/calculate?state=UP&voltage=33kV")
                        .header("Authorization", token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", is("IEX API Error: 500 - {\"error\":\"Internal Server Error\"}")))
                .andExpect(jsonPath("$.success").doesNotExist());
    }

    @Test
    @DisplayName("Upstream timeout: maps to HTTP 500 { message: 'Failed to calculate landing price from IEX API' } without success field")
    void calculate_upstreamTimeout_returnsTimeoutErrorMessage() throws Exception {
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/api/v1/calculator"))
                .willReturn(WireMock.aResponse()
                        .withFixedDelay(1500) // exceeds 500ms read timeout
                        .withStatus(200)
                        .withBody("{\"landedCost\":6.85}")));

        mockMvc.perform(get("/api/landing-price-calc/calculate?state=UP&voltage=33kV")
                        .header("Authorization", token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", is("Failed to calculate landing price from IEX API")))
                .andExpect(jsonPath("$.success").doesNotExist());
    }
}

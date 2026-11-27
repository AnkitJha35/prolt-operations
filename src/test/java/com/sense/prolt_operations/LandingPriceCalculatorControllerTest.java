package com.sense.prolt_operations;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.dto.calculator.LandingPriceCalculationRequest;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.testutil.TestUserHelper;

@SpringBootTest
@AutoConfigureMockMvc
class LandingPriceCalculatorControllerTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private TestUserHelper.TestUser testUser;

    @BeforeEach
    void setUp() {
        testUser = createTestUser(Role.ADMIN);
        token = testUser.getBearerToken();
    }

    @AfterEach
    void tearDown() {
        if (testUser != null) {
            deleteTestUser(testUser);
        }
    }

    @Test
    @DisplayName("Unauthenticated request returns 401 Unauthorized")
    void testUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/landing-price-calculator/calculate?state=UP&consumerCategory=HV-2&voltageLevel=33kV"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/landing-price-calculator/calculate: returns 200 with calculated response")
    void testGetCalculation() throws Exception {
        mockMvc.perform(get("/api/landing-price-calculator/calculate")
                .param("state", "UP")
                .param("consumerCategory", "HV-2")
                .param("voltageLevel", "33kV")
                .param("mcpRsPerKwh", "4.50")
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("UP")))
                .andExpect(jsonPath("$.consumerCategory", is("HV-2")))
                .andExpect(jsonPath("$.voltageLevel", is("33kV")))
                .andExpect(jsonPath("$.losses").exists())
                .andExpect(jsonPath("$.regulatoryCharges").exists())
                .andExpect(jsonPath("$.marketLandings.DAM").exists())
                .andExpect(jsonPath("$.discomTariff").exists())
                .andExpect(jsonPath("$.comparison").exists());
    }

    @Test
    @DisplayName("POST /api/landing-price-calculator/calculate: returns 200 with full multi-market response")
    void testPostCalculation() throws Exception {
        LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                "UP", "HV-2", "33 kV", null, null, 1, null, 4500.0, 4350.0, 4200.0,
                1500.0, 0.99, false, 0.02, LandingPriceCalculationRequest.CalculationMode.OPEN_ACCESS_LANDED);

        mockMvc.perform(post("/api/landing-price-calculator/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("UP")))
                .andExpect(jsonPath("$.marketLandings.DAM").exists())
                .andExpect(jsonPath("$.marketLandings.RTM").exists())
                .andExpect(jsonPath("$.marketLandings.GDAM").exists())
                .andExpect(jsonPath("$.bestMarketSource").exists())
                .andExpect(jsonPath("$.bestMarketLandedPrice").isNumber());
    }

    @Test
    @DisplayName("Validation: POST with missing required state returns 400 Bad Request")
    void testMissingStateReturns400() throws Exception {
        LandingPriceCalculationRequest req = new LandingPriceCalculationRequest(
                "", "HV-2", "33 kV", null, null, 1, 4.50, null, null, null,
                null, null, null, null, null);

        mockMvc.perform(post("/api/landing-price-calculator/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }
}

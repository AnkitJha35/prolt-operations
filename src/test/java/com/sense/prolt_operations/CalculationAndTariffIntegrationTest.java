package com.sense.prolt_operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.JwtService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CalculationAndTariffIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.sense.prolt_operations.service.LandingPriceService landingPriceService;

    private String adminToken;
    private com.sense.prolt_operations.testutil.TestUserHelper.TestUser testUser;

    @BeforeEach
    void setUp() {
        testUser = createTestUser(Role.ADMIN);
        adminToken = testUser.getToken();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        deleteTestUser(testUser);
    }

    @Test
    @DisplayName("CALC-01: Calculate Landed Cost Savings (Scenario Acme Steel Mills)")
    void testCalc01_CalculateLandedCostSavings() throws Exception {
        String requestBody = """
            {
                "clientName": "Acme Steel Mills",
                "state": "Uttar Pradesh",
                "tariffCategory": "HV-2",
                "contractDemandKva": 5000,
                "connectedVoltage": "33kV",
                "consumptionKwh": 1250000,
                "monthYear": "Jun 2026"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/savings-calculator/calculate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.discomBillAmount").isNumber())
            .andExpect(jsonPath("$.data.iexTotalCost").isNumber())
            .andExpect(jsonPath("$.data.totalSavings").isNumber())
            .andExpect(jsonPath("$.data.percentageSavings").isNumber())
            .andExpect(jsonPath("$.data.breakdown.energyCost").isNumber())
            .andExpect(jsonPath("$.data.breakdown.ctuLossAndCharges").isNumber())
            .andExpect(jsonPath("$.data.breakdown.stLossAndCharges").isNumber())
            .andExpect(jsonPath("$.data.breakdown.crossSubsidySurcharge").isNumber())
            .andExpect(jsonPath("$.data.breakdown.additionalSurcharge").isNumber())
            .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        double discom = root.path("data").path("discomBillAmount").asDouble();
        double iex = root.path("data").path("iexTotalCost").asDouble();
        double savings = root.path("data").path("totalSavings").asDouble();
        assertThat(discom).isGreaterThan(0.0);
        assertThat(iex).isGreaterThan(0.0);
        assertThat(discom - iex).isCloseTo(savings, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("CALC-01a: Missing state field returns 400 Bad Request with parity message")
    void testCalc01a_MissingStateField() throws Exception {
        String requestBody = """
            {
                "clientName": "Acme Steel Mills",
                "tariffCategory": "HV-2",
                "contractDemandKva": 5000,
                "connectedVoltage": "33kV",
                "consumptionKwh": 1250000,
                "monthYear": "Jun 2026"
            }
            """;

        mockMvc.perform(post("/api/savings-calculator/calculate")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("State is required to calculate savings. Please edit this entry to select a state."));
    }

    @Test
    @DisplayName("Savings Calculator CRUD, History Versioning, Overview, and Calculations")
    void testSavingsCalculatorCrudAndCalculations() throws Exception {
        String createPayload = """
            {
                "clientName": "Bharat Heavy Metals Ltd",
                "industryName": "Foundry",
                "address": "Plot 12, Industrial Area, Ghaziabad",
                "sanctionedLoadKw": 1500,
                "stateCode": "UP",
                "discom": "NPCL",
                "consumerCategory": "HV-2",
                "voltageLevel": "33 kV",
                "proltMargin": 12.5,
                "traderMargin": 0.02,
                "todConsumptions": {
                    "2026-06": { "T1": 150000, "T2": 250000, "T3": 100000 }
                }
            }
            """;

        // 1. Create entry
        MvcResult createRes = mockMvc.perform(post("/api/savings-calculator")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.clientName").value("Bharat Heavy Metals Ltd"))
            .andReturn();

        JsonNode createdNode = objectMapper.readTree(createRes.getResponse().getContentAsString());
        String entryId = createdNode.path("id").asText();

        // 2. Get All
        mockMvc.perform(get("/api/savings-calculator")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        // 3. Get by ID
        mockMvc.perform(get("/api/savings-calculator/" + entryId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientName").value("Bharat Heavy Metals Ltd"));

        // 4. Update entry -> increments history version
        String updatePayload = """
            {
                "clientName": "Bharat Heavy Metals Private Limited",
                "industryName": "Foundry",
                "address": "Plot 12, Industrial Area, Ghaziabad",
                "sanctionedLoadKw": 1800,
                "stateCode": "UP"
            }
            """;
        mockMvc.perform(put("/api/savings-calculator/" + entryId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clientName").value("Bharat Heavy Metals Private Limited"));

        // 5. Check History
        mockMvc.perform(get("/api/savings-calculator/" + entryId + "/history")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        // 6. Get Client Overview
        mockMvc.perform(get("/api/savings-calculator/" + entryId + "/overview")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSavings").exists());

        // 7. Calculate savings
        mockMvc.perform(post("/api/savings-calculator/" + entryId + "/calculate")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalBaselineCost").exists());

        // 8. Calculate market decision
        mockMvc.perform(post("/api/savings-calculator/" + entryId + "/calculate-market-decision")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.marketDecision.length()").value(96));

        // 9. Demand shift insights
        mockMvc.perform(post("/api/savings-calculator/" + entryId + "/demand-shift-insights")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shiftableCapacityMw").exists());

        // 10. Export Excel
        MvcResult excelRes = mockMvc.perform(get("/api/savings-calculator/" + entryId + "/export-excel")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn();
        assertThat(excelRes.getResponse().getContentAsByteArray().length).isGreaterThan(100);

        // 11. Delete entry
        mockMvc.perform(delete("/api/savings-calculator/" + entryId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Entry deleted successfully."));
    }

    @Test
    @DisplayName("Savings Calculator New Endpoints & Resource Defaults")
    void testSavingsCalculatorNewEndpoints() throws Exception {
        // Resource defaults
        mockMvc.perform(get("/api/savings-calculator-new/entries/resource-defaults?stateCode=UP&discom=NPCL")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fppaChargePercent").exists())
            .andExpect(jsonPath("$.demandChargeKwRate").exists());

        // Create New Entry
        String createPayload = """
            {
                "clientName": "Tata Motors Plant",
                "industryName": "Automobile",
                "address": "Sector 5, Greater Noida",
                "stateCode": "UP",
                "discom": "NPCL",
                "consumerCategory": "HV-2",
                "voltageLevel": "33 kV"
            }
            """;
        MvcResult res = mockMvc.perform(post("/api/savings-calculator-new/entries")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
            .andExpect(status().isCreated())
            .andReturn();

        String entryId = objectMapper.readTree(res.getResponse().getContentAsString()).path("id").asText();

        // Calculate
        mockMvc.perform(get("/api/savings-calculator-new/entries/" + entryId + "/calculate")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSavings").exists());

        // Clean up
        mockMvc.perform(delete("/api/savings-calculator-new/entries/" + entryId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Landing Price Calculator Proxy Endpoints")
    void testLandingPriceCalcEndpoints() throws Exception {
        org.mockito.Mockito.when(landingPriceService.getConsumerCategories("UP")).thenReturn(List.of(Map.of("id", "HV-1")));
        org.mockito.Mockito.when(landingPriceService.getVoltages("UP", "HV-2")).thenReturn(List.of(Map.of("id", "33kV")));
        org.mockito.Mockito.when(landingPriceService.getTodMonths("UP", "HV-2", "33kV")).thenReturn(List.of("Jan"));
        org.mockito.Mockito.when(landingPriceService.getTodSlots("UP", "HV-2", "33kV", "Jan")).thenReturn(List.of(Map.of("slot", "Slot 1")));
        org.mockito.Mockito.when(landingPriceService.calculate(org.mockito.ArgumentMatchers.any())).thenReturn(Map.of("landedCost", 6.85));

        mockMvc.perform(get("/api/landing-price-calc/consumer-category?state=UP")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/landing-price-calc/voltage?state=UP&consumerCategory=HV-2")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/landing-price-calc/tod-months?state=UP")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/landing-price-calc/tod-slots?state=UP")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/landing-price-calc/calculate?state=UP&voltage=33kV&consumerCategory=HV-2&iexPrice=4.50")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.landedCost").exists());
    }

    @Test
    @DisplayName("Trader Performance & Daily Endpoints")
    void testTraderPerformanceAndDaily() throws Exception {
        mockMvc.perform(get("/api/trader-performance")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/trader-performance-daily/meters")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/trader-performance-daily/consumers")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        mockMvc.perform(post("/api/trader-performance-daily/analysis")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalActualEnergyKwh").exists());
    }
}

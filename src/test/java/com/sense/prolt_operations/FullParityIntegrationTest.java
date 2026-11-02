package com.sense.prolt_operations;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.JwtService;

@AutoConfigureMockMvc
public class FullParityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    private String adminToken;
    private com.sense.prolt_operations.testutil.TestUserHelper.TestUser testUser;

    @BeforeEach
    void setUp() {
        testUser = createTestUser(Role.SUPER_ADMIN);
        adminToken = testUser.getToken();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        deleteTestUser(testUser);
    }

    @Test
    @DisplayName("PARITY-01: Health & Database Connectivity Probes")
    void testHealthProbes() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("healthy"));

        mockMvc.perform(get("/health/database"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.database").value("connected"));
    }

    @Test
    @DisplayName("PARITY-02: Actuator & Prometheus Observability Endpoints")
    void testActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());

        mockMvc.perform(get("/actuator/prometheus")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PARITY-03: Upload Subsystem Health & Multipart Persistence")
    void testUploadSubsystem() throws Exception {
        mockMvc.perform(get("/api/uploads/health")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.storage").value("healthy"));

        // Build valid 96 rows CSV
        StringBuilder csvBuilder = new StringBuilder();
        csvBuilder.append("interval,purchase bid (mw),sell bid (mw),mcv (mw),final scheduled volume (mw),mcp (rs/mwh)\n");
        for (int i = 1; i <= 96; i++) {
            csvBuilder.append(String.format("%d,150.0,120.0,100.0,95.0,3450.0\n", i));
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "dam_sample.csv", "text/csv", csvBuilder.toString().getBytes());

        // 1. Initial upload creates dataset
        mockMvc.perform(multipart("/api/uploads")
                .file(file)
                .param("market", "DAM")
                .param("deliveryDate", "2026-11-20")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.market").value("DAM"))
                .andExpect(jsonPath("$.rowCount").value(96))
                .andExpect(jsonPath("$.parsed").value(true));

        // 2. Subsequent upload with action=replace succeeds
        mockMvc.perform(multipart("/api/uploads")
                .file(file)
                .param("market", "DAM")
                .param("deliveryDate", "2026-11-20")
                .param("action", "replace")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PARITY-04: Resource Center Master Tables CRUD & Overlap Validation")
    void testResourceCenter() throws Exception {
        // GET /api/resource-center/region-state
        mockMvc.perform(get("/api/resource-center/region-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // POST /api/resource-center/state-charges
        Map<String, Object> chargePayload = Map.of(
            "state", "UP",
            "category", "Heavy Industrial",
            "subCategory", "Continuous",
            "fromDate", "2026-01-01",
            "toDate", "2026-12-31",
            "crossSubsidy", 1.25,
            "distributionWheelingCharges", 0.45,
            "discom", "NPCL (Uttar Pradesh)"
        );

        mockMvc.perform(post("/api/resource-center/state-charges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(chargePayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Record created successfully"));
    }

    @Test
    @DisplayName("PARITY-04A: Resource Center bulk request boundary behavior")
    void testResourceCenterBulkRequestBoundaries() throws Exception {
        // Node accepts an empty array and reports that zero records were uploaded.
        mockMvc.perform(post("/api/resource-center/region-state/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Bulk uploaded 0 records successfully"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        // Blank rows are filtered before the bulk operation, just as in Node.
        mockMvc.perform(post("/api/resource-center/region-state/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"stateName\":\"   \"}, {}]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Bulk uploaded 0 records successfully"));

        mockMvc.perform(post("/api/resource-center/region-state/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payload must be an array of records"));

        mockMvc.perform(post("/api/resource-center/not-a-resource/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported resource type"));
    }

    @Test
    @DisplayName("PARITY-05: Database Demand, Holidays, and City State Registry")
    void testDatabaseSubsystem() throws Exception {
        // GET demand
        mockMvc.perform(get("/api/database/demand")
                .param("date", "2026-07-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allIndiaDemand").exists());

        // GET holidays
        mockMvc.perform(get("/api/database/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // POST city-state
        Map<String, Object> cityState = Map.of(
            "cityName", "Noida",
            "stateName", "Uttar Pradesh",
            "population", 642381,
            "latitude", 28.5355,
            "longitude", 77.3910
        );

        mockMvc.perform(post("/api/database/city-state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cityState)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PARITY-05A: City State Registry input and duplicate behavior")
    void testCityStateInputAndDuplicateBehavior() throws Exception {
        String payload = """
                {"cityName":"  Parity City  ","stateName":"  Parity State  ",
                 "population":"12345","latitude":"12.345","longitude":"67.89"}
                """;

        mockMvc.perform(post("/api/database/city-state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cityName").value("Parity City"))
                .andExpect(jsonPath("$.data.stateName").value("Parity State"))
                .andExpect(jsonPath("$.data.population").value(12345));

        mockMvc.perform(post("/api/database/city-state")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("City and State entry already exists."));

        mockMvc.perform(post("/api/database/city-state")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cityName\":null,\"stateName\":\"State\",\"population\":0,\"latitude\":0,\"longitude\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required fields"));
    }

    @Test
    @DisplayName("PARITY-06: API Logs Telemetry & Stats Calculations")
    void testApiLogs() throws Exception {
        mockMvc.perform(get("/api/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").isNumber());

        mockMvc.perform(get("/api/logs/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.accuracy").isNumber());
    }

    @Test
    @DisplayName("PARITY-07: Market Operations Multi-Market Interval Comparison")
    void testMarketOperations() throws Exception {
        mockMvc.perform(get("/api/market-operations")
                .param("startDate", "2026-07-01")
                .param("endDate", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("PARITY-08: Forecast Dual Mount Routes & UP Market Regional Queries")
    void testForecastAndUpMarket() throws Exception {
        // Forecast dual mount
        mockMvc.perform(get("/api/forecast/dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/forecast/dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // UP Market
        mockMvc.perform(get("/api/up-market/dam")
                .param("startDate", "2026-07-01")
                .param("endDate", "2026-07-02"))
                .andExpect(status().isNotFound()); // empty data returns 404 matching Express contract
    }
}

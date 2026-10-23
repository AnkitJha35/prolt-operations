package com.sense.prolt_operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.config.AppProperties;
import com.sense.prolt_operations.dto.dataset.PersistDatasetDTO;
import com.sense.prolt_operations.entity.Dataset;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.DamRecordRepository;
import com.sense.prolt_operations.repository.DatasetRepository;
import com.sense.prolt_operations.repository.GdamNewRecordRepository;
import com.sense.prolt_operations.repository.GdamRecordRepository;
import com.sense.prolt_operations.repository.RtmRecordRepository;
import com.sense.prolt_operations.repository.StateDemandDataRepository;
import com.sense.prolt_operations.repository.StateMarketRecordRepository;
import com.sense.prolt_operations.repository.UploadHistoryRepository;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.JwtService;
import com.sense.prolt_operations.service.PersistenceService;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MarketAndScraperIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DamRecordRepository damRecordRepository;

    @Autowired
    private GdamRecordRepository gdamRecordRepository;

    @Autowired
    private GdamNewRecordRepository gdamNewRecordRepository;

    @Autowired
    private RtmRecordRepository rtmRecordRepository;

    @Autowired
    private UploadHistoryRepository uploadHistoryRepository;

    @Autowired
    private StateDemandDataRepository stateDemandDataRepository;

    @Autowired
    private StateMarketRecordRepository stateMarketRecordRepository;

    private String validJwtToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.findByUsername("admin")
                .orElseGet(() -> {
                    User u = User.create();
                    u.setUsername("admin");
                    u.setEmail("admin@probus.io");
                    u.setPasswordHash("$2a$10$Rv4pjBCD9Lh7c4x1QTgxtO8uCxvpzt5tuDhdOn5VtLGAkJAKvl/cm");
                    u.setRole(Role.ADMIN);
                    return userRepository.save(u);
                });
        validJwtToken = jwtService.generateToken(user);
    }

    // ==========================================
    // 3.3 DATA-01: Market Datasets & Timeseries
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("DATA-01a: Timeseries with invalid market returns 400 Bad Request")
    void testTimeseriesInvalidMarket() throws Exception {
        mockMvc.perform(get("/api/datasets/timeseries")
                        .header("Authorization", "Bearer " + validJwtToken)
                        .param("date", "2026-06-20")
                        .param("market", "XYZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid market specified. Must be DAM, GDAM, or RTM"));
    }

    @Test
    @Order(2)
    @DisplayName("DATA-01b: Timeseries with no records for date returns 200 with empty list and count 0")
    void testTimeseriesNoRecordsFound() throws Exception {
        mockMvc.perform(get("/api/datasets/timeseries")
                        .header("Authorization", "Bearer " + validJwtToken)
                        .param("date", "2026-06-20")
                        .param("market", "DAM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @Order(3)
    @DisplayName("DATA-01: Timeseries returns 96-block MCP, MCV, purchaseBid, sellBid")
    void testTimeseriesFullDayDamRecords() throws Exception {
        LocalDate date = LocalDate.of(2026, 6, 20);
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 1; i <= 96; i++) {
            Map<String, Object> r = new HashMap<>();
            r.put("intervalNumber", i);
            int startH = (i - 1) * 15 / 60;
            int startM = ((i - 1) * 15) % 60;
            int endH = (i * 15) / 60;
            int endM = (i * 15) % 60;
            r.put("intervalTime", String.format("%02d:%02d - %02d:%02d", startH, startM, endH, endM));
            r.put("purchaseBid", 5120.00);
            r.put("sellBid", 4900.00);
            r.put("mcv", 4210.50);
            r.put("fsv", 4210.50);
            r.put("mcp", 3450.25);
            records.add(r);
        }

        PersistDatasetDTO dto = new PersistDatasetDTO("DAM", date, "dam_2026-06-20.csv", records, null, false);
        Dataset dataset = persistenceService.persistDataset(dto);
        assertThat(dataset).isNotNull();

        // Test endpoint: /api/datasets/timeseries
        mockMvc.perform(get("/api/datasets/timeseries")
                        .header("Authorization", "Bearer " + validJwtToken)
                        .param("date", "2026-06-20")
                        .param("market", "DAM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(96))
                .andExpect(jsonPath("$.data[0].timeBlock").value(1))
                .andExpect(jsonPath("$.data[0].time").value("00:00 - 00:15"))
                .andExpect(jsonPath("$.data[0].mcp").value(3450.25))
                .andExpect(jsonPath("$.data[0].mcv").value(4210.50))
                .andExpect(jsonPath("$.data[0].purchaseBid").value(5120.00))
                .andExpect(jsonPath("$.data[0].sellBid").value(4900.00));
    }

    // ==========================================
    // 3.4 SCRAPE-01: Scraper Ingestion & Webhooks
    // ==========================================

    @Test
    @Order(4)
    @DisplayName("SCRAPE-01a: Missing or invalid x-webhook-secret header returns 401 Unauthorized")
    void testScraperImportUnauthorized() throws Exception {
        Map<String, Object> body = Map.of(
                "market", "DAM",
                "deliveryDate", "2026-06-21",
                "records", List.of(Map.of("timeBlock", 1, "mcp", 3800.0, "mcv", 4500.0, "purchaseBid", 5000.0, "sellBid", 4800.0))
        );

        // Missing header
        mockMvc.perform(post("/api/scraper/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid webhook secret"));

        // Invalid secret
        mockMvc.perform(post("/api/scraper/import")
                        .header("x-webhook-secret", "wrong-secret-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid webhook secret"));
    }

    @Test
    @Order(5)
    @DisplayName("SCRAPE-01: Inbound Webhook Data Import succeeds with valid x-webhook-secret")
    void testScraperImportSuccess() throws Exception {
        Map<String, Object> record = Map.of(
                "timeBlock", 1,
                "mcp", 3800.00,
                "mcv", 4500.00,
                "purchaseBid", 5000.00,
                "sellBid", 4800.00
        );

        Map<String, Object> body = Map.of(
                "market", "DAM",
                "deliveryDate", "2026-06-21",
                "records", List.of(record)
        );

        mockMvc.perform(post("/api/scraper/import")
                        .header("x-webhook-secret", appProperties.getWebhookSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Successfully imported 1 records for DAM 2026-06-21"))
                .andExpect(jsonPath("$.datasetId").isNotEmpty());
    }

    @Test
    @Order(6)
    @DisplayName("SCRAPE-02: Webhook import supports x-webhook-token header and wrapped payload")
    void testScraperImportWrappedPayload() throws Exception {
        Map<String, Object> payload = Map.of(
                "market", "RTM",
                "deliveryDate", "2026-06-22",
                "fileName", "webhook_rtm_test.csv",
                "records", List.of(Map.of("intervalNumber", 1, "mcp", 4000.0, "mcv", 3000.0, "purchaseBid", 3500.0, "sellBid", 3200.0))
        );

        Map<String, Object> body = Map.of(
                "type", "dataset",
                "payload", payload
        );

        mockMvc.perform(post("/api/scraper/import")
                        .header("x-webhook-token", appProperties.getWebhookSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.datasetId").isNotEmpty());
    }

    // ==========================================
    // Additional Coverage: Dataset Management & History
    // ==========================================

    @Test
    @Order(7)
    @DisplayName("Dataset CRUD & Summary Endpoints")
    void testDatasetEndpoints() throws Exception {
        // Summary
        mockMvc.perform(get("/api/datasets/summary")
                        .header("Authorization", "Bearer " + validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalDatasets").isNumber());

        // Get All Datasets (paginated)
        MvcResult result = mockMvc.perform(get("/api/datasets")
                        .header("Authorization", "Bearer " + validJwtToken)
                        .param("page", "1")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").isNumber())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode dataArray = jsonNode.get("data");
        assertThat(dataArray.size()).isGreaterThan(0);
        String datasetId = dataArray.get(0).get("id").asText();

        // Get by ID
        mockMvc.perform(get("/api/datasets/" + datasetId)
                        .header("Authorization", "Bearer " + validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(datasetId));

        // Get History
        mockMvc.perform(get("/api/datasets/" + datasetId + "/history")
                        .header("Authorization", "Bearer " + validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // Upload History list
        mockMvc.perform(get("/api/upload-history")
                        .header("Authorization", "Bearer " + validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // Delete Dataset
        mockMvc.perform(delete("/api/datasets/" + datasetId)
                        .header("Authorization", "Bearer " + validJwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Dataset deleted successfully"));

        // Duplicate Delete returns 409 Conflict
        mockMvc.perform(delete("/api/datasets/" + datasetId)
                        .header("Authorization", "Bearer " + validJwtToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dataset is already deleted"));
    }

    // ==========================================
    // Public Dashboard Endpoints
    // ==========================================

    @Test
    @Order(8)
    @DisplayName("Public Dashboard Endpoints require no authentication")
    void testPublicDashboardEndpoints() throws Exception {
        // Overview
        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isMap());

        // DAM Data
        mockMvc.perform(get("/api/dashboard/dam")
                        .param("startDate", "2026-06-20")
                        .param("endDate", "2026-06-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // DAM Analytics
        mockMvc.perform(get("/api/dashboard/dam/analytics")
                        .param("startDate", "2026-06-20")
                        .param("endDate", "2026-06-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

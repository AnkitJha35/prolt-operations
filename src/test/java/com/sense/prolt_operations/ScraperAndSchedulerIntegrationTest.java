package com.sense.prolt_operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.scheduler.AppScheduler;
import com.sense.prolt_operations.security.JwtService;
import com.sense.prolt_operations.service.ScraperService;
import com.sense.prolt_operations.service.StarrocksService;

@AutoConfigureMockMvc
public class ScraperAndSchedulerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private ScraperService scraperService;

    @Autowired
    private StarrocksService starrocksService;

    @Autowired
    private AppScheduler appScheduler;

    private String validJwtToken;
    private com.sense.prolt_operations.testutil.TestUserHelper.TestUser testUser;

    @BeforeEach
    void setUp() {
        testUser = createTestUser(Role.ADMIN);
        validJwtToken = testUser.getToken();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        deleteTestUser(testUser);
    }

    private List<Map<String, Object>> create96SampleRecords(String date) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (int i = 1; i <= 96; i++) {
            int hh = (i - 1) / 4;
            int mm = ((i - 1) % 4) * 15;
            String time = String.format("%02d:%02d", hh, mm);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("intervalNumber", i);
            r.put("intervalTime", time);
            r.put("purchaseBid", 5000.0 + i);
            r.put("sellBid", 4800.0 + i);
            r.put("mcv", 4500.0 + i);
            r.put("fsv", 4500.0 + i);
            r.put("mcp", 3500.0 + i);
            r.put("date", date);
            records.add(r);
        }
        return records;
    }

    @Test
    @DisplayName("POST /api/scraper/sync DAM: mocks ScraperService and persists 96 records")
    void testScraperSyncDamSuccess() throws Exception {
        String today = LocalDate.now().toString();
        when(scraperService.scrapeDam()).thenReturn(create96SampleRecords(today));

        mockMvc.perform(post("/api/scraper/sync")
                        .header("Authorization", "Bearer " + validJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("market", "DAM"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Successfully scraped and saved 96 records for DAM"))
                .andExpect(jsonPath("$.datasetId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/scraper/sync REC: returns 500 with 'REC persistence not yet ported'")
    void testScraperSyncRecReturns500() throws Exception {
        mockMvc.perform(post("/api/scraper/sync")
                        .header("Authorization", "Bearer " + validJwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("market", "REC"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("REC persistence not yet ported"));
    }

    @Test
    @DisplayName("StarrocksService: safe query handling for telemetry data")
    void testStarrocksService() {
        List<String> meters = starrocksService.getAvailableMeters();
        assertThat(meters).isNotNull();

        Map<String, Double> demandMap = starrocksService.getConsumerActualDemandMap(List.of("2026-06-20"), "TEST_METER");
        assertThat(demandMap).isNotNull();
    }

    @Test
    @DisplayName("AppScheduler: background cron jobs execute safely under runScraper guard")
    void testAppSchedulerGuards() {
        appScheduler.runDailyChargeAlerts();
        appScheduler.runStateDemand();
        appScheduler.runHourlyWeather();
        appScheduler.runRtmScraper();
        appScheduler.runMidnightWeather();
        appScheduler.runMonthlyCtuCharges();
        appScheduler.runDailyMarketScrapers();
        appScheduler.runNppPolling();
        appScheduler.runMarketForecastRecommendations();
    }
}

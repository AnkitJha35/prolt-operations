package com.sense.prolt_operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.dto.calculator.CreateSavingsEntryDTO;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.TraderPerformanceEntry;
import com.sense.prolt_operations.entity.TraderPerformanceEntryHistory;
import com.sense.prolt_operations.repository.TraderPerformanceEntryHistoryRepository;
import com.sense.prolt_operations.repository.TraderPerformanceEntryRepository;
import com.sense.prolt_operations.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
class TraderPerformanceAuditingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TraderPerformanceEntryRepository entryRepository;

    @Autowired
    private TraderPerformanceEntryHistoryRepository historyRepository;

    private String traderToken;
    private String updaterToken;

    @BeforeEach
    void setUp() {
        traderToken = "Bearer " + jwtService.generateToken("trader1", "traderUser", "trader@probus.io", Role.CLIENT, List.of(), List.of());
        updaterToken = "Bearer " + jwtService.generateToken("updater1", "updaterUser", "updater@probus.io", Role.ADMIN, List.of(), List.of());
    }

    @Test
    @DisplayName("Create unauthenticated: createdBy and updatedBy are null matching Node behavior")
    void create_withoutToken_savesNullCreatedByAndUpdatedBy() throws Exception {
        CreateSavingsEntryDTO dto = new CreateSavingsEntryDTO(
                "Client Anonymous",
                "Textiles",
                "123 Industrial Area",
                new BigDecimal("500.00"),
                "UP",
                "PVVNL",
                "HV-2",
                "11kV",
                null, null, null, null, null, null, true, null, null, null, null, null
        );

        MvcResult result = mockMvc.perform(post("/api/trader-performance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.updatedBy").doesNotExist())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String entryId = responseJson.get("id").asText();

        TraderPerformanceEntry entry = entryRepository.findById(entryId).orElseThrow();
        assertThat(entry.getCreatedBy()).isNull();
        assertThat(entry.getUpdatedBy()).isNull();

        List<TraderPerformanceEntryHistory> histories = historyRepository.findByEntryIdOrderByVersionDesc(entryId);
        assertThat(histories).isNotEmpty();
        assertThat(histories.get(0).getCreatedBy()).isNull();
        assertThat(histories.get(0).getUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("Create authenticated: createdBy is set to username and updatedBy is null matching Node behavior")
    void create_withToken_savesUsernameAsCreatedByAndNullUpdatedBy() throws Exception {
        CreateSavingsEntryDTO dto = new CreateSavingsEntryDTO(
                "Client Authenticated",
                "Solar",
                "456 Solar Park",
                new BigDecimal("1000.00"),
                "UP",
                "MVVNL",
                "HV-1",
                "33kV",
                null, null, null, null, null, null, true, null, null, null, null, null
        );

        MvcResult result = mockMvc.perform(post("/api/trader-performance")
                        .header("Authorization", traderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.createdBy").value("traderUser"))
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        String entryId = responseJson.get("id").asText();

        TraderPerformanceEntry entry = entryRepository.findById(entryId).orElseThrow();
        assertThat(entry.getCreatedBy()).isEqualTo("traderUser");
        assertThat(entry.getUpdatedBy()).isNull();

        List<TraderPerformanceEntryHistory> histories = historyRepository.findByEntryIdOrderByVersionDesc(entryId);
        assertThat(histories).isNotEmpty();
        assertThat(histories.get(0).getCreatedBy()).isEqualTo("traderUser");
        assertThat(histories.get(0).getUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("Update unauthenticated: sets updatedBy to null while preserving createdBy")
    void update_withoutToken_setsUpdatedByToNull() throws Exception {
        CreateSavingsEntryDTO createDto = new CreateSavingsEntryDTO(
                "Client Update Test",
                "Chemicals",
                "789 Chemical Zone",
                new BigDecimal("750.00"),
                "UP",
                "DVVNL",
                "HV-2",
                "11kV",
                null, null, null, null, null, null, true, null, null, null, null, null
        );

        MvcResult createResult = mockMvc.perform(post("/api/trader-performance")
                        .header("Authorization", traderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();

        String entryId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        CreateSavingsEntryDTO updateDto = new CreateSavingsEntryDTO(
                "Client Update Test Renamed",
                "Chemicals",
                "789 Chemical Zone New",
                new BigDecimal("800.00"),
                "UP",
                "DVVNL",
                "HV-2",
                "11kV",
                null, null, null, null, null, null, true, null, null, null, null, null
        );

        mockMvc.perform(put("/api/trader-performance/" + entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        TraderPerformanceEntry entry = entryRepository.findById(entryId).orElseThrow();
        assertThat(entry.getCreatedBy()).isEqualTo("traderUser");
        assertThat(entry.getUpdatedBy()).isNull();

        List<TraderPerformanceEntryHistory> histories = historyRepository.findByEntryIdOrderByVersionDesc(entryId);
        assertThat(histories).hasSize(2);
        TraderPerformanceEntryHistory v2 = histories.get(0);
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.getCreatedBy()).isEqualTo("traderUser");
        assertThat(v2.getUpdatedBy()).isNull();
    }

    @Test
    @DisplayName("Update authenticated: sets updatedBy to updater username while preserving createdBy")
    void update_withToken_setsUpdatedByToUpdaterUsername() throws Exception {
        CreateSavingsEntryDTO createDto = new CreateSavingsEntryDTO(
                "Client Auth Update Test",
                "Manufacturing",
                "101 Factory Road",
                new BigDecimal("1200.00"),
                "UP",
                "PuVVNL",
                "HV-2",
                "33kV",
                null, null, null, null, null, null, true, null, null, null, null, null
        );

        MvcResult createResult = mockMvc.perform(post("/api/trader-performance")
                        .header("Authorization", traderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated())
                .andReturn();

        String entryId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        CreateSavingsEntryDTO updateDto = new CreateSavingsEntryDTO(
                "Client Auth Update Test Updated",
                "Manufacturing",
                "101 Factory Road",
                new BigDecimal("1500.00"),
                "UP",
                "PuVVNL",
                "HV-2",
                "33kV",
                null, null, null, null, null, null, true, null, null, null, null, null
        );

        mockMvc.perform(put("/api/trader-performance/" + entryId)
                        .header("Authorization", updaterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedBy").value("updaterUser"));

        TraderPerformanceEntry entry = entryRepository.findById(entryId).orElseThrow();
        assertThat(entry.getCreatedBy()).isEqualTo("traderUser");
        assertThat(entry.getUpdatedBy()).isEqualTo("updaterUser");

        List<TraderPerformanceEntryHistory> histories = historyRepository.findByEntryIdOrderByVersionDesc(entryId);
        assertThat(histories).hasSize(2);
        TraderPerformanceEntryHistory v2 = histories.get(0);
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.getCreatedBy()).isEqualTo("traderUser");
        assertThat(v2.getUpdatedBy()).isEqualTo("updaterUser");
    }
}

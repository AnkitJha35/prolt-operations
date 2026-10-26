package com.sense.prolt_operations;

import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.JwtService;
import com.sense.prolt_operations.service.ChartGeneratorService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProposalAndDocumentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChartGeneratorService chartGeneratorService;

    private String validToken;
    private com.sense.prolt_operations.testutil.TestUserHelper.TestUser testUser;

    @BeforeEach
    void setUp() {
        testUser = createTestUser(Role.ADMIN);
        validToken = testUser.getToken();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        deleteTestUser(testUser);
    }

    @Test
    @DisplayName("PROP-01: Generate Commercial Proposal DOCX with dynamic UP NPCL defaults")
    void testGenerateCommercialProposal() throws Exception {
        String payload = """
            {
                "client_name": "Acme Steel Mills Ltd",
                "state": "UP",
                "discom": "NPCL",
                "connectivity": "33 kV",
                "sanctioned_load": "5 MW",
                "feeder_type": "Dedicated Feeder"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/proposals/generate-commercial")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=Commercial_Proposal.docx"))
            .andReturn();

        byte[] docxBytes = result.getResponse().getContentAsByteArray();
        assertThat(docxBytes).isNotEmpty();
        // Check standard ZIP / DOCX magic bytes (PK\003\004)
        assertThat(docxBytes[0]).isEqualTo((byte) 0x50);
        assertThat(docxBytes[1]).isEqualTo((byte) 0x4B);

        // Verify document structure using Apache POI
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            assertThat(doc.getTables()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("PROP-02: Generate Technical Proposal DOCX with JFreeChart embedding")
    void testGenerateTechnicalProposal() throws Exception {
        String payload = """
            {
                "client_name": "Tata Motors Sanand",
                "state": "Gujarat",
                "monthlyData": [
                    { "month": "Jan 2026", "savings": 450000 },
                    { "month": "Feb 2026", "savings": 520000 },
                    { "month": "Mar 2026", "savings": 610000 }
                ],
                "average_monthly_savings": 526666,
                "savings_in_words": "Fifty Two Lakhs"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/proposals/generate-technical")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=Technical_Proposal.docx"))
            .andReturn();

        byte[] docxBytes = result.getResponse().getContentAsByteArray();
        assertThat(docxBytes).isNotEmpty();
        assertThat(docxBytes[0]).isEqualTo((byte) 0x50);
        assertThat(docxBytes[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    @DisplayName("PROP-03: Generate generic proposal endpoint /api/proposals/generate")
    void testProposalGenerate() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/proposals/generate")
                .header("Authorization", "Bearer " + validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"client_name\": \"Jindal Power\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes).isNotEmpty();
    }

    @Test
    @DisplayName("Pure Java JFreeChart rendering verification (no python / puppeteer)")
    void testChartGeneratorService() {
        byte[] barChart = chartGeneratorService.generateSavingsChart(
            List.of(Map.of("month", "Jun 2026", "savings", 3105000)),
            "₹31,05,000",
            "Thirty One Lakhs"
        );
        assertThat(barChart).isNotEmpty();
        // PNG magic number: 0x89 0x50 0x4E 0x47
        assertThat(barChart[0]).isEqualTo((byte) 0x89);
        assertThat(barChart[1]).isEqualTo((byte) 0x50);
        assertThat(barChart[2]).isEqualTo((byte) 0x4E);
        assertThat(barChart[3]).isEqualTo((byte) 0x47);

        byte[] pieChart = chartGeneratorService.generateConsumptionMixChart(70.0, 30.0, 700000, 300000);
        assertThat(pieChart).isNotEmpty();
        assertThat(pieChart[0]).isEqualTo((byte) 0x89);
    }
}

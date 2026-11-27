package com.sense.prolt_operations;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.security.JwtService;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UploadLimitsIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Trader Performance: Upload of 200 small files succeeds without Tomcat part count rejection")
    void upload200SmallFilesToTraderPerformance_succeeds() throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/trader-performance/upload");
        for (int i = 0; i < 200; i++) {
            MockMultipartFile file = new MockMultipartFile(
                    "files",
                    "trade_report_" + i + ".pdf",
                    "application/pdf",
                    ("dummy pdf content for report " + i).getBytes()
            );
            builder.file(file);
        }

        mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(200)));
    }

    @Test
    @DisplayName("Trader Performance: Upload of 201 files is rejected with Too many files")
    void uploadExceedingFileCount_toTraderPerformance_rejected() throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/trader-performance/upload");
        for (int i = 0; i < 201; i++) {
            MockMultipartFile file = new MockMultipartFile(
                    "files",
                    "trade_report_" + i + ".pdf",
                    "application/pdf",
                    ("dummy pdf content " + i).getBytes()
            );
            builder.file(file);
        }

        mockMvc.perform(builder)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("Too many files")));
    }

    @Test
    @DisplayName("Trader Performance: File exceeding 10MB limit is rejected with File too large")
    void uploadExceeding10MBFile_toTraderPerformance_rejected() throws Exception {
        // 10MB + 1 byte
        byte[] largeBytes = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile largeFile = new MockMultipartFile(
                "files",
                "large_report.pdf",
                "application/pdf",
                largeBytes
        );

        mockMvc.perform(multipart("/api/trader-performance/upload").file(largeFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("File too large")));
    }

    @Test
    @DisplayName("Trader Performance Daily: File exceeding 25MB limit is rejected with File too large")
    void uploadExceeding25MBFile_toTraderPerformanceDaily_rejected() throws Exception {
        // 25MB + 1 byte
        byte[] largeBytes = new byte[25 * 1024 * 1024 + 1];
        MockMultipartFile largeFile = new MockMultipartFile(
                "files",
                "large_daily_report.pdf",
                "application/pdf",
                largeBytes
        );

        mockMvc.perform(multipart("/api/trader-performance-daily/upload").file(largeFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", is("File too large")));
    }

    @Test
    @DisplayName("Uploads: Unsupported file extension (.exe) is rejected matching Node multer filter")
    void uploadUnsupportedExtension_rejected() throws Exception {
        var uploader = createTestUser(Role.ADMIN, "uploader", "uploader@probus.io");
        try {
            String token = uploader.getBearerToken();
            MockMultipartFile badExtFile = new MockMultipartFile(
                    "file",
                    "malicious.exe",
                    "application/octet-stream",
                    "binary".getBytes()
            );

            mockMvc.perform(multipart("/api/uploads")
                            .file(badExtFile)
                            .param("market", "DAM")
                            .param("deliveryDate", "2026-06-22")
                            .header("Authorization", token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", containsString("Unsupported file extension: .exe")));
        } finally {
            deleteTestUser(uploader);
        }
    }
}

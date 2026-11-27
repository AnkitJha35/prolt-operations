package com.sense.prolt_operations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigurationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("CORS Preflight: protected endpoint allows arbitrary origin, POST method, and custom headers")
    void corsPreflight_protectedEndpoint_allowsArbitraryOriginAndHeaders() throws Exception {
        mockMvc.perform(options("/api/datasets/summary")
                .header("Origin", "https://arbitrary-domain.com")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://arbitrary-domain.com"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
            .andExpect(header().string("Access-Control-Max-Age", "3600"))
            .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")))
            .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));
    }

    @Test
    @DisplayName("CORS Preflight: public auth endpoint allows arbitrary origin and credentials")
    void corsPreflight_publicEndpoint_allowsArbitraryOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/send-otp")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
            .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    @DisplayName("CORS Preflight: supports all configured methods (GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD)")
    void corsPreflight_supportsAllHttpMethods() throws Exception {
        for (String method : new String[]{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"}) {
            mockMvc.perform(options("/api/resource-center")
                    .header("Origin", "https://another-client.io")
                    .header("Access-Control-Request-Method", method))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://another-client.io"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString(method)));
        }
    }

    @Test
    @DisplayName("CORS Actual Request: includes CORS response headers on simple GET request")
    void corsActualRequest_includesCorsHeaders() throws Exception {
        mockMvc.perform(get("/health")
                .header("Origin", "https://frontend.probus.io"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.probus.io"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("CORS Preflight: superadmin endpoint (/api/users) allows preflight without credentials")
    void corsPreflight_superAdminEndpoint_succeedsWithoutAuth() throws Exception {
        mockMvc.perform(options("/api/users")
                .header("Origin", "https://admin.example.com")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://admin.example.com"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
            .andExpect(header().string("Access-Control-Max-Age", "3600"))
            .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")))
            .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));
    }
}

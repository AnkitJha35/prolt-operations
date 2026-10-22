package com.sense.prolt_operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.JwtService;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthAndUserIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.sense.prolt_operations.service.OtpService otpService;

    // Helper to generate admin / superadmin tokens
    private String getSuperAdminToken() {
        User superAdmin = userRepository.findByUsername("superadmin")
                .orElseGet(() -> {
                    User u = User.create();
                    u.setUsername("superadmin");
                    u.setEmail("super.admin@probus.io");
                    u.setPasswordHash("$2a$10$ZIjskOOICYsMX.vWy6f0V.I5GjW25i9FnzSmAuFkgyvyUakVaHXBu");
                    u.setRole(Role.SUPER_ADMIN);
                    return userRepository.save(u);
                });
        return jwtService.generateToken(superAdmin);
    }

    private String getAdminToken() {
        User admin = userRepository.findByUsername("admin")
                .orElseGet(() -> {
                    User u = User.create();
                    u.setUsername("admin");
                    u.setEmail("admin@iexdashboard.local");
                    u.setPasswordHash("$2a$10$ZIjskOOICYsMX.vWy6f0V.I5GjW25i9FnzSmAuFkgyvyUakVaHXBu");
                    u.setRole(Role.ADMIN);
                    return userRepository.save(u);
                });
        return jwtService.generateToken(admin);
    }

    @Test
    @Order(1)
    @DisplayName("AUTH-01: Standard Username/Password Login (Success)")
    void testAuth01_Login_Success() throws Exception {
        String payload = """
            {
                "username": "admin",
                "password": "admin"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    @Order(2)
    @DisplayName("AUTH-01a: Missing username or password returns 400")
    void testAuth01a_Login_MissingCredentials() throws Exception {
        String payload = """
            {
                "username": "",
                "password": ""
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(3)
    @DisplayName("AUTH-01b: Invalid credentials returns 401")
    void testAuth01b_Login_InvalidCredentials() throws Exception {
        String payload = """
            {
                "username": "admin",
                "password": "WrongPassword123!"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    @Order(4)
    @DisplayName("AUTH-02: Email OTP Generation (Success)")
    void testAuth02_SendOtp_Success() throws Exception {
        String payload = """
            {
                "email": "admin@iexdashboard.local"
            }
            """;

        mockMvc.perform(post("/api/auth/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OTP sent successfully"));
    }

    @Test
    @Order(5)
    @DisplayName("AUTH-02: Removed alias /api/auth/otp/generate returns 401/404")
    void testAuth02_OtpGenerateAlias_Removed() throws Exception {
        String payload = """
            {
                "email": "admin@iexdashboard.local"
            }
            """;

        // /otp/generate was removed; without auth it returns 401 Unauthorized
        mockMvc.perform(post("/api/auth/otp/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    @DisplayName("AUTH-02a: Send OTP to non-existent email returns 404")
    void testAuth02a_SendOtp_NotFound() throws Exception {
        String payload = """
            {
                "email": "nonexistent@probus.io"
            }
            """;

        mockMvc.perform(post("/api/auth/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User with this email not found"));
    }

    @Test
    @Order(7)
    @DisplayName("AUTH-03: Email OTP Verification - Superadmin Backdoor (Enabled -> Success)")
    void testAuth03_VerifyOtp_Superadmin_BypassEnabled_Success() throws Exception {
        otpService.setSuperadminBypassEnabled(true);
        String payload = """
            {
                "email": "super.admin@probus.io",
                "otp": "749261"
            }
            """;

        mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value("superadmin"))
                .andExpect(jsonPath("$.user.role").value("SUPER_ADMIN"));
    }

    @Test
    @Order(8)
    @DisplayName("AUTH-03: Email OTP Verification - Superadmin Backdoor (Disabled -> Fails)")
    void testAuth03_VerifyOtp_Superadmin_BypassDisabled_Fails() throws Exception {
        try {
            otpService.setSuperadminBypassEnabled(false);
            String payload = """
                {
                    "email": "super.admin@probus.io",
                    "otp": "749261"
                }
                """;

            mockMvc.perform(post("/api/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
        } finally {
            otpService.setSuperadminBypassEnabled(true);
        }
    }

    @Test
    @Order(9)
    @DisplayName("AUTH-03b: Prod Redis Failure Enforced when allowMemoryFallback is false")
    void testAuth03_ProdRedisFailure_Enforced() throws Exception {
        try {
            otpService.setAllowMemoryFallback(false);
            String payload = """
                {
                    "email": "admin@iexdashboard.local"
                }
                """;

            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Redis connection error"));
        } finally {
            otpService.setAllowMemoryFallback(true);
        }
    }

    @Test
    @Order(9)
    @DisplayName("AUTH-03a: Incorrect OTP returns 401")
    void testAuth03a_VerifyOtp_IncorrectOtp() throws Exception {
        String payload = """
            {
                "email": "super.admin@probus.io",
                "otp": "000000"
            }
            """;

        mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
    }

    @Test
    @Order(10)
    @DisplayName("GET /api/auth/me returns current user profile")
    void testGetMe_Success() throws Exception {
        String token = getAdminToken();

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.username").value("admin"));
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/auth/me without token returns 401")
    void testGetMe_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @Order(12)
    @DisplayName("USER-01: Create User (RBAC Superadmin Enforced)")
    void testUser01_CreateUser_Success() throws Exception {
        String token = getSuperAdminToken();
        String payload = """
            {
                "username": "trader_john",
                "email": "john@probus.io",
                "password": "SecurePassword123!",
                "role": "CLIENT",
                "hiddenModules": ["trader-performance", "admin-settings"],
                "readOnlyModules": ["savings-calculator"]
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("trader_john"))
                .andExpect(jsonPath("$.data.email").value("john@probus.io"))
                .andExpect(jsonPath("$.data.role").value("CLIENT"))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String createdId = jsonNode.get("data").get("id").asText();
        assertThat(createdId).isNotBlank();

        // Verify update
        String updatePayload = """
            {
                "role": "ADMIN"
            }
            """;
        mockMvc.perform(put("/api/users/" + createdId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        // Verify list users
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // Verify audit logs
        mockMvc.perform(get("/api/users/audit-logs")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // Verify delete
        mockMvc.perform(delete("/api/users/" + createdId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deleted successfully"));
    }

    @Test
    @Order(13)
    @DisplayName("USER-01a: Non-SuperAdmin calling /api/users returns 403 Forbidden")
    void testUser01a_Forbidden_NonSuperAdmin() throws Exception {
        String token = getAdminToken(); // Role ADMIN, not SUPER_ADMIN
        String payload = """
            {
                "username": "intruder",
                "email": "intruder@probus.io",
                "password": "Password123!",
                "role": "CLIENT"
            }
            """;

        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Forbidden: Super Admin access required"));
    }

    @Test
    @Order(14)
    @DisplayName("USER-01b: Duplicate username or email returns 400 Bad Request")
    void testUser01b_DuplicateUser_ReturnsBadRequest() throws Exception {
        String token = getSuperAdminToken();
        String payload = """
            {
                "username": "admin",
                "email": "admin@iexdashboard.local",
                "password": "Password123!",
                "role": "ADMIN"
            }
            """;

        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Username or email already exists"));
    }

    @Test
    @Order(15)
    @DisplayName("Health endpoints: /health and /health/database return 200")
    void testHealthEndpoints() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("healthy"));

        mockMvc.perform(get("/health/database"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.database").value("connected"));
    }
}

package com.sense.prolt_operations;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.testutil.TestUserHelper;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthAndUserIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.sense.prolt_operations.service.OtpService otpService;

    @Autowired(required = false)
    private com.sense.prolt_operations.security.AuthRateLimiterFilter authRateLimiterFilter;

    private TestUserHelper.TestUser superAdminUser;
    private TestUserHelper.TestUser adminUser;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        if (authRateLimiterFilter != null) {
            authRateLimiterFilter.getLimiter().reset();
        }
    }

    @AfterEach
    void tearDown() {
        if (superAdminUser != null) {
            deleteTestUser(superAdminUser);
            superAdminUser = null;
        }
        if (adminUser != null) {
            deleteTestUser(adminUser);
            adminUser = null;
        }
    }

    private String getSuperAdminToken() {
        if (superAdminUser == null) {
            superAdminUser = createTestUser(Role.SUPER_ADMIN);
        }
        return superAdminUser.getBearerToken();
    }

    private String getAdminToken() {
        if (adminUser == null) {
            adminUser = createTestUser(Role.ADMIN);
        }
        return adminUser.getBearerToken();
    }

    @Test
    @Order(1)
    @DisplayName("AUTH-01: Standard Username/Password Login (Success)")
    void testAuth01_Login_Success() throws Exception {
        TestUserHelper.TestUser loginUser = createTestUser(Role.ADMIN);
        try {
            String payload = String.format("""
                {
                    "username": "%s",
                    "password": "%s"
                }
                """, loginUser.getUser().getUsername(), loginUser.getRawPassword());

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.token").isString())
                    .andExpect(jsonPath("$.user.username").value(loginUser.getUser().getUsername()))
                    .andExpect(jsonPath("$.user.role").value("ADMIN"));
        } finally {
            deleteTestUser(loginUser);
        }
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
        TestUserHelper.TestUser invalidUser = createTestUser(Role.ADMIN);
        try {
            String payload = String.format("""
                {
                    "username": "%s",
                    "password": "WrongPassword123!"
                }
                """, invalidUser.getUser().getUsername());

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid credentials"));
        } finally {
            deleteTestUser(invalidUser);
        }
    }

    @Test
    @Order(4)
    @DisplayName("AUTH-02: Email OTP Generation (Success)")
    void testAuth02_SendOtp_Success() throws Exception {
        TestUserHelper.TestUser otpUser = createTestUser(Role.ADMIN);
        try {
            String payload = String.format("""
                {
                    "email": "%s"
                }
                """, otpUser.getUser().getEmail());

            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("OTP sent successfully"));
        } finally {
            deleteTestUser(otpUser);
        }
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
        TestUserHelper.TestUser superAdmin = testUserHelper.createOrUpdateSuperAdmin("super.admin@probus.io");
        try {
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
        } finally {
            deleteTestUser(superAdmin);
        }
    }

    @Test
    @Order(8)
    @DisplayName("AUTH-03: Email OTP Verification - Superadmin Backdoor (Disabled -> Fails)")
    void testAuth03_VerifyOtp_Superadmin_BypassDisabled_Fails() throws Exception {
        TestUserHelper.TestUser superAdmin = testUserHelper.createOrUpdateSuperAdmin("super.admin@probus.io");
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
            deleteTestUser(superAdmin);
        }
    }

    @Test
    @Order(9)
    @DisplayName("AUTH-03b: Prod Redis Failure Enforced when allowMemoryFallback is false")
    void testAuth03_ProdRedisFailure_Enforced() throws Exception {
        TestUserHelper.TestUser user = createTestUser(Role.ADMIN);
        try {
            otpService.setAllowMemoryFallback(false);
            String payload = String.format("""
                {
                    "email": "%s"
                }
                """, user.getUser().getEmail());

            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Redis connection error"));
        } finally {
            otpService.setAllowMemoryFallback(true);
            deleteTestUser(user);
        }
    }

    @Test
    @Order(10)
    @DisplayName("AUTH-03a: Incorrect OTP returns 401")
    void testAuth03a_VerifyOtp_IncorrectOtp() throws Exception {
        TestUserHelper.TestUser superAdmin = testUserHelper.createOrUpdateSuperAdmin("super.admin@probus.io");
        try {
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
        } finally {
            deleteTestUser(superAdmin);
        }
    }

    @Test
    @Order(11)
    @DisplayName("GET /api/auth/me returns current user profile")
    void testGetMe_Success() throws Exception {
        TestUserHelper.TestUser meUser = createTestUser(Role.ADMIN);
        try {
            mockMvc.perform(get("/api/auth/me")
                    .header("Authorization", meUser.getBearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.user.username").value(meUser.getUser().getUsername()))
                    .andExpect(jsonPath("$.user.email").doesNotExist())
                    .andExpect(jsonPath("$.user.name").doesNotExist())
                    .andExpect(jsonPath("$.user.iat").isNumber())
                    .andExpect(jsonPath("$.user.exp").isNumber())
                    .andExpect(jsonPath("$.data").doesNotExist());
        } finally {
            deleteTestUser(meUser);
        }
    }

    @Test
    @Order(12)
    @DisplayName("GET /api/auth/me without token returns 401")
    void testGetMe_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @Order(13)
    @DisplayName("USER-01: Create User (RBAC Superadmin Enforced)")
    void testUser01_CreateUser_Success() throws Exception {
        String token = getSuperAdminToken();
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = "trader_john_" + uniqueSuffix;
        String email = username + "@probus.io";

        String payload = String.format("""
            {
                "username": "%s",
                "email": "%s",
                "fullName": "Trader John",
                "password": "SecurePassword123!",
                "role": "CLIENT",
                "hiddenModules": ["trader-performance", "admin-settings"],
                "readOnlyModules": ["savings-calculator"]
            }
            """, username, email);

        MvcResult result = mockMvc.perform(post("/api/users")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.fullName").value("Trader John"))
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
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        // Verify list users
        mockMvc.perform(get("/api/users")
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // Verify audit logs
        mockMvc.perform(get("/api/users/audit-logs")
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        // Verify delete
        mockMvc.perform(delete("/api/users/" + createdId)
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deleted successfully"));
    }

    @Test
    @Order(14)
    @DisplayName("USER-01a: Non-SuperAdmin calling /api/users returns 403 Forbidden")
    void testUser01a_Forbidden_NonSuperAdmin() throws Exception {
        String token = getAdminToken(); // Role ADMIN, not SUPER_ADMIN
        String payload = """
            {
                "username": "intruder_user",
                "email": "intruder@probus.io",
                "fullName": "Intruder User",
                "password": "Password123!",
                "role": "CLIENT"
            }
            """;

        mockMvc.perform(post("/api/users")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Forbidden: Super Admin access required"));
    }

    @Test
    @Order(15)
    @DisplayName("USER-01b: Duplicate username or email returns 400 Bad Request")
    void testUser01b_DuplicateUser_ReturnsBadRequest() throws Exception {
        TestUserHelper.TestUser dupUser = createTestUser(Role.ADMIN);
        try {
            String token = getSuperAdminToken();
            String payload = String.format("""
                {
                    "username": "%s",
                    "email": "%s",
                    "fullName": "Duplicate User",
                    "password": "Password123!",
                    "role": "ADMIN"
                }
                """, dupUser.getUser().getUsername(), dupUser.getUser().getEmail());

            mockMvc.perform(post("/api/users")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Username or email already exists"));
        } finally {
            deleteTestUser(dupUser);
        }
    }

    @Test
    @Order(16)
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

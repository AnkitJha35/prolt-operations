package com.sense.prolt_operations;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.AuthRateLimiterFilter;
import com.sense.prolt_operations.service.OtpService;
import com.sense.prolt_operations.testutil.TestUserHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OtpOnlyAuthenticationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private AuthRateLimiterFilter authRateLimiterFilter;

    private TestUserHelper.TestUser superAdminUser;

    @BeforeEach
    void setUp() {
        if (authRateLimiterFilter != null) {
            authRateLimiterFilter.getLimiter().reset();
        }
        otpService.setAllowMemoryFallback(true);
        otpService.setSuperadminBypassEnabled(false);
    }

    @AfterEach
    void tearDown() {
        if (superAdminUser != null) {
            deleteTestUser(superAdminUser);
            superAdminUser = null;
        }
    }

    private String getSuperAdminToken() {
        if (superAdminUser == null) {
            superAdminUser = createTestUser(Role.SUPER_ADMIN);
        }
        return superAdminUser.getBearerToken();
    }

    @Test
    @Order(1)
    @DisplayName("OTP-01: Successful OTP flow: send-otp -> verify-otp returns JWT and user details")
    void testOtpLoginSuccess() throws Exception {
        TestUserHelper.TestUser user = createTestUser(Role.CLIENT);
        String email = user.getUser().getEmail();
        try {
            // Send OTP
            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\"}", email)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("OTP sent successfully"));

            String otp = otpService.getInMemoryStore().get(email.toLowerCase()).otp();
            assertThat(otp).isNotBlank();

            // Verify OTP
            mockMvc.perform(post("/api/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\", \"otp\": \"%s\"}", email, otp)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.token").isString())
                    .andExpect(jsonPath("$.user.email").value(email.toLowerCase()))
                    .andExpect(jsonPath("$.user.username").value(user.getUser().getUsername()));
        } finally {
            deleteTestUser(user);
        }
    }

    @Test
    @Order(2)
    @DisplayName("OTP-02: Invalid OTP returns 401 Unauthorized")
    void testInvalidOtpReturnsUnauthorized() throws Exception {
        TestUserHelper.TestUser user = createTestUser(Role.CLIENT);
        String email = user.getUser().getEmail();
        try {
            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\"}", email)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\", \"otp\": \"000000\"}", email)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
        } finally {
            deleteTestUser(user);
        }
    }

    @Test
    @Order(3)
    @DisplayName("OTP-03: Expired OTP returns 401 Unauthorized")
    void testExpiredOtpReturnsUnauthorized() throws Exception {
        TestUserHelper.TestUser user = createTestUser(Role.CLIENT);
        String email = user.getUser().getEmail();
        try {
            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\"}", email)))
                    .andExpect(status().isOk());

            String normalized = email.toLowerCase();
            String otp = otpService.getInMemoryStore().get(normalized).otp();

            // Artificially expire the stored OTP
            otpService.getInMemoryStore().put(normalized,
                    new OtpService.StoredOtp(otp, java.time.Instant.now().minus(Duration.ofMinutes(1))));

            mockMvc.perform(post("/api/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\", \"otp\": \"%s\"}", email, otp)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
        } finally {
            deleteTestUser(user);
        }
    }

    @Test
    @Order(4)
    @DisplayName("OTP-04: OTP single-use: replay/reuse attempt returns 401 Unauthorized")
    void testOtpReplayRejection() throws Exception {
        TestUserHelper.TestUser user = createTestUser(Role.CLIENT);
        String email = user.getUser().getEmail();
        try {
            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\"}", email)))
                    .andExpect(status().isOk());

            String otp = otpService.getInMemoryStore().get(email.toLowerCase()).otp();

            // First verify succeeds
            mockMvc.perform(post("/api/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\", \"otp\": \"%s\"}", email, otp)))
                    .andExpect(status().isOk());

            // Second verify with same OTP fails
            mockMvc.perform(post("/api/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\", \"otp\": \"%s\"}", email, otp)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Invalid or expired OTP"));
        } finally {
            deleteTestUser(user);
        }
    }

    @Test
    @Order(5)
    @DisplayName("OTP-05: Rate limiting: 10 attempts allowed, 11th returns 429")
    void testAuthRateLimitingOnOtpEndpoints() throws Exception {
        String testIp = "198.51.100.99";

        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post("/api/auth/send-otp")
                    .with(r -> { r.setRemoteAddr(testIp); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"nonexistent_rl@probus.io\"}"))
                    .andExpect(status().isNotFound());
        }

        // 11th request returns 429
        mockMvc.perform(post("/api/auth/send-otp")
                .with(r -> { r.setRemoteAddr(testIp); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"nonexistent_rl@probus.io\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", is("Too many login attempts, please try again after 15 minutes")));
    }

    @Test
    @Order(6)
    @DisplayName("OTP-06: Disabled user rejected on send-otp with 401 Unauthorized")
    void testDisabledUserRejectedOnSendOtp() throws Exception {
        TestUserHelper.TestUser user = testUserHelper.createUser(Role.CLIENT, "disabled_otp_user", "disabled_otp@probus.io", false);
        try {
            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\": \"disabled_otp@probus.io\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Account is disabled. Please contact administrator."));
        } finally {
            deleteTestUser(user);
        }
    }

    @Test
    @Order(7)
    @DisplayName("OTP-07: Send OTP to non-existent email returns 404 Not Found")
    void testUnknownEmailReturns404() throws Exception {
        mockMvc.perform(post("/api/auth/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"doesnotexist_abc123@probus.io\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User with this email not found"));
    }

    @Test
    @Order(8)
    @DisplayName("USER-01: User creation via POST /api/users without password succeeds")
    void testUserCreationWithoutPassword() throws Exception {
        String token = getSuperAdminToken();
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = "nopass_user_" + uid;
        String email = username + "@probus.io";

        String createPayload = String.format("""
            {
                "username": "%s",
                "email": "%s",
                "fullName": "No Password User",
                "role": "CLIENT"
            }
            """, username, email);

        MvcResult res = mockMvc.perform(post("/api/users")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.email").value(email))
                .andReturn();

        String createdId = objectMapper.readTree(res.getResponse().getContentAsString()).get("data").get("id").asText();

        try {
            User persisted = userRepository.findById(createdId).orElseThrow();
            assertThat(persisted.getUsername()).isEqualTo(username);
            assertThat(persisted.getEmail()).isEqualTo(email);
        } finally {
            userRepository.deleteById(createdId);
        }
    }

    @Test
    @Order(9)
    @DisplayName("USER-02: User update via PUT /api/users/{id} without password succeeds")
    void testUserUpdateWithoutPassword() throws Exception {
        TestUserHelper.TestUser client = createTestUser(Role.CLIENT);
        String token = getSuperAdminToken();

        try {
            String updatePayload = """
                {
                    "fullName": "Updated OTP Full Name",
                    "role": "ADMIN"
                }
                """;

            mockMvc.perform(put("/api/users/" + client.getUser().getId())
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.fullName").value("Updated OTP Full Name"))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"));

            User persisted = userRepository.findById(client.getUser().getId()).orElseThrow();
            assertThat(persisted.getFullName()).isEqualTo("Updated OTP Full Name");
            assertThat(persisted.getRole()).isEqualTo(Role.ADMIN);
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(10)
    @DisplayName("AUTH-LEGACY: Decommissioned POST /api/auth/login returns 404 Not Found")
    void testLegacyLoginEndpointReturns404() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"admin\", \"password\": \"AnyPassword123!\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(11)
    @DisplayName("SECURITY-01: PasswordEncoder bean is completely absent from Spring ApplicationContext")
    void testPasswordEncoderBeanIsAbsent() {
        String[] beanNames = applicationContext.getBeanNamesForType(PasswordEncoder.class);
        assertThat(beanNames)
                .as("PasswordEncoder bean must NOT exist in the ApplicationContext after complete password removal")
                .isEmpty();
    }
}

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.testutil.TestUserHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserEnableDisableIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.sense.prolt_operations.service.OtpService otpService;

    @Autowired
    private com.sense.prolt_operations.security.JwtService jwtService;

    private TestUserHelper.TestUser superAdminUser;
    private TestUserHelper.TestUser adminUser;

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
    @DisplayName("STATUS-01: Super Admin can disable a user via PATCH /api/users/{id}/status")
    void testSuperAdminCanDisableUser() throws Exception {
        TestUserHelper.TestUser client = createTestUser(Role.CLIENT);
        try {
            String payload = """
                {
                    "enabled": false
                }
                """;

            mockMvc.perform(patch("/api/users/" + client.getUser().getId() + "/status")
                    .header("Authorization", getSuperAdminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.enabled").value(false));

            User updated = userRepository.findById(client.getUser().getId()).orElseThrow();
            assertThat(updated.isEnabled()).isFalse();
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(2)
    @DisplayName("STATUS-02: Super Admin can re-enable a disabled user via PATCH /api/users/{id}/status")
    void testSuperAdminCanReEnableUser() throws Exception {
        TestUserHelper.TestUser client = testUserHelper.createUser(Role.CLIENT, "test_re_enable", "test_re_enable@probus.io", false);
        try {
            assertThat(client.getUser().isEnabled()).isFalse();

            String payload = """
                {
                    "enabled": true
                }
                """;

            mockMvc.perform(patch("/api/users/" + client.getUser().getId() + "/status")
                    .header("Authorization", getSuperAdminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.enabled").value(true));

            User updated = userRepository.findById(client.getUser().getId()).orElseThrow();
            assertThat(updated.isEnabled()).isTrue();
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(3)
    @DisplayName("STATUS-03: Non-Super Admin cannot call PATCH /api/users/{id}/status (403 Forbidden)")
    void testNonSuperAdminCannotUpdateStatus() throws Exception {
        TestUserHelper.TestUser client = createTestUser(Role.CLIENT);
        try {
            String payload = """
                {
                    "enabled": false
                }
                """;

            mockMvc.perform(patch("/api/users/" + client.getUser().getId() + "/status")
                    .header("Authorization", getAdminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isForbidden());
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(4)
    @DisplayName("STATUS-04: Super Admin cannot disable own account (400 Bad Request)")
    void testSuperAdminCannotDisableOwnAccount() throws Exception {
        TestUserHelper.TestUser sa = createTestUser(Role.SUPER_ADMIN);
        try {
            String payload = """
                {
                    "enabled": false
                }
                """;

            mockMvc.perform(patch("/api/users/" + sa.getUser().getId() + "/status")
                    .header("Authorization", sa.getBearerToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("You cannot disable your own account"));
        } finally {
            deleteTestUser(sa);
        }
    }

    @Test
    @Order(5)
    @DisplayName("STATUS-05: Cannot disable last active Super Administrator (400 Bad Request)")
    void testCannotDisableLastSuperAdmin() throws Exception {
        // Ensure there is only 1 superadmin by querying existing ones and disabling or testing single
        long count = userRepository.countByRoleAndEnabledTrue(Role.SUPER_ADMIN);
        // Create an isolated secondary super admin if needed, but if count is 1, calling disable fails
        TestUserHelper.TestUser caller = createTestUser(Role.ADMIN);
        TestUserHelper.TestUser target = createTestUser(Role.SUPER_ADMIN);
        try {
            // Disable all other superadmins except target
            var allSuperAdmins = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == Role.SUPER_ADMIN && !u.getId().equals(target.getUser().getId()))
                    .toList();
            for (User u : allSuperAdmins) {
                u.setEnabled(false);
                userRepository.save(u);
            }

            // Now target is the ONLY active super admin in database
            assertThat(userRepository.countByRoleAndEnabledTrue(Role.SUPER_ADMIN)).isEqualTo(1);

            // Caller is an enabled admin whose token carries SUPER_ADMIN permission
            String callerToken = "Bearer " + jwtService.generateToken(
                    caller.getUser().getId(),
                    caller.getUser().getUsername(),
                    Role.SUPER_ADMIN,
                    caller.getUser().getTokenVersion(),
                    java.util.List.of(),
                    java.util.List.of()
            );

            String payload = """
                {
                    "enabled": false
                }
                """;

            // Caller tries to disable target:
            mockMvc.perform(patch("/api/users/" + target.getUser().getId() + "/status")
                    .header("Authorization", callerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Cannot disable the last active Super Administrator account"));
        } finally {
            deleteTestUser(caller);
            deleteTestUser(target);
        }
    }

    @Test
    @Order(6)
    @DisplayName("STATUS-06: PUT /api/users/{id} preserves enabled status and cannot modify it")
    void testPutUserPreservesEnabledStatus() throws Exception {
        TestUserHelper.TestUser client = testUserHelper.createUser(Role.CLIENT, "test_preserve_status", "preserve@probus.io", false);
        try {
            assertThat(client.getUser().isEnabled()).isFalse();

            String payload = """
                {
                    "email": "preserve_updated@probus.io",
                    "fullName": "Updated Preserved Name",
                    "role": "CLIENT",
                    "enabled": true
                }
                """;

            mockMvc.perform(put("/api/users/" + client.getUser().getId())
                    .header("Authorization", getSuperAdminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.enabled").value(false));

            User updated = userRepository.findById(client.getUser().getId()).orElseThrow();
            assertThat(updated.isEnabled()).isFalse();
            assertThat(updated.getEmail()).isEqualTo("preserve_updated@probus.io");
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(7)
    @DisplayName("STATUS-07: Disabled user cannot authenticate via OTP (401 Unauthorized) and legacy login returns 404")
    void testDisabledUserCannotLoginWithPassword() throws Exception {
        TestUserHelper.TestUser client = testUserHelper.createUser(Role.CLIENT, "test_login_disabled", "login_disabled@probus.io", false);
        try {
            // Verify /api/auth/send-otp is rejected with 401
            String otpPayload = String.format("""
                {
                    "email": "%s"
                }
                """, client.getUser().getEmail());

            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(otpPayload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Account is disabled. Please contact administrator."));

            // Verify legacy /api/auth/login returns 404
            String legacyPayload = String.format("""
                {
                    "username": "%s",
                    "password": "AnyPassword123!"
                }
                """, client.getUser().getUsername());

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(legacyPayload))
                    .andExpect(status().isNotFound());
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(8)
    @DisplayName("STATUS-08: Disabled user cannot request OTP (401 Unauthorized)")
    void testDisabledUserCannotSendOtp() throws Exception {
        TestUserHelper.TestUser client = testUserHelper.createUser(Role.CLIENT, "test_otp_disabled", "otp_disabled@probus.io", false);
        try {
            String payload = String.format("""
                {
                    "email": "%s"
                }
                """, client.getUser().getEmail());

            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Account is disabled. Please contact administrator."));
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(9)
    @DisplayName("STATUS-09: Disabled user cannot verify OTP (401 Unauthorized)")
    void testDisabledUserCannotVerifyOtp() throws Exception {
        TestUserHelper.TestUser client = testUserHelper.createUser(Role.CLIENT, "test_votp_disabled", "votp_disabled@probus.io", false);
        try {
            String payload = String.format("""
                {
                    "email": "%s",
                    "otp": "123456"
                }
                """, client.getUser().getEmail());

            // Even if OTP is in store:
            otpService.getInMemoryStore().put(client.getUser().getEmail(), new com.sense.prolt_operations.service.OtpService.StoredOtp("123456", java.time.Instant.now().plusSeconds(300)));

            mockMvc.perform(post("/api/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Account is disabled. Please contact administrator."));
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(10)
    @DisplayName("STATUS-10: Disabled user cannot access /api/auth/me (401 Unauthorized)")
    void testDisabledUserCannotAccessGetMe() throws Exception {
        TestUserHelper.TestUser client = testUserHelper.createUser(Role.CLIENT, "test_me_disabled", "me_disabled@probus.io", true);
        try {
            // Initially enabled, getMe succeeds
            mockMvc.perform(get("/api/auth/me")
                    .header("Authorization", client.getBearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.user.enabled").value(true));

            // Now disable user
            User u = userRepository.findById(client.getUser().getId()).orElseThrow();
            u.setEnabled(false);
            userRepository.save(u);

            // Next /api/auth/me call fails with 401
            mockMvc.perform(get("/api/auth/me")
                    .header("Authorization", client.getBearerToken()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Account is disabled. Please contact administrator."));
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(11)
    @DisplayName("STATUS-11: Re-enabled user can authenticate normally again via OTP")
    void testReEnabledUserCanLoginAgain() throws Exception {
        TestUserHelper.TestUser client = testUserHelper.createUser(Role.CLIENT, "test_relogin", "relogin@probus.io", false);
        try {
            // Cannot request OTP initially
            String otpPayload = String.format("""
                {
                    "email": "%s"
                }
                """, client.getUser().getEmail());

            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(otpPayload))
                    .andExpect(status().isUnauthorized());

            // Re-enable
            User u = userRepository.findById(client.getUser().getId()).orElseThrow();
            u.setEnabled(true);
            userRepository.save(u);

            // Can send and verify OTP now
            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(otpPayload))
                    .andExpect(status().isOk());

            String otp = otpService.getInMemoryStore().get("relogin@probus.io").otp();

            String verifyPayload = String.format("""
                {
                    "email": "%s",
                    "otp": "%s"
                }
                """, client.getUser().getEmail(), otp);

            mockMvc.perform(post("/api/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.user.enabled").value(true));
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(12)
    @DisplayName("STATUS-12: Audit log records status change")
    void testAuditLogRecordsStatusChange() throws Exception {
        TestUserHelper.TestUser client = createTestUser(Role.CLIENT);
        try {
            String payload = """
                {
                    "enabled": false
                }
                """;

            mockMvc.perform(patch("/api/users/" + client.getUser().getId() + "/status")
                    .header("Authorization", getSuperAdminToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(status().isOk());

            // Check audit logs
            mockMvc.perform(get("/api/users/audit-logs")
                    .header("Authorization", getSuperAdminToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.targetUserId == '" + client.getUser().getId() + "')].action").value("UPDATE_STATUS"));
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(13)
    @DisplayName("STATUS-13: GET /api/users returns enabled status on UserDTO")
    void testGetAllUsersReturnsEnabledStatus() throws Exception {
        TestUserHelper.TestUser client = createTestUser(Role.CLIENT);
        try {
            mockMvc.perform(get("/api/users")
                    .header("Authorization", getSuperAdminToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.id == '" + client.getUser().getId() + "')].enabled").value(true));
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(14)
    @DisplayName("STATUS-14: AuthUserResponse contains enabled field on OTP verification")
    void testAuthUserResponseContainsEnabledField() throws Exception {
        TestUserHelper.TestUser client = createTestUser(Role.CLIENT);
        try {
            String otpPayload = String.format("""
                {
                    "email": "%s"
                }
                """, client.getUser().getEmail());

            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(otpPayload))
                    .andExpect(status().isOk());

            String otp = otpService.getInMemoryStore().get(client.getUser().getEmail().toLowerCase()).otp();

            String verifyPayload = String.format("""
                {
                    "email": "%s",
                    "otp": "%s"
                }
                """, client.getUser().getEmail(), otp);

            mockMvc.perform(post("/api/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyPayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.enabled").value(true));
        } finally {
            deleteTestUser(client);
        }
    }
}

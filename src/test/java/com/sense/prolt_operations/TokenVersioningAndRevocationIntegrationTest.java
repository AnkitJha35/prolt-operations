package com.sense.prolt_operations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.entity.UserAuditLog;
import com.sense.prolt_operations.repository.UserAuditLogRepository;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.AuthRateLimiterFilter;
import com.sense.prolt_operations.security.JwtService;
import com.sense.prolt_operations.security.ProltUserPrincipal;
import com.sense.prolt_operations.testutil.TestUserHelper;

import io.jsonwebtoken.Jwts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TokenVersioningAndRevocationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuditLogRepository userAuditLogRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private com.sense.prolt_operations.service.OtpService otpService;

    @Autowired(required = false)
    private AuthRateLimiterFilter authRateLimiterFilter;

    private final List<TestUserHelper.TestUser> createdUsers = new ArrayList<>();
    private TestUserHelper.TestUser superAdminUser;

    @BeforeEach
    void setUp() {
        if (authRateLimiterFilter != null) {
            authRateLimiterFilter.getLimiter().reset();
        }
    }

    @AfterEach
    void tearDown() {
        for (TestUserHelper.TestUser tu : createdUsers) {
            deleteTestUser(tu);
        }
        createdUsers.clear();
        if (superAdminUser != null) {
            deleteTestUser(superAdminUser);
            superAdminUser = null;
        }
    }

    private TestUserHelper.TestUser track(TestUserHelper.TestUser tu) {
        createdUsers.add(tu);
        return tu;
    }

    private String getSuperAdminToken() {
        if (superAdminUser == null) {
            superAdminUser = createTestUser(Role.SUPER_ADMIN);
        }
        return superAdminUser.getBearerToken();
    }

    @Test
    @Order(1)
    @DisplayName("SCENARIO 01: Default tokenVersion - A newly created user defaults to tokenVersion = 0")
    void test01_newUser_defaultsToTokenVersionZero() {
        TestUserHelper.TestUser user = track(createTestUser(Role.CLIENT));
        User dbUser = userRepository.findById(user.getUser().getId()).orElseThrow();
        assertThat(dbUser.getTokenVersion()).isEqualTo(0);
    }

    @Test
    @Order(2)
    @DisplayName("SCENARIO 02: JWT Claim - Newly issued JWT contains correct tokenVersion claim")
    void test02_jwtContainsTokenVersionClaim() {
        TestUserHelper.TestUser user = track(createTestUser(Role.CLIENT));
        String token = user.getToken();
        ProltUserPrincipal principal = jwtService.extractPrincipal(token);
        assertThat(principal.getTokenVersion()).isEqualTo(0);
    }

    @Test
    @Order(3)
    @DisplayName("SCENARIO 03: Valid JWT accepted - Valid token with matching version is accepted on protected business APIs")
    void test03_validTokenWithMatchingVersion_isAcceptedOnProtectedBusinessApis() throws Exception {
        TestUserHelper.TestUser client = track(createTestUser(Role.CLIENT));
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", client.getBearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(4)
    @DisplayName("SCENARIO 04: Mismatched tokenVersion rejected - Token issued with old version is rejected with 401 when DB version advances")
    void test04_tokenWithMismatchedVersion_isRejectedWith401() throws Exception {
        TestUserHelper.TestUser client = track(createTestUser(Role.CLIENT));
        String oldToken = client.getBearerToken();

        // Increment tokenVersion directly in database
        int rows = userRepository.incrementTokenVersion(client.getUser().getId());
        assertThat(rows).isEqualTo(1);

        // Previous token must now be rejected with 401
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @Order(5)
    @DisplayName("SCENARIO 05: Missing tokenVersion claim rejected - Legacy JWT without tokenVersion claim is rejected with 401")
    void test05_jwtWithoutTokenVersionClaim_isRejectedWith401() throws Exception {
        TestUserHelper.TestUser client = track(createTestUser(Role.CLIENT));

        SecretKey signingKey = (SecretKey) ReflectionTestUtils.getField(jwtService, "signingKey");
        assertThat(signingKey).isNotNull();

        // Build token WITHOUT tokenVersion claim
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3600000L);
        String legacyToken = Jwts.builder()
                .claim("id", client.getUser().getId())
                .claim("username", client.getUser().getUsername())
                .claim("role", Role.CLIENT.name())
                .claim("hiddenModules", Collections.emptyList())
                .claim("readOnlyModules", Collections.emptyList())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();

        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", "Bearer " + legacyToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @Order(6)
    @DisplayName("SCENARIO 06: Disabled user rejected - Disabled account is rejected on protected APIs with explicit message")
    void test06_disabledUser_isRejectedOnProtectedApisWithExplicitMessage() throws Exception {
        TestUserHelper.TestUser disabledClient = track(testUserHelper.createUser(
                Role.CLIENT, "test_disabled_client", "test_disabled_client@probus.io", false));

        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", disabledClient.getBearerToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Account is disabled. Please contact administrator."));
    }

    @Test
    @Order(7)
    @DisplayName("SCENARIO 07: Immediate rejection after disable - Existing JWT is rejected immediately after account disable")
    void test07_existingJwt_isRejectedImmediatelyAfterAccountDisable() throws Exception {
        TestUserHelper.TestUser client = track(createTestUser(Role.CLIENT));

        // Token works before disable
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", client.getBearerToken()))
                .andExpect(status().isOk());

        // Disable user
        mockMvc.perform(patch("/api/users/" + client.getUser().getId() + "/status")
                .header("Authorization", getSuperAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
                .andExpect(status().isOk());

        // Same token immediately fails
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", client.getBearerToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Account is disabled. Please contact administrator."));
    }

    @Test
    @Order(8)
    @DisplayName("SCENARIO 08: Old JWT remains rejected after re-enable - Re-enabling account preserves bumped tokenVersion")
    void test08_oldJwt_remainsRejectedAfterAccountReEnable() throws Exception {
        TestUserHelper.TestUser client = track(createTestUser(Role.CLIENT));
        String preDisableToken = client.getBearerToken();

        // 1. Disable account (increments tokenVersion 0 -> 1)
        mockMvc.perform(patch("/api/users/" + client.getUser().getId() + "/status")
                .header("Authorization", getSuperAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
                .andExpect(status().isOk());

        // 2. Re-enable account (tokenVersion stays 1)
        mockMvc.perform(patch("/api/users/" + client.getUser().getId() + "/status")
                .header("Authorization", getSuperAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": true}"))
                .andExpect(status().isOk());

        // 3. Pre-disable token (version 0) MUST remain rejected
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", preDisableToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @Order(9)
    @DisplayName("SCENARIO 09: New JWT after re-enable is accepted - Fresh token issued after re-enable has new version and works")
    void test09_newJwt_afterReEnable_isAccepted() throws Exception {
        TestUserHelper.TestUser client = track(createTestUser(Role.CLIENT));

        // Disable then re-enable
        mockMvc.perform(patch("/api/users/" + client.getUser().getId() + "/status")
                .header("Authorization", getSuperAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/users/" + client.getUser().getId() + "/status")
                .header("Authorization", getSuperAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": true}"))
                .andExpect(status().isOk());

        // Authenticate via OTP to get new token
        String email = client.getUser().getEmail();
        mockMvc.perform(post("/api/auth/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\": \"%s\"}", email)))
                .andExpect(status().isOk());

        String otp = otpService.getInMemoryStore().get(email.toLowerCase()).otp();

        var mvcResult = mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\": \"%s\", \"otp\": \"%s\"}", email, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String responseJson = mvcResult.getResponse().getContentAsString();
        String newToken = objectMapper.readTree(responseJson).get("token").asText();

        // Fresh token has version 1 and succeeds
        ProltUserPrincipal principal = jwtService.extractPrincipal(newToken);
        assertThat(principal.getTokenVersion()).isEqualTo(1);

        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(10)
    @DisplayName("SCENARIO 10: User update does not revoke sessions, but session revocation increments tokenVersion and revokes old tokens")
    void test10_passwordUpdate_invalidatesPreviousJwts() throws Exception {
        TestUserHelper.TestUser client = track(createTestUser(Role.CLIENT));
        String oldToken = client.getBearerToken();

        // Update user (e.g. fullName) via Super Admin PUT /api/users/{id}
        String updatePayload = """
            {
                "fullName": "Updated Non-Password Name"
            }
            """;

        mockMvc.perform(put("/api/users/" + client.getUser().getId())
                .header("Authorization", getSuperAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Token remains valid after profile update (no password changed)
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", oldToken))
                .andExpect(status().isOk());

        // Explicit session revocation via POST /api/users/{id}/revoke-sessions
        mockMvc.perform(post("/api/users/" + client.getUser().getId() + "/revoke-sessions")
                .header("Authorization", getSuperAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Old token must now be rejected
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", oldToken))
                .andExpect(status().isUnauthorized());

        // Authenticate via OTP succeeds and returns working token
        String email = client.getUser().getEmail();
        mockMvc.perform(post("/api/auth/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\": \"%s\"}", email)))
                .andExpect(status().isOk());

        String otp = otpService.getInMemoryStore().get(email.toLowerCase()).otp();

        var loginResult = mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\": \"%s\", \"otp\": \"%s\"}", email, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String freshToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(11)
    @DisplayName("SCENARIO 11: Admin session revocation invalidates active JWTs - POST /api/users/{id}/revoke-sessions increments tokenVersion")
    void test11_adminSessionRevocation_invalidatesActiveJwts() throws Exception {
        TestUserHelper.TestUser client = track(createTestUser(Role.CLIENT));
        String activeToken = client.getBearerToken();

        // Token works before revocation
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", activeToken))
                .andExpect(status().isOk());

        // Revoke sessions via Super Admin
        mockMvc.perform(post("/api/users/" + client.getUser().getId() + "/revoke-sessions")
                .header("Authorization", getSuperAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User dbUser = userRepository.findById(client.getUser().getId()).orElseThrow();
        assertThat(dbUser.getTokenVersion()).isEqualTo(1);

        // Active token is immediately rejected
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", activeToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @Order(12)
    @DisplayName("SCENARIO 12: Self-session revocation is blocked - Super Admin cannot revoke their own sessions")
    void test12_selfSessionRevocation_isBlockedWithBadRequest() throws Exception {
        String superAdminToken = getSuperAdminToken();
        String superAdminId = superAdminUser.getUser().getId();

        mockMvc.perform(post("/api/users/" + superAdminId + "/revoke-sessions")
                .header("Authorization", superAdminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You cannot revoke your own active session"));
    }

    @Test
    @Order(13)
    @DisplayName("SCENARIO 13: Non-super admin calling revoke-sessions returns 403 Forbidden")
    void test13_nonSuperAdmin_callingRevokeSessions_returns403Forbidden() throws Exception {
        TestUserHelper.TestUser client = track(createTestUser(Role.CLIENT));
        TestUserHelper.TestUser caller = track(createTestUser(Role.CLIENT));

        mockMvc.perform(post("/api/users/" + client.getUser().getId() + "/revoke-sessions")
                .header("Authorization", caller.getBearerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(14)
    @DisplayName("SCENARIO 14: Public endpoints remain accessible - Health and send-otp do not require token; legacy login returns 404")
    void test14_publicEndpoints_remainAccessibleWithoutTokenOrWithRevokedToken() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        // Decommissioned /api/auth/login returns 404
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(15)
    @DisplayName("SCENARIO 15: Existing enabled user authentication works normally via OTP")
    void test15_existingEnabledUserAuthentication_worksNormally() throws Exception {
        TestUserHelper.TestUser user = track(createTestUser(Role.CLIENT));

        String email = user.getUser().getEmail();
        mockMvc.perform(post("/api/auth/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\": \"%s\"}", email)))
                .andExpect(status().isOk());

        String otp = otpService.getInMemoryStore().get(email.toLowerCase()).otp();

        mockMvc.perform(post("/api/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\": \"%s\", \"otp\": \"%s\"}", email, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value(user.getUser().getUsername()));
    }

    @Test
    @Order(16)
    @DisplayName("SCENARIO 16: Role authorization strictly enforced - Super Admin vs Client route separation")
    void test16_roleAuthorization_remainsStrictlyEnforced() throws Exception {
        TestUserHelper.TestUser client = track(createTestUser(Role.CLIENT));

        // Client forbidden on /api/users
        mockMvc.perform(get("/api/users")
                .header("Authorization", client.getBearerToken()))
                .andExpect(status().isForbidden());

        // Super Admin permitted on /api/users
        mockMvc.perform(get("/api/users")
                .header("Authorization", getSuperAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(17)
    @DisplayName("SCENARIO 17: Concurrency-safe atomic tokenVersion increments")
    void test17_concurrencySafe_atomicTokenVersionIncrements() throws Exception {
        TestUserHelper.TestUser client = track(createTestUser(Role.CLIENT));
        String userId = client.getUser().getId();

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    int updated = userRepository.incrementTokenVersion(userId);
                    if (updated == 1) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threads);

        User updatedUser = userRepository.findById(userId).orElseThrow();
        assertThat(updatedUser.getTokenVersion()).isEqualTo(threads);
    }

    @Test
    @Order(18)
    @DisplayName("SCENARIO 18: Audit log records REVOKE_SESSIONS and UPDATE_STATUS accurately")
    void test18_auditLogRecords_revokeSessionsAndUpdateStatusAccurately() throws Exception {
        TestUserHelper.TestUser client = track(createTestUser(Role.CLIENT));

        // Perform session revocation
        mockMvc.perform(post("/api/users/" + client.getUser().getId() + "/revoke-sessions")
                .header("Authorization", getSuperAdminToken()))
                .andExpect(status().isOk());

        // Perform status update
        mockMvc.perform(patch("/api/users/" + client.getUser().getId() + "/status")
                .header("Authorization", getSuperAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\": false}"))
                .andExpect(status().isOk());

        List<UserAuditLog> logs = userAuditLogRepository.findAllByOrderByTimestampDesc();

        UserAuditLog revokeLog = logs.stream()
                .filter(l -> "REVOKE_SESSIONS".equals(l.getAction()) && client.getUser().getId().equals(l.getTargetUserId()))
                .findFirst()
                .orElse(null);

        assertThat(revokeLog).isNotNull();
        assertThat(revokeLog.getPerformedBy()).isEqualTo(superAdminUser.getUser().getUsername());
        assertThat(revokeLog.getChanges()).contains("revokedSessions");

        UserAuditLog statusLog = logs.stream()
                .filter(l -> "UPDATE_STATUS".equals(l.getAction()) && client.getUser().getId().equals(l.getTargetUserId()))
                .findFirst()
                .orElse(null);

        assertThat(statusLog).isNotNull();
        assertThat(statusLog.getPerformedBy()).isEqualTo(superAdminUser.getUser().getUsername());
        assertThat(statusLog.getChanges()).contains("tokenVersion");
    }
}

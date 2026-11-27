package com.sense.prolt_operations.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.BaseIntegrationTest;
import com.sense.prolt_operations.dto.auth.RefreshResult;
import com.sense.prolt_operations.dto.user.UpdateUserDTO;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.entity.UserAuditLog;
import com.sense.prolt_operations.exception.UnauthorizedException;
import com.sense.prolt_operations.repository.UserAuditLogRepository;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.service.RefreshTokenService;
import com.sense.prolt_operations.service.UserService;
import com.sense.prolt_operations.testutil.TestUserHelper;

import jakarta.servlet.http.Cookie;

@AutoConfigureMockMvc
public class UserLifecyclePrivilegeHardeningIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuditLogRepository userAuditLogRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private final List<TestUserHelper.TestUser> createdUsers = new ArrayList<>();

    private TestUserHelper.TestUser track(TestUserHelper.TestUser user) {
        createdUsers.add(user);
        return user;
    }

    @AfterEach
    void tearDown() {
        for (TestUserHelper.TestUser u : createdUsers) {
            deleteTestUser(u);
        }
        createdUsers.clear();
    }

    // =========================================================================
    // 1. User Deletion Protection Tests
    // =========================================================================

    @Test
    @DisplayName("DEL-01: Authenticated user cannot delete their own account (Self-Deletion Guard)")
    void testSelfDeletionRejected() throws Exception {
        TestUserHelper.TestUser superAdmin = track(createTestUser(Role.SUPER_ADMIN));

        mockMvc.perform(delete("/api/users/" + superAdmin.getUser().getId())
                .header("Authorization", superAdmin.getBearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You cannot delete your own account"));

        // Verify rejected audit log was recorded
        List<UserAuditLog> logs = userAuditLogRepository.findAllByOrderByTimestampDesc();
        boolean foundAudit = logs.stream().anyMatch(l ->
                "DELETE_REJECTED".equals(l.getAction()) &&
                superAdmin.getUser().getId().equals(l.getTargetUserId()) &&
                l.getChanges() != null && l.getChanges().contains("Self-deletion is prohibited"));
        assertThat(foundAudit).as("Expected DELETE_REJECTED audit log for self-deletion attempt").isTrue();
    }

    @Test
    @DisplayName("DEL-02: Non-SuperAdmin (ADMIN) attempting to delete a user receives 403 Forbidden")
    void testAdminCannotDeleteUser() throws Exception {
        TestUserHelper.TestUser admin = track(createTestUser(Role.ADMIN));
        TestUserHelper.TestUser target = track(createTestUser(Role.CLIENT));

        mockMvc.perform(delete("/api/users/" + target.getUser().getId())
                .header("Authorization", admin.getBearerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DEL-03: Deletion of the last active SUPER_ADMIN is rejected")
    void testLastActiveSuperAdminDeletionRejected() throws Exception {
        TestUserHelper.TestUser targetSuperAdmin = track(createTestUser(Role.SUPER_ADMIN));

        var otherSAs = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.SUPER_ADMIN && u.isEnabled() && !u.getId().equals(targetSuperAdmin.getUser().getId()))
                .toList();
        for (User u : otherSAs) {
            u.setEnabled(false);
            userRepository.save(u);
        }
        userRepository.flush();

        try {
            assertThat(userRepository.countByRoleAndEnabledTrue(Role.SUPER_ADMIN)).isEqualTo(1);

            org.junit.jupiter.api.Assertions.assertThrows(
                    com.sense.prolt_operations.exception.BadRequestException.class,
                    () -> userService.deleteUser(targetSuperAdmin.getUser().getId(), "system-auditor", null),
                    "Expected deletion of the last active Super Admin to throw BadRequestException"
            );
        } finally {
            for (User u : otherSAs) {
                u.setEnabled(true);
                userRepository.save(u);
            }
            userRepository.flush();
        }
    }

    @Test
    @DisplayName("DEL-04: SUPER_ADMIN can delete an eligible client or admin user")
    void testEligibleUserDeletionAllowed() throws Exception {
        TestUserHelper.TestUser superAdmin = track(createTestUser(Role.SUPER_ADMIN));
        TestUserHelper.TestUser targetClient = track(createTestUser(Role.CLIENT));

        mockMvc.perform(delete("/api/users/" + targetClient.getUser().getId())
                .header("Authorization", superAdmin.getBearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deleted successfully"));

        assertThat(userRepository.findById(targetClient.getUser().getId())).isEmpty();
    }

    @Test
    @DisplayName("DEL-05: Disabled SUPER_ADMIN can be deleted when active SUPER_ADMIN exists")
    void testDisabledSuperAdminDeletionAllowed() throws Exception {
        TestUserHelper.TestUser activeSA = track(createTestUser(Role.SUPER_ADMIN));
        TestUserHelper.TestUser disabledSA = track(createTestUser(Role.SUPER_ADMIN));

        disabledSA.getUser().setEnabled(false);
        userRepository.saveAndFlush(disabledSA.getUser());

        // Active SA deletes the disabled SA
        mockMvc.perform(delete("/api/users/" + disabledSA.getUser().getId())
                .header("Authorization", activeSA.getBearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(userRepository.findById(disabledSA.getUser().getId())).isEmpty();
        assertThat(userRepository.findById(activeSA.getUser().getId())).isPresent();
    }

    // =========================================================================
    // 2. Role and Privilege Update Tests
    // =========================================================================

    @Test
    @DisplayName("ROLE-01: Authorized role update from CLIENT to ADMIN succeeds")
    void testAuthorizedRoleUpdateSuccess() throws Exception {
        TestUserHelper.TestUser superAdmin = track(createTestUser(Role.SUPER_ADMIN));
        TestUserHelper.TestUser targetClient = track(createTestUser(Role.CLIENT));

        UpdateUserDTO updateDto = new UpdateUserDTO(null, null, "ADMIN", null, null);

        mockMvc.perform(put("/api/users/" + targetClient.getUser().getId())
                .header("Authorization", superAdmin.getBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        User updatedUser = userRepository.findById(targetClient.getUser().getId()).orElseThrow();
        assertThat(updatedUser.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("ROLE-02: Non-SuperAdmin attempting role update receives 403 Forbidden")
    void testUnauthorizedRoleUpdateForbidden() throws Exception {
        TestUserHelper.TestUser admin = track(createTestUser(Role.ADMIN));
        TestUserHelper.TestUser targetClient = track(createTestUser(Role.CLIENT));

        UpdateUserDTO updateDto = new UpdateUserDTO(null, null, "ADMIN", null, null);

        mockMvc.perform(put("/api/users/" + targetClient.getUser().getId())
                .header("Authorization", admin.getBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE-03: Demoting the last active SUPER_ADMIN to ADMIN is rejected")
    void testDemoteLastActiveSuperAdminRejected() throws Exception {
        TestUserHelper.TestUser targetSA = track(createTestUser(Role.SUPER_ADMIN));

        var otherSAs = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.SUPER_ADMIN && u.isEnabled() && !u.getId().equals(targetSA.getUser().getId()))
                .toList();
        for (User u : otherSAs) {
            u.setEnabled(false);
            userRepository.save(u);
        }
        userRepository.flush();

        try {
            assertThat(userRepository.countByRoleAndEnabledTrue(Role.SUPER_ADMIN)).isEqualTo(1);

            UpdateUserDTO demoteDto = new UpdateUserDTO(null, null, "ADMIN", null, null);

            org.junit.jupiter.api.Assertions.assertThrows(
                    com.sense.prolt_operations.exception.BadRequestException.class,
                    () -> userService.updateUser(targetSA.getUser().getId(), demoteDto, "system-auditor", null),
                    "Expected demoting the last active Super Admin to throw BadRequestException"
            );
        } finally {
            for (User u : otherSAs) {
                u.setEnabled(true);
                userRepository.save(u);
            }
            userRepository.flush();
        }
    }

    @Test
    @DisplayName("ROLE-04: Role update increments tokenVersion and revokes old access token and refresh sessions")
    void testRoleUpdateInvalidatesTokensAndSessions() throws Exception {
        TestUserHelper.TestUser superAdmin = track(createTestUser(Role.SUPER_ADMIN));
        TestUserHelper.TestUser targetUser = track(createTestUser(Role.CLIENT));

        String oldAccessToken = targetUser.getBearerToken();
        int initialVersion = targetUser.getUser().getTokenVersion();

        // 1. Target user can access protected endpoint before role change
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", oldAccessToken))
                .andExpect(status().isOk());

        // 2. Create an active refresh token session for targetUser
        RefreshResult refreshResult = refreshTokenService.createSession(targetUser.getUser(), "127.0.0.1", "TestAgent");
        String rawRefreshToken = refreshResult.refreshCookie().getValue();

        // 3. SuperAdmin changes targetUser's role to ADMIN
        UpdateUserDTO roleUpdateDto = new UpdateUserDTO(null, null, "ADMIN", null, null);
        mockMvc.perform(put("/api/users/" + targetUser.getUser().getId())
                .header("Authorization", superAdmin.getBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(roleUpdateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        // 4. Verify tokenVersion incremented in database
        User updated = userRepository.findById(targetUser.getUser().getId()).orElseThrow();
        assertThat(updated.getTokenVersion()).isEqualTo(initialVersion + 1);

        // 5. Old access token MUST now return 401 Unauthorized
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", oldAccessToken))
                .andExpect(status().isUnauthorized());

        // 6. Old refresh token session MUST be rejected
        org.junit.jupiter.api.Assertions.assertThrows(
                UnauthorizedException.class,
                () -> refreshTokenService.rotateSession(rawRefreshToken, "127.0.0.1", "TestAgent")
        );
    }

    // =========================================================================
    // 3. Module Permissions Invalidation Tests
    // =========================================================================

    @Test
    @DisplayName("MOD-01: Module permission changes increment tokenVersion and invalidate old tokens")
    void testModulePermissionChangeInvalidatesTokens() throws Exception {
        TestUserHelper.TestUser superAdmin = track(createTestUser(Role.SUPER_ADMIN));
        TestUserHelper.TestUser targetUser = track(createTestUser(Role.CLIENT));

        String oldAccessToken = targetUser.getBearerToken();
        int initialVersion = targetUser.getUser().getTokenVersion();

        // 1. Active before update
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", oldAccessToken))
                .andExpect(status().isOk());

        // 2. SuperAdmin updates hiddenModules
        UpdateUserDTO updateDto = new UpdateUserDTO(null, null, null, List.of("TARIFF_ANALYSIS"), null);
        mockMvc.perform(put("/api/users/" + targetUser.getUser().getId())
                .header("Authorization", superAdmin.getBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        // 3. Verify tokenVersion was incremented
        User updated = userRepository.findById(targetUser.getUser().getId()).orElseThrow();
        assertThat(updated.getTokenVersion()).isEqualTo(initialVersion + 1);

        // 4. Old access token rejected with 401
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", oldAccessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("MOD-02: Non-authorization profile updates (fullName, email) preserve tokenVersion and active sessions")
    void testProfileUpdatePreservesTokens() throws Exception {
        TestUserHelper.TestUser superAdmin = track(createTestUser(Role.SUPER_ADMIN));
        TestUserHelper.TestUser targetUser = track(createTestUser(Role.CLIENT));

        String originalAccessToken = targetUser.getBearerToken();
        int initialVersion = targetUser.getUser().getTokenVersion();

        // SuperAdmin updates only fullName
        UpdateUserDTO profileUpdateDto = new UpdateUserDTO(null, "Updated Full Name", null, null, null);
        mockMvc.perform(put("/api/users/" + targetUser.getUser().getId())
                .header("Authorization", superAdmin.getBearerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(profileUpdateDto)))
                .andExpect(status().isOk());

        // Verify tokenVersion did NOT change
        User updated = userRepository.findById(targetUser.getUser().getId()).orElseThrow();
        assertThat(updated.getTokenVersion()).isEqualTo(initialVersion);

        // Access token remains valid
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", originalAccessToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 4. Concurrency Safety Tests
    // =========================================================================

    @Test
    @DisplayName("CONC-01: Concurrent deletion attempts on last 2 active SUPER_ADMINs preserves at least 1")
    void testConcurrentLastAdminDeletionSafety() throws Exception {
        TestUserHelper.TestUser sa1 = track(createTestUser(Role.SUPER_ADMIN));
        TestUserHelper.TestUser sa2 = track(createTestUser(Role.SUPER_ADMIN));

        var otherSAs = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.SUPER_ADMIN && u.isEnabled() && !u.getId().equals(sa1.getUser().getId()) && !u.getId().equals(sa2.getUser().getId()))
                .toList();
        for (User u : otherSAs) {
            u.setEnabled(false);
            userRepository.save(u);
        }
        userRepository.flush();

        try {
            assertThat(userRepository.countByRoleAndEnabledTrue(Role.SUPER_ADMIN)).isEqualTo(2);

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            Runnable task1 = () -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    userService.deleteUser(sa1.getUser().getId(), "concurrency-test-1", null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            };

            Runnable task2 = () -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    userService.deleteUser(sa2.getUser().getId(), "concurrency-test-2", null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            };

            Future<?> f1 = executor.submit(task1);
            Future<?> f2 = executor.submit(task2);

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown(); // Release both threads simultaneously

            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Exactly one should succeed, and one should fail (or both fail if contention occurred)
            // CRITICAL ASSERTION: At least ONE of the two Super Admins MUST still exist and remain active!
            long remainingActiveSuperAdmins = userRepository.countByRoleAndEnabledTrue(Role.SUPER_ADMIN);
            assertThat(remainingActiveSuperAdmins).as("At least one active SUPER_ADMIN must be preserved").isGreaterThanOrEqualTo(1);
        } finally {
            for (User u : otherSAs) {
                u.setEnabled(true);
                userRepository.save(u);
            }
            userRepository.flush();
        }
    }
}

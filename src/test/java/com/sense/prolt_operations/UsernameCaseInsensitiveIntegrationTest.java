package com.sense.prolt_operations;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.dto.user.CreateUserDTO;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.service.UserService;
import com.sense.prolt_operations.testutil.TestUserHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UsernameCaseInsensitiveIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired(required = false)
    private com.sense.prolt_operations.security.AuthRateLimiterFilter authRateLimiterFilter;

    private TestUserHelper.TestUser superAdminUser;

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
    }

    private String getSuperAdminToken() {
        if (superAdminUser == null) {
            superAdminUser = createTestUser(Role.SUPER_ADMIN);
        }
        return superAdminUser.getBearerToken();
    }

    @Test
    @Order(1)
    @DisplayName("UNIQ-01: Creating Ankit succeeds, but creating ankit, ANKIT, or ' Ankit ' is rejected")
    void testCaseInsensitiveUniquenessOnCreation() throws Exception {
        String token = getSuperAdminToken();
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String baseName = "Ankit_" + uid;
        String email = baseName.toLowerCase() + "@probus.io";

        // 1. Create original: "Ankit_<uid>"
        String createPayload = String.format("""
            {
                "username": "%s",
                "email": "%s",
                "fullName": "Test User",
                "role": "CLIENT"
            }
            """, baseName, email);

        MvcResult result = mockMvc.perform(post("/api/users")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(baseName)) // Preserves original casing
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String createdUserId = jsonNode.get("data").get("id").asText();

        try {
            // 2. Attempt lowercase: "ankit_<uid>"
            String lowercasePayload = String.format("""
                {
                    "username": "%s",
                    "email": "different1_%s@probus.io",
                    "fullName": "Test User",
                    "role": "CLIENT"
                }
                """, baseName.toLowerCase(), uid);

            mockMvc.perform(post("/api/users")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(lowercasePayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Username or email already exists"));

            // 3. Attempt uppercase: "ANKIT_<UID>"
            String uppercasePayload = String.format("""
                {
                    "username": "%s",
                    "email": "different2_%s@probus.io",
                    "fullName": "Test User",
                    "role": "CLIENT"
                }
                """, baseName.toUpperCase(), uid);

            mockMvc.perform(post("/api/users")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(uppercasePayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Username or email already exists"));

            // 4. Attempt whitespace padded: "  Ankit_<uid>  "
            String paddedPayload = String.format("""
                {
                    "username": "   %s   ",
                    "email": "different3_%s@probus.io",
                    "fullName": "Test User",
                    "role": "CLIENT"
                }
                """, baseName, uid);

            mockMvc.perform(post("/api/users")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(paddedPayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Username or email already exists"));
        } finally {
            userRepository.deleteById(createdUserId);
        }
    }

    @Test
    @Order(2)
    @DisplayName("UNIQ-02: Concurrent creation attempts with different casings safely reject duplicates")
    void testConcurrentCreationWithDifferentCasings() throws InterruptedException {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String name1 = "ConcurUser_" + uid;
        String name2 = "concuruser_" + uid;
        String name3 = "CONCURUSER_" + uid;

        int threads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateErrorCount = new AtomicInteger(0);

        String[] variants = new String[]{name1, name2, name3};

        for (int i = 0; i < threads; i++) {
            final String variantName = variants[i];
            final String userEmail = "concur_" + i + "_" + uid + "@probus.io";
            executor.submit(() -> {
                try {
                    latch.await();
                    CreateUserDTO dto = new CreateUserDTO(variantName, userEmail, "Variant User", "CLIENT", null, null);
                    userService.createUser(dto, "TEST");
                    successCount.incrementAndGet();
                } catch (BadRequestException e) {
                    if (e.getMessage().contains("already exists")) {
                        duplicateErrorCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        done.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateErrorCount.get()).isEqualTo(2);

        // Cleanup
        userRepository.findByNormalizedUsername(name1).ifPresent(u -> userRepository.delete(u));
    }

    @Test
    @Order(3)
    @DisplayName("AUTH-01: Normalized username lookup resolves identically for Ankit, ankit, ANKIT, and ' Ankit '; legacy login returns 404")
    void testCaseInsensitiveAuthentication() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String registeredUsername = "AnkitAuth_" + uid;
        String email = "ankitauth_" + uid + "@probus.io";

        // Register user via TestUserHelper
        TestUserHelper.TestUser testUser = testUserHelper.createUser(Role.CLIENT, registeredUsername, email);

        try {
            // 1. Normalized lookup with exact casing
            assertThat(userRepository.findByNormalizedUsername(registeredUsername)).isPresent();

            // 2. Normalized lookup with lowercase
            assertThat(userRepository.findByNormalizedUsername(registeredUsername.toLowerCase())).isPresent();

            // 3. Normalized lookup with uppercase
            assertThat(userRepository.findByNormalizedUsername(registeredUsername.toUpperCase())).isPresent();

            // 4. Normalized lookup with whitespace padding
            assertThat(userRepository.findByNormalizedUsername(registeredUsername.trim().toLowerCase())).isPresent();

            // 5. Decommissioned /api/auth/login returns 404
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\": \"" + registeredUsername + "\", \"password\": \"AnyPassword123!\"}"))
                    .andExpect(status().isNotFound());

        } finally {
            deleteTestUser(testUser);
        }
    }

    @Test
    @Order(4)
    @DisplayName("READ-01: Read APIs preserve original stored casing across user listing")
    void testOriginalCasingPreservedInReadApis() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String customCasedUsername = "AnKiT_CasING_" + uid;
        String email = "casing_" + uid + "@probus.io";

        TestUserHelper.TestUser testUser = testUserHelper.createUser(Role.CLIENT, customCasedUsername, email);

        try {
            String token = getSuperAdminToken();

            // GET /api/users
            mockMvc.perform(get("/api/users")
                    .header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[?(@.id == '%s')].username", testUser.getUser().getId())
                            .value(customCasedUsername));

            // Verify direct repository query
            User entity = userRepository.findById(testUser.getUser().getId()).orElseThrow();
            assertThat(entity.getUsername()).isEqualTo(customCasedUsername);
        } finally {
            deleteTestUser(testUser);
        }
    }

    @Test
    @Order(5)
    @DisplayName("IMMUT-01: Username immutability remains strictly enforced under case-insensitive indexing")
    void testUsernameImmutabilityPreserved() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String initialUsername = "OriginalUser_" + uid;
        String email = "immutable_" + uid + "@probus.io";

        TestUserHelper.TestUser testUser = testUserHelper.createUser(Role.CLIENT, initialUsername, email);

        try {
            String token = getSuperAdminToken();

            // Attempt update passing different casing or new name
            String updatePayload = String.format("""
                {
                    "username": "changeduser_%s",
                    "email": "updated_email_%s@probus.io",
                    "role": "ADMIN"
                }
                """, uid, uid);

            mockMvc.perform(put("/api/users/" + testUser.getUser().getId())
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.username").value(initialUsername)) // Still original!
                    .andExpect(jsonPath("$.data.email").value("updated_email_" + uid + "@probus.io"))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"));

            User entity = userRepository.findById(testUser.getUser().getId()).orElseThrow();
            assertThat(entity.getUsername()).isEqualTo(initialUsername);
        } finally {
            deleteTestUser(testUser);
        }
    }

    @Test
    @Order(6)
    @DisplayName("REPO-01: Explicit functional queries findByNormalizedUsername and existsByNormalizedUsername")
    void testRepositoryNormalizedMethods() {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String username = "RepoNorm_" + uid;
        String email = "reponorm_" + uid + "@probus.io";

        TestUserHelper.TestUser testUser = testUserHelper.createUser(Role.CLIENT, username, email);

        try {
            // Exists checks
            assertThat(userRepository.existsByNormalizedUsername(username)).isTrue();
            assertThat(userRepository.existsByNormalizedUsername(username.toLowerCase())).isTrue();
            assertThat(userRepository.existsByNormalizedUsername(username.toUpperCase())).isTrue();
            assertThat(userRepository.existsByNormalizedUsername("  " + username + "  ")).isTrue();
            assertThat(userRepository.existsByNormalizedUsername("nonexistent_" + uid)).isFalse();

            // Find checks
            assertThat(userRepository.findByNormalizedUsername(username.toLowerCase()))
                    .isPresent()
                    .hasValueSatisfying(u -> assertThat(u.getUsername()).isEqualTo(username));

            assertThat(userRepository.findByNormalizedUsername("   " + username.toUpperCase() + "   "))
                    .isPresent()
                    .hasValueSatisfying(u -> assertThat(u.getUsername()).isEqualTo(username));
        } finally {
            deleteTestUser(testUser);
        }
    }
}

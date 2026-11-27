package com.sense.prolt_operations;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.sense.prolt_operations.service.OtpService;
import com.sense.prolt_operations.service.UserService;
import com.sense.prolt_operations.testutil.TestUserHelper;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EmailAndFullNameIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private OtpService otpService;

    @Autowired(required = false)
    private com.sense.prolt_operations.security.AuthRateLimiterFilter authRateLimiterFilter;

    private TestUserHelper.TestUser superAdminUser;

    @BeforeEach
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
    @DisplayName("EMAIL-01: Case-insensitive email uniqueness on user creation (mixed-case, uppercase, whitespace-padded)")
    void testCaseInsensitiveEmailUniquenessOnCreation() throws Exception {
        String token = getSuperAdminToken();
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String username = "unique_user_" + uid;
        String baseEmail = "Alice." + uid + "@Probus.IO";

        // 1. Create original with mixed-case: Alice.<uid>@Probus.IO
        String createPayload = String.format("""
            {
                "username": "%s",
                "email": "%s",
                "fullName": "Alice Wonderland",
                "role": "CLIENT"
            }
            """, username, baseEmail);

        MvcResult result = mockMvc.perform(post("/api/users")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(baseEmail.trim().toLowerCase())) // Normalized email stored
                .andExpect(jsonPath("$.data.fullName").value("Alice Wonderland"))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String createdUserId = jsonNode.get("data").get("id").asText();

        try {
            // 2. Attempt lowercase duplicate: alice.<uid>@probus.io
            String lowercasePayload = String.format("""
                {
                    "username": "diff1_%s",
                    "email": "%s",
                    "fullName": "Alice Duplicate",
                    "role": "CLIENT"
                }
                """, uid, baseEmail.toLowerCase());

            mockMvc.perform(post("/api/users")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(lowercasePayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Username or email already exists"));

            // 3. Attempt uppercase duplicate: ALICE.<UID>@PROBUS.IO
            String uppercasePayload = String.format("""
                {
                    "username": "diff2_%s",
                    "email": "%s",
                    "fullName": "Alice Uppercase",
                    "role": "CLIENT"
                }
                """, uid, baseEmail.toUpperCase());

            mockMvc.perform(post("/api/users")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(uppercasePayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Username or email already exists"));

            // 4. Attempt whitespace-padded duplicate: "   Alice.<uid>@Probus.IO   "
            String paddedPayload = String.format("""
                {
                    "username": "diff3_%s",
                    "email": "   %s   ",
                    "fullName": "Alice Padded",
                    "role": "CLIENT"
                }
                """, uid, baseEmail);

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
    @DisplayName("EMAIL-02: Concurrent user creation with different casings of same email permits only one winner")
    void testConcurrentCreationWithDifferentEmailCasings() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String email1 = "Racer_" + uid + "@probus.io";
        String email2 = "racer_" + uid + "@probus.io";
        String email3 = "RACER_" + uid + "@PROBUS.IO";

        int threads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateErrorCount = new AtomicInteger(0);

        String[] emailVariants = new String[]{email1, email2, email3};

        for (int i = 0; i < threads; i++) {
            final String variantEmail = emailVariants[i];
            final String userName = "racer_usr_" + i + "_" + uid;
            executor.submit(() -> {
                try {
                    latch.await();
                    CreateUserDTO dto = new CreateUserDTO(userName, variantEmail, "Racer User", "CLIENT", null, null);
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

        assertThat(successCount.get())
                .as("Exactly one thread should succeed creating user with same normalized email")
                .isEqualTo(1);
        assertThat(duplicateErrorCount.get())
                .as("Other threads should receive 'already exists' exception")
                .isEqualTo(2);

        // Clean up created user
        userRepository.findByNormalizedEmail("racer_" + uid + "@probus.io")
                .ifPresent(u -> userRepository.delete(u));
    }

    @Test
    @Order(3)
    @DisplayName("EMAIL-03: Case-insensitive email authentication login via OTP and 404 for decommissioned /api/auth/login")
    void testCaseInsensitiveEmailLogin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String registeredUsername = "auth_user_" + uid;
        String registeredEmail = "Auth.User." + uid + "@Probus.IO";

        // Register user through service without password
        CreateUserDTO dto = new CreateUserDTO(registeredUsername, registeredEmail, "Auth User Full", "CLIENT", null, null);
        userService.createUser(dto, "TEST");

        try {
            // 1. Send OTP and verify with lowercase email
            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\"}", registeredEmail.toLowerCase())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            String otp1 = otpService.getInMemoryStore().get(registeredEmail.toLowerCase()).otp();

            mockMvc.perform(post("/api/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\", \"otp\": \"%s\"}", registeredEmail.toLowerCase(), otp1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.user.username").value(registeredUsername))
                    .andExpect(jsonPath("$.user.email").value(registeredEmail.toLowerCase()))
                    .andExpect(jsonPath("$.user.fullName").value("Auth User Full"))
                    .andExpect(jsonPath("$.token").isString());

            // 2. Send OTP and verify with uppercase email
            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\"}", registeredEmail.toUpperCase())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            String otp2 = otpService.getInMemoryStore().get(registeredEmail.toLowerCase()).otp();

            mockMvc.perform(post("/api/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"%s\", \"otp\": \"%s\"}", registeredEmail.toUpperCase(), otp2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.user.username").value(registeredUsername));

            // 3. Send OTP and verify with padded email: "  Auth.User.<uid>@Probus.IO  "
            mockMvc.perform(post("/api/auth/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"   %s   \"}", registeredEmail)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            String otp3 = otpService.getInMemoryStore().get(registeredEmail.toLowerCase()).otp();

            mockMvc.perform(post("/api/auth/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"email\": \"   %s   \", \"otp\": \"%s\"}", registeredEmail, otp3)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.user.username").value(registeredUsername));

            // 4. Verify decommissioned /api/auth/login returns 404 Not Found
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"username\": \"%s\", \"password\": \"AnyPass123!\"}", registeredEmail)))
                    .andExpect(status().isNotFound());

        } finally {
            userRepository.findByNormalizedUsername(registeredUsername)
                    .ifPresent(u -> userRepository.delete(u));
        }
    }

    @Test
    @Order(4)
    @DisplayName("EMAIL-04: Direct email update without verification; duplicate email update is rejected")
    void testDirectEmailUpdateAndDuplicateRejection() throws Exception {
        String token = getSuperAdminToken();
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String user1Username = "update_u1_" + uid;
        String user1Email = "user1_" + uid + "@probus.io";
        String user2Username = "update_u2_" + uid;
        String user2Email = "user2_" + uid + "@probus.io";

        CreateUserDTO u1 = new CreateUserDTO(user1Username, user1Email, "User One", "CLIENT", null, null);
        CreateUserDTO u2 = new CreateUserDTO(user2Username, user2Email, "User Two", "CLIENT", null, null);
        var res1 = userService.createUser(u1, "TEST");
        var res2 = userService.createUser(u2, "TEST");

        try {
            // 1. Attempt to update User 2's email to User 1's email (in uppercase): USER1_<UID>@PROBUS.IO
            String dupUpdatePayload = String.format("""
                {
                    "email": "%s"
                }
                """, user1Email.toUpperCase());

            mockMvc.perform(put("/api/users/" + res2.id())
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(dupUpdatePayload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Username or email already exists"));

            // 2. Update User 2's email to a new unique mixed-case email: New.Email.<uid>@Probus.IO
            String newEmail = "New.Email." + uid + "@Probus.IO";
            String validUpdatePayload = String.format("""
                {
                    "email": "%s"
                }
                """, newEmail);

            mockMvc.perform(put("/api/users/" + res2.id())
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validUpdatePayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.email").value(newEmail.toLowerCase()))
                    .andExpect(jsonPath("$.data.username").value(user2Username)); // Username preserved

            // Direct DB check
            User user2InDb = userRepository.findById(res2.id()).orElseThrow();
            assertThat(user2InDb.getEmail()).isEqualTo(newEmail.toLowerCase());
            assertThat(user2InDb.getUsername()).isEqualTo(user2Username);

        } finally {
            userRepository.deleteById(res1.id());
            userRepository.deleteById(res2.id());
        }
    }

    @Test
    @Order(5)
    @DisplayName("NAME-01: FullName validation (required on create, non-blank, max 100 chars, trimmed)")
    void testFullNameValidationOnCreate() throws Exception {
        String token = getSuperAdminToken();
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        // 1. Missing / null fullName
        String missingNamePayload = String.format("""
            {
                "username": "no_name_%s",
                "email": "no_name_%s@probus.io",
                "role": "CLIENT"
            }
            """, uid, uid);

        mockMvc.perform(post("/api/users")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(missingNamePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Full name is required"));

        // 2. Blank / whitespace-only fullName
        String blankNamePayload = String.format("""
            {
                "username": "blank_name_%s",
                "email": "blank_name_%s@probus.io",
                "fullName": "     ",
                "role": "CLIENT"
            }
            """, uid, uid);

        mockMvc.perform(post("/api/users")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(blankNamePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Full name is required"));

        // 3. Exceeds 100 characters
        String longName = "A".repeat(101);
        String longNamePayload = String.format("""
            {
                "username": "long_name_%s",
                "email": "long_name_%s@probus.io",
                "fullName": "%s",
                "role": "CLIENT"
            }
            """, uid, uid, longName);

        mockMvc.perform(post("/api/users")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(longNamePayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Full name must not exceed 100 characters"));

        // 4. Valid fullName with padding is trimmed on persist
        String validPaddedPayload = String.format("""
            {
                "username": "padded_name_%s",
                "email": "padded_name_%s@probus.io",
                "fullName": "   Johnathan Doe   ",
                "role": "CLIENT"
            }
            """, uid, uid);

        MvcResult res = mockMvc.perform(post("/api/users")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validPaddedPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fullName").value("Johnathan Doe"))
                .andReturn();

        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        String id = node.get("data").get("id").asText();
        userRepository.deleteById(id);
    }

    @Test
    @Order(6)
    @DisplayName("NAME-02: FullName editing via update API and non-unique fullNames permitted")
    void testFullNameUpdateAndNonUniqueness() throws Exception {
        String token = getSuperAdminToken();
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);

        // Create 2 users with identical full names (Allowed!)
        CreateUserDTO u1 = new CreateUserDTO("user_a_" + uid, "usera_" + uid + "@probus.io", "Same Common Name", "CLIENT", null, null);
        CreateUserDTO u2 = new CreateUserDTO("user_b_" + uid, "userb_" + uid + "@probus.io", "Same Common Name", "CLIENT", null, null);
        var res1 = userService.createUser(u1, "TEST");
        var res2 = userService.createUser(u2, "TEST");

        try {
            assertThat(res1.fullName()).isEqualTo("Same Common Name");
            assertThat(res2.fullName()).isEqualTo("Same Common Name");

            // Update user 1's full name
            String updateNamePayload = """
                {
                    "fullName": "  Updated Name For Alice  "
                }
                """;

            mockMvc.perform(put("/api/users/" + res1.id())
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updateNamePayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.fullName").value("Updated Name For Alice"));

            // Verify in database
            User user1InDb = userRepository.findById(res1.id()).orElseThrow();
            assertThat(user1InDb.getFullName()).isEqualTo("Updated Name For Alice");

        } finally {
            userRepository.deleteById(res1.id());
            userRepository.deleteById(res2.id());
        }
    }

    @Test
    @Order(7)
    @DisplayName("NAME-03: Legacy user compatibility (null fullName returns null in DTO and falls back to username for name property)")
    void testLegacyUserNullFullNameCompatibility() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String legacyUsername = "legacy_user_" + uid;
        String legacyEmail = "legacy_" + uid + "@probus.io";

        // Save directly via repository simulating pre-existing user
        User legacy = User.create();
        legacy.setUsername(legacyUsername);
        legacy.setEmail(legacyEmail);
        legacy.setFullName(legacyUsername); // In V10+ fullName is NOT NULL in DB
        legacy.setRole(Role.CLIENT);
        legacy = userRepository.saveAndFlush(legacy);

        try {
            // 1. GET /api/users exposes fullName, does not crash
            String token = getSuperAdminToken();
            mockMvc.perform(get("/api/users")
                    .header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.id == '%s')].fullName", legacy.getId())
                            .value(org.hamcrest.Matchers.contains(legacyUsername)))
                    .andExpect(jsonPath("$.data[?(@.id == '%s')].username", legacy.getId())
                            .value(legacyUsername));

            // 2. AuthUserResponse fallback for "name": when fullName is null, name returns username
            var authResp = new com.sense.prolt_operations.dto.auth.AuthUserResponse(
                    legacy.getId(),
                    legacy.getUsername(),
                    legacy.getEmail(),
                    null,
                    legacy.getRole().name(),
                    java.util.Collections.emptyList(),
                    java.util.Collections.emptyList()
            );
            assertThat(authResp.fullName()).isNull();
            assertThat(authResp.name()).isEqualTo(legacyUsername); // Backward-compatible name getter

        } finally {
            userRepository.deleteById(legacy.getId());
        }
    }

    @Test
    @Order(8)
    @DisplayName("OTP-01: OTP generation and verification with mixed-case and normalized email")
    void testOtpGenerationAndVerificationWithMixedCaseEmail() {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String email = "Otp.User." + uid + "@Probus.IO";
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);

        // Pre-create user in repository
        User user = User.create();
        user.setUsername("otp_test_" + uid);
        user.setEmail(normalizedEmail);
        user.setFullName("OTP User");
        user.setRole(Role.CLIENT);
        userRepository.save(user);

        try {
            // Generate OTP with mixed case
            otpService.generateAndSendOtp(email);

            // In memory fallback store contains normalized email
            assertThat(otpService.getInMemoryStore()).containsKey(normalizedEmail);
            String otp = otpService.getInMemoryStore().get(normalizedEmail).otp();
            assertThat(otp).isNotBlank();

            // Verify with uppercase email
            boolean verified = otpService.verifyOtp(email.toUpperCase(), otp);
            assertThat(verified).isTrue();

            // Single use: second attempt fails
            boolean secondAttempt = otpService.verifyOtp(normalizedEmail, otp);
            assertThat(secondAttempt).isFalse();

        } finally {
            userRepository.delete(user);
        }
    }
}

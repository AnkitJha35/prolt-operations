package com.sense.prolt_operations;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
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
import com.sense.prolt_operations.dto.user.UpdateUserDTO;
import com.sense.prolt_operations.dto.user.UserDTO;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.testutil.TestUserHelper;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UsernameImmutabilityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private TestUserHelper.TestUser superAdminUser;

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
    @DisplayName("IMMUT-01: UpdateUserDTO must NOT contain username field or record component")
    void testUsernameAbsentFromUpdateDTO() {
        RecordComponent[] components = UpdateUserDTO.class.getRecordComponents();
        List<String> componentNames = Arrays.stream(components)
                .map(RecordComponent::getName)
                .toList();

        assertThat(componentNames)
                .as("UpdateUserDTO must not include username")
                .doesNotContain("username");

        assertThat(componentNames)
                .as("UpdateUserDTO should only define updatable user fields")
                .containsExactlyInAnyOrder("email", "fullName", "role", "hiddenModules", "readOnlyModules");
    }

    @Test
    @Order(2)
    @DisplayName("IMMUT-02: User entity username column must be configured with updatable = false")
    void testUserEntityUsernameIsUpdatableFalse() throws NoSuchFieldException {
        Field usernameField = User.class.getDeclaredField("username");
        Column columnAnnotation = usernameField.getAnnotation(Column.class);

        assertThat(columnAnnotation).isNotNull();
        assertThat(columnAnnotation.updatable())
                .as("User.username column must be marked updatable = false")
                .isFalse();
        assertThat(columnAnnotation.nullable())
                .as("User.username column must be non-nullable")
                .isFalse();
        assertThat(columnAnnotation.unique())
                .as("User.username column must be unique")
                .isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("IMMUT-03: Read DTOs and endpoints must expose username as visible and read-only")
    void testUsernameExposedInReadEndpoints() throws Exception {
        TestUserHelper.TestUser testUser = createTestUser(Role.ADMIN);
        try {
            // 1. GET /api/auth/me exposes username
            mockMvc.perform(get("/api/auth/me")
                    .header("Authorization", testUser.getBearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.user.username").value(testUser.getUser().getUsername()));

            // 2. GET /api/users exposes username in list
            String token = getSuperAdminToken();
            mockMvc.perform(get("/api/users")
                    .header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[?(@.id == '%s')].username", testUser.getUser().getId())
                            .value(testUser.getUser().getUsername()));
        } finally {
            deleteTestUser(testUser);
        }
    }

    @Test
    @Order(4)
    @DisplayName("IMMUT-04: Submitting username in update API is safely ignored and username remains unchanged")
    void testAdminUpdateIgnoresUsernameInPayload() throws Exception {
        String token = getSuperAdminToken();
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String initialUsername = "user_orig_" + uniqueSuffix;
        String initialEmail = initialUsername + "@probus.io";

        // Create user
        String createPayload = String.format("""
            {
                "username": "%s",
                "email": "%s",
                "fullName": "Original User",
                "role": "CLIENT"
            }
            """, initialUsername, initialEmail);

        MvcResult createResult = mockMvc.perform(post("/api/users")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value(initialUsername))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String createdUserId = jsonNode.get("data").get("id").asText();

        try {
            // Attempt to update username along with email
            String updatedEmail = "user_updated_" + uniqueSuffix + "@probus.io";
            String updatePayloadWithUsernameAttempt = String.format("""
                {
                    "username": "hacked_username_%s",
                    "email": "%s",
                    "role": "ADMIN"
                }
                """, uniqueSuffix, updatedEmail);

            mockMvc.perform(put("/api/users/" + createdUserId)
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayloadWithUsernameAttempt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.username").value(initialUsername)) // Must remain original!
                    .andExpect(jsonPath("$.data.email").value(updatedEmail))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"));

            // Verify database state directly
            User persistedUser = userRepository.findById(createdUserId).orElseThrow();
            assertThat(persistedUser.getUsername())
                    .as("Stored username in database must not change after update attempt")
                    .isEqualTo(initialUsername);
            assertThat(persistedUser.getEmail())
                    .as("Email must update successfully")
                    .isEqualTo(updatedEmail);
        } finally {
            userRepository.deleteById(createdUserId);
        }
    }

    @Test
    @Order(5)
    @DisplayName("IMMUT-05: Updating email, role, or modules preserves existing username")
    void testUsernamePreservedWhenUpdatingOtherFields() throws Exception {
        String token = getSuperAdminToken();
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String initialUsername = "fixed_user_" + uniqueSuffix;
        String initialEmail = initialUsername + "@probus.io";

        // Create user
        String createPayload = String.format("""
            {
                "username": "%s",
                "email": "%s",
                "fullName": "Fixed User",
                "role": "CLIENT",
                "hiddenModules": ["mod1"],
                "readOnlyModules": ["mod2"]
            }
            """, initialUsername, initialEmail);

        MvcResult createResult = mockMvc.perform(post("/api/users")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String createdUserId = jsonNode.get("data").get("id").asText();

        try {
            // Update other fields
            String newEmail = "updated_" + uniqueSuffix + "@probus.io";
            String updatePayload = String.format("""
                {
                    "email": "%s",
                    "role": "ADMIN",
                    "hiddenModules": ["mod3", "mod4"],
                    "readOnlyModules": ["mod5"]
                }
                """, newEmail);

            mockMvc.perform(put("/api/users/" + createdUserId)
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.username").value(initialUsername))
                    .andExpect(jsonPath("$.data.email").value(newEmail))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"))
                    .andExpect(jsonPath("$.data.hiddenModules[0]").value("mod3"))
                    .andExpect(jsonPath("$.data.readOnlyModules[0]").value("mod5"));

            // Verify in database
            User persisted = userRepository.findById(createdUserId).orElseThrow();
            assertThat(persisted.getUsername()).isEqualTo(initialUsername);
            assertThat(persisted.getEmail()).isEqualTo(newEmail);
            assertThat(persisted.getRole()).isEqualTo(Role.ADMIN);
        } finally {
            userRepository.deleteById(createdUserId);
        }
    }
}

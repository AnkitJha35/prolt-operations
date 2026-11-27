package com.sense.prolt_operations.security.state;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.BaseIntegrationTest;
import com.sense.prolt_operations.dto.user.UpdateUserStatusDTO;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.exception.SecurityStateUnavailableException;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.repository.UserSecurityState;
import com.sense.prolt_operations.service.UserService;
import com.sense.prolt_operations.testutil.TestUserHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecurityStateProviderIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserSecurityStateProvider activeProvider;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("PROVIDER-01: Local profile starts cleanly without Redis using DatabaseSecurityStateProvider")
    void test01_localProfile_startsWithoutRedis() {
        // Assert the active integration test provider (default/local) is DatabaseSecurityStateProvider
        assertThat(activeProvider).isInstanceOf(DatabaseSecurityStateProvider.class);

        // Also assert via isolated ApplicationContextRunner with dummy Redis config and database provider
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityStateConfig.class))
                .withPropertyValues(
                        "app.security-state.provider=database",
                        "spring.data.redis.url=redis://non-existent-host:9999"
                )
                .withBean(UserRepository.class, () -> Mockito.mock(UserRepository.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(UserSecurityStateProvider.class);
                    assertThat(context.getBean(UserSecurityStateProvider.class))
                            .isInstanceOf(DatabaseSecurityStateProvider.class);
                });
    }

    @Test
    @Order(2)
    @DisplayName("PROVIDER-02: Local profile authenticates using database-backed security-state validation")
    void test02_localProfile_authenticatesUsingDatabaseValidation() throws Exception {
        TestUserHelper.TestUser client = createTestUser(Role.CLIENT);
        try {
            mockMvc.perform(get("/api/datasets/summary")
                    .header("Authorization", client.getBearerToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(3)
    @DisplayName("PROVIDER-03: Local account disable invalidates existing JWTs immediately")
    void test03_localProfile_accountDisable_invalidatesExistingJwt() throws Exception {
        TestUserHelper.TestUser client = createTestUser(Role.CLIENT);
        try {
            // Active before disable
            mockMvc.perform(get("/api/datasets/summary")
                    .header("Authorization", client.getBearerToken()))
                    .andExpect(status().isOk());

            // Disable account via UserService
            userService.updateUserStatus(client.getUser().getId(), new UpdateUserStatusDTO(false), "admin", null);

            // Active token immediately rejected on protected endpoint
            mockMvc.perform(get("/api/datasets/summary")
                    .header("Authorization", client.getBearerToken()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Account is disabled. Please contact administrator."));
        } finally {
            deleteTestUser(client);
        }
    }

    @Test
    @Order(4)
    @DisplayName("PROVIDER-04: Production profile configures RedisSecurityStateProvider")
    void test04_productionProfile_usesRedisProvider() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityStateConfig.class))
                .withPropertyValues(
                        "app.security-state.provider=redis",
                        "spring.data.redis.url=redis://localhost:6379"
                )
                .withBean(StringRedisTemplate.class, () -> Mockito.mock(StringRedisTemplate.class))
                .withBean(UserRepository.class, () -> Mockito.mock(UserRepository.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(UserSecurityStateProvider.class);
                    assertThat(context.getBean(UserSecurityStateProvider.class))
                            .isInstanceOf(RedisSecurityStateProvider.class);
                });
    }

    @Test
    @Order(5)
    @DisplayName("PROVIDER-05: Production Redis failure rejects protected requests (fail-closed, no DB fallback)")
    @SuppressWarnings("unchecked")
    void test05_productionRedisFailure_rejectsProtectedRequests_failClosed() {
        StringRedisTemplate mockRedis = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> mockOps = Mockito.mock(ValueOperations.class);
        when(mockRedis.opsForValue()).thenReturn(mockOps);

        UserRepository mockRepo = Mockito.mock(UserRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        when(mockOps.get(anyString())).thenThrow(new RedisConnectionFailureException("Simulated Redis Connection Loss"));

        RedisSecurityStateProvider redisProvider = new RedisSecurityStateProvider(mockRedis, mockRepo, mapper);

        // Assert: Must fail closed by throwing SecurityStateUnavailableException
        assertThatThrownBy(() -> redisProvider.getSecurityState("user-123"))
                .isInstanceOf(SecurityStateUnavailableException.class)
                .hasMessageContaining("Redis security state cache is unavailable");

        // Assert: PostgreSQL was NEVER called as a silent fallback!
        verify(mockRepo, never()).findSecurityStateById(anyString());
    }

    @Test
    @Order(6)
    @DisplayName("PROVIDER-06: Redis cache hit avoids PostgreSQL lookup")
    @SuppressWarnings("unchecked")
    void test06_redisCacheHit_avoidsPostgreSQL() throws Exception {
        StringRedisTemplate mockRedis = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> mockOps = Mockito.mock(ValueOperations.class);
        when(mockRedis.opsForValue()).thenReturn(mockOps);

        UserRepository mockRepo = Mockito.mock(UserRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        UserSecurityState cachedState = new UserSecurityState("u-hit", "hit_user", Role.CLIENT, true, 2);
        String cachedJson = mapper.writeValueAsString(cachedState);

        when(mockOps.get("user:security-state:u-hit")).thenReturn(cachedJson);

        RedisSecurityStateProvider redisProvider = new RedisSecurityStateProvider(mockRedis, mockRepo, mapper);

        Optional<UserSecurityState> result = redisProvider.getSecurityState("u-hit");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("u-hit");
        assertThat(result.get().tokenVersion()).isEqualTo(2);
        assertThat(result.get().enabled()).isTrue();

        // Critical requirement: PostgreSQL is NOT queried on cache hit!
        verify(mockRepo, never()).findSecurityStateById(anyString());
    }

    @Test
    @Order(7)
    @DisplayName("PROVIDER-07: Redis cache miss loads PostgreSQL state and populates Redis atomically")
    @SuppressWarnings("unchecked")
    void test07_redisCacheMiss_loadsPostgreSQLStateAndPopulatesRedis() {
        StringRedisTemplate mockRedis = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> mockOps = Mockito.mock(ValueOperations.class);
        when(mockRedis.opsForValue()).thenReturn(mockOps);

        UserRepository mockRepo = Mockito.mock(UserRepository.class);
        ObjectMapper mapper = new ObjectMapper();

        // Cache miss: returns null
        when(mockOps.get("user:security-state:u-miss")).thenReturn(null);

        UserSecurityState dbState = new UserSecurityState("u-miss", "miss_user", Role.CLIENT, true, 1);
        when(mockRepo.findSecurityStateById("u-miss")).thenReturn(Optional.of(dbState));

        RedisSecurityStateProvider redisProvider = new RedisSecurityStateProvider(mockRedis, mockRepo, mapper);

        Optional<UserSecurityState> result = redisProvider.getSecurityState("u-miss");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("u-miss");
        assertThat(result.get().tokenVersion()).isEqualTo(1);

        // Verify DB was called on miss
        verify(mockRepo).findSecurityStateById("u-miss");

        // Verify Redis was populated via atomic Lua script execution
        verify(mockRedis).execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    @Order(8)
    @DisplayName("PROVIDER-08: Configuration validation fails application startup on invalid provider or missing Redis URL")
    void test08_providerSelectionValidation_failsStartupOnInvalidConfig() {
        // Test A: Invalid provider name fails startup
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityStateConfig.class))
                .withPropertyValues("app.security-state.provider=unsupported_provider")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasRootCauseMessage("Invalid configuration: 'app.security-state.provider' must be either 'database' or 'redis', but was 'unsupported_provider'");
                });

        // Test B: Redis provider with missing Redis URL fails startup
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SecurityStateConfig.class))
                .withPropertyValues(
                        "app.security-state.provider=redis",
                        "spring.data.redis.url=",
                        "app.redis-url="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable rootCause = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure());
                    assertThat(rootCause)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("requires a valid Redis URL configured");
                });
    }
}

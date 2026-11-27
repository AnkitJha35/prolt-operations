package com.sense.prolt_operations.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class DistributedOtpRateLimiterKeyTest {

    @Nested
    @DisplayName("Production Environment HMAC Key Validation")
    class ProductionEnvironmentTests {

        private MockEnvironment createProdEnv() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("prod");
            return env;
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        @DisplayName("validateConfiguration() throws IllegalStateException in production when key is null or blank")
        void testStartupValidationFailsWhenKeyMissingOrBlank(String key) {
            RateLimitProperties props = new RateLimitProperties();
            props.setAccountHmacKey(key);

            DistributedOtpRateLimiter limiter = new DistributedOtpRateLimiter(props, createProdEnv(), null);

            assertThatThrownBy(limiter::validateConfiguration)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RATE_LIMIT_HMAC_KEY")
                    .hasMessageContaining("FATAL: Production rate limiter misconfiguration");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        @DisplayName("hashAccount() throws IllegalStateException in production when key is null or blank")
        void testHashAccountFailsFastWhenKeyMissingOrBlank(String key) {
            RateLimitProperties props = new RateLimitProperties();
            props.setAccountHmacKey(key);

            DistributedOtpRateLimiter limiter = new DistributedOtpRateLimiter(props, createProdEnv(), null);

            assertThatThrownBy(() -> limiter.hashAccount("user@probus.io"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RATE_LIMIT_HMAC_KEY")
                    .hasMessageContaining("FATAL: Production rate limiter misconfiguration");
        }

        @Test
        @DisplayName("Valid HMAC key in production passes startup validation and computes 64-char hex HMAC")
        void testValidKeyInProduction() {
            RateLimitProperties props = new RateLimitProperties();
            props.setAccountHmacKey("production-secret-hmac-key-v18n-test-only");

            DistributedOtpRateLimiter limiter = new DistributedOtpRateLimiter(props, createProdEnv(), null);

            assertThatCode(limiter::validateConfiguration).doesNotThrowAnyException();

            String hash = limiter.hashAccount("user@probus.io");
            assertThat(hash).isNotNull().hasSize(64).matches("^[0-9a-f]{64}$");
        }
    }

    @Nested
    @DisplayName("Non-Production Environment Safe Fallback")
    class NonProductionEnvironmentTests {

        private MockEnvironment createLocalEnv() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("local");
            return env;
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        @DisplayName("validateConfiguration() does not throw in non-production when key is missing or blank")
        void testStartupValidationSucceedsInNonProduction(String key) {
            RateLimitProperties props = new RateLimitProperties();
            props.setAccountHmacKey(key);

            DistributedOtpRateLimiter limiter = new DistributedOtpRateLimiter(props, createLocalEnv(), null);

            assertThatCode(limiter::validateConfiguration).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        @DisplayName("hashAccount() returns fallback MD5 digest in non-production when key is missing or blank")
        void testHashAccountFallbackInNonProduction(String key) {
            RateLimitProperties props = new RateLimitProperties();
            props.setAccountHmacKey(key);

            DistributedOtpRateLimiter limiter = new DistributedOtpRateLimiter(props, createLocalEnv(), null);

            String hash = limiter.hashAccount("user@probus.io");
            // MD5 hex digest is 32 characters
            assertThat(hash).isNotNull().hasSize(32).matches("^[0-9a-f]{32}$");
        }

        @Test
        @DisplayName("Valid HMAC key in non-production computes 64-char hex HMAC")
        void testValidKeyInNonProduction() {
            RateLimitProperties props = new RateLimitProperties();
            props.setAccountHmacKey("local-dev-test-secret-key");

            DistributedOtpRateLimiter limiter = new DistributedOtpRateLimiter(props, createLocalEnv(), null);

            String hash = limiter.hashAccount("user@probus.io");
            assertThat(hash).isNotNull().hasSize(64).matches("^[0-9a-f]{64}$");
        }
    }

    @Nested
    @DisplayName("Normalization and Hashing Semantics")
    class HashingSemanticsTests {

        private DistributedOtpRateLimiter createLimiter(String key) {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("dev");
            RateLimitProperties props = new RateLimitProperties();
            props.setAccountHmacKey(key);
            return new DistributedOtpRateLimiter(props, env, null);
        }

        @Test
        @DisplayName("Email normalization trims whitespace and lowers case before hashing")
        void testEmailNormalization() {
            DistributedOtpRateLimiter limiter = createLimiter("test-normalization-secret-key");

            String hash1 = limiter.hashAccount("user@example.com");
            String hash2 = limiter.hashAccount("  User@Example.COM  ");
            String hash3 = limiter.hashAccount("\tUSER@EXAMPLE.COM\n");

            assertThat(hash1).isEqualTo(hash2).isEqualTo(hash3);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        @DisplayName("Null or blank email returns 'anonymous' without hashing")
        void testAnonymousEmailHandling(String email) {
            DistributedOtpRateLimiter limiter = createLimiter("test-secret-key");
            assertThat(limiter.hashAccount(email)).isEqualTo("anonymous");
        }

        @Test
        @DisplayName("Different secret keys produce distinct HMAC hashes for the same email")
        void testDifferentKeysProduceDistinctHashes() {
            DistributedOtpRateLimiter limiter1 = createLimiter("secret-key-alpha-123");
            DistributedOtpRateLimiter limiter2 = createLimiter("secret-key-beta-456");

            String hash1 = limiter1.hashAccount("analyst@probus.io");
            String hash2 = limiter2.hashAccount("analyst@probus.io");

            assertThat(hash1).isNotEqualTo(hash2);
        }
    }
}

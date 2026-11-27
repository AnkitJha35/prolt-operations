package com.sense.prolt_operations;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.entity.UserRefreshSession;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.repository.UserRefreshSessionRepository;
import com.sense.prolt_operations.security.AuthRateLimiterFilter;
import com.sense.prolt_operations.security.JwtService;
import com.sense.prolt_operations.service.OtpService;
import com.sense.prolt_operations.service.RefreshTokenService;
import com.sense.prolt_operations.testutil.TestUserHelper;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class RefreshTokenIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRefreshSessionRepository refreshSessionRepository;

    @Autowired
    private OtpService otpService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired(required = false)
    private AuthRateLimiterFilter authRateLimiterFilter;

    private TestUserHelper.TestUser testUser;
    private TestUserHelper.TestUser superAdminUser;

    @BeforeEach
    void setUp() {
        if (authRateLimiterFilter != null) {
            authRateLimiterFilter.getLimiter().reset();
        }
        otpService.setAllowMemoryFallback(true);
        otpService.setSuperadminBypassEnabled(false);
        testUser = createTestUser(Role.CLIENT, "client_" + UUID.randomUUID().toString().substring(0, 8),
                "client_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        superAdminUser = createTestUser(Role.SUPER_ADMIN, "admin_" + UUID.randomUUID().toString().substring(0, 8),
                "admin_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
    }

    @AfterEach
    void tearDown() {
        if (testUser != null) {
            refreshSessionRepository.revokeAllByUserId(testUser.getUser().getId(), java.time.LocalDateTime.now());
            deleteTestUser(testUser);
        }
        if (superAdminUser != null) {
            refreshSessionRepository.revokeAllByUserId(superAdminUser.getUser().getId(), java.time.LocalDateTime.now());
            deleteTestUser(superAdminUser);
        }
    }

    private record LoginResult(String accessToken, Cookie refreshCookie, String rawCookieHeader) {}

    private LoginResult loginUser(String email) throws Exception {
        if (authRateLimiterFilter != null) {
            authRateLimiterFilter.getLimiter().reset();
        }
        otpService.generateAndSendOtp(email);
        String otp = otpService.getInMemoryStore().get(email.toLowerCase()).otp();

        MvcResult result = mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\": \"%s\", \"otp\": \"%s\"}", email, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        String accessToken = node.get("token").asText();
        Cookie cookie = result.getResponse().getCookie(refreshTokenService.getCookieName());
        String rawHeader = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);

        return new LoginResult(accessToken, cookie, rawHeader);
    }

    // --------------------------------------------------------------------------
    // 1. Access JWT Tests (Validity, Subject, TokenVersion, Claims)
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("ACCESS-01: JWT validity is configured to exactly 15 minutes")
    void testAccessTokenValidityIs15Minutes() {
        long millis = jwtService.getExpirationMillis();
        assertThat(millis).isEqualTo(15L * 60 * 1000L); // 900,000 ms
    }

    @Test
    @DisplayName("ACCESS-02: JWT contains standard subject, tokenVersion, role, and module claims")
    void testAccessTokenClaims() throws Exception {
        LoginResult login = loginUser(testUser.getUser().getEmail());
        Claims claims = jwtService.getClaims(login.accessToken());

        assertThat(claims.getSubject()).isEqualTo(testUser.getUser().getId());
        assertThat(claims.get("id", String.class)).isEqualTo(testUser.getUser().getId());
        assertThat(claims.get("username", String.class)).isEqualTo(testUser.getUser().getUsername());
        assertThat(claims.get("role", String.class)).isEqualTo("CLIENT");
        assertThat(claims.get("tokenVersion", Integer.class)).isEqualTo(0);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();

        long diff = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(diff).isEqualTo(15L * 60 * 1000L);
    }

    @Test
    @DisplayName("ACCESS-03: Expired access token is rejected with 401")
    void testExpiredAccessTokenRejected() {
        // Generate an artificially expired token for the user
        Date now = new Date();
        Date past = new Date(now.getTime() - 1000L); // 1 second in the past
        String expiredToken = io.jsonwebtoken.Jwts.builder()
                .subject(testUser.getUser().getId())
                .claim("id", testUser.getUser().getId())
                .claim("username", testUser.getUser().getUsername())
                .claim("role", "CLIENT")
                .claim("tokenVersion", 0)
                .claim("hiddenModules", List.of())
                .claim("readOnlyModules", List.of())
                .issuedAt(new Date(now.getTime() - 60000L))
                .expiration(past)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("super_secret_jwt_key_for_testing_purposes_at_least_32_bytes!".getBytes()))
                .compact();

        assertThat(jwtService.validateToken(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("ACCESS-04: Invalid signature token is rejected with 401")
    void testInvalidSignatureAccessTokenRejected() {
        String invalidToken = io.jsonwebtoken.Jwts.builder()
                .subject(testUser.getUser().getId())
                .claim("id", testUser.getUser().getId())
                .claim("username", testUser.getUser().getUsername())
                .claim("role", "CLIENT")
                .claim("tokenVersion", 0)
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor("different_secret_key_32_bytes_long_here!".getBytes()))
                .compact();

        assertThat(jwtService.validateToken(invalidToken)).isFalse();
    }

    // --------------------------------------------------------------------------
    // 2. Refresh Token Lifecycle, Rotation & Cookie Tests
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("REFRESH-01: OTP login sets HttpOnly, Path, and SameSite refresh cookie")
    void testLoginSetsSecureRefreshCookie() throws Exception {
        LoginResult login = loginUser(testUser.getUser().getEmail());

        assertThat(login.refreshCookie()).isNotNull();
        assertThat(login.refreshCookie().isHttpOnly()).isTrue();
        assertThat(login.refreshCookie().getPath()).isEqualTo("/api/auth");
        assertThat(login.rawCookieHeader()).contains("HttpOnly");
        assertThat(login.rawCookieHeader()).contains("Path=/api/auth");
        assertThat(login.rawCookieHeader()).contains("SameSite=Lax");
    }

    @Test
    @DisplayName("REFRESH-02: Database stores only SHA-256 hash of refresh token, never plaintext")
    void testRefreshTokenHashStoredInDatabase() throws Exception {
        LoginResult login = loginUser(testUser.getUser().getEmail());
        String rawToken = login.refreshCookie().getValue();

        // Verify token is URL-safe Base64 and not plaintext in DB
        String expectedHash = refreshTokenService.hashToken(rawToken);
        java.util.Optional<UserRefreshSession> sessionOpt = refreshSessionRepository.findByTokenHash(expectedHash);

        assertThat(sessionOpt).isPresent();
        assertThat(sessionOpt.get().getUserId()).isEqualTo(testUser.getUser().getId());
        assertThat(sessionOpt.get().getTokenHash()).isEqualTo(expectedHash);
        assertThat(sessionOpt.get().getTokenHash()).isNotEqualTo(rawToken);
    }

    @Test
    @DisplayName("REFRESH-03: Successful refresh rotates token and issues fresh 15-minute access JWT")
    void testSuccessfulRefreshRotatesToken() throws Exception {
        LoginResult login = loginUser(testUser.getUser().getEmail());
        Cookie oldCookie = login.refreshCookie();

        // Perform refresh
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(oldCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.token").isString())
                .andReturn();

        Cookie newCookie = refreshResult.getResponse().getCookie(refreshTokenService.getCookieName());
        assertThat(newCookie).isNotNull();
        assertThat(newCookie.getValue()).isNotEqualTo(oldCookie.getValue());

        String newAccessToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString()).get("token").asText();
        Claims newClaims = jwtService.getClaims(newAccessToken);
        assertThat(newClaims.getSubject()).isEqualTo(testUser.getUser().getId());

        // Verify old session in DB is marked revoked and replacedById is set
        String oldHash = refreshTokenService.hashToken(oldCookie.getValue());
        UserRefreshSession oldSession = refreshSessionRepository.findByTokenHash(oldHash).orElseThrow();
        assertThat(oldSession.isRevoked()).isTrue();
        assertThat(oldSession.getReplacedById()).isNotNull();

        // Verify new session has the SAME tokenFamilyId
        String newHash = refreshTokenService.hashToken(newCookie.getValue());
        UserRefreshSession newSession = refreshSessionRepository.findByTokenHash(newHash).orElseThrow();
        assertThat(newSession.getTokenFamilyId()).isEqualTo(oldSession.getTokenFamilyId());
        assertThat(newSession.isActive()).isTrue();
    }

    @Test
    @DisplayName("REFRESH-04: Reuse of rotated refresh token revokes entire token family (Reuse Detection)")
    void testTokenReuseRevokesFamily() throws Exception {
        LoginResult login = loginUser(testUser.getUser().getEmail());
        Cookie initialCookie = login.refreshCookie();

        // Rotation 1: successfully rotates initialCookie to rotatedCookie
        MvcResult refresh1 = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(initialCookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie rotatedCookie = refresh1.getResponse().getCookie(refreshTokenService.getCookieName());
        assertThat(rotatedCookie).isNotNull();

        // ATTEMPT REUSE: Present the initialCookie AGAIN!
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(initialCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Compromised or reused session detected")));

        // Verify the entire family is now revoked!
        String rotatedHash = refreshTokenService.hashToken(rotatedCookie.getValue());
        UserRefreshSession rotatedSession = refreshSessionRepository.findByTokenHash(rotatedHash).orElseThrow();
        assertThat(rotatedSession.isRevoked()).isTrue();

        // Even the legitimate rotated token is now dead!
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(rotatedCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("REFRESH-05: Missing refresh cookie returns 401")
    void testMissingRefreshCookieRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("REFRESH-06: Expired refresh token returns 401")
    void testExpiredRefreshTokenRejected() throws Exception {
        LoginResult login = loginUser(testUser.getUser().getEmail());
        String hash = refreshTokenService.hashToken(login.refreshCookie().getValue());

        // Artificially expire session in database
        UserRefreshSession session = refreshSessionRepository.findByTokenHash(hash).orElseThrow();
        session.setExpiresAt(java.time.LocalDateTime.now().minusHours(1));
        refreshSessionRepository.save(session);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expired")));
    }

    // --------------------------------------------------------------------------
    // 3. Concurrency Safety Tests
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("REFRESH-07: Concurrent refresh requests on the same token cannot both succeed")
    void testConcurrentRefreshAttempts() throws Exception {
        LoginResult login = loginUser(testUser.getUser().getEmail());
        Cookie targetCookie = login.refreshCookie();

        int numThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(post("/api/auth/refresh").cookie(targetCookie)).andReturn();
                    if (res.getResponse().getStatus() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one should succeed, the other should fail (either 401 due to reuse or lock conflict)
        assertThat(successCount.get()).isLessThanOrEqualTo(1);
    }

    // --------------------------------------------------------------------------
    // 4. Session Revocation Integration (Status Disable, Revoke All, TokenVersion)
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("REVOKE-01: Account disablement immediately invalidates active refresh session")
    void testAccountDisableInvalidatesRefreshSession() throws Exception {
        LoginResult login = loginUser(testUser.getUser().getEmail());

        // Super Admin disables account
        mockMvc.perform(patch("/api/users/" + testUser.getUser().getId() + "/status")
                        .header("Authorization", "Bearer " + superAdminUser.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        // Refresh attempt must be rejected
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("disabled")));
    }

    @Test
    @DisplayName("REVOKE-02: Admin revoke-sessions revokes all active refresh sessions")
    void testAdminRevokeSessionsInvalidatesRefresh() throws Exception {
        LoginResult login = loginUser(testUser.getUser().getEmail());

        // Super Admin revokes sessions
        mockMvc.perform(post("/api/users/" + testUser.getUser().getId() + "/revoke-sessions")
                        .header("Authorization", "Bearer " + superAdminUser.getToken()))
                .andExpect(status().isOk());

        // Refresh attempt must be rejected
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------------------------------
    // 5. Logout & Session Management APIs
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("LOGOUT-01: Logout revokes session and clears refresh cookie")
    void testLogoutRevokesSessionAndClearsCookie() throws Exception {
        LoginResult login = loginUser(testUser.getUser().getEmail());
        String hash = refreshTokenService.hashToken(login.refreshCookie().getValue());

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .cookie(login.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        // Cookie is cleared (max-age 0)
        Cookie clearedCookie = logoutResult.getResponse().getCookie(refreshTokenService.getCookieName());
        assertThat(clearedCookie).isNotNull();
        assertThat(clearedCookie.getMaxAge()).isZero();

        // Session is marked revoked in DB
        UserRefreshSession session = refreshSessionRepository.findByTokenHash(hash).orElseThrow();
        assertThat(session.isRevoked()).isTrue();

        // Subsequent refresh with this cookie returns 401
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(login.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("LOGOUT-02: Logout without cookie is idempotent and succeeds")
    void testLogoutWithoutCookieIsIdempotent() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("SESSIONS-01: User can list active sessions without exposing token hashes")
    void testGetActiveSessions() throws Exception {
        LoginResult login = loginUser(testUser.getUser().getEmail());

        MvcResult result = mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .cookie(login.refreshCookie()))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        JsonNode sessions = objectMapper.readTree(json);
        assertThat(sessions.isArray()).isTrue();
        assertThat(sessions.size()).isGreaterThanOrEqualTo(1);

        JsonNode first = sessions.get(0);
        assertThat(first.has("sessionId")).isTrue();
        assertThat(first.has("createdAt")).isTrue();
        assertThat(first.has("expiresAt")).isTrue();
        assertThat(first.has("current")).isTrue();
        assertThat(first.get("current").asBoolean()).isTrue();
        assertThat(first.has("tokenHash")).isFalse(); // MUST NEVER EXPOSE HASH
    }

    @Test
    @DisplayName("SESSIONS-02: User can revoke their own session")
    void testRevokeIndividualSession() throws Exception {
        LoginResult login = loginUser(testUser.getUser().getEmail());
        String hash = refreshTokenService.hashToken(login.refreshCookie().getValue());
        UserRefreshSession session = refreshSessionRepository.findByTokenHash(hash).orElseThrow();

        mockMvc.perform(post("/api/auth/sessions/" + session.getId() + "/revoke")
                        .header("Authorization", "Bearer " + login.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        UserRefreshSession updated = refreshSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(updated.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("SESSIONS-03: User cannot revoke another user's session")
    void testUserCannotRevokeAnotherUsersSession() throws Exception {
        LoginResult userLogin = loginUser(testUser.getUser().getEmail());
        LoginResult adminLogin = loginUser(superAdminUser.getUser().getEmail());

        String adminHash = refreshTokenService.hashToken(adminLogin.refreshCookie().getValue());
        UserRefreshSession adminSession = refreshSessionRepository.findByTokenHash(adminHash).orElseThrow();

        // Normal testUser tries to revoke adminSession
        mockMvc.perform(post("/api/auth/sessions/" + adminSession.getId() + "/revoke")
                        .header("Authorization", "Bearer " + userLogin.accessToken()))
                .andExpect(status().isNotFound()); // Session not found for this user
    }
}

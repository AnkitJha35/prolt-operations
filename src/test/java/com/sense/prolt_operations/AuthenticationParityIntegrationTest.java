package com.sense.prolt_operations;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.JwtService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationParityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.webhook-secret}")
    private String webhookSecret;

    private String clientToken;
    private String superAdminToken;
    private String expiredToken;
    private final String badToken = "invalid.malformed.token";
    private com.sense.prolt_operations.testutil.TestUserHelper.TestUser clientTestUser;
    private com.sense.prolt_operations.testutil.TestUserHelper.TestUser superAdminTestUser;

    @BeforeEach
    void setUp() {
        clientTestUser = createTestUser(Role.CLIENT);
        clientToken = clientTestUser.getToken();

        superAdminTestUser = createTestUser(Role.SUPER_ADMIN);
        superAdminToken = superAdminTestUser.getToken();

        // Generate expired token
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        Date now = new Date();
        Date pastExpiry = new Date(now.getTime() - 3600_000); // 1 hour ago
        expiredToken = Jwts.builder()
                .subject(clientTestUser.getUser().getUsername())
                .claim("id", clientTestUser.getUser().getId())
                .claim("username", clientTestUser.getUser().getUsername())
                .claim("role", "CLIENT")
                .issuedAt(new Date(now.getTime() - 7200_000))
                .expiration(pastExpiry)
                .signWith(Keys.hmacShaKeyFor(keyBytes))
                .compact();
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        deleteTestUser(clientTestUser);
        deleteTestUser(superAdminTestUser);
    }

    @Test
    @DisplayName("PARITY-AUTH-01: Expired token on public /health returns 200, on protected /api/datasets/summary returns 401")
    void testExpiredTokenBehavior() throws Exception {
        // Public route: unauthenticated access proceeds, returns 200
        mockMvc.perform(get("/health")
                .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("healthy"));

        // Protected route: invalid/expired token returns 401
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));
    }

    @Test
    @DisplayName("PARITY-AUTH-02: Public route group (/api/dashboard/overview) allows no token, bad token, client and superadmin")
    void testPublicRouteGroup() throws Exception {
        // No token
        mockMvc.perform(get("/api/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Bad token
        mockMvc.perform(get("/api/dashboard/overview")
                .header("Authorization", "Bearer " + badToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Client token
        mockMvc.perform(get("/api/dashboard/overview")
                .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Superadmin token
        mockMvc.perform(get("/api/dashboard/overview")
                .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PARITY-AUTH-03: Protected route group (/api/datasets/summary) rejects no token & bad token with 401, permits client and superadmin")
    void testProtectedRouteGroup() throws Exception {
        // No token
        mockMvc.perform(get("/api/datasets/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        // Bad token
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", "Bearer " + badToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        // Client token
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Superadmin token
        mockMvc.perform(get("/api/datasets/summary")
                .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PARITY-AUTH-04: SuperAdmin route group (/api/users) rejects no token (401), bad token (401), non-superadmin (403), permits superadmin (200)")
    void testSuperAdminRouteGroup() throws Exception {
        // No token -> 401
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        // Bad token -> 401
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + badToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        // Client / non-superadmin role -> 403 Forbidden: Super Admin access required
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Forbidden: Super Admin access required"));

        // Superadmin role -> 200
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("PARITY-AUTH-05: Scraper /sync requires authentication; /import uses constant-time webhook token")
    void testScraperEndpointsAuth() throws Exception {
        // POST /api/scraper/sync without auth -> 401
        mockMvc.perform(post("/api/scraper/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"market\":\"DAM\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        // POST /api/scraper/import without webhook token -> 401 {"success":false,"message":"Unauthorized"}
        mockMvc.perform(post("/api/scraper/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"dataset\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        // POST /api/scraper/import with invalid token -> 401 {"success":false,"message":"Unauthorized"}
        mockMvc.perform(post("/api/scraper/import")
                .header("x-webhook-token", "wrong_token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"dataset\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized"));

        // POST /api/scraper/import with valid token -> passes auth, returns 400 for incomplete payload
        mockMvc.perform(post("/api/scraper/import")
                .header("x-webhook-token", webhookSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Missing type or payload"));
    }
}

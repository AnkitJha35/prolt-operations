package com.sense.prolt_operations.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.dto.auth.AuthResponse;
import com.sense.prolt_operations.dto.auth.AuthUserResponse;
import com.sense.prolt_operations.dto.auth.RefreshResult;
import com.sense.prolt_operations.dto.auth.UserSessionDTO;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.entity.UserAuditLog;
import com.sense.prolt_operations.entity.UserRefreshSession;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.exception.UnauthorizedException;
import com.sense.prolt_operations.repository.UserAuditLogRepository;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.repository.UserRefreshSessionRepository;
import com.sense.prolt_operations.repository.UserSecurityState;
import com.sense.prolt_operations.security.JwtService;
import com.sense.prolt_operations.security.state.UserSecurityStateProvider;

import jakarta.annotation.PostConstruct;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final UserRefreshSessionRepository refreshSessionRepository;
    private final UserRepository userRepository;
    private final UserAuditLogRepository userAuditLogRepository;
    private final UserSecurityStateProvider securityStateProvider;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.refresh-token.expires-in:7d}")
    private String expiresIn;

    @Value("${app.auth.refresh-token.cookie-name:iex_refresh_token}")
    private String cookieName;

    @Value("${app.auth.refresh-token.cookie-http-only:true}")
    private boolean cookieHttpOnly;

    @Value("${app.auth.refresh-token.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.auth.refresh-token.cookie-same-site:Lax}")
    private String cookieSameSite;

    @Value("${app.auth.refresh-token.cookie-path:/api/auth}")
    private String cookiePath;

    @Value("${app.auth.refresh-token.cookie-domain:}")
    private String cookieDomain;

    @Value("${app.auth.refresh-token.cleanup-retention-days:30}")
    private int cleanupRetentionDays;

    private long refreshTokenValidityMillis;

    public RefreshTokenService(UserRefreshSessionRepository refreshSessionRepository,
                               UserRepository userRepository,
                               UserAuditLogRepository userAuditLogRepository,
                               UserSecurityStateProvider securityStateProvider,
                               JwtService jwtService,
                               ObjectMapper objectMapper) {
        this.refreshSessionRepository = refreshSessionRepository;
        this.userRepository = userRepository;
        this.userAuditLogRepository = userAuditLogRepository;
        this.securityStateProvider = securityStateProvider;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        this.refreshTokenValidityMillis = parseDuration(expiresIn, 7L * 24 * 60 * 60 * 1000L);
    }

    private long parseDuration(String exp, long defaultMillis) {
        if (exp == null || exp.isBlank()) {
            return defaultMillis;
        }
        String clean = exp.trim().toLowerCase(Locale.ROOT);
        long millis;
        try {
            if (clean.endsWith("d")) {
                long days = Long.parseLong(clean.substring(0, clean.length() - 1));
                millis = days * 24 * 60 * 60 * 1000L;
            } else if (clean.endsWith("h")) {
                long hours = Long.parseLong(clean.substring(0, clean.length() - 1));
                millis = hours * 60 * 60 * 1000L;
            } else if (clean.endsWith("m")) {
                long minutes = Long.parseLong(clean.substring(0, clean.length() - 1));
                millis = minutes * 60 * 1000L;
            } else if (clean.endsWith("s")) {
                long seconds = Long.parseLong(clean.substring(0, clean.length() - 1));
                millis = seconds * 1000L;
            } else {
                millis = Long.parseLong(clean);
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse refresh token duration '{}', using default {} ms", exp, defaultMillis);
            return defaultMillis;
        }

        if (millis <= 0) {
            throw new IllegalArgumentException("Refresh token validity must be strictly positive (got " + millis + " ms)");
        }
        return millis;
    }

    public String generateRawRefreshToken() {
        byte[] randomBytes = new byte[32]; // 256 bits of entropy
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm unavailable", e);
        }
    }

    public ResponseCookie createRefreshTokenCookie(String rawToken) {
        long maxAgeSeconds = refreshTokenValidityMillis / 1000L;
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, rawToken)
                .httpOnly(cookieHttpOnly)
                .secure(cookieSecure)
                .path(cookiePath)
                .maxAge(maxAgeSeconds)
                .sameSite(cookieSameSite);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain.trim());
        }
        return builder.build();
    }

    public ResponseCookie createLogoutCookie() {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, "")
                .httpOnly(cookieHttpOnly)
                .secure(cookieSecure)
                .path(cookiePath)
                .maxAge(0)
                .sameSite(cookieSameSite);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain.trim());
        }
        return builder.build();
    }

    @Transactional
    public RefreshResult createSession(User user, String ip, String userAgent) {
        String rawToken = generateRawRefreshToken();
        String tokenHash = hashToken(rawToken);
        String familyId = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofMillis(refreshTokenValidityMillis));

        UserRefreshSession session = new UserRefreshSession(
                user.getId(),
                tokenHash,
                familyId,
                user.getTokenVersion(),
                expiresAt,
                ip,
                userAgent
        );
        refreshSessionRepository.save(session);

        String accessToken = jwtService.generateToken(user);
        AuthResponse authResponse = new AuthResponse(true, accessToken, toAuthUserResponse(user));
        ResponseCookie cookie = createRefreshTokenCookie(rawToken);

        log.info("Created new refresh session for user '{}' (family='{}', expiresAt={})",
                user.getUsername(), familyId, expiresAt);
        return new RefreshResult(authResponse, cookie);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshResult rotateSession(String rawRefreshToken, String ip, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh token cookie is required");
        }

        String tokenHash = hashToken(rawRefreshToken);

        // Concurrency-safe pessimistic write lock to prevent parallel double-rotation
        Optional<UserRefreshSession> sessionOpt = refreshSessionRepository.findByTokenHashForUpdate(tokenHash);
        if (sessionOpt.isEmpty()) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        UserRefreshSession session = sessionOpt.get();

        // 1. User security state check (enabled and tokenVersion parity)
        Optional<UserSecurityState> stateOpt = securityStateProvider.getSecurityState(session.getUserId());
        if (stateOpt.isEmpty()) {
            session.setRevokedAt(LocalDateTime.now());
            refreshSessionRepository.save(session);
            throw new UnauthorizedException("User not found");
        }

        UserSecurityState userState = stateOpt.get();
        if (!userState.enabled()) {
            session.setRevokedAt(LocalDateTime.now());
            refreshSessionRepository.save(session);
            log.warn("Refresh rejected: user '{}' ({}) is disabled", userState.username(), session.getUserId());
            throw new UnauthorizedException("Account is disabled. Please contact administrator.");
        }

        if (session.getTokenVersion() != userState.tokenVersion()) {
            session.setRevokedAt(LocalDateTime.now());
            refreshSessionRepository.save(session);
            log.warn("Refresh rejected: tokenVersion mismatch for user '{}' (session={}, current={})",
                    userState.username(), session.getTokenVersion(), userState.tokenVersion());
            throw new UnauthorizedException("Session revoked due to security update. Please log in again.");
        }

        // 2. Reuse detection: if token is already revoked or replaced, the token was compromised
        if (session.isRevoked() || session.isReplaced()) {
            log.warn("SECURITY ALERT: Refresh token reuse detected for user '{}', family '{}', session '{}'. Revoking entire token family!",
                    session.getUserId(), session.getTokenFamilyId(), session.getId());
            refreshSessionRepository.revokeFamily(session.getTokenFamilyId(), LocalDateTime.now());
            createAuditLog("REFRESH_TOKEN_REUSE_DETECTED", session.getUserId(), "SYSTEM",
                    Map.of("familyId", session.getTokenFamilyId(), "reusedSessionId", session.getId()));
            throw new UnauthorizedException("Compromised or reused session detected. Please log in again.");
        }

        // 3. Expiration check
        if (session.isExpired()) {
            session.setRevokedAt(LocalDateTime.now());
            refreshSessionRepository.save(session);
            log.info("Refresh token expired for session '{}', user '{}'", session.getId(), session.getUserId());
            throw new UnauthorizedException("Refresh token expired. Please log in again.");
        }

        // 4. Load full User entity for token generation
        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // 5. Generate new rotated refresh token in the SAME family
        String newRawToken = generateRawRefreshToken();
        String newTokenHash = hashToken(newRawToken);
        LocalDateTime newExpiresAt = LocalDateTime.now().plus(Duration.ofMillis(refreshTokenValidityMillis));

        UserRefreshSession newSession = new UserRefreshSession(
                user.getId(),
                newTokenHash,
                session.getTokenFamilyId(),
                user.getTokenVersion(),
                newExpiresAt,
                ip,
                userAgent
        );
        newSession = refreshSessionRepository.save(newSession);

        // 6. Invalidate old session by marking replaced and revoked
        LocalDateTime now = LocalDateTime.now();
        session.setRevokedAt(now);
        session.setReplacedById(newSession.getId());
        session.setLastUsedAt(now);
        refreshSessionRepository.save(session);

        // 7. Generate fresh 15-minute access JWT
        String newAccessToken = jwtService.generateToken(user);
        AuthResponse authResponse = new AuthResponse(true, newAccessToken, toAuthUserResponse(user));
        ResponseCookie newCookie = createRefreshTokenCookie(newRawToken);

        log.debug("Rotated refresh session for user '{}' (oldSession='{}', newSession='{}', family='{}')",
                user.getUsername(), session.getId(), newSession.getId(), session.getTokenFamilyId());

        return new RefreshResult(authResponse, newCookie);
    }

    @Transactional
    public ResponseCookie logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = hashToken(rawRefreshToken);
            refreshSessionRepository.findByTokenHashForUpdate(tokenHash)
                    .ifPresent(session -> {
                        if (!session.isRevoked()) {
                            session.setRevokedAt(LocalDateTime.now());
                            refreshSessionRepository.save(session);
                            log.info("Revoked refresh session '{}' during logout", session.getId());
                        }
                    });
        }
        return createLogoutCookie();
    }

    @Transactional(readOnly = true)
    public List<UserSessionDTO> getUserSessions(String userId, String currentRawToken) {
        String currentHash = (currentRawToken != null && !currentRawToken.isBlank()) ? hashToken(currentRawToken) : null;
        List<UserRefreshSession> activeSessions =
                refreshSessionRepository.findAllByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, LocalDateTime.now());

        return activeSessions.stream()
                .map(s -> new UserSessionDTO(
                        s.getId(),
                        s.getCreatedAt(),
                        s.getLastUsedAt(),
                        s.getExpiresAt(),
                        s.getCreatedIp(),
                        s.getUserAgent(),
                        currentHash != null && currentHash.equals(s.getTokenHash())
                ))
                .toList();
    }

    @Transactional
    public void revokeUserSession(String userId, String sessionId) {
        UserRefreshSession session = refreshSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (!session.isRevoked()) {
            session.setRevokedAt(LocalDateTime.now());
            refreshSessionRepository.save(session);
            log.info("User '{}' revoked session '{}'", userId, sessionId);
        }
    }

    @Transactional
    public void revokeAllSessionsForUser(String userId) {
        int count = refreshSessionRepository.revokeAllByUserId(userId, LocalDateTime.now());
        log.info("Revoked all active refresh sessions ({}) for user '{}'", count, userId);
    }

    @Scheduled(cron = "${app.auth.refresh-token.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanupExpiredAndRevokedSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(cleanupRetentionDays);
        int deleted = refreshSessionRepository.deleteExpiredOrRevokedOlderThan(cutoff);
        log.info("Purged {} expired or revoked refresh sessions older than cutoff {}", deleted, cutoff);
    }

    public String getCookieName() {
        return cookieName;
    }

    public AuthUserResponse toAuthUserResponse(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole() != null ? user.getRole().name() : null,
                user.isEnabled(),
                user.getHiddenModulesList(),
                user.getReadOnlyModulesList()
        );
    }

    private void createAuditLog(String action, String targetUserId, String performedBy, Map<String, Object> changes) {
        try {
            UserAuditLog audit = UserAuditLog.create();
            audit.setAction(action);
            audit.setTargetUserId(targetUserId);
            audit.setTargetUser(targetUserId);
            audit.setPerformedBy(performedBy);
            audit.setChanges(objectMapper.writeValueAsString(changes));
            audit.setTimestamp(LocalDateTime.now());
            userAuditLogRepository.save(audit);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit log changes: {}", e.getMessage());
        }
    }
}

package com.sense.prolt_operations.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.exception.UnauthorizedException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${app.jwt.secret:}")
    private String secret;

    @Value("${app.jwt.expires-in:7d}")
    private String expiresIn;

    @Autowired(required = false)
    private org.springframework.core.env.Environment environment;

    private SecretKey signingKey;
    private long expirationMillis;

    @PostConstruct
    public void init() {
        boolean isProd = environment != null && java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        byte[] bytes = (secret != null) ? secret.getBytes(StandardCharsets.UTF_8) : new byte[0];

        if (isProd) {
            if (bytes.length < 32) {
                throw new IllegalStateException("FATAL: In production, app.jwt.secret must be provided and must be at least 32 bytes long!");
            }
        }

        if (bytes.length < 32) {
            throw new IllegalStateException("FATAL: app.jwt.secret must be at least 32 bytes long (got " + bytes.length + " bytes). Please configure a secure secret in your profile.");
        }

        this.signingKey = Keys.hmacShaKeyFor(bytes);
        this.expirationMillis = parseExpiration(expiresIn);
    }

    private long parseExpiration(String exp) {
        if (exp == null || exp.isBlank()) {
            return 7L * 24 * 60 * 60 * 1000L; // default 7d
        }
        String clean = exp.trim().toLowerCase();
        try {
            if (clean.endsWith("d")) {
                long days = Long.parseLong(clean.substring(0, clean.length() - 1));
                return days * 24 * 60 * 60 * 1000L;
            } else if (clean.endsWith("h")) {
                long hours = Long.parseLong(clean.substring(0, clean.length() - 1));
                return hours * 60 * 60 * 1000L;
            } else if (clean.endsWith("m")) {
                long minutes = Long.parseLong(clean.substring(0, clean.length() - 1));
                return minutes * 60 * 1000L;
            } else if (clean.endsWith("s")) {
                long seconds = Long.parseLong(clean.substring(0, clean.length() - 1));
                return seconds * 1000L;
            } else {
                return Long.parseLong(clean);
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to parse JWT expires-in '{}', defaulting to 7 days", exp);
            return 7L * 24 * 60 * 60 * 1000L;
        }
    }

    public String generateToken(User user) {
        List<String> hidden = user.getHiddenModulesList();
        List<String> readOnly = user.getReadOnlyModulesList();
        return generateToken(user.getId(), user.getUsername(), user.getEmail(), user.getRole(), hidden, readOnly);
    }

    public String generateToken(String id, String username, String email, Role role,
                                List<String> hiddenModules, List<String> readOnlyModules) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(username)
                .claim("id", id)
                .claim("username", username)
                .claim("email", email)
                .claim("role", role != null ? role.name() : Role.CLIENT.name())
                .claim("hiddenModules", hiddenModules != null ? hiddenModules : Collections.emptyList())
                .claim("readOnlyModules", readOnlyModules != null ? readOnlyModules : Collections.emptyList())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims getClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT verification failed: {}", e.getMessage());
            throw new UnauthorizedException("Invalid or expired token. Please log in again.");
        }
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public ProltUserPrincipal extractPrincipal(String token) {
        Claims claims = getClaims(token);
        String id = claims.get("id", String.class);
        String username = claims.get("username", String.class);
        if (username == null) {
            username = claims.getSubject();
        }
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);
        List<String> hidden = claims.get("hiddenModules", List.class);
        List<String> readOnly = claims.get("readOnlyModules", List.class);

        return new ProltUserPrincipal(id, username, email, role != null ? role : Role.CLIENT.name(),
                hidden != null ? hidden : Collections.emptyList(),
                readOnly != null ? readOnly : Collections.emptyList());
    }
}

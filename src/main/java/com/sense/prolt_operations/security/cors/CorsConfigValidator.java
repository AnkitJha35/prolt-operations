package com.sense.prolt_operations.security.cors;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class CorsConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(CorsConfigValidator.class);

    private final Environment environment;

    @Value("${app.cors-origins:}")
    private String corsOriginsRaw;

    private List<String> parsedAllowedOrigins = Collections.emptyList();
    private boolean isProduction = false;

    public CorsConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateConfiguration() {
        this.isProduction = determineIfProduction(environment);

        if (!isProduction) {
            log.info("CORS policy initialized in NON-PRODUCTION mode: Unrestricted CORS with credentials enabled");
            return;
        }

        log.info("CORS policy initialized in PRODUCTION mode: Validating explicit origin allowlist");

        if (corsOriginsRaw == null || corsOriginsRaw.trim().isEmpty()) {
            throw new IllegalStateException(
                "FATAL: Production CORS misconfiguration: 'app.cors-origins' must be configured with explicit trusted origins!"
            );
        }

        String[] rawEntries = corsOriginsRaw.split(",");
        List<String> origins = new ArrayList<>();

        for (String entry : rawEntries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.equals("*") || trimmed.contains("*")) {
                throw new IllegalStateException(
                    "FATAL: Production CORS misconfiguration: Wildcard origins ('*') are strictly prohibited in production! Offending entry: '" + trimmed + "'"
                );
            }

            validateOriginSyntax(trimmed);
            origins.add(trimmed);
        }

        if (origins.isEmpty()) {
            throw new IllegalStateException(
                "FATAL: Production CORS misconfiguration: 'app.cors-origins' allowlist is empty!"
            );
        }

        this.parsedAllowedOrigins = Collections.unmodifiableList(origins);
        log.info("Production CORS allowlist validated successfully: {} origin(s) configured", origins.size());
    }

    public static boolean determineIfProduction(Environment env) {
        if (env == null) {
            return false;
        }

        List<String> activeProfiles = Arrays.asList(env.getActiveProfiles());

        // Check if ANY active profile is a production profile (Production precedence rule)
        boolean hasProdActive = activeProfiles.stream().anyMatch(p ->
            "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)
        );
        if (hasProdActive) {
            return true;
        }

        // If no active profile specified, check default profiles
        if (activeProfiles.isEmpty()) {
            boolean hasProdDefault = Arrays.stream(env.getDefaultProfiles()).anyMatch(p ->
                "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)
            );
            if (hasProdDefault) {
                return true;
            }
        }

        // Check app.node-env
        String nodeEnv = env.getProperty("app.node-env");
        if ("production".equalsIgnoreCase(nodeEnv)) {
            return true;
        }

        return false;
    }

    private void validateOriginSyntax(String origin) {
        try {
            URI uri = URI.create(origin);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalStateException(
                    "FATAL: Malformed CORS origin in production allowlist: Scheme must be http or https. Value: '" + origin + "'"
                );
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalStateException(
                    "FATAL: Malformed CORS origin in production allowlist: Missing host. Value: '" + origin + "'"
                );
            }
            // Origins must not contain path, query, or fragment
            if (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/")) {
                throw new IllegalStateException(
                    "FATAL: Malformed CORS origin in production allowlist: Path not permitted. Value: '" + origin + "'"
                );
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalStateException(
                    "FATAL: Malformed CORS origin in production allowlist: Query/Fragment not permitted. Value: '" + origin + "'"
                );
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "FATAL: Malformed CORS origin in production allowlist: Invalid URI syntax. Value: '" + origin + "'", e
            );
        }
    }

    public boolean isProduction() {
        return isProduction;
    }

    public List<String> getParsedAllowedOrigins() {
        return parsedAllowedOrigins;
    }

    public String getCorsOriginsRaw() {
        return corsOriginsRaw;
    }
}

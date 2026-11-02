package com.sense.prolt_operations.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.sense.prolt_operations.exception.AppBaseException;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    // PARITY: Superadmin fixed OTP backdoor from Node backend (backend/src/modules/auth/otp.service.ts).
    // Configurable via properties: active in dev/test, disabled in prod.
    @Value("${app.auth.superadmin-otp-bypass.enabled:false}")
    private boolean superadminBypassEnabled;

    @Value("${app.auth.superadmin-otp-bypass.otp-value:}")
    private String superadminFixedOtp;

    @Value("${app.auth.superadmin-otp-bypass.email:super.admin@probus.io}")
    private String superadminEmail;

    // PARITY: In-memory store fallback allowed in dev/test; prohibited in prod.
    @Value("${app.auth.otp.allow-memory-fallback:false}")
    private boolean allowMemoryFallback;

    @Value("${spring.mail.username:}")
    private String smtpUser;

    @Value("${app.mail.from:\"Prolt Operations Centre\" <proltenergy.operations@probus.io>}")
    private String mailFrom;

    @Autowired(required = false)
    private org.springframework.core.env.Environment environment;

    public record StoredOtp(String otp, Instant expiresAt) {}
    private final Map<String, StoredOtp> inMemoryStore = new ConcurrentHashMap<>();

    public OtpService(UserRepository userRepository,
                      @Autowired(required = false) StringRedisTemplate redisTemplate,
                      @Autowired(required = false) JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
    }

    @jakarta.annotation.PostConstruct
    public void validateStartupConfiguration() {
        if (superadminBypassEnabled) {
            boolean isProd = environment != null && java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
            if (isProd) {
                throw new IllegalStateException("FATAL: Superadmin OTP bypass cannot be enabled in the 'prod' profile!");
            }
            if (superadminFixedOtp == null || superadminFixedOtp.isBlank()) {
                throw new IllegalStateException("FATAL: Superadmin OTP bypass is enabled, but app.auth.superadmin-otp-bypass.otp-value is not configured!");
            }
        }
    }

    public void generateAndSendOtp(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required");
        }
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);

        // Check if user exists
        userRepository.findByNormalizedEmail(normalizedEmail)
                .orElseThrow(() -> new NotFoundException("User with this email not found"));

        // PARITY: Superadmin fixed OTP bypass from Node backend (backend/src/modules/auth/otp.service.ts:L24-L28).
        if (superadminBypassEnabled && superadminEmail != null && superadminEmail.trim().equalsIgnoreCase(normalizedEmail)) {
            log.info("[DEV MODE] Superadmin login requested. Use fixed OTP.");
            return;
        }

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);

        // In prod without memory fallback: Redis is mandatory
        if (!allowMemoryFallback) {
            if (redisTemplate == null) {
                log.error("Redis is unavailable for OTP storage in production profile: RedisTemplate bean not found");
                throw new AppBaseException("Redis connection error", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            try {
                redisTemplate.opsForValue().set("otp:" + normalizedEmail, otp, 5, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.error("Redis is unavailable for OTP storage in production profile: {}", e.getMessage(), e);
                throw new AppBaseException("Redis connection error", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            // Dev/Test profile: allow in-memory fallback
            inMemoryStore.put(normalizedEmail, new StoredOtp(otp, expiresAt));
            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set("otp:" + normalizedEmail, otp, 5, TimeUnit.MINUTES);
                } catch (Exception e) {
                    log.warn("Redis write failed for OTP, using in-memory store (allowed in dev/test): {}", e.getMessage());
                }
            }
        }

        if (mailSender != null && smtpUser != null && !smtpUser.isBlank()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(mailFrom);
                helper.setTo(normalizedEmail);
                helper.setSubject("Your Login OTP - Prolt by Probus");
                helper.setText("Your One-Time Password (OTP) for login is: " + otp + ". It will expire in 5 minutes.", false);
                mailSender.send(message);
                log.info("OTP email sent successfully to {}", normalizedEmail);
            } catch (Exception e) {
                log.error("Failed to send OTP email", e);
                throw new AppBaseException("Something went very wrong!", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            if (allowMemoryFallback) {
                log.info("[DEV MODE] Generated OTP for {}: {}", normalizedEmail, otp);
            } else {
                log.info("OTP generated but no mail transport is configured");
            }
        }
    }

    public boolean verifyOtp(String email, String enteredOtp) {
        if (email == null || enteredOtp == null) {
            return false;
        }
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        String trimmedOtp = enteredOtp.trim();

        // PARITY: Superadmin fixed OTP backdoor from Node backend (backend/src/modules/auth/otp.service.ts:L20-L21, L106-L109).
        if (superadminBypassEnabled && superadminEmail != null && superadminEmail.trim().equalsIgnoreCase(normalizedEmail)) {
            return superadminFixedOtp != null && superadminFixedOtp.equals(trimmedOtp);
        }

        // In prod without memory fallback: must verify via Redis
        if (!allowMemoryFallback) {
            if (redisTemplate == null) {
                log.error("Redis is unavailable for OTP verification in production profile: RedisTemplate bean not found");
                throw new AppBaseException("Redis connection error", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            try {
                String redisOtp = redisTemplate.opsForValue().get("otp:" + normalizedEmail);
                if (redisOtp != null && redisOtp.equals(trimmedOtp)) {
                    redisTemplate.delete("otp:" + normalizedEmail);
                    return true;
                }
                return false;
            } catch (Exception e) {
                log.error("Redis is unavailable for OTP verification in production profile: {}", e.getMessage(), e);
                throw new AppBaseException("Redis connection error", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        // In dev/test: check in-memory store first
        StoredOtp stored = inMemoryStore.get(normalizedEmail);
        if (stored != null) {
            if (stored.expiresAt().isBefore(Instant.now())) {
                inMemoryStore.remove(normalizedEmail);
                return false;
            }
            if (stored.otp().equals(trimmedOtp)) {
                inMemoryStore.remove(normalizedEmail);
                if (redisTemplate != null) {
                    try {
                        redisTemplate.delete("otp:" + normalizedEmail);
                    } catch (Exception ignored) {
                    }
                }
                return true;
            }
        }

        if (redisTemplate != null) {
            try {
                String redisOtp = redisTemplate.opsForValue().get("otp:" + normalizedEmail);
                if (redisOtp != null && redisOtp.equals(trimmedOtp)) {
                    redisTemplate.delete("otp:" + normalizedEmail);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    public boolean isSuperadminBypassEnabled() {
        return superadminBypassEnabled;
    }

    public void setSuperadminBypassEnabled(boolean superadminBypassEnabled) {
        this.superadminBypassEnabled = superadminBypassEnabled;
    }

    public String getSuperadminFixedOtp() {
        return superadminFixedOtp;
    }

    public void setSuperadminFixedOtp(String superadminFixedOtp) {
        this.superadminFixedOtp = superadminFixedOtp;
    }

    public String getSuperadminEmail() {
        return superadminEmail;
    }

    public void setSuperadminEmail(String superadminEmail) {
        this.superadminEmail = superadminEmail;
    }

    public boolean isAllowMemoryFallback() {
        return allowMemoryFallback;
    }

    public void setAllowMemoryFallback(boolean allowMemoryFallback) {
        this.allowMemoryFallback = allowMemoryFallback;
    }

    public Map<String, StoredOtp> getInMemoryStore() {
        return inMemoryStore;
    }
}

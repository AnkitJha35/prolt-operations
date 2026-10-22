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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.repository.UserRepository;

import jakarta.mail.internet.MimeMessage;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    public static final String SUPERADMIN_EMAIL = "super.admin@probus.io";
    public static final String SUPERADMIN_FIXED_OTP = "749261";

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.mail.username:}")
    private String smtpUser;

    @Value("${app.mail.from:\"Prolt Operations Centre\" <proltenergy.operations@probus.io>}")
    private String mailFrom;

    private record StoredOtp(String otp, Instant expiresAt) {}
    private final Map<String, StoredOtp> inMemoryStore = new ConcurrentHashMap<>();

    public OtpService(UserRepository userRepository,
                      @Autowired(required = false) StringRedisTemplate redisTemplate,
                      @Autowired(required = false) JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
    }

    public void generateAndSendOtp(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required");
        }
        String normalizedEmail = email.trim().toLowerCase();

        // Check if user exists
        userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new NotFoundException("User with this email not found"));

        if (SUPERADMIN_EMAIL.equalsIgnoreCase(normalizedEmail)) {
            log.info("[DEV MODE] Superadmin login requested. Use fixed OTP.");
            return;
        }

        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);
        inMemoryStore.put(normalizedEmail, new StoredOtp(otp, expiresAt));

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("otp:" + normalizedEmail, otp, 5, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("Redis write failed for OTP: {}", e.getMessage());
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
                log.error("Failed to send OTP email to {}: {}", normalizedEmail, e.getMessage());
            }
        } else {
            log.info("[DEV MODE] Generated OTP for {}: {}", normalizedEmail, otp);
        }
    }

    public boolean verifyOtp(String email, String enteredOtp) {
        if (email == null || enteredOtp == null) {
            return false;
        }
        String normalizedEmail = email.trim().toLowerCase();

        // Superadmin bypass
        if (SUPERADMIN_EMAIL.equalsIgnoreCase(normalizedEmail)) {
            return SUPERADMIN_FIXED_OTP.equals(enteredOtp.trim());
        }

        StoredOtp stored = inMemoryStore.get(normalizedEmail);
        if (stored != null) {
            if (stored.expiresAt().isBefore(Instant.now())) {
                inMemoryStore.remove(normalizedEmail);
                return false;
            }
            if (stored.otp().equals(enteredOtp.trim())) {
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
                if (redisOtp != null && redisOtp.equals(enteredOtp.trim())) {
                    redisTemplate.delete("otp:" + normalizedEmail);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }
}

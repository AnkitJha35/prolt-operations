package com.sense.prolt_operations.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.exception.AppBaseException;
import com.sense.prolt_operations.repository.UserRepository;

class OtpServiceTest {

    private UserRepository userRepository;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private Logger logger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        logger = (Logger) org.slf4j.LoggerFactory.getLogger(OtpService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    @Test
    void normalizesEmailAndVerifiesCaseInsensitively() {
        OtpService service = serviceWithMemoryFallback(null);
        String mixedEmail = "User@Example.test ";
        String normalizedEmail = "user@example.test";
        when(userRepository.findByNormalizedEmail(normalizedEmail)).thenReturn(Optional.of(User.create()));

        // 1. Generate with mixed-case and verify with uppercase
        service.generateAndSendOtp(mixedEmail);
        assertThat(service.getInMemoryStore()).containsKey(normalizedEmail);
        String otp = service.getInMemoryStore().get(normalizedEmail).otp();
        assertThat(service.verifyOtp("USER@EXAMPLE.TEST", otp)).isTrue();

        // 2. Once verified, OTP is single-use and consumed
        assertThat(service.verifyOtp(mixedEmail, otp)).isFalse();

        // 3. Generate again with normalized email and verify with mixed-case email
        service.generateAndSendOtp(normalizedEmail);
        String otp2 = service.getInMemoryStore().get(normalizedEmail).otp();
        assertThat(service.verifyOtp(mixedEmail, otp2)).isTrue();
    }

    @Test
    void bypassEmailComparisonIsCaseInsensitive() {
        OtpService service = serviceWithMemoryFallback(null);
        service.setSuperadminBypassEnabled(true);
        service.setSuperadminEmail("super.admin@probus.io");
        service.setSuperadminFixedOtp("749261");

        assertThat(service.verifyOtp("Super.Admin@probus.io", "749261")).isTrue();
        assertThat(service.verifyOtp("super.admin@probus.io", "749261")).isTrue();
        assertThat(service.verifyOtp("wrong@probus.io", "749261")).isFalse();
        assertThat(service.verifyOtp("super.admin@probus.io", "wrong_otp")).isFalse();
    }

    @Test
    void mailFailurePropagatesAfterOtpIsStoredWithoutLoggingTheOtp() {
        org.springframework.mail.javamail.JavaMailSender mailSender = mock(org.springframework.mail.javamail.JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenThrow(new IllegalStateException("mail transport failed"));
        OtpService service = serviceWithMemoryFallback(mailSender);
        ReflectionTestUtils.setField(service, "smtpUser", "mailer@example.test");
        String email = "admin@example.test";
        when(userRepository.findByNormalizedEmail(email)).thenReturn(Optional.of(User.create()));

        assertThatThrownBy(() -> service.generateAndSendOtp(email))
                .isInstanceOf(AppBaseException.class)
                .hasMessage("Something went very wrong!");

        String storedOtp = service.getInMemoryStore().get(email).otp();
        assertThat(logMessages()).doesNotContain(storedOtp);
    }

    @Test
    void logsOtpOnlyWhenMemoryFallbackIsExplicitlyAllowed() {
        String email = "admin@example.test";
        when(userRepository.findByNormalizedEmail(email)).thenReturn(Optional.of(User.create()));

        OtpService devService = serviceWithMemoryFallback(null);
        devService.generateAndSendOtp(email);
        String devOtp = devService.getInMemoryStore().get(email).otp();
        assertThat(logMessages()).contains(devOtp);

        logAppender.list.clear();
        OtpService prodService = new OtpService(userRepository, redisTemplate, null);
        ReflectionTestUtils.setField(prodService, "allowMemoryFallback", false);
        ReflectionTestUtils.setField(prodService, "smtpUser", "");
        prodService.generateAndSendOtp(email);

        assertThat(logMessages()).doesNotContain(email);
        assertThat(logMessages()).doesNotContainPattern("\\b\\d{6}\\b");
    }

    private OtpService serviceWithMemoryFallback(org.springframework.mail.javamail.JavaMailSender mailSender) {
        OtpService service = new OtpService(userRepository, redisTemplate, mailSender);
        ReflectionTestUtils.setField(service, "allowMemoryFallback", true);
        ReflectionTestUtils.setField(service, "smtpUser", "");
        return service;
    }

    private String logMessages() {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
    }
}

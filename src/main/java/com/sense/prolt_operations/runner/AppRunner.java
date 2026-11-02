package com.sense.prolt_operations.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;

@Component
public class AppRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${app.admin.username:}")
    private String adminUsername;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    public AppRunner(UserRepository userRepository,
                     PasswordEncoder passwordEncoder,
                     Environment environment) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean allBlank = (adminUsername == null || adminUsername.isBlank())
                && (adminEmail == null || adminEmail.isBlank())
                && (adminPassword == null || adminPassword.isBlank());
        if (allBlank) {
            log.info("AppRunner initialized (admin sync skipped, credentials not configured).");
            return;
        }

        if (adminUsername == null || adminUsername.isBlank()
                || adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.info("AppRunner admin creation skipped: username, email, and password must all be non-blank");
            return;
        }

        String normalizedUsername = adminUsername.trim();
        String normalizedEmail = adminEmail.trim().toLowerCase(java.util.Locale.ROOT);

        if (userRepository.findByNormalizedUsername(normalizedUsername).isPresent()
                || userRepository.findByNormalizedEmail(normalizedEmail).isPresent()) {
            log.info("Admin user already exists, skipping: {}", adminUsername);
            return;
        }

        if (adminPassword.length() < 12 || adminPassword.equals(adminUsername)) {
            log.error("Admin user password policy violation: password must be at least 12 characters and not equal to username");
            if (isProdProfile()) {
                throw new IllegalStateException("Admin user password policy violation in production profile");
            }
            return;
        }

        User user = User.create();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setFullName("System Administrator");
        user.setPasswordHash(passwordEncoder.encode(adminPassword));
        user.setRole(Role.ADMIN);
        userRepository.save(user);
        log.info("Created default admin user: {}", adminUsername);
        log.info("AppRunner initialized successfully.");
    }

    private boolean isProdProfile() {
        if (environment == null) {
            return false;
        }
        if (environment.matchesProfiles("prod")) {
            return true;
        }
        for (String profile : environment.getActiveProfiles()) {
            if (profile != null && profile.toLowerCase().contains("prod")) {
                return true;
            }
        }
        return false;
    }
}

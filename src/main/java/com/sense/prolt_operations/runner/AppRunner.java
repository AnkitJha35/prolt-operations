package com.sense.prolt_operations.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AppRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AppRunner.class);

    private final com.sense.prolt_operations.repository.UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @org.springframework.beans.factory.annotation.Value("${app.admin.username:}")
    private String adminUsername;

    @org.springframework.beans.factory.annotation.Value("${app.admin.email:}")
    private String adminEmail;

    @org.springframework.beans.factory.annotation.Value("${app.admin.password:}")
    private String adminPassword;

    public AppRunner(com.sense.prolt_operations.repository.UserRepository userRepository,
                     org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (adminUsername == null || adminUsername.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("AppRunner initialized (admin sync skipped, credentials not configured).");
            return;
        }
        userRepository.findByUsername(adminUsername).ifPresentOrElse(
            user -> {
                if (user.getPasswordHash() == null || !passwordEncoder.matches(adminPassword, user.getPasswordHash())) {
                    user.setPasswordHash(passwordEncoder.encode(adminPassword));
                    userRepository.save(user);
                    log.info("Synchronized password hash for admin user: {}", adminUsername);
                }
            },
            () -> {
                com.sense.prolt_operations.entity.User user = com.sense.prolt_operations.entity.User.create();
                user.setUsername(adminUsername);
                user.setEmail(adminEmail);
                user.setPasswordHash(passwordEncoder.encode(adminPassword));
                user.setRole(com.sense.prolt_operations.entity.Role.ADMIN);
                userRepository.save(user);
                log.info("Created default admin user: {}", adminUsername);
            }
        );
        log.info("AppRunner initialized successfully.");
    }
}

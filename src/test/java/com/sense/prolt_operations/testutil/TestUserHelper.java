package com.sense.prolt_operations.testutil;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.JwtService;

@Component
public class TestUserHelper {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public TestUserHelper(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public static class TestUser {
        private final User user;
        private final String rawPassword;
        private final String rawToken;

        public TestUser(User user, String rawPassword, String rawToken) {
            this.user = user;
            this.rawPassword = rawPassword;
            this.rawToken = rawToken;
        }

        public User getUser() {
            return user;
        }

        public String getRawPassword() {
            return rawPassword;
        }

        public String getToken() {
            return rawToken;
        }

        public String getBearerToken() {
            return "Bearer " + rawToken;
        }
    }

    public TestUser createUser(Role role) {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return createUser(role, "test_" + uid, "test_" + uid + "@probus.io");
    }

    public TestUser createUser(Role role, String username, String email) {
        String rawPassword = "TestPassword_" + UUID.randomUUID().toString().replace("-", "") + "!";
        User user = User.create();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user = userRepository.save(user);
        String token = jwtService.generateToken(user);
        return new TestUser(user, rawPassword, token);
    }

    public TestUser createOrUpdateSuperAdmin(String email) {
        String rawPassword = "TestSuperAdminPassword_" + UUID.randomUUID().toString().replace("-", "") + "!";
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = User.create();
            u.setUsername("superadmin");
            u.setEmail(email);
            u.setFullName("System Administrator");
            return u;
        });
        user.setRole(Role.SUPER_ADMIN);
        user.setFullName("System Administrator");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user = userRepository.save(user);
        String token = jwtService.generateToken(user);
        return new TestUser(user, rawPassword, token);
    }

    public void deleteUser(TestUser testUser) {
        if (testUser != null && testUser.getUser() != null && testUser.getUser().getId() != null) {
            try {
                userRepository.deleteById(testUser.getUser().getId());
            } catch (Exception ignored) {
            }
        }
    }

    public void deleteUser(User user) {
        if (user != null && user.getId() != null) {
            try {
                userRepository.deleteById(user.getId());
            } catch (Exception ignored) {
            }
        }
    }
}

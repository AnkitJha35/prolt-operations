package com.sense.prolt_operations.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.dto.user.CreateUserDTO;
import com.sense.prolt_operations.dto.user.UpdateUserDTO;
import com.sense.prolt_operations.dto.user.UserDTO;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.entity.UserAuditLog;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.repository.UserAuditLogRepository;
import com.sense.prolt_operations.repository.UserRepository;

@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserAuditLogRepository userAuditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public UserService(UserRepository userRepository,
                       UserAuditLogRepository userAuditLogRepository,
                       PasswordEncoder passwordEncoder,
                       ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.userAuditLogRepository = userAuditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toUserDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserAuditLog> getAuditLogs() {
        return userAuditLogRepository.findAllByOrderByTimestampDesc();
    }

    public UserDTO createUser(CreateUserDTO dto, String performedBy) {
        if (dto == null || dto.username() == null || dto.username().isBlank()) {
            throw new BadRequestException("Username is required");
        }
        if (dto.email() == null || dto.email().isBlank()) {
            throw new BadRequestException("Email is required");
        }
        if (dto.role() == null || (!dto.role().equalsIgnoreCase("ADMIN") && !dto.role().equalsIgnoreCase("CLIENT"))) {
            throw new BadRequestException("Valid role is required (ADMIN or CLIENT)");
        }

        String username = dto.username().trim();
        String email = dto.email().trim();

        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            throw new BadRequestException("Username or email already exists");
        }

        String rawPassword = (dto.password() != null && !dto.password().isBlank())
                ? dto.password().trim()
                : UUID.randomUUID().toString().replace("-", "");
        String passwordHash = passwordEncoder.encode(rawPassword);

        User user = User.create();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setRole(Role.valueOf(dto.role().toUpperCase()));
        user.setHiddenModulesList(dto.hiddenModules() != null ? dto.hiddenModules() : Collections.emptyList());
        user.setReadOnlyModulesList(dto.readOnlyModules() != null ? dto.readOnlyModules() : Collections.emptyList());

        user = userRepository.save(user);

        // Audit log
        Map<String, Object> changes = new HashMap<>();
        changes.put("username", user.getUsername());
        changes.put("email", user.getEmail());
        changes.put("role", user.getRole().name());
        changes.put("hiddenModules", user.getHiddenModulesList());
        changes.put("readOnlyModules", user.getReadOnlyModulesList());

        createAuditLog("CREATE", user.getId(), user.getUsername(), performedBy, changes);

        log.info("User created successfully: {}", user.getUsername());
        return toUserDTO(user);
    }

    public UserDTO updateUser(String id, UpdateUserDTO dto, String performedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (dto.username() != null) {
            if (dto.username().isBlank()) {
                throw new BadRequestException("Username must be a valid string");
            }
        }
        if (dto.email() != null) {
            if (dto.email().isBlank()) {
                throw new BadRequestException("Email must be a valid string");
            }
        }
        if (dto.role() != null) {
            if (!dto.role().equalsIgnoreCase("ADMIN") && !dto.role().equalsIgnoreCase("CLIENT")) {
                throw new BadRequestException("Valid role is required (ADMIN or CLIENT)");
            }
        }

        Map<String, Object> changes = new HashMap<>();

        if (dto.username() != null && !dto.username().trim().equals(user.getUsername())) {
            changes.put("username", Map.of("old", user.getUsername(), "new", dto.username().trim()));
            user.setUsername(dto.username().trim());
        }
        if (dto.email() != null && !dto.email().trim().equals(user.getEmail())) {
            changes.put("email", Map.of("old", user.getEmail(), "new", dto.email().trim()));
            user.setEmail(dto.email().trim());
        }
        if (dto.role() != null && !dto.role().equalsIgnoreCase(user.getRole().name())) {
            changes.put("role", Map.of("old", user.getRole().name(), "new", dto.role().toUpperCase()));
            user.setRole(Role.valueOf(dto.role().toUpperCase()));
        }
        if (dto.hiddenModules() != null) {
            changes.put("hiddenModules", Map.of("old", user.getHiddenModulesList(), "new", dto.hiddenModules()));
            user.setHiddenModulesList(dto.hiddenModules());
        }
        if (dto.readOnlyModules() != null) {
            changes.put("readOnlyModules", Map.of("old", user.getReadOnlyModulesList(), "new", dto.readOnlyModules()));
            user.setReadOnlyModulesList(dto.readOnlyModules());
        }
        if (dto.password() != null && !dto.password().isBlank()) {
            changes.put("password", Map.of("old", "***", "new", "***"));
            user.setPasswordHash(passwordEncoder.encode(dto.password().trim()));
        }

        user = userRepository.save(user);

        if (!changes.isEmpty()) {
            createAuditLog("UPDATE", user.getId(), user.getUsername(), performedBy, changes);
        }

        log.info("User updated successfully: {}", user.getUsername());
        return toUserDTO(user);
    }

    public String deleteUser(String id, String performedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        userRepository.delete(user);

        createAuditLog("DELETE", id, user.getUsername(), performedBy, Map.of("deleted", true));

        log.info("User deleted successfully: {}", user.getUsername());
        return "User deleted successfully";
    }

    private void createAuditLog(String action, String targetUserId, String targetUser,
                                String performedBy, Object changes) {
        try {
            UserAuditLog audit = UserAuditLog.create();
            audit.setAction(action);
            audit.setTargetUserId(targetUserId);
            audit.setTargetUser(targetUser);
            audit.setPerformedBy(performedBy != null ? performedBy : "SYSTEM");
            audit.setChanges(objectMapper.writeValueAsString(changes));
            userAuditLogRepository.save(audit);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit log changes: {}", e.getMessage());
        }
    }

    private UserDTO toUserDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getHiddenModulesList(),
                user.getReadOnlyModulesList(),
                user.getCreatedAt()
        );
    }
}

package com.sense.prolt_operations.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.prolt_operations.dto.user.CreateUserDTO;
import com.sense.prolt_operations.dto.user.UpdateUserDTO;
import com.sense.prolt_operations.dto.user.UpdateUserStatusDTO;
import com.sense.prolt_operations.dto.user.UserDTO;
import com.sense.prolt_operations.entity.Role;
import com.sense.prolt_operations.entity.User;
import com.sense.prolt_operations.entity.UserAuditLog;
import com.sense.prolt_operations.exception.BadRequestException;
import com.sense.prolt_operations.exception.NotFoundException;
import com.sense.prolt_operations.repository.UserAuditLogRepository;
import com.sense.prolt_operations.repository.UserRepository;
import com.sense.prolt_operations.security.ProltUserPrincipal;
import com.sense.prolt_operations.security.state.UserSecurityStateProvider;

@Service
@Transactional
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final UserAuditLogRepository userAuditLogRepository;
    private final ObjectMapper objectMapper;
    private final UserSecurityStateProvider securityStateProvider;
    private final RefreshTokenService refreshTokenService;
    private final PlatformTransactionManager transactionManager;

    public UserService(UserRepository userRepository,
                       UserAuditLogRepository userAuditLogRepository,
                       ObjectMapper objectMapper,
                       UserSecurityStateProvider securityStateProvider,
                       RefreshTokenService refreshTokenService,
                       PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.userAuditLogRepository = userAuditLogRepository;
        this.objectMapper = objectMapper;
        this.securityStateProvider = securityStateProvider;
        this.refreshTokenService = refreshTokenService;
        this.transactionManager = transactionManager;
    }

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toUserDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<com.sense.prolt_operations.dto.user.UserAuditLogDTO> getAuditLogs() {
        return userAuditLogRepository.findAllByOrderByTimestampDesc()
                .stream()
                .map(com.sense.prolt_operations.dto.user.UserAuditLogDTO::fromEntity)
                .toList();
    }

    private String normalizeEmail(String email) {
        return (email != null) ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    public UserDTO createUser(CreateUserDTO dto, String performedBy) {
        if (dto == null || !StringUtils.hasText(dto.username())) {
            throw new BadRequestException("Username is required");
        }
        if (!StringUtils.hasText(dto.email())) {
            throw new BadRequestException("Email is required");
        }
        if (!StringUtils.hasText(dto.fullName())) {
            throw new BadRequestException("Full name is required");
        }
        if (dto.fullName().trim().length() > 100) {
            throw new BadRequestException("Full name must not exceed 100 characters");
        }
        if (dto.role() == null || (!dto.role().equalsIgnoreCase("ADMIN") && !dto.role().equalsIgnoreCase("CLIENT"))) {
            throw new BadRequestException("Valid role is required (ADMIN or CLIENT)");
        }

        String username = dto.username().trim();
        String email = normalizeEmail(dto.email());
        String fullName = dto.fullName().trim();

        if (userRepository.existsByNormalizedUsername(username) || userRepository.existsByNormalizedEmail(email)) {
            throw new BadRequestException("Username or email already exists");
        }

        User user = User.create();
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setRole(Role.valueOf(dto.role().toUpperCase(Locale.ROOT)));
        user.setHiddenModulesList(dto.hiddenModules() != null ? dto.hiddenModules() : Collections.emptyList());
        user.setReadOnlyModulesList(dto.readOnlyModules() != null ? dto.readOnlyModules() : Collections.emptyList());

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("Database unique constraint violation during user creation for username '{}' or email '{}': {}", username, email, e.getMessage());
            throw new BadRequestException("Username or email already exists");
        }

        // Audit log
        Map<String, Object> changes = new HashMap<>();
        changes.put("username", user.getUsername());
        changes.put("email", user.getEmail());
        changes.put("fullName", user.getFullName());
        changes.put("role", user.getRole().name());
        changes.put("hiddenModules", user.getHiddenModulesList());
        changes.put("readOnlyModules", user.getReadOnlyModulesList());

        createAuditLog("CREATE", user.getId(), user.getUsername(), performedBy, changes);

        log.info("User created successfully: {}", user.getUsername());
        return toUserDTO(user);
    }

    public UserDTO updateUser(String id, UpdateUserDTO dto, String performedBy) {
        return updateUser(id, dto, performedBy, null);
    }

    public UserDTO updateUser(String id, UpdateUserDTO dto, String performedBy, Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (dto.email() != null) {
            if (dto.email().isBlank()) {
                throw new BadRequestException("Email must be a valid string");
            }
        }
        if (dto.fullName() != null) {
            if (dto.fullName().isBlank()) {
                throw new BadRequestException("Full name must be a valid string");
            }
            if (dto.fullName().trim().length() > 100) {
                throw new BadRequestException("Full name must not exceed 100 characters");
            }
        }
        if (dto.role() != null) {
            if (!dto.role().equalsIgnoreCase("ADMIN") && !dto.role().equalsIgnoreCase("CLIENT") && !dto.role().equalsIgnoreCase("SUPER_ADMIN")) {
                throw new BadRequestException("Valid role is required (ADMIN, CLIENT, or SUPER_ADMIN)");
            }
        }

        // Guard: Prevent demotion of last active SUPER_ADMIN
        boolean isDemotingSuperAdmin = user.getRole() == Role.SUPER_ADMIN
                && user.isEnabled()
                && dto.role() != null
                && !dto.role().equalsIgnoreCase("SUPER_ADMIN");

        final String targetId = id;
        if (isDemotingSuperAdmin) {
            List<User> activeSuperAdmins = userRepository.findActiveUsersByRoleForUpdate(Role.SUPER_ADMIN);
            long remainingActive = activeSuperAdmins.stream()
                    .filter(u -> !u.getId().equals(targetId))
                    .count();
            if (remainingActive == 0) {
                createAuditLog("UPDATE_REJECTED", targetId, user.getUsername(), performedBy,
                        Map.of("reason", "Cannot demote last active Super Administrator"));
                throw new BadRequestException("Cannot demote the last active Super Administrator account");
            }
        }

        Map<String, Object> changes = new HashMap<>();
        boolean authAttributesChanged = false;

        if (dto.email() != null) {
            String normalizedNewEmail = normalizeEmail(dto.email());
            if (!normalizedNewEmail.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.existsByNormalizedEmailAndIdNot(normalizedNewEmail, user.getId())) {
                    throw new BadRequestException("Username or email already exists");
                }
                changes.put("email", Map.of("old", user.getEmail() != null ? user.getEmail() : "", "new", normalizedNewEmail));
                user.setEmail(normalizedNewEmail);
            }
        }
        if (dto.fullName() != null) {
            String trimmedFullName = dto.fullName().trim();
            if (!trimmedFullName.equals(user.getFullName())) {
                changes.put("fullName", Map.of("old", user.getFullName() != null ? user.getFullName() : "", "new", trimmedFullName));
                user.setFullName(trimmedFullName);
            }
        }
        if (dto.role() != null && !dto.role().equalsIgnoreCase(user.getRole().name())) {
            authAttributesChanged = true;
            changes.put("role", Map.of("old", user.getRole().name(), "new", dto.role().toUpperCase(Locale.ROOT)));
            user.setRole(Role.valueOf(dto.role().toUpperCase(Locale.ROOT)));
        }
        if (dto.hiddenModules() != null && !dto.hiddenModules().equals(user.getHiddenModulesList())) {
            authAttributesChanged = true;
            changes.put("hiddenModules", Map.of("old", user.getHiddenModulesList(), "new", dto.hiddenModules()));
            user.setHiddenModulesList(dto.hiddenModules());
        }
        if (dto.readOnlyModules() != null && !dto.readOnlyModules().equals(user.getReadOnlyModulesList())) {
            authAttributesChanged = true;
            changes.put("readOnlyModules", Map.of("old", user.getReadOnlyModulesList(), "new", dto.readOnlyModules()));
            user.setReadOnlyModulesList(dto.readOnlyModules());
        }

        if (authAttributesChanged) {
            user.incrementTokenVersion();
            refreshTokenService.revokeAllSessionsForUser(user.getId());
            changes.put("tokenVersion", user.getTokenVersion());
        }

        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("Database unique constraint violation during user update for user '{}': {}", id, e.getMessage());
            throw new BadRequestException("Username or email already exists");
        }

        if (authAttributesChanged) {
            securityStateProvider.updateSecurityState(user.getId(), user.isEnabled(), user.getTokenVersion());
        }

        if (!changes.isEmpty()) {
            createAuditLog("UPDATE", user.getId(), user.getUsername(), performedBy, changes);
        }

        log.info("User updated successfully: {} (authAttributesChanged={})", user.getUsername(), authAttributesChanged);
        return toUserDTO(user);
    }

    public UserDTO updateUserStatus(String id, UpdateUserStatusDTO dto, String performedBy, Authentication authentication) {
        if (dto == null || dto.enabled() == null) {
            throw new BadRequestException("enabled status is required");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (!dto.enabled()) {
            // Guard 1: Prevent self-disabling
            if (authentication != null && authentication.getPrincipal() instanceof ProltUserPrincipal principal) {
                if (principal.getId().equals(user.getId()) || principal.getUsername().equalsIgnoreCase(user.getUsername())) {
                    throw new BadRequestException("You cannot disable your own account");
                }
            } else if (performedBy != null && performedBy.equalsIgnoreCase(user.getUsername())) {
                throw new BadRequestException("You cannot disable your own account");
            }

            // Guard 2: Prevent disabling last active SUPER_ADMIN (concurrency-safe row locking)
            final String targetId = id;
            if (user.getRole() == Role.SUPER_ADMIN && user.isEnabled()) {
                List<User> activeSuperAdmins = userRepository.findActiveUsersByRoleForUpdate(Role.SUPER_ADMIN);
                long remainingActive = activeSuperAdmins.stream()
                        .filter(u -> !u.getId().equals(targetId))
                        .count();
                if (remainingActive == 0) {
                    throw new BadRequestException("Cannot disable the last active Super Administrator account");
                }
            }
        }

        if (user.isEnabled() != dto.enabled()) {
            boolean oldStatus = user.isEnabled();
            user.setEnabled(dto.enabled());
            if (!dto.enabled()) {
                // Invalidate all active sessions immediately upon account disablement
                user.incrementTokenVersion();
                refreshTokenService.revokeAllSessionsForUser(user.getId());
            }
            user = userRepository.saveAndFlush(user);

            Map<String, Object> changes = Map.of(
                "enabled", Map.of("old", oldStatus, "new", dto.enabled()),
                "tokenVersion", user.getTokenVersion()
            );
            createAuditLog("UPDATE_STATUS", user.getId(), user.getUsername(), performedBy, changes);
            securityStateProvider.updateSecurityState(user.getId(), user.isEnabled(), user.getTokenVersion());
            log.info("User status updated: {} enabled={}, tokenVersion={}", user.getUsername(), dto.enabled(), user.getTokenVersion());
        }

        return toUserDTO(user);
    }

    @Transactional
    public UserDTO revokeSessions(String id, String performedBy, Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Safeguard: Prevent accidental self-revocation by active admin
        if (authentication != null && authentication.getPrincipal() instanceof ProltUserPrincipal principal) {
            if (principal.getId().equals(user.getId()) || principal.getUsername().equalsIgnoreCase(user.getUsername())) {
                throw new BadRequestException("You cannot revoke your own active session");
            }
        } else if (performedBy != null && performedBy.equalsIgnoreCase(user.getUsername())) {
            throw new BadRequestException("You cannot revoke your own active session");
        }

        user.incrementTokenVersion();
        user = userRepository.saveAndFlush(user);
        refreshTokenService.revokeAllSessionsForUser(user.getId());

        createAuditLog("REVOKE_SESSIONS", user.getId(), user.getUsername(), performedBy,
                Map.of("revokedSessions", true, "tokenVersion", user.getTokenVersion()));
        securityStateProvider.updateSecurityState(user.getId(), user.isEnabled(), user.getTokenVersion());

        log.info("Sessions revoked for user '{}' by '{}' (new tokenVersion={})", user.getUsername(), performedBy, user.getTokenVersion());
        return toUserDTO(user);
    }

    public String deleteUser(String id, String performedBy) {
        return deleteUser(id, performedBy, null);
    }

    public String deleteUser(String id, String performedBy, Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Guard 1: Prevent self-deletion
        if (authentication != null && authentication.getPrincipal() instanceof ProltUserPrincipal principal) {
            if (principal.getId().equals(user.getId()) || principal.getUsername().equalsIgnoreCase(user.getUsername())) {
                createAuditLog("DELETE_REJECTED", user.getId(), user.getUsername(), performedBy,
                        Map.of("reason", "Self-deletion is prohibited"));
                throw new BadRequestException("You cannot delete your own account");
            }
        } else if (performedBy != null && performedBy.equalsIgnoreCase(user.getUsername())) {
            createAuditLog("DELETE_REJECTED", user.getId(), user.getUsername(), performedBy,
                    Map.of("reason", "Self-deletion is prohibited"));
            throw new BadRequestException("You cannot delete your own account");
        }

        // Guard 2: Prevent deletion of last active SUPER_ADMIN (concurrency-safe row locking)
        final String targetId = id;
        if (user.getRole() == Role.SUPER_ADMIN && user.isEnabled()) {
            List<User> activeSuperAdmins = userRepository.findActiveUsersByRoleForUpdate(Role.SUPER_ADMIN);
            long remainingActive = activeSuperAdmins.stream()
                    .filter(u -> !u.getId().equals(targetId))
                    .count();
            if (remainingActive == 0) {
                createAuditLog("DELETE_REJECTED", user.getId(), user.getUsername(), performedBy,
                        Map.of("reason", "Cannot delete last active Super Administrator"));
                throw new BadRequestException("Cannot delete the last active Super Administrator account");
            }
        }

        refreshTokenService.revokeAllSessionsForUser(id);
        userRepository.deleteById(id);
        userRepository.flush();
        securityStateProvider.evictSecurityState(id);

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
            TransactionTemplate tt = new TransactionTemplate(transactionManager);
            tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tt.execute(status -> {
                userAuditLogRepository.save(audit);
                return null;
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit log changes: {}", e.getMessage());
        }
    }

    private UserDTO toUserDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole() != null ? user.getRole().name() : null,
                user.isEnabled(),
                user.getHiddenModulesList(),
                user.getReadOnlyModulesList(),
                user.getCreatedAt()
        );
    }
}

package com.sense.prolt_operations.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.sense.prolt_operations.dto.user.CreateUserDTO;
import com.sense.prolt_operations.dto.user.UpdateUserDTO;
import com.sense.prolt_operations.dto.user.UpdateUserStatusDTO;
import com.sense.prolt_operations.dto.user.UserDTO;
import com.sense.prolt_operations.entity.UserAuditLog;
import com.sense.prolt_operations.security.ProltUserPrincipal;
import com.sense.prolt_operations.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "User administration (Super Admin only)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieve list of all users")
    public ResponseEntity<com.sense.prolt_operations.dto.response.ApiResponse<List<UserDTO>>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(com.sense.prolt_operations.dto.response.ApiResponse.ok(users));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Get audit logs", description = "Retrieve user audit logs")
    public ResponseEntity<com.sense.prolt_operations.dto.response.ApiResponse<List<com.sense.prolt_operations.dto.user.UserAuditLogDTO>>> getAuditLogs() {
        List<com.sense.prolt_operations.dto.user.UserAuditLogDTO> logs = userService.getAuditLogs();
        return ResponseEntity.ok(com.sense.prolt_operations.dto.response.ApiResponse.ok(logs));
    }

    @PostMapping
    @Operation(summary = "Create user", description = "Create a new user (Super Admin only)")
    public ResponseEntity<com.sense.prolt_operations.dto.response.ApiResponse<UserDTO>> createUser(@RequestBody CreateUserDTO dto, Authentication authentication) {
        String performedBy = extractPerformedBy(authentication);
        UserDTO created = userService.createUser(dto, performedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(com.sense.prolt_operations.dto.response.ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Update an existing user (Super Admin only)")
    public ResponseEntity<com.sense.prolt_operations.dto.response.ApiResponse<UserDTO>> updateUser(@PathVariable String id,
                                                          @RequestBody UpdateUserDTO dto,
                                                          Authentication authentication) {
        String performedBy = extractPerformedBy(authentication);
        UserDTO updated = userService.updateUser(id, dto, performedBy, authentication);
        return ResponseEntity.ok(com.sense.prolt_operations.dto.response.ApiResponse.ok(updated));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update user status", description = "Enable or disable a user account (Super Admin only)")
    public ResponseEntity<com.sense.prolt_operations.dto.response.ApiResponse<UserDTO>> updateUserStatus(@PathVariable String id,
                                                                @RequestBody @Valid UpdateUserStatusDTO dto,
                                                                Authentication authentication) {
        String performedBy = extractPerformedBy(authentication);
        UserDTO updated = userService.updateUserStatus(id, dto, performedBy, authentication);
        return ResponseEntity.ok(com.sense.prolt_operations.dto.response.ApiResponse.ok(updated));
    }

    @PostMapping("/{id}/revoke-sessions")
    @Operation(summary = "Revoke user sessions", description = "Invalidate all active JWT sessions for a user by incrementing token version (Super Admin only)")
    public ResponseEntity<com.sense.prolt_operations.dto.response.ApiResponse<UserDTO>> revokeUserSessions(@PathVariable String id,
                                                                  Authentication authentication) {
        String performedBy = extractPerformedBy(authentication);
        UserDTO updated = userService.revokeSessions(id, performedBy, authentication);
        return ResponseEntity.ok(com.sense.prolt_operations.dto.response.ApiResponse.ok(
            "All active sessions for user successfully revoked",
            updated
        ));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Delete an existing user (Super Admin only)")
    public ResponseEntity<com.sense.prolt_operations.dto.response.OperationResponse> deleteUser(@PathVariable String id,
                                                          Authentication authentication) {
        String performedBy = extractPerformedBy(authentication);
        String message = userService.deleteUser(id, performedBy, authentication);
        return ResponseEntity.ok(com.sense.prolt_operations.dto.response.OperationResponse.ok(message));
    }

    private String extractPerformedBy(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof ProltUserPrincipal p) {
            return p.getUsername();
        }
        if (authentication != null && authentication.getName() != null) {
            return authentication.getName();
        }
        return "SYSTEM";
    }
}

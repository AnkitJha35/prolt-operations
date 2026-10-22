package com.sense.prolt_operations.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sense.prolt_operations.dto.user.CreateUserDTO;
import com.sense.prolt_operations.dto.user.UpdateUserDTO;
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
    public ResponseEntity<Map<String, Object>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(Map.of("success", true, "data", users));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Get audit logs", description = "Retrieve user audit logs")
    public ResponseEntity<Map<String, Object>> getAuditLogs() {
        List<UserAuditLog> logs = userService.getAuditLogs();
        return ResponseEntity.ok(Map.of("success", true, "data", logs));
    }

    @PostMapping
    @Operation(summary = "Create user", description = "Create a new user (Super Admin only)")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody CreateUserDTO dto, Authentication authentication) {
        String performedBy = extractPerformedBy(authentication);
        UserDTO created = userService.createUser(dto, performedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "data", created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Update an existing user (Super Admin only)")
    public ResponseEntity<Map<String, Object>> updateUser(@PathVariable String id,
                                                          @RequestBody UpdateUserDTO dto,
                                                          Authentication authentication) {
        String performedBy = extractPerformedBy(authentication);
        UserDTO updated = userService.updateUser(id, dto, performedBy);
        return ResponseEntity.ok(Map.of("success", true, "data", updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Delete an existing user (Super Admin only)")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable String id,
                                                          Authentication authentication) {
        String performedBy = extractPerformedBy(authentication);
        String message = userService.deleteUser(id, performedBy);
        return ResponseEntity.ok(Map.of("success", true, "message", message));
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

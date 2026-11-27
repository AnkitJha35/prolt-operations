package com.sense.prolt_operations.dto.user;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserAuditLogDTO(
    String id,
    String action,
    String targetUserId,
    String targetUser,
    String performedBy,
    String changes,
    LocalDateTime timestamp
) {
    public static UserAuditLogDTO fromEntity(com.sense.prolt_operations.entity.UserAuditLog entity) {
        if (entity == null) {
            return null;
        }
        return new UserAuditLogDTO(
            entity.getId(),
            entity.getAction(),
            entity.getTargetUserId(),
            entity.getTargetUser(),
            entity.getPerformedBy(),
            entity.getChanges(),
            entity.getTimestamp()
        );
    }
}

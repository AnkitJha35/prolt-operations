package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "UserAuditLog", schema = "public")
public class UserAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "action")
    private String action;

    @Column(name = "targetUserId")
    private String targetUserId;

    @Column(name = "targetUser")
    private String targetUser;

    @Column(name = "performedBy")
    private String performedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes")
    private String changes;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @PrePersist
    public void prePersist() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    protected UserAuditLog() {
    }

    public static UserAuditLog create() {
        return new UserAuditLog();
    }

    public UserAuditLog(String id, String action, String targetUserId, String targetUser, String performedBy) {
        this.id = id;
        this.action = action;
        this.targetUserId = targetUserId;
        this.targetUser = targetUser;
        this.performedBy = performedBy;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(String targetUser) {
        this.targetUser = targetUser;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public String getChanges() {
        return changes;
    }

    public void setChanges(String changes) {
        this.changes = changes;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserAuditLog that = (UserAuditLog) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UserAuditLog{" + "id=" + id + ", " + "action=" + action + ", " + "targetUserId=" + targetUserId + ", " + "targetUser=" + targetUser + ", " + "performedBy=" + performedBy + ", " + "changes=" + changes + ", " + "timestamp=" + timestamp + "}";
    }
}

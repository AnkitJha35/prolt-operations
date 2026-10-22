package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "AuditLog", schema = "public")
public class AuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "tableName")
    private String tableName;

    @Column(name = "recordId")
    private String recordId;

    @Column(name = "action")
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "oldData")
    private String oldData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "newData")
    private String newData;

    @Column(name = "userId")
    private String userId;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    protected AuditLog() {
    }

    public static AuditLog create() {
        return new AuditLog();
    }

    public AuditLog(String id, String tableName, String recordId, String action, String oldData) {
        this.id = id;
        this.tableName = tableName;
        this.recordId = recordId;
        this.action = action;
        this.oldData = oldData;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOldData() {
        return oldData;
    }

    public void setOldData(String oldData) {
        this.oldData = oldData;
    }

    public String getNewData() {
        return newData;
    }

    public void setNewData(String newData) {
        this.newData = newData;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditLog that = (AuditLog) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AuditLog{" + "id=" + id + ", " + "tableName=" + tableName + ", " + "recordId=" + recordId + ", " + "action=" + action + ", " + "oldData=" + oldData + ", " + "newData=" + newData + ", " + "userId=" + userId + ", " + "createdAt=" + createdAt + "}";
    }
}

package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "user_refresh_sessions", schema = "public")
public class UserRefreshSession implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "userId", nullable = false)
    private String userId;

    @Column(name = "tokenHash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "tokenFamilyId", nullable = false, length = 36)
    private String tokenFamilyId;

    @Column(name = "tokenVersion", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expiresAt", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "lastUsedAt")
    private LocalDateTime lastUsedAt;

    @Column(name = "revokedAt")
    private LocalDateTime revokedAt;

    @Column(name = "replacedById", length = 36)
    private String replacedById;

    @Column(name = "createdIp", length = 45)
    private String createdIp;

    @Column(name = "userAgent", length = 500)
    private String userAgent;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public UserRefreshSession() {
    }

    public UserRefreshSession(String userId, String tokenHash, String tokenFamilyId,
                              int tokenVersion, LocalDateTime expiresAt,
                              String createdIp, String userAgent) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.tokenFamilyId = tokenFamilyId;
        this.tokenVersion = tokenVersion;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.createdIp = createdIp;
        this.userAgent = userAgent;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isReplaced() {
        return replacedById != null;
    }

    public boolean isActive() {
        return !isExpired() && !isRevoked() && !isReplaced();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getTokenFamilyId() {
        return tokenFamilyId;
    }

    public void setTokenFamilyId(String tokenFamilyId) {
        this.tokenFamilyId = tokenFamilyId;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getReplacedById() {
        return replacedById;
    }

    public void setReplacedById(String replacedById) {
        this.replacedById = replacedById;
    }

    public String getCreatedIp() {
        return createdIp;
    }

    public void setCreatedIp(String createdIp) {
        this.createdIp = createdIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserRefreshSession that = (UserRefreshSession) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UserRefreshSession{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", tokenFamilyId='" + tokenFamilyId + '\'' +
                ", tokenVersion=" + tokenVersion +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                ", revokedAt=" + revokedAt +
                ", replacedById='" + replacedById + '\'' +
                '}';
    }
}

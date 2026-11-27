package com.sense.prolt_operations.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "User", schema = "public")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private String id;

    @Column(name = "username", nullable = false, unique = true, updatable = false)
    private String username;

    @Column(name = "email")
    private String email;

    @Column(name = "fullName", length = 100, nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    @Column(name = "hiddenModules")
    private String[] hiddenModules = new String[0];

    @Column(name = "readOnlyModules")
    private String[] readOnlyModules = new String[0];

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "tokenVersion", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "createdAt")
    private LocalDateTime createdAt;

    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (hiddenModules == null) {
            hiddenModules = new String[0];
        }
        if (readOnlyModules == null) {
            readOnlyModules = new String[0];
        }
        if (role == null) {
            role = Role.CLIENT;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    protected User() {
    }

    public static User create() {
        return new User();
    }

    public User(String id, String username, String email, Role role) {
        this(id, username, email, null, role);
    }

    public User(String id, String username, String email, String fullName, Role role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String[] getHiddenModules() {
        return hiddenModules;
    }

    public void setHiddenModules(String[] hiddenModules) {
        this.hiddenModules = hiddenModules != null ? hiddenModules : new String[0];
    }

    public java.util.List<String> getHiddenModulesList() {
        return hiddenModules != null ? java.util.Arrays.asList(hiddenModules) : java.util.Collections.emptyList();
    }

    public void setHiddenModulesList(java.util.List<String> list) {
        this.hiddenModules = list != null ? list.toArray(new String[0]) : new String[0];
    }

    public String[] getReadOnlyModules() {
        return readOnlyModules;
    }

    public void setReadOnlyModules(String[] readOnlyModules) {
        this.readOnlyModules = readOnlyModules != null ? readOnlyModules : new String[0];
    }

    public java.util.List<String> getReadOnlyModulesList() {
        return readOnlyModules != null ? java.util.Arrays.asList(readOnlyModules) : java.util.Collections.emptyList();
    }

    public void setReadOnlyModulesList(java.util.List<String> list) {
        this.readOnlyModules = list != null ? list.toArray(new String[0]) : new String[0];
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public void incrementTokenVersion() {
        this.tokenVersion++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User that = (User) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{" + "id=" + id + ", " + "username=" + username + ", " + "email=" + email + ", " + "fullName=" + fullName + ", " + "enabled=" + enabled + ", " + "tokenVersion=" + tokenVersion + ", " + "createdAt=" + createdAt + ", " + "updatedAt=" + updatedAt + "}";
    }
}

package com.sense.prolt_operations.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class ProltUserPrincipal implements UserDetails {

    private final String id;
    private final String username;
    private final String role;
    private final List<String> hiddenModules;
    private final List<String> readOnlyModules;
    private final long issuedAt;
    private final long expiresAt;
    private final Collection<? extends GrantedAuthority> authorities;

    public ProltUserPrincipal(String id, String username, String role,
                              List<String> hiddenModules, List<String> readOnlyModules,
                              long issuedAt, long expiresAt) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.hiddenModules = hiddenModules != null ? hiddenModules : Collections.emptyList();
        this.readOnlyModules = readOnlyModules != null ? readOnlyModules : Collections.emptyList();
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + role),
            new SimpleGrantedAuthority(role)
        );
    }

    public String getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public List<String> getHiddenModules() {
        return hiddenModules;
    }

    public List<String> getReadOnlyModules() {
        return readOnlyModules;
    }

    public long getIssuedAt() {
        return issuedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}

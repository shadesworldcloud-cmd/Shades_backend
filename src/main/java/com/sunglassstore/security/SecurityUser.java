package com.sunglassstore.security;

import com.sunglassstore.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Adapts our User entity to Spring Security's UserDetails interface.
 * Flattens roles into ROLE_ prefixed authorities and adds raw permission names.
 */
@Getter
public class SecurityUser implements UserDetails {

    private final Long userId;
    private final String email;
    private final String passwordHash;
    private final String name;
    private final boolean active;
    private final boolean accountLocked;
    private final long passwordVersion;
    private final Set<GrantedAuthority> authorities;

    public SecurityUser(User user) {
        this.userId = user.getUserId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.name = user.getName();
        this.active = Boolean.TRUE.equals(user.getIsActive());
        this.accountLocked = Boolean.TRUE.equals(user.getAccountLocked());
        this.passwordVersion = passwordFingerprint(user.getPasswordHash());
        this.authorities = buildAuthorities(user);
    }

    /**
     * Ties access tokens to the current password without depending on a database
     * DATETIME conversion. LocalDateTime has no timezone, so using it as an epoch
     * caused otherwise unrelated profile updates to invalidate tokens when the
     * JDBC, JVM and MySQL session timezones differed.
     */
    private static long passwordFingerprint(String passwordHash) {
        if (passwordHash == null) {
            return 0L;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private Set<GrantedAuthority> buildAuthorities(User user) {
        Set<GrantedAuthority> auths = new HashSet<>();
        user.getRoles().forEach(role -> {
            auths.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleName()));
            role.getPermissions().forEach(permission ->
                    auths.add(new SimpleGrantedAuthority(permission.getPermissionName()))
            );
        });
        return auths;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}

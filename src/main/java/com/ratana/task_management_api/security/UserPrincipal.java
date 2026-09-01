package com.ratana.task_management_api.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ratana.task_management_api.enums.UserStatus;
import com.ratana.task_management_api.model.User;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final UUID departmentId;
    private final Set<String> permissions;
    private final Set<String> roles;

    @JsonIgnore
    private final String password;

    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;

    public UserPrincipal(UUID id, String email, String password,
                         UUID departmentId,
                         Collection<? extends GrantedAuthority> authorities,
                         boolean enabled) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.departmentId = departmentId;
        this.authorities = authorities;
        this.enabled = enabled;
        this.roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> auth.startsWith("ROLE_"))
                .map(auth -> auth.replace("ROLE_", ""))
                .collect(Collectors.toUnmodifiableSet());

        this.permissions = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(auth -> !auth.startsWith("ROLE_"))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static UserPrincipal createUserPrincipal(User user) {
        Collection<? extends GrantedAuthority> authorities =
                Stream.ofNullable(user.getRoles())
                        .flatMap(Collection::stream)
                        .flatMap(role -> Stream.concat(
                                Stream.of(new SimpleGrantedAuthority("ROLE_" + role.getName())),
                                Stream.ofNullable(role.getPermissions()).flatMap(Collection::stream)
                                        .map(perm -> new SimpleGrantedAuthority(perm.getName()))
                        ))
                        .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));

        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getDepartment() != null
                        ? user.getDepartment().getId()
                        : null,
                authorities,
                user.getStatus() == UserStatus.ACTIVE
        );
    }

    /** Permission authorities only (excludes ROLE_*). */
    public Set<String> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission);
    }

    public boolean hasAnyPermission(String... required) {
        if (required == null || required.length == 0) {
            return false;
        }
        for (String permission : required) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public @Nullable String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}

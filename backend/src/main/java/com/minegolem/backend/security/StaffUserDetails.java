package com.minegolem.backend.security;


import com.minegolem.backend.domain.entity.StaffUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class StaffUserDetails implements UserDetails {

    private final UUID userId;
    private final UUID gymId;
    private final String email;
    private final String passwordHash;
    private final String roleName;
    private final Set<GrantedAuthority> authorities;
    private final boolean active;

    public StaffUserDetails(StaffUser staffUser) {
        this.userId = staffUser.getId();
        this.gymId = staffUser.getGym().getId();
        this.email = staffUser.getEmail();
        this.passwordHash = staffUser.getPasswordHash();
        this.roleName = staffUser.getRole().getName();
        this.active = staffUser.isActive();

        this.authorities = Stream.concat(
            Stream.of(new SimpleGrantedAuthority("ROLE_" + roleName)),
            staffUser.getPermissionNames().stream().map(SimpleGrantedAuthority::new)
        ).collect(Collectors.toSet());
    }

    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return active; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return active; }
}

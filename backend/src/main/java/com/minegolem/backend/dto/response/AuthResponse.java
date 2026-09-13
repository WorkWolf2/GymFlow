package com.minegolem.backend.dto.response;

import com.minegolem.backend.security.StaffUserDetails;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UUID userId,
    UUID gymId,
    String email,
    String fullName,
    String role,
    Set<String> permissions
) {
    public static AuthResponse of(String accessToken, String refreshToken,
                                   long expiresIn, StaffUserDetails u,
                                   String fullName) {
        Set<String> perms = u.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn,
            u.getUserId(), u.getGymId(), u.getEmail(), fullName, u.getRoleName(), perms);
    }
}

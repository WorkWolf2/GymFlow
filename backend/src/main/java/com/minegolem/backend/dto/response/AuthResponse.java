package com.minegolem.backend.dto.response;

import com.minegolem.backend.security.StaffUserDetails;

import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UUID userId,
    UUID gymId,
    String email,
    String fullName,
    String role
) {
    public static AuthResponse of(String accessToken, String refreshToken,
                                   long expiresIn, StaffUserDetails u,
                                   String fullName) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn,
            u.getUserId(), u.getGymId(), u.getEmail(), fullName, u.getRoleName());
    }
}

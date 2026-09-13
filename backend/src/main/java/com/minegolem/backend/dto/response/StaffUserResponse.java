package com.minegolem.backend.dto.response;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record StaffUserResponse(
    UUID id,
    UUID gymId,
    String email,
    String firstName,
    String lastName,
    String fullName,
    Long roleId,
    String roleName,
    Set<String> permissions,
    boolean active,
    LocalDateTime lastLoginAt,
    LocalDateTime createdAt
) {}

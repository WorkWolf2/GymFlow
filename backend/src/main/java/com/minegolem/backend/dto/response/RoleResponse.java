package com.minegolem.backend.dto.response;

import java.util.Set;

public record RoleResponse(
    Long id,
    String name,
    Set<String> permissions
) {}

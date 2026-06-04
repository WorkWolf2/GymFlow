package com.minegolem.backend.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record ExpiringItemResponse(
    UUID userId,
    String userFullName,
    LocalDate expiryDate,
    String detail
) {}

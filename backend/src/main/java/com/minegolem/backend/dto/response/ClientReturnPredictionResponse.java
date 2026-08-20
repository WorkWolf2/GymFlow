package com.minegolem.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ClientReturnPredictionResponse(
    int score,
    String level,
    String label,
    String color,
    String badgeClass,
    int activeBlocks,
    LocalDateTime lastAccessTime,
    Long daysSinceLastAccess,
    String timeAgoText,
    long accessesLast30Days,
    List<String> factors
) {}

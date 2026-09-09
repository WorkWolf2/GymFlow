package com.minegolem.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DossierProgressResponse(
    UUID id,
    LocalDate recordedDate,
    BigDecimal weight,
    BigDecimal height,
    BigDecimal bodyFatPercentage,
    BigDecimal muscleMass,
    String measurements,
    String observations,
    String customParameters,
    String biaUrl,
    String progressNotes,
    String performanceNotes,
    String coachNotes,
    String criticalIssues,
    String changesMade,
    LocalDate nextCheckDate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

package com.minegolem.backend.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DossierProgramResponse(
    UUID id,
    String programFileUrl,
    LocalDate startDate,
    LocalDate reviewDate,
    String changesMade,
    String coachName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

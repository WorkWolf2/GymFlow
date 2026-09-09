package com.minegolem.backend.dto.response;

import com.minegolem.backend.domain.enums.ExperienceLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DossierProfileResponse(
    UUID id,
    LocalDate firstRegistrationDate,
    String mainGoal,
    String secondaryGoals,
    ExperienceLevel experienceLevel,
    BigDecimal initialWeight,
    BigDecimal initialHeight,
    String initialBiaUrl,
    String initialMeasurements,
    String initialAssessment,
    String initialLimitations,
    LocalDateTime updatedAt
) {}

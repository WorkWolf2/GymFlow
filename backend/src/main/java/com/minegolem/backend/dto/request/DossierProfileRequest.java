package com.minegolem.backend.dto.request;

import com.minegolem.backend.domain.enums.ExperienceLevel;
import java.math.BigDecimal;

public record DossierProfileRequest(
    String mainGoal,
    String secondaryGoals,
    ExperienceLevel experienceLevel,
    BigDecimal initialWeight,
    BigDecimal initialHeight,
    String initialMeasurements,
    String initialAssessment,
    String initialLimitations
) {}

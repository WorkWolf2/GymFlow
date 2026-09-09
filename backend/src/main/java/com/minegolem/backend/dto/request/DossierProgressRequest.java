package com.minegolem.backend.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DossierProgressRequest(
    @NotNull LocalDate recordedDate,
    @DecimalMin("0.0") BigDecimal weight,
    @DecimalMin("0.0") BigDecimal height,
    @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal bodyFatPercentage,
    @DecimalMin("0.0") BigDecimal muscleMass,
    @Size(max=10000) String measurements,
    @Size(max=10000) String observations,
    @Size(max=10000) String customParameters,
    @Size(max=10000) String progressNotes,
    @Size(max=10000) String performanceNotes,
    @Size(max=10000) String coachNotes,
    @Size(max=10000) String criticalIssues,
    @Size(max=10000) String changesMade,
    LocalDate nextCheckDate
) {}

package com.minegolem.backend.dto.request;

import java.time.LocalDate;

public record DossierProgramRequest(
    LocalDate startDate,
    LocalDate reviewDate,
    String changesMade,
    String coachName
) {}

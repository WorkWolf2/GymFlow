package com.minegolem.backend.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record UserPackageInfo(
    UUID id,
    String name,
    String color,
    LocalDate startDate,
    LocalDate endDate
) {}

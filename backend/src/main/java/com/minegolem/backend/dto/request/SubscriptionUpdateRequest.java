package com.minegolem.backend.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SubscriptionUpdateRequest(
    UUID subscriptionTypeId,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    BigDecimal price,
    String notes
) {}

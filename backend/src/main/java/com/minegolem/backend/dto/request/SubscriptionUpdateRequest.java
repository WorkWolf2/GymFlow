package com.minegolem.backend.dto.request;

import com.minegolem.backend.domain.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SubscriptionUpdateRequest(
    UUID subscriptionTypeId,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    BigDecimal price,
    String notes,
    PaymentMethod paymentMethod,
    Integer totalEntries,
    Integer remainingEntries
) {}
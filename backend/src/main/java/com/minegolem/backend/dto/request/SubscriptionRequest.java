package com.minegolem.backend.dto.request;

import com.minegolem.backend.domain.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SubscriptionRequest(
    @NotNull UUID userId,
    @NotNull UUID subscriptionTypeId,
    @NotNull LocalDate startDate,
    @NotNull @DecimalMin("0") BigDecimal price,
    @DecimalMin("0.00") BigDecimal discountAmount,
    @NotNull PaymentMethod paymentMethod,
    String notes
) {}

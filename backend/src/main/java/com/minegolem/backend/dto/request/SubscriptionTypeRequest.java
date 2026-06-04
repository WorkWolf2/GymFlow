package com.minegolem.backend.dto.request;

import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionTypeRequest(
    @NotBlank String name,
    SubscriptionTypeEnum type,
    @DecimalMin("0.00") BigDecimal defaultPrice,
    Integer validityDays,
    LocalDate forcedExpiry,
    String description,
    String color,
    Boolean active
) {}

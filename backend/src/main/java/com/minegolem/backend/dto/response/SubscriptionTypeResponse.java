package com.minegolem.backend.dto.response;

import com.minegolem.backend.domain.entity.SubscriptionType;
import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SubscriptionTypeResponse(
    UUID id,
    String name,
    SubscriptionTypeEnum type,
    BigDecimal defaultPrice,
    Integer validityDays,
    LocalDate forcedExpiry,
    String description,
    String color,
    boolean active
) {
    public static SubscriptionTypeResponse from(SubscriptionType type) {
        return new SubscriptionTypeResponse(
            type.getId(),
            type.getName(),
            type.getType(),
            type.getBasePrice(),
            type.getValidityDays(),
            type.getForcedExpiry(),
            type.getDescription(),
            type.getColor(),
            type.isActive()
        );
    }
}

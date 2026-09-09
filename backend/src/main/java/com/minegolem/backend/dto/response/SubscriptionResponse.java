package com.minegolem.backend.dto.response;

import com.minegolem.backend.domain.entity.Subscription;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SubscriptionResponse(
    UUID id,
    UUID userId,
    SubscriptionTypeResponse subscriptionType,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal price,
    String notes,
    boolean active,
    LocalDate suspendedFrom,
    LocalDate suspendedTo,
    boolean suspended,
    boolean stopAndGoApplied,
    boolean annual,
    boolean canApplyStopAndGo
) {
    public static SubscriptionResponse from(Subscription subscription) {
        boolean isAnnual = subscription.isAnnual();
        boolean notDeleted = subscription.getDeletedAt() == null;
        boolean notExpired = !subscription.isExpired();
        boolean canApply = notDeleted && notExpired && isAnnual && !subscription.isCurrentlySuspended();

        return new SubscriptionResponse(
            subscription.getId(),
            subscription.getUser().getId(),
            SubscriptionTypeResponse.from(subscription.getSubscriptionType()),
            subscription.getStartDate(),
            subscription.getEndDate(),
            subscription.getPrice(),
            subscription.getNotes(),
            subscription.isActive(),
            subscription.getSuspendedFrom(),
            subscription.getSuspendedTo(),
            subscription.isCurrentlySuspended(),
            subscription.isStopAndGoApplied(),
            isAnnual,
            canApply
        );
    }
}


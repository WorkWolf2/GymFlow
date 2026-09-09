package com.minegolem.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subscription_type_id", nullable = false)
    private SubscriptionType subscriptionType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private StaffUser createdBy;

    @Column(name = "suspended_from")
    private LocalDate suspendedFrom;

    @Column(name = "suspended_to")
    private LocalDate suspendedTo;

    @Column(name = "stop_and_go_applied", nullable = false)
    @Builder.Default
    private boolean stopAndGoApplied = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean isSuspended(LocalDate date) {
        return suspendedFrom != null && suspendedTo != null && !date.isBefore(suspendedFrom) && !date.isAfter(suspendedTo);
    }

    public boolean isCurrentlySuspended() {
        return isSuspended(LocalDate.now());
    }

    public boolean isActive() {
        LocalDate now = LocalDate.now();
        return deletedAt == null
            && !endDate.isBefore(now)
            && !startDate.isAfter(now)
            && !isCurrentlySuspended();
    }

    public boolean isExpired() {
        return endDate.isBefore(LocalDate.now());
    }

    public boolean isAnnual() {
        if (subscriptionType == null) return false;
        Integer days = subscriptionType.getValidityDays();
        String name = subscriptionType.getName();
        return (days != null && days >= 360) || (name != null && name.toLowerCase().contains("annuale"));
    }

}

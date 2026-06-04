package com.minegolem.backend.domain.entity;


import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "subscription_types")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionType extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionTypeEnum type;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Column(name = "validity_days")
    private Integer validityDays;

    @Column(name = "forced_expiry")
    private LocalDate forcedExpiry;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 7)
    @Builder.Default
    private String color = "#6366f1";

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}

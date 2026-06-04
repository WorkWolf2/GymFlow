package com.minegolem.backend.domain.entity;


import com.minegolem.backend.domain.enums.DenialReason;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accesses")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Access {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "nfc_tag_uid")
    private String nfcTagUid;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "device_ip")
    private String deviceIp;

    @Column(name = "access_time", nullable = false)
    @Builder.Default
    private LocalDateTime accessTime = LocalDateTime.now();

    @Column(nullable = false)
    private boolean granted;

    @Enumerated(EnumType.STRING)
    @Column(name = "denial_reason")
    private DenialReason denialReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

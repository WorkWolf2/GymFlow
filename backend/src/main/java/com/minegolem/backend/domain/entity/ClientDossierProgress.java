package com.minegolem.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "client_dossier_progress")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClientDossierProgress extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "recorded_date", nullable = false) private LocalDate recordedDate;
    @Column(precision = 7, scale = 2) private BigDecimal weight;
    @Column(precision = 7, scale = 2) private BigDecimal height;
    @Column(name = "body_fat_percentage", precision = 5, scale = 2) private BigDecimal bodyFatPercentage;
    @Column(name = "muscle_mass", precision = 7, scale = 2) private BigDecimal muscleMass;
    @Column(columnDefinition = "TEXT") private String measurements;
    @Column(columnDefinition = "TEXT") private String observations;
    @Column(name = "custom_parameters", columnDefinition = "TEXT") private String customParameters;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
}

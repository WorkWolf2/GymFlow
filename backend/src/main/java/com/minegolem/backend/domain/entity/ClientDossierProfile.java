package com.minegolem.backend.domain.entity;

import com.minegolem.backend.domain.enums.ExperienceLevel;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "client_dossier_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClientDossierProfile extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "main_goal", columnDefinition = "TEXT")
    private String mainGoal;

    @Column(name = "secondary_goals", columnDefinition = "TEXT")
    private String secondaryGoals;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", length = 20)
    private ExperienceLevel experienceLevel;

    @Column(name = "initial_weight")
    private BigDecimal initialWeight;

    @Column(name = "initial_height")
    private BigDecimal initialHeight;

    @Column(name = "initial_bia_file_path", length = 1024)
    private String initialBiaFilePath;

    @Column(name = "initial_measurements", columnDefinition = "TEXT")
    private String initialMeasurements;

    @Column(name = "initial_assessment", columnDefinition = "TEXT")
    private String initialAssessment;

    @Column(name = "initial_limitations", columnDefinition = "TEXT")
    private String initialLimitations;
}

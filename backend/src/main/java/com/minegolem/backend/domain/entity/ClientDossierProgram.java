package com.minegolem.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_dossier_programs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClientDossierProgram extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "program_file_path", length = 1024)
    private String programFilePath;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(name = "changes_made", columnDefinition = "TEXT")
    private String changesMade;

    @Column(name = "coach_name", length = 150)
    private String coachName;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

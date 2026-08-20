package com.minegolem.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "client_dossier_notes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClientDossierNote extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "author_id") private StaffUser author;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
}

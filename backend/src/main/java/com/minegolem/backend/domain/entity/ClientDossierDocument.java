package com.minegolem.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "client_dossier_documents")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClientDossierDocument extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 255) private String name;
    @Column(name = "file_path", nullable = false, length = 1024) private String filePath;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
}

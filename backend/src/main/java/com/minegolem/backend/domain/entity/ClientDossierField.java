package com.minegolem.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "client_dossier_fields")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClientDossierField extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "field_name", nullable = false, length = 100) private String fieldName;
    @Column(name = "field_value", columnDefinition = "TEXT") private String fieldValue;
    @Column(name = "value_type", nullable = false, length = 30) private String valueType;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
}

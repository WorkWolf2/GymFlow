package com.minegolem.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "medical_certificates")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalCertificate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "file_path")
    private String filePath;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean isValid() {
        return deletedAt == null && expiryDate != null && !expiryDate.isBefore(LocalDate.now());
    }

    public boolean isExpiringSoon(int daysThreshold) {
        return deletedAt == null
            && expiryDate != null
            && !expiryDate.isBefore(LocalDate.now())
            && expiryDate.isBefore(LocalDate.now().plusDays(daysThreshold));
    }

    public enum Status {
        VALID, EXPIRING_SOON, EXPIRED, MISSING
    }

    public Status getStatus() {
        if (deletedAt != null) return Status.MISSING;
        if (expiryDate == null) return Status.MISSING;
        if (expiryDate.isBefore(LocalDate.now())) return Status.EXPIRED;
        if (expiryDate.isBefore(LocalDate.now().plusDays(30))) return Status.EXPIRING_SOON;
        return Status.VALID;
    }
}

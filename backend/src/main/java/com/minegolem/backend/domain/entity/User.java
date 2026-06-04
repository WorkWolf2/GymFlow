package com.minegolem.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_code", nullable = false, unique = true, updatable = false)
    private Long clientCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String email;
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "birth_place")
    private String birthPlace;

    @Column(name = "birth_province", length = 2)
    private String birthProvince;

    @Column(length = 1)
    private String sex;

    @Column(name = "fiscal_code")
    private String fiscalCode;

    private String address;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "avatar_path")
    private String avatarPath;

    @Column(name = "doc_front_path")
    private String docFrontPath;

    @Column(name = "doc_back_path")
    private String docBackPath;

    @Column(name = "signature_path")
    private String signaturePath;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}

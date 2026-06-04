package com.minegolem.backend.dto.response;

import com.minegolem.backend.domain.entity.MedicalCertificate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
    UUID id,
    Long clientCode,
    String firstName,
    String lastName,
    String fullName,
    String email,
    String phone,
    LocalDate birthDate,
    String birthPlace,
    String birthProvince,
    String sex,
    String fiscalCode,
    String address,
    String notes,
    String avatarPath,
    String docFrontPath,
    String docBackPath,
    boolean active,
    LocalDateTime createdAt,
    // computed
    boolean hasActiveSubscription,
    MedicalCertificate.Status certStatus,
    boolean hasCertificate,
    LocalDate certIssuedDate,
    LocalDate certExpiryDate,
    String nfcTagUid
) {}

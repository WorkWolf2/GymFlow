package com.minegolem.backend.repository;


import com.minegolem.backend.domain.entity.MedicalCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicalCertificateRepository extends JpaRepository<MedicalCertificate, UUID> {

    @Query("""
        SELECT mc FROM MedicalCertificate mc
        WHERE mc.user.id = :userId
          AND mc.deletedAt IS NULL
        ORDER BY mc.expiryDate DESC
        """)
    List<MedicalCertificate> findActiveByUser(@Param("userId") UUID userId);

    Optional<MedicalCertificate> findFirstByUser_IdAndDeletedAtIsNullOrderByExpiryDateDesc(UUID userId);

    @Query("""
        SELECT mc FROM MedicalCertificate mc
        WHERE mc.user.gym.id = :gymId
          AND mc.deletedAt IS NULL
          AND mc.expiryDate BETWEEN :from AND :to
        """)
    List<MedicalCertificate> findExpiringSoon(@Param("gymId") UUID gymId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    @Query("""
        SELECT mc FROM MedicalCertificate mc
        JOIN FETCH mc.user u
        WHERE u.gym.id = :gymId
          AND u.deletedAt IS NULL
          AND mc.deletedAt IS NULL
          AND mc.expiryDate BETWEEN :from AND :to
        ORDER BY mc.expiryDate ASC, u.lastName ASC, u.firstName ASC
        """)
    List<MedicalCertificate> findForExpiryReport(@Param("gymId") UUID gymId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    @Query("""
        SELECT mc FROM MedicalCertificate mc
        JOIN FETCH mc.user u
        WHERE u.gym.id = :gymId
          AND u.deletedAt IS NULL
          AND mc.deletedAt IS NULL
          AND mc.expiryDate >= :today
          AND NOT EXISTS (
              SELECT newer.id FROM MedicalCertificate newer
              WHERE newer.user.id = u.id
                AND newer.deletedAt IS NULL
                AND newer.expiryDate > mc.expiryDate
          )
        ORDER BY mc.expiryDate ASC, u.lastName ASC, u.firstName ASC
        """)
    List<MedicalCertificate> findLatestValidForReport(@Param("gymId") UUID gymId,
                                                       @Param("today") LocalDate today);
}

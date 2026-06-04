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
}

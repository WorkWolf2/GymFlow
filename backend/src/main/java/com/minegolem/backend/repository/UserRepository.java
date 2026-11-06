package com.minegolem.backend.repository;


import com.minegolem.backend.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query(value = """
        SELECT u FROM User u
        WHERE u.gym.id = :gymId
          AND u.deletedAt IS NULL
          AND (:hasNfc IS NULL OR :hasNfc = false OR EXISTS (
               SELECT n.id FROM NfcTag n WHERE n.user.id = u.id AND n.active = true
          ))
          AND (:withoutNfc IS NULL OR :withoutNfc = false OR NOT EXISTS (
               SELECT n.id FROM NfcTag n WHERE n.user.id = u.id AND n.active = true
          ))
          AND (:activeSubOnly IS NULL OR :activeSubOnly = false OR EXISTS (
               SELECT s.id FROM Subscription s 
               WHERE s.user.id = u.id AND s.deletedAt IS NULL 
                 AND s.startDate <= :today AND s.endDate >= :today
                 AND (s.suspendedFrom IS NULL OR :today < s.suspendedFrom OR :today > s.suspendedTo)
          ))
          AND (:expiredSubOnly IS NULL OR :expiredSubOnly = false OR NOT EXISTS (
               SELECT s.id FROM Subscription s 
               WHERE s.user.id = u.id AND s.deletedAt IS NULL 
                 AND s.startDate <= :today AND s.endDate >= :today
                 AND (s.suspendedFrom IS NULL OR :today < s.suspendedFrom OR :today > s.suspendedTo)
          ))
          AND (:validCertOnly IS NULL OR :validCertOnly = false OR EXISTS (
               SELECT mc.id FROM MedicalCertificate mc 
               WHERE mc.user.id = u.id AND mc.deletedAt IS NULL 
                 AND mc.expiryDate >= :today
          ))
          AND (:expiredCertOnly IS NULL OR :expiredCertOnly = false OR NOT EXISTS (
               SELECT mc.id FROM MedicalCertificate mc 
               WHERE mc.user.id = u.id AND mc.deletedAt IS NULL 
                 AND mc.expiryDate >= :today
          ))
          AND (
              :searchLower IS NULL OR :searchLower = '' OR (
                  (:exactCode IS NOT NULL AND u.clientCode = :exactCode)
                  OR (u.clientCode IS NOT NULL AND CAST(u.clientCode AS string) LIKE CONCAT(:searchLower, '%'))
                  OR LOWER(u.firstName) LIKE CONCAT('%', :searchLower, '%')
                  OR LOWER(u.lastName) LIKE CONCAT('%', :searchLower, '%')
                  OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE CONCAT('%', :searchLower, '%')
                  OR LOWER(CONCAT(u.lastName, ' ', u.firstName)) LIKE CONCAT('%', :searchLower, '%')
                  OR (:word1 IS NOT NULL AND :word2 IS NOT NULL AND (
                      (LOWER(u.firstName) LIKE CONCAT('%', :word1, '%') AND LOWER(u.lastName) LIKE CONCAT('%', :word2, '%'))
                      OR
                      (LOWER(u.firstName) LIKE CONCAT('%', :word2, '%') AND LOWER(u.lastName) LIKE CONCAT('%', :word1, '%'))
                  ))
                  OR LOWER(u.email) LIKE CONCAT('%', :searchLower, '%')
                  OR LOWER(u.fiscalCode) LIKE CONCAT('%', :searchLower, '%')
                  OR (:searchPhone IS NOT NULL AND LOWER(u.phone) LIKE CONCAT('%', :searchPhone, '%'))
                  OR EXISTS (
                      SELECT t.id FROM NfcTag t WHERE t.user.id = u.id AND t.active = true AND LOWER(t.tagUid) LIKE CONCAT('%', :searchLower, '%')
                  )
                  OR EXISTS (
                      SELECT sub.id FROM Subscription sub
                      WHERE sub.user.id = u.id
                        AND sub.deletedAt IS NULL
                        AND LOWER(sub.subscriptionType.name) LIKE CONCAT('%', :searchLower, '%')
                  )
              )
          )
        ORDER BY
          CASE
            WHEN :exactCode IS NOT NULL AND u.clientCode = :exactCode THEN 0
            WHEN :searchLower IS NOT NULL AND (LOWER(u.lastName) = :searchLower OR LOWER(u.firstName) = :searchLower) THEN 1
            WHEN :searchLower IS NOT NULL AND (LOWER(u.lastName) LIKE CONCAT(:searchLower, '%') OR LOWER(u.firstName) LIKE CONCAT(:searchLower, '%')) THEN 2
            WHEN :searchLower IS NOT NULL AND CAST(u.clientCode AS string) LIKE CONCAT(:searchLower, '%') THEN 3
            ELSE 10
          END ASC,
          u.lastName ASC,
          u.firstName ASC
        """,
        countQuery = """
        SELECT COUNT(u) FROM User u
        WHERE u.gym.id = :gymId
          AND u.deletedAt IS NULL
          AND (:hasNfc IS NULL OR :hasNfc = false OR EXISTS (
               SELECT n.id FROM NfcTag n WHERE n.user.id = u.id AND n.active = true
          ))
          AND (:withoutNfc IS NULL OR :withoutNfc = false OR NOT EXISTS (
               SELECT n.id FROM NfcTag n WHERE n.user.id = u.id AND n.active = true
          ))
          AND (:activeSubOnly IS NULL OR :activeSubOnly = false OR EXISTS (
               SELECT s.id FROM Subscription s 
               WHERE s.user.id = u.id AND s.deletedAt IS NULL 
                 AND s.startDate <= :today AND s.endDate >= :today
                 AND (s.suspendedFrom IS NULL OR :today < s.suspendedFrom OR :today > s.suspendedTo)
          ))
          AND (:expiredSubOnly IS NULL OR :expiredSubOnly = false OR NOT EXISTS (
               SELECT s.id FROM Subscription s 
               WHERE s.user.id = u.id AND s.deletedAt IS NULL 
                 AND s.startDate <= :today AND s.endDate >= :today
                 AND (s.suspendedFrom IS NULL OR :today < s.suspendedFrom OR :today > s.suspendedTo)
          ))
          AND (:validCertOnly IS NULL OR :validCertOnly = false OR EXISTS (
               SELECT mc.id FROM MedicalCertificate mc 
               WHERE mc.user.id = u.id AND mc.deletedAt IS NULL 
                 AND mc.expiryDate >= :today
          ))
          AND (:expiredCertOnly IS NULL OR :expiredCertOnly = false OR NOT EXISTS (
               SELECT mc.id FROM MedicalCertificate mc 
               WHERE mc.user.id = u.id AND mc.deletedAt IS NULL 
                 AND mc.expiryDate >= :today
          ))
          AND (
              :searchLower IS NULL OR :searchLower = '' OR (
                  (:exactCode IS NOT NULL AND u.clientCode = :exactCode)
                  OR (u.clientCode IS NOT NULL AND CAST(u.clientCode AS string) LIKE CONCAT(:searchLower, '%'))
                  OR LOWER(u.firstName) LIKE CONCAT('%', :searchLower, '%')
                  OR LOWER(u.lastName) LIKE CONCAT('%', :searchLower, '%')
                  OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE CONCAT('%', :searchLower, '%')
                  OR LOWER(CONCAT(u.lastName, ' ', u.firstName)) LIKE CONCAT('%', :searchLower, '%')
                  OR (:word1 IS NOT NULL AND :word2 IS NOT NULL AND (
                      (LOWER(u.firstName) LIKE CONCAT('%', :word1, '%') AND LOWER(u.lastName) LIKE CONCAT('%', :word2, '%'))
                      OR
                      (LOWER(u.firstName) LIKE CONCAT('%', :word2, '%') AND LOWER(u.lastName) LIKE CONCAT('%', :word1, '%'))
                  ))
                  OR LOWER(u.email) LIKE CONCAT('%', :searchLower, '%')
                  OR LOWER(u.fiscalCode) LIKE CONCAT('%', :searchLower, '%')
                  OR (:searchPhone IS NOT NULL AND LOWER(u.phone) LIKE CONCAT('%', :searchPhone, '%'))
                  OR EXISTS (
                      SELECT t.id FROM NfcTag t WHERE t.user.id = u.id AND t.active = true AND LOWER(t.tagUid) LIKE CONCAT('%', :searchLower, '%')
                  )
                  OR EXISTS (
                      SELECT sub.id FROM Subscription sub
                      WHERE sub.user.id = u.id
                        AND sub.deletedAt IS NULL
                        AND LOWER(sub.subscriptionType.name) LIKE CONCAT('%', :searchLower, '%')
                  )
              )
          )
        """)
    Page<User> searchByGymAdvanced(@Param("gymId") UUID gymId,
                                   @Param("searchLower") String searchLower,
                                   @Param("exactCode") Long exactCode,
                                   @Param("searchPhone") String searchPhone,
                                   @Param("word1") String word1,
                                   @Param("word2") String word2,
                                   @Param("hasNfc") Boolean hasNfc,
                                   @Param("withoutNfc") Boolean withoutNfc,
                                   @Param("activeSubOnly") Boolean activeSubOnly,
                                   @Param("expiredSubOnly") Boolean expiredSubOnly,
                                   @Param("validCertOnly") Boolean validCertOnly,
                                   @Param("expiredCertOnly") Boolean expiredCertOnly,
                                   @Param("today") java.time.LocalDate today,
                                   Pageable pageable);

    default Page<User> searchByGym(UUID gymId, String search, Boolean withoutNfc, Pageable pageable) {
        return searchByGymAdvanced(gymId, search != null ? search.toLowerCase() : null, null, null, null, null,
            null, withoutNfc, null, null, null, null, java.time.LocalDate.now(), pageable);
    }


    Optional<User> findByIdAndGymIdAndDeletedAtIsNull(UUID id, UUID gymId);

    Optional<User> findFirstByGymIdAndPhoneAndDeletedAtIsNull(UUID gymId, String phone);

    Optional<User> findByClientCodeAndDeletedAtIsNull(Long clientCode);

    @Query("SELECT COALESCE(MAX(u.clientCode), 0) + 1 FROM User u")
    Long nextClientCode();

    boolean existsByEmailAndGymId(String email, UUID gymId);

    @Query("""
        SELECT u FROM User u
        JOIN NfcTag t ON t.user.id = u.id
        WHERE t.tagUid = :tagUid
          AND t.active = true
          AND u.deletedAt IS NULL
        """)
    Optional<User> findByNfcTagUid(@Param("tagUid") String tagUid);

    long countByGymIdAndActiveTrueAndDeletedAtIsNull(UUID gymId);

    @Query("""
        SELECT u FROM User u
        WHERE u.gym.id = :gymId
          AND u.deletedAt IS NULL
          AND u.active = true
          AND u.email IS NOT NULL
          AND u.email <> ''
        """)
    java.util.List<User> findAllWithValidEmailByGymId(@Param("gymId") UUID gymId);

    List<User> findByGymIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(UUID gymId);

    @Query("""
        SELECT u FROM User u
        WHERE u.gym.id = :gymId
          AND u.deletedAt IS NULL
          AND NOT EXISTS (
              SELECT mc.id FROM MedicalCertificate mc
              WHERE mc.user.id = u.id
                AND mc.deletedAt IS NULL
                AND mc.expiryDate >= :today
          )
        ORDER BY u.lastName ASC, u.firstName ASC
        """)
    List<User> findWithoutValidCertificate(@Param("gymId") UUID gymId,
                                            @Param("today") java.time.LocalDate today);
}

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
        ORDER BY u.lastName ASC, u.firstName ASC
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
        """)
    Page<User> findAllByGymFiltered(@Param("gymId") UUID gymId,
                                    @Param("hasNfc") Boolean hasNfc,
                                    @Param("withoutNfc") Boolean withoutNfc,
                                    @Param("activeSubOnly") Boolean activeSubOnly,
                                    @Param("expiredSubOnly") Boolean expiredSubOnly,
                                    @Param("validCertOnly") Boolean validCertOnly,
                                    @Param("expiredCertOnly") Boolean expiredCertOnly,
                                    @Param("today") java.time.LocalDate today,
                                    Pageable pageable);

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
              (:exactCode IS NOT NULL AND u.clientCode = :exactCode)
              OR (u.clientCode IS NOT NULL AND CAST(u.clientCode AS string) LIKE :codePrefixPattern)
              OR LOWER(u.firstName) LIKE :searchPattern
              OR LOWER(u.lastName) LIKE :searchPattern
              OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE :searchPattern
              OR LOWER(CONCAT(u.lastName, ' ', u.firstName)) LIKE :searchPattern
              OR (:hasWords = true AND (
                  (LOWER(u.firstName) LIKE :w1Pattern AND LOWER(u.lastName) LIKE :w2Pattern)
                  OR
                  (LOWER(u.firstName) LIKE :w2Pattern AND LOWER(u.lastName) LIKE :w1Pattern)
              ))
              OR LOWER(u.email) LIKE :searchPattern
              OR LOWER(u.fiscalCode) LIKE :searchPattern
              OR (:hasPhone = true AND LOWER(u.phone) LIKE :phonePattern)
              OR EXISTS (
                  SELECT t.id FROM NfcTag t WHERE t.user.id = u.id AND t.active = true AND LOWER(t.tagUid) LIKE :searchPattern
              )
              OR EXISTS (
                  SELECT sub.id FROM Subscription sub
                  WHERE sub.user.id = u.id
                    AND sub.deletedAt IS NULL
                    AND LOWER(sub.subscriptionType.name) LIKE :searchPattern
              )
          )
        ORDER BY
          CASE
            WHEN :exactCode IS NOT NULL AND u.clientCode = :exactCode THEN 0
            WHEN LOWER(u.lastName) = :searchLower OR LOWER(u.firstName) = :searchLower THEN 1
            WHEN LOWER(u.lastName) LIKE :namePrefixPattern OR LOWER(u.firstName) LIKE :namePrefixPattern THEN 2
            WHEN u.clientCode IS NOT NULL AND CAST(u.clientCode AS string) LIKE :codePrefixPattern THEN 3
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
              (:exactCode IS NOT NULL AND u.clientCode = :exactCode)
              OR (u.clientCode IS NOT NULL AND CAST(u.clientCode AS string) LIKE :codePrefixPattern)
              OR LOWER(u.firstName) LIKE :searchPattern
              OR LOWER(u.lastName) LIKE :searchPattern
              OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE :searchPattern
              OR LOWER(CONCAT(u.lastName, ' ', u.firstName)) LIKE :searchPattern
              OR (:hasWords = true AND (
                  (LOWER(u.firstName) LIKE :w1Pattern AND LOWER(u.lastName) LIKE :w2Pattern)
                  OR
                  (LOWER(u.firstName) LIKE :w2Pattern AND LOWER(u.lastName) LIKE :w1Pattern)
              ))
              OR LOWER(u.email) LIKE :searchPattern
              OR LOWER(u.fiscalCode) LIKE :searchPattern
              OR (:hasPhone = true AND LOWER(u.phone) LIKE :phonePattern)
              OR EXISTS (
                  SELECT t.id FROM NfcTag t WHERE t.user.id = u.id AND t.active = true AND LOWER(t.tagUid) LIKE :searchPattern
              )
              OR EXISTS (
                  SELECT sub.id FROM Subscription sub
                  WHERE sub.user.id = u.id
                    AND sub.deletedAt IS NULL
                    AND LOWER(sub.subscriptionType.name) LIKE :searchPattern
              )
          )
        """)
    Page<User> searchByGymFiltered(@Param("gymId") UUID gymId,
                                   @Param("searchLower") String searchLower,
                                   @Param("searchPattern") String searchPattern,
                                   @Param("namePrefixPattern") String namePrefixPattern,
                                   @Param("codePrefixPattern") String codePrefixPattern,
                                   @Param("exactCode") Long exactCode,
                                   @Param("hasPhone") boolean hasPhone,
                                   @Param("phonePattern") String phonePattern,
                                   @Param("hasWords") boolean hasWords,
                                   @Param("w1Pattern") String w1Pattern,
                                   @Param("w2Pattern") String w2Pattern,
                                   @Param("hasNfc") Boolean hasNfc,
                                   @Param("withoutNfc") Boolean withoutNfc,
                                   @Param("activeSubOnly") Boolean activeSubOnly,
                                   @Param("expiredSubOnly") Boolean expiredSubOnly,
                                   @Param("validCertOnly") Boolean validCertOnly,
                                   @Param("expiredCertOnly") Boolean expiredCertOnly,
                                   @Param("today") java.time.LocalDate today,
                                   Pageable pageable);

    default Page<User> searchByGym(UUID gymId, String search, Boolean withoutNfc, Pageable pageable) {
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        if (cleanSearch == null) {
            return findAllByGymFiltered(gymId, null, withoutNfc, null, null, null, null, java.time.LocalDate.now(), pageable);
        }
        String lower = cleanSearch.toLowerCase();
        return searchByGymFiltered(
            gymId, lower, "%" + lower + "%", lower + "%", cleanSearch + "%",
            null, false, "", false, "", "",
            null, withoutNfc, null, null, null, null,
            java.time.LocalDate.now(), pageable
        );
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

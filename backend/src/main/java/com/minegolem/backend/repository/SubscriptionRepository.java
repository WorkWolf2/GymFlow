package com.minegolem.backend.repository;


import com.minegolem.backend.domain.entity.Subscription;
import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findByUserIdAndDeletedAtIsNullOrderByStartDateDesc(UUID userId);

    @Query("""
        SELECT s FROM Subscription s
        JOIN FETCH s.user u
        JOIN FETCH s.subscriptionType
        WHERE u.gym.id = :gymId
          AND u.deletedAt IS NULL
          AND s.deletedAt IS NULL
          AND s.startDate <= :to
          AND s.endDate >= :from
        """)
    List<Subscription> findByGymAndDateOverlap(@Param("gymId") UUID gymId,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    @Query("""
        SELECT s FROM Subscription s
        WHERE s.user.id = :userId
          AND s.deletedAt IS NULL
          AND s.subscriptionType.type = :type
          AND s.startDate <= :today
          AND s.endDate >= :today
        """)
    List<Subscription> findActiveByUserAndType(@Param("userId") UUID userId,
                                               @Param("type") SubscriptionTypeEnum type,
                                               @Param("today") LocalDate today);

    @Query("""
        SELECT s FROM Subscription s
        WHERE s.user.gym.id = :gymId
          AND s.deletedAt IS NULL
          AND s.endDate BETWEEN :from AND :to
        ORDER BY s.endDate ASC
        """)
    List<Subscription> findExpiringSoon(@Param("gymId") UUID gymId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    @Query("""
        SELECT s FROM Subscription s
        JOIN FETCH s.user u
        JOIN FETCH s.subscriptionType st
        WHERE u.gym.id = :gymId
          AND u.deletedAt IS NULL
          AND u.active = true
          AND s.deletedAt IS NULL
          AND st.type = :type
          AND s.endDate = :expiryDate
          AND u.email IS NOT NULL
          AND u.email <> ''
          AND NOT EXISTS (
              SELECT renewed.id FROM Subscription renewed
              WHERE renewed.user.id = u.id
                AND renewed.id <> s.id
                AND renewed.deletedAt IS NULL
                AND renewed.subscriptionType.type = :type
                AND renewed.endDate > s.endDate
          )
        """)
    List<Subscription> findExpiringForAutomaticEmail(@Param("gymId") UUID gymId,
                                                     @Param("type") SubscriptionTypeEnum type,
                                                     @Param("expiryDate") LocalDate expiryDate);

    long countByUserGymIdAndDeletedAtIsNullAndEndDateGreaterThanEqual(UUID gymId, LocalDate today);

    @Query("""
        SELECT s FROM Subscription s
        JOIN FETCH s.user u
        JOIN FETCH s.subscriptionType
        WHERE u.gym.id = :gymId
          AND u.deletedAt IS NULL
          AND s.deletedAt IS NULL
          AND s.endDate BETWEEN :from AND :to
        ORDER BY s.endDate ASC, u.lastName ASC, u.firstName ASC
        """)
    List<Subscription> findForExpiryReport(@Param("gymId") UUID gymId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    @Query("""
        SELECT s FROM Subscription s
        JOIN FETCH s.user u
        JOIN FETCH s.subscriptionType
        WHERE u.gym.id = :gymId
          AND u.deletedAt IS NULL
          AND s.deletedAt IS NULL
          AND s.startDate BETWEEN :from AND :to
        ORDER BY s.startDate ASC, u.lastName ASC, u.firstName ASC
        """)
    List<Subscription> findForStartReport(@Param("gymId") UUID gymId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);
}

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

    long countByUserGymIdAndDeletedAtIsNullAndEndDateGreaterThanEqual(UUID gymId, LocalDate today);
}

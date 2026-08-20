package com.minegolem.backend.repository;

import com.minegolem.backend.domain.entity.EmailNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EmailNotificationLogRepository extends JpaRepository<EmailNotificationLog, UUID> {

    @Query("SELECT COUNT(e) > 0 FROM EmailNotificationLog e " +
           "WHERE e.notificationType = :notificationType " +
           "AND e.targetType = :targetType " +
           "AND e.targetId = :targetId " +
           "AND e.daysBefore = :daysBefore")
    boolean existsByNotificationTypeAndTargetTypeAndTargetIdAndDaysBefore(
        @Param("notificationType") String notificationType,
        @Param("targetType") String targetType,
        @Param("targetId") UUID targetId,
        @Param("daysBefore") int daysBefore
    );
}

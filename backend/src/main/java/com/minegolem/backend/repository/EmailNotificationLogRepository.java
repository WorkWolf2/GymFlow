package com.minegolem.backend.repository;

import com.minegolem.backend.domain.entity.EmailNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EmailNotificationLogRepository extends JpaRepository<EmailNotificationLog, UUID> {

    boolean existsByNotificationTypeAndTargetTypeAndTargetIdAndDaysBefore(
        String notificationType,
        String targetType,
        UUID targetId,
        int daysBefore
    );
}

package com.minegolem.backend.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "email_notification_logs",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_email_notification_target",
            columnNames = {"notification_type", "target_type", "target_id", "days_before"}
        )
    }
)
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailNotificationLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "gym_id", nullable = false)
    private UUID gymId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "days_before", nullable = false)
    private int daysBefore;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}

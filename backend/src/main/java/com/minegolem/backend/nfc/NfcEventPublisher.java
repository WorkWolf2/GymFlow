package com.minegolem.backend.nfc;


import com.minegolem.backend.domain.entity.Access;
import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.domain.enums.DenialReason;
import com.minegolem.backend.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NfcEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final RealtimeEventService realtimeEventService;

    public record AccessEvent(
        String type,
        UUID accessId,
        UUID userId,
        Long clientCode,
        String userName,
        String avatarPath,
        String tagUid,
        boolean granted,
        DenialReason denialReason,
        LocalDateTime timestamp,
        String deviceId
    ) {}

    public record UnknownTagEvent(
        String type,
        String tagUid,
        String deviceId,
        LocalDateTime timestamp
    ) {}

    public void publishAccessEvent(Access access, User user, String deviceId) {
        AccessEvent event = new AccessEvent(
            "ACCESS_EVENT",
            access.getId(),
            user != null ? user.getId() : null,
            user != null ? user.getClientCode() : null,
            user != null ? user.getFullName() : "Unknown",
            user != null ? user.getAvatarPath() : null,
            access.getNfcTagUid(),
            access.isGranted(),
            access.getDenialReason(),
            access.getAccessTime(),
            deviceId
        );

        messagingTemplate.convertAndSend("/topic/accesses", event);
        if (access.getGym() != null) {
            realtimeEventService.publish(access.getGym().getId(), "ACCESS", "CREATED", access.getId());
            realtimeEventService.publish(access.getGym().getId(), "DASHBOARD", "UPDATED", access.getId());
        }
        log.debug("Published access event: granted={} user={}", access.isGranted(),
            user != null ? user.getFullName() : "unknown");
    }

    public void publishUnknownTag(String tagUid, String deviceId) {
        UnknownTagEvent event = new UnknownTagEvent(
            "TAG_UNKNOWN",
            tagUid,
            deviceId,
            LocalDateTime.now()
        );
        messagingTemplate.convertAndSend("/topic/nfc/unknown-tag", event);
        log.info("Published unknown tag event: {}", tagUid);
    }
}

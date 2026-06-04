package com.minegolem.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RealtimeEventService {

    private final SimpMessagingTemplate messagingTemplate;

    public record RealtimeEvent(
        String type,
        String action,
        UUID gymId,
        String entityId,
        Map<String, Object> payload,
        Instant timestamp
    ) {}

    public void publish(UUID gymId, String type, String action) {
        publish(gymId, type, action, null, Map.of());
    }

    public void publish(UUID gymId, String type, String action, UUID entityId) {
        publish(gymId, type, action, entityId != null ? entityId.toString() : null, Map.of());
    }

    public void publish(UUID gymId, String type, String action, String entityId, Map<String, Object> payload) {
        RealtimeEvent event = new RealtimeEvent(
            type,
            action,
            gymId,
            entityId,
            payload != null ? payload : Map.of(),
            Instant.now()
        );
        messagingTemplate.convertAndSend("/topic/realtime", event);
    }
}

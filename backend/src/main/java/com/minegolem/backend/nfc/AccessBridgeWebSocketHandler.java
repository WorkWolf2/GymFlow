package com.minegolem.backend.nfc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minegolem.backend.dto.request.AccessBridgeRequest;
import com.minegolem.backend.dto.response.AccessBridgeResponse;
import com.minegolem.backend.service.AccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Raw WebSocket endpoint used only by the local access bridge. The bridge starts
 * the outbound connection; the VPS never needs to reach the local network.
 */
@Component
@Slf4j
public class AccessBridgeWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AccessService accessService;

    public AccessBridgeWebSocketHandler(ObjectMapper objectMapper, @Lazy AccessService accessService) {
        this.objectMapper = objectMapper;
        this.accessService = accessService;
    }

    @Value("${access.bridge.api-key:}")
    private String bridgeApiKey;

    private final ConcurrentMap<UUID, WebSocketSession> sessionsByGym = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> gymBySession = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Access bridge WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode payload = objectMapper.readTree(message.getPayload());
        String type = payload.path("type").asText();

        if ("REGISTER".equals(type)) {
            register(session, payload);
            return;
        }

        UUID gymId = gymBySession.get(session.getId());
        if (gymId == null) {
            closeUnauthorized(session, "REGISTER richiesto prima di inviare eventi");
            return;
        }

        if ("SCAN".equals(type)) {
            handleScan(session, payload);
        } else if ("COMMAND_RESULT".equals(type)) {
            log.info("Bridge command result gym={} commandId={} success={}", gymId,
                payload.path("commandId").asText(), payload.path("success").asBoolean());
        } else if ("PING".equals(type)) {
            send(session, Map.of("type", "PONG"));
        } else {
            send(session, Map.of("type", "ERROR", "message", "Messaggio bridge non supportato"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID gymId = gymBySession.remove(session.getId());
        if (gymId != null) {
            sessionsByGym.remove(gymId, session);
            log.warn("Access bridge disconnected for gym {}: {}", gymId, status);
        }
    }

    public boolean sendOpenDoor(UUID gymId, int relaySeconds, String source) {
        WebSocketSession session = sessionsByGym.get(gymId);
        if (session == null || !session.isOpen()) {
            log.warn("No connected access bridge for gym {}; door command not sent", gymId);
            return false;
        }

        try {
            send(session, Map.of(
                "type", "OPEN_DOOR",
                "commandId", UUID.randomUUID().toString(),
                "relaySeconds", relaySeconds,
                "source", source
            ));
            return true;
        } catch (IOException e) {
            sessionsByGym.remove(gymId, session);
            log.error("Cannot send door command to bridge for gym {}", gymId, e);
            return false;
        }
    }

    private void register(WebSocketSession session, JsonNode payload) throws IOException {
        String apiKey = payload.path("apiKey").asText();
        if (!accessService.isValidBridgeApiKey(apiKey)) {
            closeUnauthorized(session, "Chiave bridge non valida");
            return;
        }

        UUID gymId;
        try {
            gymId = UUID.fromString(payload.path("gymId").asText());
        } catch (IllegalArgumentException e) {
            closeUnauthorized(session, "gymId non valido");
            return;
        }

        WebSocketSession oldSession = sessionsByGym.put(gymId, session);
        gymBySession.put(session.getId(), gymId);
        if (oldSession != null && oldSession.isOpen() && oldSession != session) {
            oldSession.close(CloseStatus.NORMAL.withReason("Nuovo bridge connesso"));
        }

        send(session, Map.of("type", "REGISTERED", "gymId", gymId.toString()));
        log.info("Access bridge registered: gym={} device={}", gymId, payload.path("deviceId").asText());
    }

    private void handleScan(WebSocketSession session, JsonNode payload) throws IOException {
        String requestId = payload.path("requestId").asText();
        try {
            AccessBridgeRequest request = new AccessBridgeRequest(
                payload.path("tagUid").asText(),
                payload.path("deviceId").asText(null),
                payload.path("deviceIp").asText(null)
            );
            AccessBridgeResponse result = accessService.validateBridge(request);
            send(session, Map.of("type", "SCAN_RESULT", "requestId", requestId, "result", result));
        } catch (Exception e) {
            log.error("Cannot validate NFC scan from bridge", e);
            send(session, Map.of(
                "type", "SCAN_RESULT", "requestId", requestId,
                "result", Map.of("granted", false, "command", "DENY", "message", "Errore validazione accesso")
            ));
        }
    }

    private void send(WebSocketSession session, Object payload) throws IOException {
        synchronized (session) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        }
    }

    private void closeUnauthorized(WebSocketSession session, String reason) throws IOException {
        session.close(CloseStatus.POLICY_VIOLATION.withReason(reason));
    }
}

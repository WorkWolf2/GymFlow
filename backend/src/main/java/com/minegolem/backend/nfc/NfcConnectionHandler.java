package com.minegolem.backend.nfc;


import com.minegolem.backend.domain.entity.Access;
import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.domain.entity.NfcTag;
import com.minegolem.backend.domain.enums.DenialReason;
import com.minegolem.backend.repository.AccessRepository;
import com.minegolem.backend.repository.GymRepository;
import com.minegolem.backend.repository.NfcTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class NfcConnectionHandler {

    private static final int BUFFER_SIZE = 1024;
    private static final String CMD_OPEN = "OPEN\n";
    private final ConcurrentMap<String, OutputStream> activeOutputs = new ConcurrentHashMap<>();

    @Value("${nfc.default-gym-id:00000000-0000-0000-0000-000000000001}")
    private String defaultGymId;

    private final NfcFrameParser frameParser;
    private final NfcAccessValidator accessValidator;
    private final NfcEventPublisher eventPublisher;
    private final AccessRepository accessRepository;
    private final GymRepository gymRepository;
    private final NfcTagRepository nfcTagRepository;
    private final com.minegolem.backend.service.Er750Service er750Service;

    public void handle(Socket socket, String clientIp) {
        try (socket) {
            socket.setSoTimeout(30_000); // 30s read timeout
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            activeOutputs.put(clientIp, out);
            byte[] buffer = new byte[BUFFER_SIZE];

            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                processFrame(buffer, bytesRead, clientIp, out);
            }

        } catch (java.net.SocketTimeoutException e) {
            log.debug("NFC connection timed out from {}", clientIp);
        } catch (IOException e) {
            log.warn("NFC connection error from {}: {}", clientIp, e.getMessage());
        } finally {
            activeOutputs.remove(clientIp);
        }
    }

    public boolean openDoor() {
        activeOutputs.values().removeIf(out -> !writeCommand(out, CMD_OPEN));
        try {
            er750Service.openDoor(3);
        } catch (Exception e) {
            log.error("Failed to open ER750 door", e);
        }
        return true;
    }

    private void processFrame(byte[] raw, int length, String clientIp, OutputStream out) throws IOException {
        try {
            frameParser.parseFrame(raw, length).ifPresentOrElse(
                frame -> handleFrame(frame, clientIp, out),
                () -> {
                    log.warn("Could not parse NFC frame from {}", clientIp);
                    writeCommand(out, "DENY:INVALID_FRAME\n");
                }
            );
        } catch (Exception e) {
            log.error("Unhandled NFC frame error from {}", clientIp, e);
            writeCommand(out, "DENY:SERVER_ERROR\n");
        }
    }

    private void handleFrame(NfcFrameParser.NfcFrame frame, String clientIp, OutputStream out) {
        log.debug("NFC frame: tag={} device={} ip={}", frame.tagUid(), frame.deviceId(), clientIp);

        NfcAccessValidator.ValidationResult result = accessValidator.validate(frame.tagUid());

        // persist access log
        Gym gym = gymRepository.findById(UUID.fromString(defaultGymId)).orElse(null);
        Access access = Access.builder()
            .gym(gym)
            .user(result.user())
            .nfcTagUid(frame.tagUid())
            .deviceId(frame.deviceId())
            .deviceIp(clientIp)
            .granted(result.granted())
            .denialReason(result.denialReason())
            .build();
        accessRepository.save(access);

        // send door command
        String cmd = result.granted()
            ? CMD_OPEN
            : "DENY:" + result.denialReason().name() + "\n";
        writeCommand(out, cmd);
        
        if (result.granted()) {
            try {
                er750Service.openDoor(3);
            } catch (Exception e) {
                log.error("Failed to open ER750 door on tag scan", e);
            }
        }

        // push WebSocket events
        if (result.denialReason() == DenialReason.TAG_UNKNOWN) {
            saveUnknownTag(frame.tagUid(), gym);
            eventPublisher.publishUnknownTag(frame.tagUid(), frame.deviceId());
        } else {
            eventPublisher.publishAccessEvent(access, result.user(), frame.deviceId());
        }

        if (!result.granted()) {
            log.info("Access DENIED for tag {} device {} reason {}", frame.tagUid(), frame.deviceId(), result.denialReason());
        }
    }

    private void saveUnknownTag(String tagUid, Gym gym) {
        if (gym == null) return;
        nfcTagRepository.findByTagUid(tagUid)
            .ifPresentOrElse(tag -> {
                if (!tag.isActive()) {
                    tag.setActive(true);
                    tag.setGym(gym);
                    nfcTagRepository.save(tag);
                }
            }, () -> {
                NfcTag tag = NfcTag.builder()
                    .gym(gym)
                    .tagUid(tagUid)
                    .active(true)
                    .build();
                nfcTagRepository.save(tag);
            });
    }

    private boolean writeCommand(OutputStream out, String command) {
        try {
            synchronized (out) {
                out.write(command.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to send NFC command {}", command.trim(), e);
            return false;
        }
    }
}

package com.minegolem.backend.service;


import com.minegolem.backend.domain.entity.Access;
import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.domain.entity.NfcTag;
import com.minegolem.backend.domain.enums.DenialReason;
import com.minegolem.backend.dto.request.AccessBridgeRequest;
import com.minegolem.backend.dto.response.AccessBridgeResponse;
import com.minegolem.backend.dto.response.AccessResponse;
import com.minegolem.backend.exception.BusinessException;
import com.minegolem.backend.repository.AccessRepository;
import com.minegolem.backend.repository.GymRepository;
import com.minegolem.backend.repository.NfcTagRepository;
import com.minegolem.backend.nfc.NfcAccessValidator;
import com.minegolem.backend.nfc.NfcConnectionHandler;
import com.minegolem.backend.nfc.NfcEventPublisher;
import com.minegolem.backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessService {

    private final AccessRepository accessRepository;
    private final GymRepository gymRepository;
    private final NfcTagRepository nfcTagRepository;
    private final NfcAccessValidator accessValidator;
    private final NfcConnectionHandler nfcConnectionHandler;
    private final NfcEventPublisher nfcEventPublisher;

    @Value("${access.bridge.api-key:}")
    private String bridgeApiKey;

    @Value("${access.bridge.relay-seconds:3}")
    private int bridgeRelaySeconds;

    @Value("${nfc.default-gym-id:00000000-0000-0000-0000-000000000001}")
    private String defaultGymId;

    @Transactional(readOnly = true)
    public Page<Access> list(Pageable pageable) {
        return accessRepository.findByGymIdOrderByAccessTimeDesc(currentGymId(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Access> listByUser(UUID userId) {
        return accessRepository.findByUserIdOrderByAccessTimeDesc(userId, PageRequest.of(0, 50));
    }

    @Transactional(readOnly = true)
    public RealtimeStats getRealtimeStats() {
        UUID gymId = currentGymId();
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twoHoursAgo = now.minusHours(2);

        long todayTotal = accessRepository.countByGymIdAndAccessTimeBetween(gymId, startOfDay, now);
        long todayDenied = accessRepository.countByGymIdAndGrantedFalseAndAccessTimeBetween(gymId, startOfDay, now);
        long present = accessRepository.countDistinctUsersPresent(gymId, twoHoursAgo);
        List<AccessResponse> recent = accessRepository.findTop20ByGymIdOrderByAccessTimeDesc(gymId)
            .stream().map(AccessResponse::from).toList();

        return new RealtimeStats(todayTotal, todayDenied, present, recent);
    }

    public record RealtimeStats(
        long accessesToday,
        long deniedToday,
        long usersPresent,
        List<AccessResponse> recentAccesses
    ) {}

    @Transactional
    public AccessResponse validateManual(String accessId) {
        if (accessId == null || accessId.isBlank()) {
            throw new BusinessException("Inserisci ID cliente o tag NFC");
        }

        String identifier = accessId.trim().toUpperCase();
        NfcAccessValidator.ValidationResult result = accessValidator.validate(identifier);
        Gym gym = gymRepository.findById(currentGymId())
            .orElseThrow(() -> new BusinessException("Palestra non trovata"));

        Access access = Access.builder()
            .gym(gym)
            .user(result.user())
            .nfcTagUid(identifier)
            .deviceId("manual")
            .deviceIp("web")
            .granted(result.granted())
            .denialReason(result.denialReason())
            .build();

        Access saved = accessRepository.save(access);
        nfcEventPublisher.publishAccessEvent(saved, result.user(), "manual");

        if (result.granted()) {
            nfcConnectionHandler.openDoor();
        }

        return AccessResponse.from(saved);
    }

    public boolean openDoor() {
        return nfcConnectionHandler.openDoor();
    }

    public boolean isValidBridgeApiKey(String apiKey) {
        return bridgeApiKey != null
            && !bridgeApiKey.isBlank()
            && apiKey != null
            && bridgeApiKey.equals(apiKey);
    }

    @Transactional
    public AccessBridgeResponse validateBridge(AccessBridgeRequest request) {
        String tagUid = normalizeTag(request.tagUid());
        if (tagUid.isBlank()) {
            throw new BusinessException("Tag NFC non valido");
        }

        String deviceId = normalizeDeviceValue(request.deviceId(), "local-bridge");
        String deviceIp = normalizeDeviceValue(request.deviceIp(), "local-pc");
        NfcAccessValidator.ValidationResult result = accessValidator.validate(tagUid);
        Gym gym = resolveGym(result);

        Access access = Access.builder()
            .gym(gym)
            .user(result.user())
            .nfcTagUid(tagUid)
            .deviceId(deviceId)
            .deviceIp(deviceIp)
            .granted(result.granted())
            .denialReason(result.denialReason())
            .build();

        Access saved = accessRepository.save(access);
        publishBridgeEvent(saved, result, tagUid, deviceId, gym);

        String command = result.granted() ? "OPEN" : "DENY";
        String message = result.granted()
            ? "Accesso consentito"
            : denialMessage(result.denialReason());

        return new AccessBridgeResponse(
            saved.getId(),
            result.granted(),
            command,
            result.denialReason(),
            message,
            result.granted() ? bridgeRelaySeconds : null,
            result.user() != null ? result.user().getId() : null,
            result.user() != null ? result.user().getClientCode() : null,
            result.user() != null ? result.user().getFullName() : null,
            tagUid,
            deviceId,
            saved.getAccessTime()
        );
    }

    private UUID currentGymId() {
        StaffUserDetails d = (StaffUserDetails)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return d.getGymId();
    }

    private String normalizeTag(String value) {
        return value == null ? "" : value.replaceAll("[^A-Fa-f0-9]", "").toUpperCase();
    }

    private String normalizeDeviceValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private Gym resolveGym(NfcAccessValidator.ValidationResult result) {
        UUID gymId = result.gymId() != null
            ? result.gymId()
            : UUID.fromString(defaultGymId);

        return gymRepository.findById(gymId)
            .orElseThrow(() -> new BusinessException("Palestra non configurata per il bridge accessi"));
    }

    private void publishBridgeEvent(
        Access access,
        NfcAccessValidator.ValidationResult result,
        String tagUid,
        String deviceId,
        Gym gym
    ) {
        if (result.denialReason() == DenialReason.TAG_UNKNOWN) {
            saveUnknownTag(tagUid, gym);
            nfcEventPublisher.publishUnknownTag(tagUid, deviceId);
            return;
        }

        nfcEventPublisher.publishAccessEvent(access, result.user(), deviceId);
    }

    private void saveUnknownTag(String tagUid, Gym gym) {
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

    private String denialMessage(DenialReason reason) {
        if (reason == null) {
            return "Accesso negato";
        }

        return switch (reason) {
            case TAG_UNKNOWN -> "Tag NFC non riconosciuto";
            case NO_USER -> "Tag non associato a nessun cliente";
            case USER_INACTIVE -> "Cliente non attivo";
            case NO_ACTIVE_SUBSCRIPTION -> "Abbonamento non attivo";
            case NO_ACTIVE_INSURANCE -> "Assicurazione non attiva";
            case CERT_MISSING -> "Certificato medico mancante";
            case CERT_EXPIRED -> "Certificato medico scaduto";
        };
    }
}

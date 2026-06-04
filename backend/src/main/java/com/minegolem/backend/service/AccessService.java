package com.minegolem.backend.service;


import com.minegolem.backend.domain.entity.Access;
import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.dto.response.AccessResponse;
import com.minegolem.backend.exception.BusinessException;
import com.minegolem.backend.repository.AccessRepository;
import com.minegolem.backend.repository.GymRepository;
import com.minegolem.backend.nfc.NfcAccessValidator;
import com.minegolem.backend.nfc.NfcConnectionHandler;
import com.minegolem.backend.nfc.NfcEventPublisher;
import com.minegolem.backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
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
    private final NfcAccessValidator accessValidator;
    private final NfcConnectionHandler nfcConnectionHandler;
    private final NfcEventPublisher nfcEventPublisher;

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

    private UUID currentGymId() {
        StaffUserDetails d = (StaffUserDetails)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return d.getGymId();
    }
}

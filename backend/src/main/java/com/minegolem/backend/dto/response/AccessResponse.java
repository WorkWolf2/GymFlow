package com.minegolem.backend.dto.response;

import com.minegolem.backend.domain.entity.Access;
import com.minegolem.backend.domain.enums.DenialReason;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccessResponse(
    UUID id,
    UUID userId,
    Long clientCode,
    String userFullName,
    String nfcTagUid,
    String deviceId,
    String deviceIp,
    LocalDateTime accessTime,
    boolean granted,
    DenialReason denialReason
) {
    public static AccessResponse from(Access access) {
        return new AccessResponse(
            access.getId(),
            access.getUser() != null ? access.getUser().getId() : null,
            access.getUser() != null ? access.getUser().getClientCode() : null,
            access.getUser() != null ? access.getUser().getFullName() : "Sconosciuto",
            access.getNfcTagUid(),
            access.getDeviceId(),
            access.getDeviceIp(),
            access.getAccessTime(),
            access.isGranted(),
            access.getDenialReason()
        );
    }
}

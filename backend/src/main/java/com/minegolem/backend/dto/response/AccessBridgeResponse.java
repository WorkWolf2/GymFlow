package com.minegolem.backend.dto.response;

import com.minegolem.backend.domain.enums.DenialReason;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccessBridgeResponse(
    UUID accessId,
    boolean granted,
    String command,
    DenialReason denialReason,
    String message,
    Integer relaySeconds,
    UUID userId,
    Long clientCode,
    String userFullName,
    String tagUid,
    String deviceId,
    LocalDateTime accessTime
) {}

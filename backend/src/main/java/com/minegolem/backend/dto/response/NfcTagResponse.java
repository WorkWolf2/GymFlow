package com.minegolem.backend.dto.response;

import com.minegolem.backend.domain.entity.NfcTag;

import java.time.LocalDateTime;
import java.util.UUID;

public record NfcTagResponse(
    UUID id,
    String tagUid,
    UUID userId,
    Long clientCode,
    String userFullName,
    LocalDateTime assignedAt,
    boolean active
) {
    public static NfcTagResponse from(NfcTag tag) {
        return new NfcTagResponse(
            tag.getId(),
            tag.getTagUid(),
            tag.getUser() != null ? tag.getUser().getId() : null,
            tag.getUser() != null ? tag.getUser().getClientCode() : null,
            tag.getUser() != null ? tag.getUser().getFullName() : null,
            tag.getAssignedAt(),
            tag.isActive()
        );
    }
}

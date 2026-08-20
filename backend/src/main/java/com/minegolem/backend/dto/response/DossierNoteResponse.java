package com.minegolem.backend.dto.response;
import java.time.LocalDateTime; import java.util.UUID;
public record DossierNoteResponse(UUID id, String content, String authorName, LocalDateTime createdAt, LocalDateTime updatedAt) {}

package com.minegolem.backend.dto.response;
import java.time.LocalDateTime; import java.util.UUID;
public record DossierDocumentResponse(UUID id, String name, String url, String description, LocalDateTime uploadedAt) {}

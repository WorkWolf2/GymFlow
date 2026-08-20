package com.minegolem.backend.dto.response;
import java.time.LocalDateTime; import java.util.UUID;
public record DossierFieldResponse(UUID id, String fieldName, String fieldValue, String valueType, LocalDateTime createdAt, LocalDateTime updatedAt) {}

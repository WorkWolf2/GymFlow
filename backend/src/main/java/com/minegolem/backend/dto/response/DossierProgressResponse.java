package com.minegolem.backend.dto.response;
import java.math.BigDecimal; import java.time.*; import java.util.UUID;
public record DossierProgressResponse(UUID id, LocalDate recordedDate, BigDecimal weight, BigDecimal height, BigDecimal bodyFatPercentage, BigDecimal muscleMass, String measurements, String observations, String customParameters, LocalDateTime createdAt, LocalDateTime updatedAt) {}

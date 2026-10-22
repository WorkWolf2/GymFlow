package com.minegolem.backend.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public record AuditLogResponse(
    Long id,
    String staffUserName,
    String action,
    String entityType,
    String entityId,
    Map<String, Object> oldValue,
    Map<String, Object> newValue,
    LocalDateTime createdAt
) {}

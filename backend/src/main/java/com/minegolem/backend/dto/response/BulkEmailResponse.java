package com.minegolem.backend.dto.response;

import java.util.List;

public record BulkEmailResponse(
    int totalSent,
    int totalFailed,
    List<String> errors
) {}

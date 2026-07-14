package com.minegolem.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AccessBridgeRequest(
    @NotBlank(message = "tagUid obbligatorio")
    String tagUid,
    String deviceId,
    String deviceIp
) {}

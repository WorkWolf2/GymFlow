package com.minegolem.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record EmailSendRequest(
    UUID userId,
    String email,
    @NotBlank String subject,
    @NotBlank String body,
    String expiryDate
) {}

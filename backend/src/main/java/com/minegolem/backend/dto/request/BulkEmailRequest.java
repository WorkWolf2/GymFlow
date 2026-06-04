package com.minegolem.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BulkEmailRequest(
    @NotBlank(message = "L'oggetto è obbligatorio") String subject,
    @NotBlank(message = "Il corpo dell'email è obbligatorio") String body
) {}

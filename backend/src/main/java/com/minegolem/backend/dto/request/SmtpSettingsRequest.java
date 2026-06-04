package com.minegolem.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SmtpSettingsRequest(
    @NotBlank(message = "Host SMTP è obbligatorio") String smtpHost,
    @NotNull(message = "Porta SMTP è obbligatoria") Integer smtpPort,
    @NotBlank(message = "Username SMTP è obbligatorio") String smtpUsername,
    String smtpPassword,
    boolean smtpStarttls
) {}

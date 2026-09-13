package com.minegolem.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StaffUserRequest(
    @NotBlank(message = "L'email è obbligatoria")
    @Email(message = "Email non valida")
    String email,

    String password,

    @NotBlank(message = "Il nome è obbligatorio")
    String firstName,

    String lastName,

    @NotNull(message = "Il ruolo è obbligatorio")
    Long roleId,

    Boolean active
) {}

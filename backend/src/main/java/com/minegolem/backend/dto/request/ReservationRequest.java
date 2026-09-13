package com.minegolem.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ReservationRequest(
    @NotNull(message = "L'ID del cliente è obbligatorio")
    UUID userId,

    @NotNull(message = "La data di prenotazione è obbligatoria")
    LocalDate reservationDate,

    @NotBlank(message = "La fascia oraria è obbligatoria")
    String timeSlot,

    String notes
) {}

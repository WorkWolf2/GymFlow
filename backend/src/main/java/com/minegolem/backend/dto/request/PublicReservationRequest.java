package com.minegolem.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record PublicReservationRequest(
    UUID gymId,

    @NotBlank(message = "Nome e cognome sono obbligatori")
    @Size(min = 2, max = 150, message = "Il nome deve avere tra 2 e 150 caratteri")
    String fullName,

    @NotBlank(message = "Il numero di telefono è obbligatorio")
    @Size(min = 5, max = 50, message = "Il recapito telefonico non è valido")
    String phone,

    String email,

    @NotNull(message = "La data di prenotazione è obbligatoria")
    LocalDate reservationDate,

    @NotBlank(message = "La fascia oraria è obbligatoria")
    String timeSlot,

    String notes
) {}

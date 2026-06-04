package com.minegolem.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UserRequest(
    @NotBlank @Size(max = 80) String firstName,
    @NotBlank @Size(max = 80) String lastName,
    String email,
    String phone,
    LocalDate birthDate,
    @Size(max = 120) String birthPlace,
    @Size(max = 2) String birthProvince,
    @Pattern(regexp = "^[MmFf]?$", message = "Il sesso deve essere M o F") String sex,
    @Size(max = 20) String fiscalCode,
    String address,
    String notes
) {}

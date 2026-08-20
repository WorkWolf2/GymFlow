package com.minegolem.backend.dto.request;
import jakarta.validation.constraints.*;
public record DossierNoteRequest(@NotBlank @Size(max = 10000) String content) {}

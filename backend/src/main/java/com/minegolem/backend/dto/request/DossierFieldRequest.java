package com.minegolem.backend.dto.request;
import jakarta.validation.constraints.*;
public record DossierFieldRequest(@NotBlank @Size(max=100) String fieldName, @Size(max=10000) String fieldValue,
 @NotBlank @Pattern(regexp="TEXT|NUMBER|DATE|BOOLEAN", message="Tipo campo non valido") String valueType) {}

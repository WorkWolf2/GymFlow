package com.minegolem.backend.dto.response;

public record MunicipalityLookupResponse(
    String cadastralCode,
    String name,
    String province
) {}

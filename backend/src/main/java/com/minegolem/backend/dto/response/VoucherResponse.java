package com.minegolem.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VoucherResponse(UUID id, UUID userId, String name, String code, BigDecimal cost,
                              LocalDate startDate, LocalDate endDate) {}

package com.minegolem.backend.dto.request;

import com.minegolem.backend.domain.enums.PaymentMethod;
import com.minegolem.backend.domain.enums.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentRequest(
    UUID userId,
    UUID subscriptionId,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @DecimalMin("0.00") BigDecimal grossAmount,
    @DecimalMin("0.00") BigDecimal discountAmount,
    @NotNull PaymentMethod method,
    PaymentType type,
    @NotNull LocalDate paymentDate,
    String notes
) {}

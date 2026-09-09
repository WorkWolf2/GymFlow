package com.minegolem.backend.dto.request;

import com.minegolem.backend.domain.enums.PaymentMethod;
import java.time.LocalDate;

public record StopAndGoRequest(
    LocalDate startDate,
    PaymentMethod paymentMethod
) {}

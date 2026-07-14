package com.minegolem.backend.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VoucherRequest(String name, String code, BigDecimal cost, LocalDate startDate, LocalDate endDate) {}

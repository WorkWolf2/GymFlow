package com.minegolem.backend.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record WeekReservationsResponse(
    LocalDate startDate,
    LocalDate endDate,
    int totalReservations,
    // Key: "YYYY-MM-DD_HH:mm", Value: List of reservations
    Map<String, List<ReservationResponse>> slots,
    // Key: "YYYY-MM-DD_HH:mm", Value: Count
    Map<String, Integer> counts
) {}

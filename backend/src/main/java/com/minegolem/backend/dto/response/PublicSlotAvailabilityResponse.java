package com.minegolem.backend.dto.response;

import java.time.LocalDate;
import java.util.List;

public record PublicSlotAvailabilityResponse(
    LocalDate date,
    int totalReservations,
    List<SlotInfo> slots
) {
    public record SlotInfo(
        String timeSlot,
        String timeSlotLabel,
        int count,
        String status, // 'AVAILABLE', 'MEDIUM', 'HIGH', 'PAST'
        String statusLabel,
        boolean isPast
    ) {}
}

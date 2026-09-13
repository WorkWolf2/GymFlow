package com.minegolem.backend.dto.response;

import com.minegolem.backend.domain.entity.Reservation;
import com.minegolem.backend.domain.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationResponse(
    UUID id,
    UUID userId,
    Long clientCode,
    String userFullName,
    String userPhone,
    String userEmail,
    boolean isGuest,
    LocalDate reservationDate,
    String timeSlot,
    String notes,
    LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation r) {
        User u = r.getUser();
        boolean isGuest = (u == null);
        String name = isGuest
            ? (r.getGuestName() != null && !r.getGuestName().isBlank() ? r.getGuestName() : "Ospite Esterno")
            : u.getFullName();
        String phone = isGuest ? r.getGuestPhone() : u.getPhone();
        String email = isGuest ? r.getGuestEmail() : u.getEmail();

        return new ReservationResponse(
            r.getId(),
            u != null ? u.getId() : null,
            u != null ? u.getClientCode() : null,
            name,
            phone,
            email,
            isGuest,
            r.getReservationDate(),
            r.getTimeSlot(),
            r.getNotes(),
            r.getCreatedAt()
        );
    }
}

package com.minegolem.backend.controller;

import com.minegolem.backend.dto.request.ReservationRequest;
import com.minegolem.backend.dto.response.ReservationResponse;
import com.minegolem.backend.dto.response.WeekReservationsResponse;
import com.minegolem.backend.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping("/week")
    @PreAuthorize("hasAnyAuthority('USER_READ', 'ACCESS_READ')")
    public ResponseEntity<WeekReservationsResponse> getWeek(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate
    ) {
        return ResponseEntity.ok(reservationService.getWeek(startDate));
    }

    @GetMapping("/slot")
    @PreAuthorize("hasAnyAuthority('USER_READ', 'ACCESS_READ')")
    public ResponseEntity<List<ReservationResponse>> getSlot(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam String timeSlot
    ) {
        return ResponseEntity.ok(reservationService.getSlot(date, timeSlot));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('USER_WRITE', 'ACCESS_READ')")
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('USER_WRITE', 'ACCESS_READ')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        reservationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

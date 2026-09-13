package com.minegolem.backend.controller;

import com.minegolem.backend.dto.request.PublicReservationRequest;
import com.minegolem.backend.dto.response.PublicGymInfoResponse;
import com.minegolem.backend.dto.response.PublicSlotAvailabilityResponse;
import com.minegolem.backend.dto.response.ReservationResponse;
import com.minegolem.backend.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/reservations")
@RequiredArgsConstructor
public class PublicReservationController {

    private final ReservationService reservationService;

    @GetMapping("/gym")
    public ResponseEntity<PublicGymInfoResponse> getGymInfo(
        @RequestParam(required = false) UUID gymId
    ) {
        return ResponseEntity.ok(reservationService.getPublicGym(gymId));
    }

    @GetMapping("/availability")
    public ResponseEntity<PublicSlotAvailabilityResponse> getAvailability(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) UUID gymId
    ) {
        return ResponseEntity.ok(reservationService.getPublicAvailability(date, gymId));
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> createPublicReservation(
        @Valid @RequestBody PublicReservationRequest request
    ) {
        ReservationResponse response = reservationService.createPublic(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

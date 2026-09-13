package com.minegolem.backend.repository;

import com.minegolem.backend.domain.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Query("""
        SELECT r FROM Reservation r
        LEFT JOIN FETCH r.user u
        WHERE r.gym.id = :gymId
          AND r.reservationDate BETWEEN :startDate AND :endDate
        ORDER BY r.reservationDate ASC, r.timeSlot ASC, COALESCE(u.lastName, r.guestName) ASC
        """)
    List<Reservation> findByGymIdAndDateRange(
        @Param("gymId") UUID gymId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT r FROM Reservation r
        LEFT JOIN FETCH r.user u
        WHERE r.gym.id = :gymId
          AND r.reservationDate = :date
          AND r.timeSlot = :timeSlot
        ORDER BY COALESCE(u.lastName, r.guestName) ASC
        """)
    List<Reservation> findByGymIdAndDateAndTimeSlot(
        @Param("gymId") UUID gymId,
        @Param("date") LocalDate date,
        @Param("timeSlot") String timeSlot
    );

    Optional<Reservation> findByIdAndGymId(UUID id, UUID gymId);

    boolean existsByGymIdAndUserIdAndReservationDateAndTimeSlot(
        UUID gymId,
        UUID userId,
        LocalDate reservationDate,
        String timeSlot
    );

    boolean existsByGymIdAndGuestPhoneAndReservationDateAndTimeSlot(
        UUID gymId,
        String guestPhone,
        LocalDate reservationDate,
        String timeSlot
    );

    long countByGymIdAndReservationDateAndTimeSlot(
        UUID gymId,
        LocalDate reservationDate,
        String timeSlot
    );
}

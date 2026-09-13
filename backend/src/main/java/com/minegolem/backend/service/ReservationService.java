package com.minegolem.backend.service;

import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.domain.entity.Reservation;
import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.dto.request.ReservationRequest;
import com.minegolem.backend.dto.response.ReservationResponse;
import com.minegolem.backend.dto.response.WeekReservationsResponse;
import com.minegolem.backend.exception.BusinessException;
import com.minegolem.backend.exception.ResourceNotFoundException;
import com.minegolem.backend.repository.GymRepository;
import com.minegolem.backend.repository.ReservationRepository;
import com.minegolem.backend.repository.UserRepository;
import com.minegolem.backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final GymRepository gymRepository;
    private final RealtimeEventService realtimeEventService;

    @Transactional(readOnly = true)
    public WeekReservationsResponse getWeek(LocalDate startDate) {
        UUID gymId = currentGymId();
        LocalDate monday = startDate != null
            ? startDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        List<Reservation> list = reservationRepository.findByGymIdAndDateRange(gymId, monday, sunday);

        Map<String, List<ReservationResponse>> slots = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();

        for (Reservation r : list) {
            String key = r.getReservationDate().toString() + "_" + r.getTimeSlot();
            slots.computeIfAbsent(key, k -> new ArrayList<>()).add(ReservationResponse.from(r));
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }

        return new WeekReservationsResponse(
            monday,
            sunday,
            list.size(),
            slots,
            counts
        );
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getSlot(LocalDate date, String timeSlot) {
        UUID gymId = currentGymId();
        return reservationRepository.findByGymIdAndDateAndTimeSlot(gymId, date, timeSlot)
            .stream()
            .map(ReservationResponse::from)
            .toList();
    }

    @Transactional
    public ReservationResponse create(ReservationRequest request) {
        UUID gymId = currentGymId();
        Gym gym = gymRepository.findById(gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("Gym", gymId));

        User user = userRepository.findByIdAndGymIdAndDeletedAtIsNull(request.userId(), gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", request.userId()));

        if (reservationRepository.existsByGymIdAndUserIdAndReservationDateAndTimeSlot(
            gymId, request.userId(), request.reservationDate(), request.timeSlot())) {
            throw new BusinessException("Il cliente " + user.getFullName() + " è già prenotato in questa fascia oraria.");
        }

        Reservation reservation = Reservation.builder()
            .gym(gym)
            .user(user)
            .reservationDate(request.reservationDate())
            .timeSlot(request.timeSlot().trim())
            .notes(request.notes() != null && !request.notes().isBlank() ? request.notes().trim() : null)
            .build();

        Reservation saved = reservationRepository.save(reservation);
        realtimeEventService.publish(gymId, "RESERVATION", "CREATED", saved.getId());

        log.info("Creata prenotazione [{}] per utente [{}] data [{}] slot [{}]",
            saved.getId(), user.getId(), request.reservationDate(), request.timeSlot());

        return ReservationResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        UUID gymId = currentGymId();
        Reservation reservation = reservationRepository.findByIdAndGymId(id, gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("Reservation", id));

        reservationRepository.delete(reservation);
        realtimeEventService.publish(gymId, "RESERVATION", "DELETED", id);

        log.info("Cancellata prenotazione [{}] per data [{}] slot [{}]",
            id, reservation.getReservationDate(), reservation.getTimeSlot());
    }

    private static final List<String> DEFAULT_TIME_SLOTS = List.of(
        "07:00", "08:00", "09:00", "10:00", "11:00", "12:00",
        "13:00", "14:00", "15:00", "16:00", "17:00", "18:00",
        "19:00", "20:00", "21:00", "22:00"
    );

    @Transactional(readOnly = true)
    public com.minegolem.backend.dto.response.PublicGymInfoResponse getPublicGym(UUID gymId) {
        Gym gym = resolveGym(gymId);
        return com.minegolem.backend.dto.response.PublicGymInfoResponse.from(gym);
    }

    @Transactional(readOnly = true)
    public com.minegolem.backend.dto.response.PublicSlotAvailabilityResponse getPublicAvailability(LocalDate date, UUID gymId) {
        Gym gym = resolveGym(gymId);
        LocalDate targetDate = date != null ? date : LocalDate.now();
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();

        List<Reservation> list = reservationRepository.findByGymIdAndDateRange(gym.getId(), targetDate, targetDate);

        Map<String, Integer> counts = new HashMap<>();
        for (Reservation r : list) {
            counts.put(r.getTimeSlot(), counts.getOrDefault(r.getTimeSlot(), 0) + 1);
        }

        List<com.minegolem.backend.dto.response.PublicSlotAvailabilityResponse.SlotInfo> slots = new ArrayList<>();
        for (String slot : DEFAULT_TIME_SLOTS) {
            int count = counts.getOrDefault(slot, 0);
            String nextH = getNextHour(slot);
            String label = slot + " - " + nextH;
            boolean isPast = targetDate.isBefore(today) || (targetDate.isEqual(today) && isSlotPast(slot, nowTime));

            String status;
            String statusLabel;

            if (isPast) {
                status = "PAST";
                statusLabel = "Fascia Trascorsa";
            } else if (count < 12) {
                status = "AVAILABLE";
                statusLabel = count == 0 ? "Fascia Libera" : "Disponibile (" + count + " presenze)";
            } else if (count <= 16) {
                status = "MEDIUM";
                statusLabel = "Affluenza Sostenuta (" + count + "/16)";
            } else {
                status = "HIGH";
                statusLabel = "Picco Affluenza (" + count + " presenze)";
            }

            slots.add(new com.minegolem.backend.dto.response.PublicSlotAvailabilityResponse.SlotInfo(
                slot, label, count, status, statusLabel, isPast
            ));
        }

        return new com.minegolem.backend.dto.response.PublicSlotAvailabilityResponse(
            targetDate,
            list.size(),
            slots
        );
    }

    @Transactional
    public ReservationResponse createPublic(com.minegolem.backend.dto.request.PublicReservationRequest request) {
        Gym gym = resolveGym(request.gymId());

        if (request.reservationDate().isBefore(LocalDate.now())) {
            throw new BusinessException("Non è possibile effettuare prenotazioni per una data passata.");
        }

        if (request.reservationDate().isEqual(LocalDate.now()) && isSlotPast(request.timeSlot().trim(), LocalTime.now())) {
            throw new BusinessException("Questa fascia oraria è già trascorsa per la giornata odierna.");
        }

        String cleanPhone = request.phone().replaceAll("\\s+", "").trim();

        if (reservationRepository.existsByGymIdAndGuestPhoneAndReservationDateAndTimeSlot(
            gym.getId(), cleanPhone, request.reservationDate(), request.timeSlot().trim())) {
            throw new BusinessException("Risulta già una prenotazione attiva con questo numero per questa fascia oraria.");
        }

        // Check if an existing client has this phone number
        Optional<User> existingUser = userRepository.findFirstByGymIdAndPhoneAndDeletedAtIsNull(gym.getId(), cleanPhone);

        Reservation reservation = Reservation.builder()
            .gym(gym)
            .user(existingUser.orElse(null))
            .guestName(request.fullName().trim())
            .guestPhone(cleanPhone)
            .guestEmail(request.email() != null && !request.email().isBlank() ? request.email().trim() : null)
            .reservationDate(request.reservationDate())
            .timeSlot(request.timeSlot().trim())
            .notes(request.notes() != null && !request.notes().isBlank() ? request.notes().trim() : null)
            .build();

        Reservation saved = reservationRepository.save(reservation);
        realtimeEventService.publish(gym.getId(), "RESERVATION", "CREATED", saved.getId());

        log.info("Creata prenotazione pubblica guest [{}] nome [{}] phone [{}] data [{}] slot [{}]",
            saved.getId(), request.fullName(), cleanPhone, request.reservationDate(), request.timeSlot());

        return ReservationResponse.from(saved);
    }

    private boolean isSlotPast(String slot, LocalTime now) {
        try {
            int h = Integer.parseInt(slot.split(":")[0]);
            int m = Integer.parseInt(slot.split(":")[1]);
            LocalTime slotStart = LocalTime.of(h, m);
            // La fascia resta aperta per i primi 30 minuti (es. 11:00-12:00 resta prenotabile fino alle 11:29, dalle 11:30 in poi viene chiusa)
            LocalTime cutoff = slotStart.plusMinutes(30);
            return !now.isBefore(cutoff);
        } catch (Exception e) {
            return false;
        }
    }

    private Gym resolveGym(UUID gymId) {
        if (gymId != null) {
            return gymRepository.findById(gymId)
                .orElseThrow(() -> ResourceNotFoundException.of("Gym", gymId));
        }
        return gymRepository.findAll().stream()
            .filter(Gym::isActive)
            .findFirst()
            .or(() -> gymRepository.findAll().stream().findFirst())
            .orElseThrow(() -> new BusinessException("Nessuna palestra attiva configurata nel sistema."));
    }

    private String getNextHour(String slot) {
        try {
            int h = Integer.parseInt(slot.split(":")[0]);
            return String.format("%02d:00", h + 1);
        } catch (Exception e) {
            return slot;
        }
    }

    private UUID currentGymId() {
        StaffUserDetails details = (StaffUserDetails)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return details.getGymId();
    }
}

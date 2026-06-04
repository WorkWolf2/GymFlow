package com.minegolem.backend.controller;


import com.minegolem.backend.domain.entity.MedicalCertificate;
import com.minegolem.backend.domain.entity.Subscription;
import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.dto.response.ExpiringItemResponse;
import com.minegolem.backend.repository.*;
import com.minegolem.backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UserRepository userRepository;
    private final AccessRepository accessRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final MedicalCertificateRepository medicalCertificateRepository;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> summary(@AuthenticationPrincipal StaffUserDetails user) {
        UUID gymId = user.getGymId();
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        long activeUsers = userRepository.countByGymIdAndActiveTrueAndDeletedAtIsNull(gymId);
        long accessesToday = accessRepository.countByGymIdAndAccessTimeBetween(gymId, startOfDay, now);
        long deniedToday = accessRepository.countByGymIdAndGrantedFalseAndAccessTimeBetween(gymId, startOfDay, now);
        long expiringSubscriptions = subscriptionRepository
            .findExpiringSoon(gymId, today, today.plusDays(7)).size();
        long expiringCerts = medicalCertificateRepository
            .findExpiringSoon(gymId, today, today.plusDays(30)).size();

        BigDecimal todayRevenue = paymentRepository.sumByGymAndDateOnly(gymId, today);
        if (todayRevenue == null) {
            todayRevenue = BigDecimal.ZERO;
        }

        BigDecimal todayExpenses = paymentRepository.sumExpensesByGymAndDate(gymId, today);
        if (todayExpenses == null) {
            todayExpenses = BigDecimal.ZERO;
        }

        return ResponseEntity.ok(new DashboardSummary(
            activeUsers, accessesToday, deniedToday,
            expiringSubscriptions, expiringCerts, todayRevenue, todayExpenses
        ));
    }

    @GetMapping("/expiring-subscriptions")
    public ResponseEntity<List<ExpiringItemResponse>> expiringSubscriptions(
        @AuthenticationPrincipal StaffUserDetails user
    ) {
        UUID gymId = user.getGymId();
        LocalDate today = LocalDate.now();
        List<ExpiringItemResponse> items = subscriptionRepository
            .findExpiringSoon(gymId, today, today.plusDays(7))
            .stream()
            .filter(s -> isActiveUser(s.getUser()))
            .sorted(Comparator.comparing(Subscription::getEndDate))
            .map(s -> new ExpiringItemResponse(
                s.getUser().getId(),
                s.getUser().getFullName(),
                s.getEndDate(),
                s.getSubscriptionType().getName()
            ))
            .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/expiring-certificates")
    public ResponseEntity<List<ExpiringItemResponse>> expiringCertificates(
        @AuthenticationPrincipal StaffUserDetails user
    ) {
        UUID gymId = user.getGymId();
        LocalDate today = LocalDate.now();
        List<ExpiringItemResponse> items = medicalCertificateRepository
            .findExpiringSoon(gymId, today, today.plusDays(30))
            .stream()
            .filter(c -> isActiveUser(c.getUser()))
            .sorted(Comparator.comparing(MedicalCertificate::getExpiryDate))
            .map(c -> new ExpiringItemResponse(
                c.getUser().getId(),
                c.getUser().getFullName(),
                c.getExpiryDate(),
                null
            ))
            .toList();
        return ResponseEntity.ok(items);
    }

    private static boolean isActiveUser(User user) {
        return user != null && user.getDeletedAt() == null;
    }

    public record DashboardSummary(
        long activeUsers,
        long accessesToday,
        long deniedToday,
        long expiringSubscriptions,
        long expiringCerts,
        BigDecimal todayRevenue,
        BigDecimal todayExpenses
    ) {}
}

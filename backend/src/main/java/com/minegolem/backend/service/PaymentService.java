package com.minegolem.backend.service;


import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.domain.entity.Payment;
import com.minegolem.backend.domain.entity.StaffUser;
import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.domain.enums.PaymentMethod;
import com.minegolem.backend.domain.enums.PaymentType;
import com.minegolem.backend.dto.request.PaymentRequest;
import com.minegolem.backend.exception.ResourceNotFoundException;
import com.minegolem.backend.repository.*;
import com.minegolem.backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final GymRepository gymRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StaffUserRepository staffUserRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<Payment> list(Pageable pageable) {
        return paymentRepository.findByGymIdAndDeletedAtIsNullOrderByPaymentDateDesc(currentGymId(), pageable);
    }

    @Transactional(readOnly = true)
    public List<Payment> listByUser(UUID userId) {
        return paymentRepository.findByUserIdAndDeletedAtIsNullOrderByPaymentDateDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Payment> listByDateRange(LocalDate from, LocalDate to) {
        return paymentRepository.findByGymAndDateRange(currentGymId(), from, to);
    }

    @Transactional
    public Payment create(PaymentRequest req) {
        UUID gymId = currentGymId();
        Gym gym = gymRepository.findById(gymId).orElseThrow();

        User user = null;
        if (req.userId() != null) {
            user = userRepository.findByIdAndGymIdAndDeletedAtIsNull(req.userId(), gymId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", req.userId()));
        }

        StaffUser staffUser = staffUserRepository.findById(currentStaffId()).orElse(null);

        Payment payment = Payment.builder()
            .gym(gym)
            .user(user)
            .amount(req.amount())
            .method(req.method())
            .type(req.type() != null ? req.type() : PaymentType.INCOME)
            .paymentDate(req.paymentDate())
            .notes(req.notes())
            .createdBy(staffUser)
            .build();

        if (req.subscriptionId() != null) {
            subscriptionRepository.findById(req.subscriptionId())
                .ifPresent(payment::setSubscription);
        }

        Payment saved = paymentRepository.save(payment);
        auditService.log("PAYMENT_CREATED", "Payment", saved.getId().toString());
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        Payment p = paymentRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.of("Payment", id));
        p.setDeletedAt(LocalDateTime.now());
        paymentRepository.save(p);
        auditService.log("PAYMENT_DELETED", "Payment", id.toString());
    }

    @Transactional(readOnly = true)
    public DailyStats getDailyStats(LocalDate date) {
        UUID gymId = currentGymId();
        BigDecimal cash = paymentRepository.sumByGymAndDate(gymId, date,
            PaymentMethod.CASH);
        BigDecimal card = paymentRepository.sumByGymAndDate(gymId, date,
            PaymentMethod.CARD);
        BigDecimal transfer = paymentRepository.sumByGymAndDate(gymId, date,
            PaymentMethod.TRANSFER);
        BigDecimal total = cash.add(card).add(transfer);
        return new DailyStats(date, total, cash, card, transfer);
    }

    @Transactional(readOnly = true)
    public List<LocalDate> getDatesWithPayments() {
        return paymentRepository.findDatesWithPayments(currentGymId());
    }

    public record DailyStats(LocalDate date, BigDecimal total, BigDecimal cash, BigDecimal card, BigDecimal transfer) {}

    private UUID currentGymId() {
        return currentDetails().getGymId();
    }

    private UUID currentStaffId() {
        return currentDetails().getUserId();
    }

    private StaffUserDetails currentDetails() {
        return (StaffUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}

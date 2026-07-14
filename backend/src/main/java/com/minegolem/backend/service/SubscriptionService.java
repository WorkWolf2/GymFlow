package com.minegolem.backend.service;


import com.minegolem.backend.domain.entity.*;
import com.minegolem.backend.domain.enums.PaymentType;
import com.minegolem.backend.dto.request.SubscriptionRequest;
import com.minegolem.backend.exception.BusinessException;
import com.minegolem.backend.exception.ResourceNotFoundException;
import com.minegolem.backend.repository.*;
import com.minegolem.backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionTypeRepository subscriptionTypeRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final GymRepository gymRepository;
    private final StaffUserRepository staffUserRepository;
    private final AuditService auditService;

    @Transactional
    public Subscription create(SubscriptionRequest request) {
        UUID gymId = currentGymId();
        UUID staffId = currentStaffId();

        User user = userRepository.findByIdAndGymIdAndDeletedAtIsNull(request.userId(), gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", request.userId()));

        SubscriptionType type = subscriptionTypeRepository.findById(request.subscriptionTypeId())
            .orElseThrow(() -> ResourceNotFoundException.of("SubscriptionType", request.subscriptionTypeId()));

        // calculate end date
        LocalDate endDate = calculateEndDate(type, request.startDate());

        StaffUser staffUser = staffUserRepository.findById(staffId).orElse(null);

        BigDecimal grossAmount = request.price();
        BigDecimal discountAmount = request.discountAmount() != null
            ? request.discountAmount()
            : BigDecimal.ZERO;
        if (discountAmount.compareTo(grossAmount) > 0) {
            throw new BusinessException("Lo sconto non può superare il prezzo dell'abbonamento");
        }
        BigDecimal netAmount = grossAmount.subtract(discountAmount);

        Subscription subscription = Subscription.builder()
            .user(user)
            .subscriptionType(type)
            .startDate(request.startDate())
            .endDate(endDate)
            .price(netAmount)
            .notes(request.notes())
            .createdBy(staffUser)
            .build();

        Subscription saved = subscriptionRepository.save(subscription);

        // auto-create payment on creation date (not start date)
        Gym gym = gymRepository.findById(gymId).orElseThrow();
        Payment payment = Payment.builder()
            .gym(gym)
            .user(user)
            .subscription(saved)
            .amount(netAmount)
            .grossAmount(grossAmount)
            .discountAmount(discountAmount)
            .method(request.paymentMethod())
            .type(PaymentType.INCOME)
            .paymentDate(LocalDate.now())
            .notes("Incasso automatico per abbonamento: " + type.getName())
            .createdBy(staffUser)
            .build();
        paymentRepository.save(payment);

        auditService.log("SUBSCRIPTION_CREATED", "Subscription", saved.getId().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Subscription> listByUser(UUID userId) {
        return subscriptionRepository.findByUserIdAndDeletedAtIsNullOrderByStartDateDesc(userId);
    }

    @Transactional
    public void delete(UUID id) {
        UUID gymId = currentGymId();
        Subscription sub = subscriptionRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.of("Subscription", id));

        if (!sub.getUser().getGym().getId().equals(gymId)) {
            throw ResourceNotFoundException.of("Subscription", id);
        }
        if (sub.getDeletedAt() != null) {
            return;
        }

        sub.setDeletedAt(java.time.LocalDateTime.now());
        subscriptionRepository.save(sub);
        auditService.log("SUBSCRIPTION_DELETED", "Subscription", id.toString());
    }

    private LocalDate calculateEndDate(SubscriptionType type, LocalDate startDate) {
        if (type.getForcedExpiry() != null) {
            return type.getForcedExpiry();
        }
        if (type.getValidityDays() != null) {
            return startDate.plusDays(type.getValidityDays());
        }
        throw new BusinessException("Il piano di abbonamento non ha una validità configurata");
    }

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

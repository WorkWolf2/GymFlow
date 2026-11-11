package com.minegolem.backend.nfc;


import com.minegolem.backend.domain.entity.MedicalCertificate;
import com.minegolem.backend.domain.entity.NfcTag;
import com.minegolem.backend.domain.entity.Subscription;
import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.domain.enums.DenialReason;
import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;
import com.minegolem.backend.repository.MedicalCertificateRepository;
import com.minegolem.backend.repository.NfcTagRepository;
import com.minegolem.backend.repository.SubscriptionRepository;
import com.minegolem.backend.repository.UserRepository;
import com.minegolem.backend.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class NfcAccessValidator {

    private final NfcTagRepository nfcTagRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MedicalCertificateRepository medicalCertificateRepository;
    private final UserRepository userRepository;
    private final RealtimeEventService realtimeEventService;

    public record ValidationResult(
        boolean granted,
        DenialReason denialReason,
        User user,
        String tagUid,
        UUID gymId,
        Subscription packageUsed,
        Integer remainingEntries,
        String packageName
    ) {
        public static ValidationResult granted(User user, String tagUid, UUID gymId) {
            return new ValidationResult(true, null, user, tagUid, gymId, null, null, null);
        }

        public static ValidationResult granted(User user, String tagUid, UUID gymId, Subscription packageUsed, Integer remainingEntries, String packageName) {
            return new ValidationResult(true, null, user, tagUid, gymId, packageUsed, remainingEntries, packageName);
        }

        public static ValidationResult denied(DenialReason reason, User user, String tagUid, UUID gymId) {
            return new ValidationResult(false, reason, user, tagUid, gymId, null, null, null);
        }

        public static ValidationResult unknownTag(String tagUid) {
            return new ValidationResult(false, DenialReason.TAG_UNKNOWN, null, tagUid, null, null, null, null);
        }
    }

    @Transactional
    public ValidationResult validate(String tagUid) {
        // 1. find NFC tag
        Optional<NfcTag> tagOpt = nfcTagRepository.findByTagUidAndActiveTrue(tagUid);

        if (tagOpt.isEmpty()) {
            Optional<User> userByCode = findUserByClientCode(tagUid);
            if (userByCode.isPresent()) {
                return validateUser(userByCode.get(), tagUid);
            }

            log.info("Unknown NFC tag: {}", tagUid);
            return ValidationResult.unknownTag(tagUid);
        }

        NfcTag tag = tagOpt.get();
        User user = tag.getUser();

        if (user == null) {
            return ValidationResult.denied(DenialReason.NO_USER, null, tagUid, tag.getGym().getId());
        }

        return validateUser(user, tagUid);
    }

    private Optional<User> findUserByClientCode(String value) {
        if (value == null || !value.matches("\\d+")) {
            return Optional.empty();
        }

        Long clientCode;
        try {
            clientCode = Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }

        return userRepository.findByClientCodeAndDeletedAtIsNull(clientCode);
    }

    private ValidationResult validateUser(User user, String tagUid) {
        UUID gymId = user.getGym().getId();

        if (!user.isActive() || user.isDeleted()) {
            log.info("Inactive user tried access: {}", user.getId());
            return ValidationResult.denied(DenialReason.USER_INACTIVE, user, tagUid, gymId);
        }

        // 3. active ABBONAMENTO or PACCHETTO?
        boolean hasActiveSub = !subscriptionRepository
            .findActiveByUserAndType(user.getId(), SubscriptionTypeEnum.ABBONAMENTO, LocalDate.now())
            .isEmpty();

        Subscription packageToUse = null;

        if (!hasActiveSub) {
            List<Subscription> activePackages = subscriptionRepository
                .findActiveByUserAndType(user.getId(), SubscriptionTypeEnum.PACCHETTO, LocalDate.now());

            if (activePackages.isEmpty()) {
                log.info("No active subscription or package for user: {}", user.getId());
                return ValidationResult.denied(DenialReason.NO_ACTIVE_SUBSCRIPTION, user, tagUid, gymId);
            }

            List<Subscription> usablePackages = activePackages.stream()
                .filter(s -> s.getRemainingEntries() == null || s.getRemainingEntries() > 0)
                .sorted(Comparator.comparing(Subscription::getEndDate)
                    .thenComparing(s -> s.getCreatedAt() != null ? s.getCreatedAt() : LocalDateTime.MIN))
                .toList();

            if (usablePackages.isEmpty()) {
                log.info("User {} has package(s) but all entries are exhausted (0 entries left)", user.getId());
                return ValidationResult.denied(DenialReason.PACKAGE_ENTRIES_EXHAUSTED, user, tagUid, gymId);
            }

            packageToUse = usablePackages.get(0);
        }

        boolean hasActiveInsurance = !subscriptionRepository
            .findActiveByUserAndType(user.getId(), SubscriptionTypeEnum.ASSICURAZIONE, LocalDate.now())
            .isEmpty();

        if (!hasActiveInsurance) {
            log.info("No active insurance for user: {}", user.getId());
            return ValidationResult.denied(DenialReason.NO_ACTIVE_INSURANCE, user, tagUid, gymId);
        }

        // 4. medical certificate valid?
        Optional<MedicalCertificate> certOpt =
            medicalCertificateRepository.findFirstByUser_IdAndDeletedAtIsNullOrderByExpiryDateDesc(user.getId());

        if (certOpt.isEmpty()) {
            log.info("No medical certificate for user: {}", user.getId());
            return ValidationResult.denied(DenialReason.CERT_MISSING, user, tagUid, gymId);
        }

        MedicalCertificate cert = certOpt.get();
        if (!cert.isValid()) {
            log.info("Expired medical certificate for user: {}", user.getId());
            return ValidationResult.denied(DenialReason.CERT_EXPIRED, user, tagUid, gymId);
        }

        // Decrement package entry if using a package with tracked entries (only on granted access)
        Integer remainingAfterDeduction = null;
        String packageName = null;

        if (packageToUse != null) {
            packageName = packageToUse.getSubscriptionType() != null
                ? packageToUse.getSubscriptionType().getName()
                : "Pacchetto";

            if (packageToUse.getRemainingEntries() != null && packageToUse.getRemainingEntries() > 0) {
                int newRemaining = packageToUse.getRemainingEntries() - 1;
                packageToUse.setRemainingEntries(newRemaining);
                subscriptionRepository.save(packageToUse);
                remainingAfterDeduction = newRemaining;
                log.info("Decremented 1 entry from package '{}' ({}) for user {}. Remaining entries: {}",
                    packageName, packageToUse.getId(), user.getId(), newRemaining);

                if (realtimeEventService != null) {
                    realtimeEventService.publish(gymId, "SUBSCRIPTION", "UPDATED", packageToUse.getId());
                    realtimeEventService.publish(gymId, "USER", "UPDATED", user.getId());
                }
            } else if (packageToUse.getRemainingEntries() != null) {
                remainingAfterDeduction = packageToUse.getRemainingEntries();
            }
        }

        log.info("Access GRANTED for user: {} tag: {}", user.getFullName(), tagUid);
        return ValidationResult.granted(user, tagUid, gymId, packageToUse, remainingAfterDeduction, packageName);
    }
}

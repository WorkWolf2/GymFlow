package com.minegolem.backend.nfc;


import com.minegolem.backend.domain.entity.MedicalCertificate;
import com.minegolem.backend.domain.entity.NfcTag;
import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.domain.enums.DenialReason;
import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;
import com.minegolem.backend.repository.MedicalCertificateRepository;
import com.minegolem.backend.repository.NfcTagRepository;
import com.minegolem.backend.repository.SubscriptionRepository;
import com.minegolem.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class NfcAccessValidator {

    private final NfcTagRepository nfcTagRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MedicalCertificateRepository medicalCertificateRepository;
    private final UserRepository userRepository;

    public record ValidationResult(
        boolean granted,
        DenialReason denialReason,
        User user,
        String tagUid
    ) {
        public static ValidationResult granted(User user, String tagUid) {
            return new ValidationResult(true, null, user, tagUid);
        }

        public static ValidationResult denied(DenialReason reason, User user, String tagUid) {
            return new ValidationResult(false, reason, user, tagUid);
        }

        public static ValidationResult unknownTag(String tagUid) {
            return new ValidationResult(false, DenialReason.TAG_UNKNOWN, null, tagUid);
        }
    }

    @Transactional(readOnly = true)
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
            return ValidationResult.denied(DenialReason.NO_USER, null, tagUid);
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
        if (!user.isActive() || user.isDeleted()) {
            log.info("Inactive user tried access: {}", user.getId());
            return ValidationResult.denied(DenialReason.USER_INACTIVE, user, tagUid);
        }

        // 3. active ABBONAMENTO subscription?
        boolean hasActiveSub = !subscriptionRepository
            .findActiveByUserAndType(user.getId(), SubscriptionTypeEnum.ABBONAMENTO, LocalDate.now())
            .isEmpty();

        if (!hasActiveSub) {
            log.info("No active subscription for user: {}", user.getId());
            return ValidationResult.denied(DenialReason.NO_ACTIVE_SUBSCRIPTION, user, tagUid);
        }

        boolean hasActiveInsurance = !subscriptionRepository
            .findActiveByUserAndType(user.getId(), SubscriptionTypeEnum.ASSICURAZIONE, LocalDate.now())
            .isEmpty();

        if (!hasActiveInsurance) {
            log.info("No active insurance for user: {}", user.getId());
            return ValidationResult.denied(DenialReason.NO_ACTIVE_INSURANCE, user, tagUid);
        }

        // 4. medical certificate valid?
        Optional<MedicalCertificate> certOpt =
            medicalCertificateRepository.findFirstByUser_IdAndDeletedAtIsNullOrderByExpiryDateDesc(user.getId());

        if (certOpt.isEmpty()) {
            log.info("No medical certificate for user: {}", user.getId());
            return ValidationResult.denied(DenialReason.CERT_MISSING, user, tagUid);
        }

        MedicalCertificate cert = certOpt.get();
        if (!cert.isValid()) {
            log.info("Expired medical certificate for user: {}", user.getId());
            return ValidationResult.denied(DenialReason.CERT_EXPIRED, user, tagUid);
        }

        log.info("Access GRANTED for user: {} tag: {}", user.getFullName(), tagUid);
        return ValidationResult.granted(user, tagUid);
    }
}

package com.minegolem.backend.service;

import com.minegolem.backend.domain.entity.EmailNotificationLog;
import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.domain.entity.MedicalCertificate;
import com.minegolem.backend.domain.entity.Subscription;
import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;
import com.minegolem.backend.exception.ResourceNotFoundException;
import com.minegolem.backend.repository.EmailNotificationLogRepository;
import com.minegolem.backend.repository.GymRepository;
import com.minegolem.backend.repository.MedicalCertificateRepository;
import com.minegolem.backend.repository.SubscriptionRepository;
import com.minegolem.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private static final String WELCOME_TEMPLATE_ID = "client-welcome";
    private static final String SUBSCRIPTION_EXPIRATION_ID = "subscription-expiration";
    private static final String CERTIFICATE_EXPIRATION_ID = "certificate-expiration";
    private static final String SUBSCRIPTION_EXPIRY_NOTICE = "SUBSCRIPTION_EXPIRY_NOTICE";
    private static final String CERTIFICATE_EXPIRY_NOTICE = "CERTIFICATE_EXPIRY_NOTICE";
    private static final DateTimeFormatter ITALIAN_DATE =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY);

    private final EmailService emailService;
    private final GymRepository gymRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MedicalCertificateRepository medicalCertificateRepository;
    private final EmailNotificationLogRepository emailNotificationLogRepository;

    @Scheduled(cron = "${notifications.expiration.cron:0 0 8 * * *}", zone = "${notifications.time-zone:Europe/Rome}")
    public void sendDailyExpirationNotices() {
        LocalDate today = LocalDate.now();
        for (Gym gym : gymRepository.findAll()) {
            if (!gym.isActive()) {
                continue;
            }
            try {
                sendExpirationNoticesForGym(gym, today);
            } catch (Exception e) {
                log.error("Errore durante l'invio automatico delle scadenze per la palestra {}", gym.getId(), e);
            }
        }
    }

    @Transactional
    public void sendWelcomeEmail(UUID gymId, UUID userId) {
        Gym gym = gymRepository.findById(gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("Gym", gymId));
        User user = userRepository.findByIdAndGymIdAndDeletedAtIsNull(userId, gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        validateRecipient(user);

        EmailTemplate template = resolveWelcomeTemplate(gym);
        emailService.sendEmail(
            gymId,
            user.getEmail(),
            applyPlaceholders(template.subject(), user, gym, null),
            applyPlaceholders(template.body(), user, gym, null)
        );
    }

    @Transactional
    public void sendExpirationNoticesForGym(Gym gym, LocalDate today) {
        sendSubscriptionNotices(gym, today, 7);
        sendSubscriptionNotices(gym, today, 1);
        sendCertificateNotices(gym, today, 7);
    }

    private void sendSubscriptionNotices(Gym gym, LocalDate today, int daysBefore) {
        LocalDate expiryDate = today.plusDays(daysBefore);
        List<Subscription> subscriptions = subscriptionRepository
            .findExpiringForAutomaticEmail(gym.getId(), SubscriptionTypeEnum.ABBONAMENTO, expiryDate);
        EmailTemplate template = resolveExpirationTemplate(
            gym,
            SUBSCRIPTION_EXPIRATION_ID,
            "Avviso abbonamento in scadenza",
            "Ciao {name},<br><br>ti ricordiamo che il tuo abbonamento scadra il <strong>{expiryDate}</strong>.<br><br>Passa in reception per il rinnovo.<br><br>Lo staff di {gymName}"
        );

        for (Subscription subscription : subscriptions) {
            User user = subscription.getUser();
            if (!hasValidRecipient(user) || hasAlreadyBeenSent(SUBSCRIPTION_EXPIRY_NOTICE, "SUBSCRIPTION", subscription.getId(), daysBefore)) {
                continue;
            }

            try {
                emailService.sendEmail(
                    gym.getId(),
                    user.getEmail(),
                    applyPlaceholders(template.subject(), user, gym, subscription.getEndDate()),
                    applyPlaceholders(template.body(), user, gym, subscription.getEndDate())
                );
                saveLog(gym.getId(), user, SUBSCRIPTION_EXPIRY_NOTICE, "SUBSCRIPTION", subscription.getId(), daysBefore);
            } catch (Exception e) {
                log.error("Errore invio avviso abbonamento {} giorni prima per subscription {}", daysBefore, subscription.getId(), e);
            }
        }
    }

    private void sendCertificateNotices(Gym gym, LocalDate today, int daysBefore) {
        LocalDate expiryDate = today.plusDays(daysBefore);
        List<MedicalCertificate> certificates = medicalCertificateRepository
            .findLatestExpiringForAutomaticEmail(gym.getId(), expiryDate);
        EmailTemplate template = resolveExpirationTemplate(
            gym,
            CERTIFICATE_EXPIRATION_ID,
            "Scadenza certificato medico",
            "Ciao {name},<br><br>ti ricordiamo che il tuo certificato medico scadra il <strong>{expiryDate}</strong>.<br><br>Consegna il certificato aggiornato in reception prima della scadenza.<br><br>Lo staff di {gymName}"
        );

        for (MedicalCertificate certificate : certificates) {
            User user = certificate.getUser();
            if (!hasValidRecipient(user) || hasAlreadyBeenSent(CERTIFICATE_EXPIRY_NOTICE, "CERTIFICATE", certificate.getId(), daysBefore)) {
                continue;
            }

            try {
                emailService.sendEmail(
                    gym.getId(),
                    user.getEmail(),
                    applyPlaceholders(template.subject(), user, gym, certificate.getExpiryDate()),
                    applyPlaceholders(template.body(), user, gym, certificate.getExpiryDate())
                );
                saveLog(gym.getId(), user, CERTIFICATE_EXPIRY_NOTICE, "CERTIFICATE", certificate.getId(), daysBefore);
            } catch (Exception e) {
                log.error("Errore invio avviso certificato {} giorni prima per certificate {}", daysBefore, certificate.getId(), e);
            }
        }
    }

    private void validateRecipient(User user) {
        if (!hasValidRecipient(user)) {
            throw new IllegalArgumentException("Il cliente non ha un indirizzo email valido");
        }
    }

    private boolean hasValidRecipient(User user) {
        return user != null
            && user.getDeletedAt() == null
            && user.isActive()
            && user.getEmail() != null
            && !user.getEmail().isBlank();
    }

    private boolean hasAlreadyBeenSent(String notificationType, String targetType, UUID targetId, int daysBefore) {
        return emailNotificationLogRepository.existsByNotificationTypeAndTargetTypeAndTargetIdAndDaysBefore(
            notificationType,
            targetType,
            targetId,
            daysBefore
        );
    }

    private void saveLog(UUID gymId, User user, String notificationType, String targetType, UUID targetId, int daysBefore) {
        emailNotificationLogRepository.save(EmailNotificationLog.builder()
            .gymId(gymId)
            .userId(user.getId())
            .notificationType(notificationType)
            .targetType(targetType)
            .targetId(targetId)
            .daysBefore(daysBefore)
            .recipientEmail(user.getEmail())
            .sentAt(LocalDateTime.now())
            .build());
    }

    private EmailTemplate resolveWelcomeTemplate(Gym gym) {
        Map<String, Object> source = findTemplate(gym.getSettings(), "emailTemplates", WELCOME_TEMPLATE_ID, "benvenuto");
        if (source == null) {
            return new EmailTemplate(
                "Benvenuto in {gymName}",
                "Ciao {name},<br><br>benvenuto in {gymName}. La tua scheda cliente e stata creata correttamente.<br><br>A presto!"
            );
        }
        return new EmailTemplate(
            value(source.get("subject"), "Benvenuto in {gymName}"),
            value(source.get("body"), "Ciao {name},<br><br>benvenuto in {gymName}.")
        );
    }

    private EmailTemplate resolveExpirationTemplate(Gym gym, String templateId, String defaultSubject, String defaultBody) {
        Map<String, Object> source = findTemplate(gym.getSettings(), "expirationTemplates", templateId, null);
        if (source == null) {
            return new EmailTemplate(defaultSubject, defaultBody);
        }
        return new EmailTemplate(
            value(source.get("subject"), defaultSubject),
            value(source.get("body"), defaultBody)
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findTemplate(Map<String, Object> settings, String key, String id, String textMatch) {
        if (settings == null || !(settings.get(key) instanceof List<?> templates)) {
            return null;
        }

        for (Object item : templates) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> template = (Map<String, Object>) raw;
            String templateId = String.valueOf(template.getOrDefault("id", ""));
            String name = String.valueOf(template.getOrDefault("name", ""));
            String category = String.valueOf(template.getOrDefault("category", ""));
            String haystack = (templateId + " " + name + " " + category).toLowerCase(Locale.ITALY);
            if (templateId.equals(id) || (textMatch != null && haystack.contains(textMatch))) {
                return template;
            }
        }
        return null;
    }

    private String applyPlaceholders(String text, User user, Gym gym, LocalDate expiryDate) {
        String name = user.getFullName();
        String formattedExpiry = expiryDate != null ? expiryDate.format(ITALIAN_DATE) : "";
        String clientCode = user.getClientCode() != null ? user.getClientCode().toString() : "";
        String gymName = gym.getName() != null ? gym.getName() : "la palestra";

        return text
            .replace("{name}", name)
            .replace("{nome}", name)
            .replace("{clientCode}", clientCode)
            .replace("{codiceCliente}", clientCode)
            .replace("{gymName}", gymName)
            .replace("{nomePalestra}", gymName)
            .replace("{expiryDate}", formattedExpiry)
            .replace("{dataScadenza}", formattedExpiry)
            .replace("{data_scadenza}", formattedExpiry)
            .replace("{scadenza}", formattedExpiry);
    }

    private String value(Object value, String fallback) {
        return value != null && !value.toString().isBlank() ? value.toString() : fallback;
    }

    private record EmailTemplate(String subject, String body) {
    }
}

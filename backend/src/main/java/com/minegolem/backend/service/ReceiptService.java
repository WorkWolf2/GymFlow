package com.minegolem.backend.service;

import com.minegolem.backend.domain.entity.*;
import com.minegolem.backend.domain.enums.PaymentMethod;
import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;
import com.minegolem.backend.exception.ResourceNotFoundException;
import com.minegolem.backend.repository.GymRepository;
import com.minegolem.backend.repository.ReceiptRepository;
import com.minegolem.backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptService {

    private static final BigDecimal STAMP_DUTY_THRESHOLD = new BigDecimal("77.47");
    private static final BigDecimal STAMP_DUTY_AMOUNT = new BigDecimal("2.00");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReceiptRepository receiptRepository;
    private final GymRepository gymRepository;
    private final FileStorageService fileStorageService;
    private final ReceiptPdfService receiptPdfService;
    private final AuditService auditService;

    @Transactional
    public synchronized Receipt createReceiptForSubscription(Subscription subscription, Payment payment) {
        Gym gym = subscription.getUser().getGym();
        User user = subscription.getUser();
        LocalDate today = payment != null && payment.getPaymentDate() != null ? payment.getPaymentDate() : LocalDate.now();
        int year = today.getYear();

        Integer maxNumber = receiptRepository.findMaxReceiptNumberByGymIdAndYear(gym.getId(), year);
        int nextNumber = (maxNumber != null ? maxNumber : 0) + 1;
        String formattedNumber = nextNumber + "/" + year;

        BigDecimal amount = payment != null ? payment.getAmount() : subscription.getPrice();
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }

        PaymentMethod method = payment != null && payment.getMethod() != null ? payment.getMethod() : PaymentMethod.CASH;

        boolean stampDutyApplied = amount.compareTo(STAMP_DUTY_THRESHOLD) > 0;
        BigDecimal stampDuty = stampDutyApplied ? STAMP_DUTY_AMOUNT : BigDecimal.ZERO;

        String causale = buildCausale(subscription);

        Receipt receipt = Receipt.builder()
            .gym(gym)
            .user(user)
            .subscription(subscription)
            .payment(payment)
            .receiptNumber(nextNumber)
            .receiptYear(year)
            .receiptFormattedNumber(formattedNumber)
            .issueDate(today)
            .causale(causale)
            .amount(amount)
            .paymentMethod(method)
            .status("PAGATO")
            .stampDutyApplied(stampDutyApplied)
            .stampDutyAmount(stampDuty)
            .notes(subscription.getNotes())
            .createdBy(subscription.getCreatedBy())
            .build();

        Receipt saved = receiptRepository.save(receipt);

        // Genera PDF e salva su MinIO
        try {
            byte[] pdfBytes = receiptPdfService.generateReceiptPdf(saved);
            String objectName = "receipts/" + gym.getId() + "/" + year + "/ricevuta_" + nextNumber + "_" + year + ".pdf";
            String storedPath = fileStorageService.storeBytes(pdfBytes, objectName, "application/pdf");
            saved.setPdfStoragePath(storedPath);
            saved = receiptRepository.save(saved);
            log.info("Ricevuta N. {} generata e salvata su MinIO: {}", formattedNumber, storedPath);
        } catch (Exception e) {
            log.error("Errore durante la generazione/salvataggio del PDF della ricevuta N. {}", formattedNumber, e);
        }

        auditService.log("RECEIPT_CREATED", "Receipt", saved.getId().toString());
        return saved;
    }

    public String buildCausale(Subscription subscription) {
        if (subscription == null || subscription.getSubscriptionType() == null) {
            return "Quota di partecipazione alle attività sportive istituzionali";
        }

        SubscriptionType type = subscription.getSubscriptionType();
        String typeName = type.getName() != null ? type.getName() : "";
        String typeNameLower = typeName.toLowerCase();

        boolean isQuotaAssociativa = type.getType() == SubscriptionTypeEnum.ASSICURAZIONE
            || typeNameLower.contains("tesseramento")
            || typeNameLower.contains("quota associativa")
            || typeNameLower.contains("quota sociale")
            || typeNameLower.contains("iscrizione annuale");

        if (isQuotaAssociativa) {
            String sportYear = calculateSportYear(subscription.getStartDate());
            return "Quota associativa annuale ASD per l'anno sportivo " + sportYear;
        } else {
            String startStr = subscription.getStartDate() != null ? subscription.getStartDate().format(DATE_FMT) : "—";
            String endStr = subscription.getEndDate() != null ? subscription.getEndDate().format(DATE_FMT) : "—";
            return "Quota di partecipazione alle attività sportive istituzionali – " + typeName + " – periodo dal " + startStr + " al " + endStr;
        }
    }

    private String calculateSportYear(LocalDate date) {
        if (date == null) date = LocalDate.now();
        int year = date.getYear();
        // Nello sport italiano l'anno sportivo inizia solitamente a settembre/ottobre (es. 2026/2027)
        if (date.getMonthValue() >= 8) {
            return year + "/" + (year + 1);
        } else {
            return (year - 1) + "/" + year;
        }
    }

    @Transactional(readOnly = true)
    public Receipt getById(UUID id) {
        UUID gymId = currentGymId();
        return receiptRepository.findByIdAndGymIdAndDeletedAtIsNull(id, gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("Receipt", id));
    }

    @Transactional(readOnly = true)
    public Optional<Receipt> getBySubscriptionId(UUID subscriptionId) {
        return receiptRepository.findFirstBySubscriptionIdAndDeletedAtIsNullOrderByCreatedAtDesc(subscriptionId);
    }

    @Transactional(readOnly = true)
    public List<Receipt> listByUser(UUID userId) {
        return receiptRepository.findByUserIdAndDeletedAtIsNullOrderByIssueDateDescReceiptNumberDesc(userId);
    }

    @Transactional(readOnly = true)
    public byte[] getReceiptPdfBytes(UUID receiptId) {
        Receipt receipt = getById(receiptId);
        if (receipt.getPdfStoragePath() != null) {
            try (InputStream is = fileStorageService.get(receipt.getPdfStoragePath())) {
                return is.readAllBytes();
            } catch (Exception e) {
                log.warn("Impossibile leggere il file PDF da MinIO per la ricevuta {}, rigenerazione al volo...", receiptId, e);
            }
        }

        // Rigenerazione al volo del PDF se non trovato su storage
        try {
            return receiptPdfService.generateReceiptPdf(receipt);
        } catch (Exception e) {
            log.error("Errore nella rigenerazione del PDF per la ricevuta {}", receiptId, e);
            throw new RuntimeException("Errore nella generazione del documento ricevuta", e);
        }
    }

    private UUID currentGymId() {
        StaffUserDetails details = (StaffUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return details.getGymId();
    }
}

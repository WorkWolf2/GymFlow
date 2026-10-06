package com.minegolem.backend.dto.response;

import com.minegolem.backend.domain.entity.Receipt;
import com.minegolem.backend.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReceiptResponse(
    UUID id,
    UUID gymId,
    UUID userId,
    String userName,
    UUID subscriptionId,
    UUID paymentId,
    Integer receiptNumber,
    Integer receiptYear,
    String receiptFormattedNumber,
    LocalDate issueDate,
    String causale,
    BigDecimal amount,
    PaymentMethod paymentMethod,
    String status,
    boolean stampDutyApplied,
    BigDecimal stampDutyAmount,
    String pdfStoragePath,
    String pdfUrl,
    String notes,
    LocalDateTime createdAt
) {
    public static ReceiptResponse from(Receipt r, String pdfUrl) {
        if (r == null) return null;
        return new ReceiptResponse(
            r.getId(),
            r.getGym() != null ? r.getGym().getId() : null,
            r.getUser() != null ? r.getUser().getId() : null,
            r.getUser() != null ? r.getUser().getFullName() : null,
            r.getSubscription() != null ? r.getSubscription().getId() : null,
            r.getPayment() != null ? r.getPayment().getId() : null,
            r.getReceiptNumber(),
            r.getReceiptYear(),
            r.getReceiptFormattedNumber(),
            r.getIssueDate(),
            r.getCausale(),
            r.getAmount(),
            r.getPaymentMethod(),
            r.getStatus(),
            r.isStampDutyApplied(),
            r.getStampDutyAmount(),
            r.getPdfStoragePath(),
            pdfUrl != null ? pdfUrl : ("/api/receipts/" + r.getId() + "/pdf"),
            r.getNotes(),
            r.getCreatedAt()
        );
    }
}

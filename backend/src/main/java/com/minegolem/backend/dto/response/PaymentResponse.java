package com.minegolem.backend.dto.response;

import com.minegolem.backend.domain.entity.Payment;
import com.minegolem.backend.domain.enums.PaymentMethod;
import com.minegolem.backend.domain.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID userId,
    String userFullName,
    BigDecimal amount,
    BigDecimal grossAmount,
    BigDecimal discountAmount,
    PaymentType type,
    PaymentMethod method,
    LocalDate paymentDate,
    String notes
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
            payment.getId(),
            payment.getUser() != null ? payment.getUser().getId() : null,
            payment.getUser() != null ? payment.getUser().getFullName() : null,
            payment.getAmount(),
            payment.getGrossAmount() != null ? payment.getGrossAmount() : payment.getAmount(),
            payment.getDiscountAmount() != null ? payment.getDiscountAmount() : BigDecimal.ZERO,
            payment.getType() != null ? payment.getType() : PaymentType.INCOME,
            payment.getMethod(),
            payment.getPaymentDate(),
            localizedNotes(payment.getNotes())
        );
    }

    private static String localizedNotes(String notes) {
        if (notes == null) return null;
        String prefix = "Auto-payment for subscription ";
        if (notes.startsWith(prefix)) {
            return "Incasso automatico per abbonamento: " + notes.substring(prefix.length());
        }
        return notes;
    }
}

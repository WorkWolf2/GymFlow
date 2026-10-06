package com.minegolem.backend.domain.entity;

import com.minegolem.backend.domain.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "receipts")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Receipt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private Gym gym;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "receipt_number", nullable = false)
    private Integer receiptNumber;

    @Column(name = "receipt_year", nullable = false)
    private Integer receiptYear;

    @Column(name = "receipt_formatted_number", nullable = false, length = 50)
    private String receiptFormattedNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String causale;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PAGATO";

    @Column(name = "stamp_duty_applied", nullable = false)
    @Builder.Default
    private boolean stampDutyApplied = false;

    @Column(name = "stamp_duty_amount")
    @Builder.Default
    private BigDecimal stampDutyAmount = BigDecimal.ZERO;

    @Column(name = "pdf_storage_path")
    private String pdfStoragePath;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private StaffUser createdBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}

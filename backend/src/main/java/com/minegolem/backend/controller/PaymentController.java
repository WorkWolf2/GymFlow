package com.minegolem.backend.controller;

import com.minegolem.backend.domain.entity.Payment;
import com.minegolem.backend.dto.request.PaymentRequest;
import com.minegolem.backend.dto.response.PaymentResponse;
import com.minegolem.backend.security.StaffUserDetails;
import com.minegolem.backend.service.PaymentService;
import com.minegolem.backend.service.PaymentReportService;
import com.minegolem.backend.service.RealtimeEventService;
import com.minegolem.backend.domain.enums.PaymentType;
import com.minegolem.backend.domain.enums.PaymentMethod;
import com.lowagie.text.DocumentException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentReportService paymentReportService;
    private final RealtimeEventService realtimeEventService;

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public ResponseEntity<Page<PaymentResponse>> list(
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(paymentService.list(pageable).map(PaymentResponse::from));
    }

    @GetMapping("/range")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public ResponseEntity<List<PaymentResponse>> listByRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(paymentService.listByDateRange(from, to).stream().map(PaymentResponse::from).toList());
    }


    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public ResponseEntity<List<PaymentResponse>> listByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(paymentService.listByUser(userId).stream().map(PaymentResponse::from).toList());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public ResponseEntity<PaymentService.DailyStats> stats(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(paymentService.getDailyStats(date != null ? date : LocalDate.now()));
    }

    @GetMapping("/calendar-dates")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public ResponseEntity<List<LocalDate>> calendarDates() {
        return ResponseEntity.ok(paymentService.getDatesWithPayments());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    public ResponseEntity<PaymentResponse> create(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @Valid @RequestBody PaymentRequest request
    ) {
        Payment payment = paymentService.create(request);
        realtimeEventService.publish(userDetails.getGymId(), "PAYMENT", "CREATED", payment.getId());
        realtimeEventService.publish(userDetails.getGymId(), "DASHBOARD", "UPDATED", payment.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment));
    }

    @GetMapping("/report/pdf")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    public void downloadPdfReport(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) PaymentType type,
        @RequestParam(required = false) PaymentMethod method,
        HttpServletResponse response
    ) throws IOException, DocumentException {
        paymentReportService.generatePdfReport(from, to, type, method, response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PAYMENT_WRITE')")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PathVariable UUID id
    ) {
        paymentService.delete(id);
        realtimeEventService.publish(userDetails.getGymId(), "PAYMENT", "DELETED", id);
        realtimeEventService.publish(userDetails.getGymId(), "DASHBOARD", "UPDATED", id);
        return ResponseEntity.noContent().build();
    }
}

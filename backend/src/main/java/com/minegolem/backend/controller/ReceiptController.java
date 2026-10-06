package com.minegolem.backend.controller;

import com.minegolem.backend.domain.entity.Receipt;
import com.minegolem.backend.dto.response.ReceiptResponse;
import com.minegolem.backend.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ')")
    public ResponseEntity<ReceiptResponse> getById(@PathVariable UUID id) {
        Receipt receipt = receiptService.getById(id);
        return ResponseEntity.ok(ReceiptResponse.from(receipt, "/api/receipts/" + id + "/pdf"));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ')")
    public ResponseEntity<byte[]> viewPdf(@PathVariable UUID id) {
        Receipt receipt = receiptService.getById(id);
        byte[] pdfBytes = receiptService.getReceiptPdfBytes(id);

        String filename = "Ricevuta_" + receipt.getReceiptNumber() + "_" + receipt.getReceiptYear() + ".pdf";

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
            .body(pdfBytes);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        Receipt receipt = receiptService.getById(id);
        byte[] pdfBytes = receiptService.getReceiptPdfBytes(id);

        String filename = "Ricevuta_" + receipt.getReceiptNumber() + "_" + receipt.getReceiptYear() + ".pdf";

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(pdfBytes);
    }

    @GetMapping("/subscription/{subscriptionId}")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ')")
    public ResponseEntity<ReceiptResponse> getBySubscription(@PathVariable UUID subscriptionId) {
        return receiptService.getBySubscriptionId(subscriptionId)
            .map(r -> ResponseEntity.ok(ReceiptResponse.from(r, "/api/receipts/" + r.getId() + "/pdf")))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ')")
    public ResponseEntity<List<ReceiptResponse>> listByUser(@PathVariable UUID userId) {
        List<ReceiptResponse> responses = receiptService.listByUser(userId).stream()
            .map(r -> ReceiptResponse.from(r, "/api/receipts/" + r.getId() + "/pdf"))
            .toList();
        return ResponseEntity.ok(responses);
    }
}

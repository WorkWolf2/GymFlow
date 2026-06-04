package com.minegolem.backend.controller;

import com.minegolem.backend.dto.request.EmailSendRequest;
import com.minegolem.backend.dto.request.BulkEmailRequest;
import com.minegolem.backend.dto.response.BulkEmailResponse;
import com.minegolem.backend.service.EmailService;
import com.minegolem.backend.domain.entity.User;
import com.minegolem.backend.repository.UserRepository;
import com.minegolem.backend.security.StaffUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.minegolem.backend.service.RealtimeEventService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final RealtimeEventService realtimeEventService;

    @PostMapping("/send")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<Void> sendEmail(
            @AuthenticationPrincipal StaffUserDetails userDetails,
            @Valid @RequestBody EmailSendRequest request) {
        String recipientEmail = request.email();
        String recipientName = "Cliente";

        if (request.userId() != null) {
            Optional<User> opt = userRepository.findById(request.userId());

            if (opt.isPresent()) {
                User user = opt.get();

                if (user.getEmail() != null && !user.getEmail().isBlank()) {
                    recipientEmail = user.getEmail();
                    recipientName = user.getFullName();
                }
            }
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            throw new IllegalArgumentException("Email non valida");
        }
        
        String expiryDate = request.expiryDate() != null ? request.expiryDate() : "";
        String customizedSubject = applyPlaceholders(request.subject(), recipientName, expiryDate);
        String customizedBody = applyPlaceholders(request.body(), recipientName, expiryDate);
            
        emailService.sendEmail(userDetails.getGymId(), recipientEmail, customizedSubject, customizedBody);
        realtimeEventService.publish(userDetails.getGymId(), "EMAIL", "SENT", request.userId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/send-all")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<BulkEmailResponse> sendBulkEmail(
            @AuthenticationPrincipal StaffUserDetails userDetails,
            @Valid @RequestBody BulkEmailRequest request) {
        
        UUID gymId = userDetails.getGymId();
        List<User> recipients = userRepository.findAllWithValidEmailByGymId(gymId);
        
        // Safety limit check
        if (recipients.size() > 500) {
            return ResponseEntity.badRequest().body(new BulkEmailResponse(
                0, 
                recipients.size(), 
                List.of("Impossibile inviare a più di 500 destinatari contemporaneamente. Trovati: " + recipients.size())
            ));
        }
        
        int totalSent = 0;
        int totalFailed = 0;
        List<String> errors = new ArrayList<>();
        
        for (User recipient : recipients) {
            try {
                String recipientName = recipient.getFullName();
                String customizedBody = request.body()
                    .replace("{name}", recipientName)
                    .replace("{nome}", recipientName);
                
                emailService.sendEmail(gymId, recipient.getEmail(), request.subject(), customizedBody);
                totalSent++;
            } catch (Exception e) {
                totalFailed++;
                errors.add("Errore per " + recipient.getFullName() + " (" + recipient.getEmail() + "): " + e.getMessage());
            }
        }
        
        realtimeEventService.publish(gymId, "EMAIL", "BULK_SENT");
        return ResponseEntity.ok(new BulkEmailResponse(totalSent, totalFailed, errors));
    }

    private String applyPlaceholders(String text, String recipientName, String expiryDate) {
        return text
            .replace("{name}", recipientName)
            .replace("{nome}", recipientName)
            .replace("{expiryDate}", expiryDate)
            .replace("{dataScadenza}", expiryDate)
            .replace("{data_scadenza}", expiryDate)
            .replace("{scadenza}", expiryDate);
    }
}

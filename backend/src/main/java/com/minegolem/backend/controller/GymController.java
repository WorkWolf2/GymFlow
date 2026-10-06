package com.minegolem.backend.controller;

import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.repository.GymRepository;
import com.minegolem.backend.security.StaffUserDetails;
import com.minegolem.backend.service.EmailService;
import com.minegolem.backend.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/gym")
@RequiredArgsConstructor
public class GymController {

    private final GymRepository gymRepository;
    private final EmailService emailService;
    private final RealtimeEventService realtimeEventService;
    private static final String SUBSCRIPTION_EXPIRATION_ID = "subscription-expiration";
    private static final String CERTIFICATE_EXPIRATION_ID = "certificate-expiration";

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<Map<String, Object>> getSettings(@AuthenticationPrincipal StaffUserDetails userDetails) {
        Gym gym = gymRepository.findById(userDetails.getGymId()).orElseThrow();
        return ResponseEntity.ok(gym.getSettings() != null ? gym.getSettings() : Map.of());
    }

    @PostMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateSettings(
            @AuthenticationPrincipal StaffUserDetails userDetails,
            @RequestBody Map<String, Object> settings) {
        Gym gym = gymRepository.findById(userDetails.getGymId()).orElseThrow();
        Map<String, Object> currentSettings = gym.getSettings();
        if (currentSettings != null) {
            Map<String, Object> merged = new HashMap<>(currentSettings);
            merged.putAll(settings);
            gym.setSettings(merged);
        } else {
            gym.setSettings(settings);
        }
        gymRepository.save(gym);
        realtimeEventService.publish(userDetails.getGymId(), "SETTINGS", "UPDATED");
        return ResponseEntity.ok(gym.getSettings());
    }

    @GetMapping("/settings/smtp")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<Map<String, Object>> getSmtpSettings(@AuthenticationPrincipal StaffUserDetails userDetails) {
        Gym gym = gymRepository.findById(userDetails.getGymId()).orElseThrow();
        Map<String, Object> settings = gym.getSettings();
        Map<String, Object> smtpConfig = new HashMap<>();
        if (settings != null) {
            smtpConfig.put("smtpHost", settings.getOrDefault("smtpHost", ""));
            smtpConfig.put("smtpPort", settings.getOrDefault("smtpPort", 587));
            smtpConfig.put("smtpUsername", settings.getOrDefault("smtpUsername", ""));
            smtpConfig.put("smtpStarttls", settings.getOrDefault("smtpStarttls", true));
            
            String pwd = (String) settings.get("smtpPassword");
            boolean hasPassword = pwd != null && !pwd.isBlank();
            smtpConfig.put("hasPassword", hasPassword);
            smtpConfig.put("smtpPassword", "");
        } else {
            smtpConfig.put("smtpHost", "");
            smtpConfig.put("smtpPort", 587);
            smtpConfig.put("smtpUsername", "");
            smtpConfig.put("smtpPassword", "");
            smtpConfig.put("smtpStarttls", true);
            smtpConfig.put("hasPassword", false);
        }
        return ResponseEntity.ok(smtpConfig);
    }

    @PostMapping("/settings/smtp")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateSmtpSettings(
            @AuthenticationPrincipal StaffUserDetails userDetails,
            @RequestBody Map<String, Object> smtpSettings) {
        Gym gym = gymRepository.findById(userDetails.getGymId()).orElseThrow();
        Map<String, Object> currentSettings = gym.getSettings() != null 
            ? new HashMap<>(gym.getSettings()) 
            : new HashMap<>();
        
        currentSettings.put("smtpHost", smtpSettings.get("smtpHost"));
        currentSettings.put("smtpPort", smtpSettings.get("smtpPort"));
        currentSettings.put("smtpUsername", smtpSettings.get("smtpUsername"));
        currentSettings.put("smtpStarttls", smtpSettings.get("smtpStarttls"));
        
        String newPassword = (String) smtpSettings.get("smtpPassword");
        if (newPassword != null && !newPassword.isBlank() && !newPassword.equals("********")) {
            currentSettings.put("smtpPassword", newPassword.trim());
        }
        
        gym.setSettings(currentSettings);
        gymRepository.save(gym);
        realtimeEventService.publish(userDetails.getGymId(), "SETTINGS", "SMTP_UPDATED");

        Map<String, Object> response = new HashMap<>(currentSettings);
        String savedPwd = (String) currentSettings.get("smtpPassword");
        response.put("hasPassword", savedPwd != null && !savedPwd.isBlank());
        response.put("smtpPassword", "");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/settings/smtp/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> testSmtpSettings(
            @AuthenticationPrincipal StaffUserDetails userDetails,
            @RequestBody Map<String, Object> smtpSettings) {
        Gym gym = gymRepository.findById(userDetails.getGymId()).orElseThrow();
        
        String host = (String) smtpSettings.get("smtpHost");
        
        Object portObj = smtpSettings.get("smtpPort");
        int port = 587;
        if (portObj != null) {
            if (portObj instanceof Number) {
                port = ((Number) portObj).intValue();
            } else {
                try {
                    port = (int) Double.parseDouble(portObj.toString());
                } catch (Exception e) {
                    port = 587;
                }
            }
        }
        
        String username = (String) smtpSettings.get("smtpUsername");
        String password = (String) smtpSettings.get("smtpPassword");
        boolean starttls = Boolean.parseBoolean(String.valueOf(smtpSettings.getOrDefault("smtpStarttls", true)));
        
        if (password == null || password.isBlank() || "********".equals(password)) {
            Map<String, Object> current = gym.getSettings();
            if (current != null && current.get("smtpPassword") != null) {
                password = (String) current.get("smtpPassword");
            }
        }
        
        java.util.Map<String, Object> result = emailService.testSmtpConnection(host, port, username, password, starttls);
        boolean success = (Boolean) result.getOrDefault("success", false);
        if (success) {
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", result.getOrDefault("message", "Connessione SMTP stabilita con successo!")));
        } else {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                "success", false,
                "message", result.getOrDefault("error", "Impossibile stabilire la connessione SMTP. Verifica i parametri inseriti."),
                "error", result.getOrDefault("error", "")));
        }
    }

    @PostMapping("/settings/smtp/send-test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> sendTestEmail(
            @AuthenticationPrincipal StaffUserDetails userDetails,
            @RequestBody Map<String, Object> request) {
        Gym gym = gymRepository.findById(userDetails.getGymId()).orElseThrow();
        String to = (String) request.get("toEmail");
        if (to == null || to.isBlank()) {
            if (gym.getSettings() != null) {
                to = (String) gym.getSettings().get("smtpUsername");
            }
        }
        if (to == null || to.isBlank()) {
            to = userDetails.getUsername(); // staff email
        }
        if (to == null || to.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Indirizzo email di destinazione mancante."));
        }

        try {
            String subject = "Test Configurazione Email - " + gym.getName();
            String body = "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e5e7eb; rounded: 8px;\">"
                    + "<h2 style=\"color: #10b981; margin-top: 0;\">✓ Test Connessione & Invio Email Riuscito!</h2>"
                    + "<p>Se stai leggendo questa email, il tuo server SMTP per <strong>" + gym.getName() + "</strong> è configurato e funzionante correttamente su GymFlow.</p>"
                    + "<hr style=\"border: none; border-top: 1px solid #e5e7eb; margin: 20px 0;\" />"
                    + "<p style=\"font-size: 13px; color: #6b7280;\">Data e ora di invio: <strong>"
                    + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                    + "</strong></p>"
                    + "</div>";
            
            emailService.sendEmail(gym.getId(), to.trim(), subject, body);
            return ResponseEntity.ok(Map.of("success", true, "message", "Email di prova inviata con successo a " + to + "! Controlla la tua casella di posta (inclusa la cartella Spam)."));
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                errorMsg += " (" + e.getCause().getMessage() + ")";
            }
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", errorMsg));
        }
    }

    @PostMapping("/settings/email-templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> updateEmailTemplates(
            @AuthenticationPrincipal StaffUserDetails userDetails,
            @RequestBody List<Map<String, Object>> emailTemplates) {
        // Ensure each template ID is a string for consistent client handling
        emailTemplates.forEach(t -> {
            Object id = t.get("id");
            if (id != null) {
                t.put("id", id.toString());
            }
        });
        Gym gym = gymRepository.findById(userDetails.getGymId()).orElseThrow();
        Map<String, Object> currentSettings = gym.getSettings() != null 
            ? new HashMap<>(gym.getSettings()) 
            : new HashMap<>();
        
        currentSettings.put("emailTemplates", emailTemplates);
        gym.setSettings(currentSettings);
        gymRepository.save(gym);
        realtimeEventService.publish(userDetails.getGymId(), "EMAIL_TEMPLATE", "UPDATED");
        // Return only the list of templates so the UI can replace its local array directly
        return ResponseEntity.ok(emailTemplates);
    }

    @GetMapping("/settings/expiration-templates")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<List<Map<String, Object>>> getExpirationTemplates(@AuthenticationPrincipal StaffUserDetails userDetails) {
        Gym gym = gymRepository.findById(userDetails.getGymId()).orElseThrow();
        Map<String, Object> settings = gym.getSettings();
        if (settings != null && settings.containsKey("expirationTemplates")) {
            return ResponseEntity.ok(normalizeExpirationTemplates((List<Map<String, Object>>) settings.get("expirationTemplates")));
        }
        return ResponseEntity.ok(defaultExpirationTemplates());
    }

    @GetMapping("/settings/email-templates")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<List<Map<String, Object>>> getEmailTemplates(@AuthenticationPrincipal StaffUserDetails userDetails) {
        Gym gym = gymRepository.findById(userDetails.getGymId()).orElseThrow();
        Map<String, Object> settings = gym.getSettings();

        if (settings != null && settings.containsKey("emailTemplates")) {
            return ResponseEntity.ok((List<Map<String, Object>>) settings.get("emailTemplates"));
        }

        // Se non ci sono template salvati, restituisce una lista vuota
        return ResponseEntity.ok(List.of());
    }

    @PostMapping("/settings/expiration-templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> updateExpirationTemplates(
            @AuthenticationPrincipal StaffUserDetails userDetails,
            @RequestBody List<Map<String, Object>> expirationTemplates) {
        List<Map<String, Object>> normalizedTemplates = normalizeExpirationTemplates(expirationTemplates);
        Gym gym = gymRepository.findById(userDetails.getGymId()).orElseThrow();
        Map<String, Object> currentSettings = gym.getSettings() != null 
            ? new HashMap<>(gym.getSettings()) 
            : new HashMap<>();
        
        currentSettings.put("expirationTemplates", normalizedTemplates);
        gym.setSettings(currentSettings);
        gymRepository.save(gym);
        realtimeEventService.publish(userDetails.getGymId(), "EXPIRATION_TEMPLATE", "UPDATED");
        return ResponseEntity.ok(normalizedTemplates);
    }

    private static final String WELCOME_TEMPLATE_ID = "client-welcome";

    private List<Map<String, Object>> normalizeExpirationTemplates(List<Map<String, Object>> templates) {
        Map<String, Object> welcome = findExpirationTemplate(templates, WELCOME_TEMPLATE_ID, "benvenut");
        Map<String, Object> subscription = findExpirationTemplate(templates, SUBSCRIPTION_EXPIRATION_ID, "abbon");
        Map<String, Object> certificate = findExpirationTemplate(templates, CERTIFICATE_EXPIRATION_ID, "cert");

        return List.of(
            normalizeSystemTemplate(welcome, WELCOME_TEMPLATE_ID, "Email di Benvenuto", "Nuovo Iscritto", "user-plus", "text-success",
                "Benvenuto in {gymName}",
                "Ciao {name},<br><br>benvenuto in <strong>{gymName}</strong>! La tua scheda cliente è stata registrata con successo.<br><br>Il tuo codice iscritto è: <strong>#{clientCode}</strong>.<br><br>A presto,<br>Lo staff di {gymName}", false),
            normalizeSystemTemplate(subscription, SUBSCRIPTION_EXPIRATION_ID, "Abbonamento in scadenza", "Abbonamento", "dumbbell", "text-accent",
                "Avviso abbonamento in scadenza - {gymName}",
                "Ciao {name},<br><br>ti ricordiamo che il tuo abbonamento scadrà il <strong>{expiryDate}</strong>.<br><br>Passa in reception per il rinnovo.<br><br>Lo staff di {gymName}", true),
            normalizeSystemTemplate(certificate, CERTIFICATE_EXPIRATION_ID, "Certificato in scadenza", "Certificato", "activity", "text-warning",
                "Scadenza certificato medico - {gymName}",
                "Ciao {name},<br><br>ti ricordiamo che il tuo certificato medico scadrà il <strong>{expiryDate}</strong>.<br><br>Consegna il certificato aggiornato in reception prima della scadenza.<br><br>Lo staff di {gymName}", true)
        );
    }

    private List<Map<String, Object>> defaultExpirationTemplates() {
        return normalizeExpirationTemplates(List.of());
    }

    private Map<String, Object> findExpirationTemplate(List<Map<String, Object>> templates, String id, String textMatch) {
        if (templates == null) {
            return null;
        }

        return templates.stream()
            .filter(t -> {
                String templateId = String.valueOf(t.getOrDefault("id", ""));
                String category = String.valueOf(t.getOrDefault("category", ""));
                String name = String.valueOf(t.getOrDefault("name", ""));
                String haystack = (templateId + " " + category + " " + name).toLowerCase();
                return templateId.equals(id) || haystack.contains(textMatch);
            })
            .findFirst()
            .orElse(null);
    }

    private Map<String, Object> normalizeSystemTemplate(
            Map<String, Object> source,
            String id,
            String name,
            String category,
            String icon,
            String colorClass,
            String defaultSubject,
            String defaultBody,
            boolean requiresExpiry) {
        Map<String, Object> template = new HashMap<>();
        template.put("id", id);
        template.put("name", name);
        template.put("category", category);
        template.put("colorClass", colorClass);
        template.put("icon", icon);
        template.put("subject", defaultSubject);
        template.put("body", defaultBody);

        if (source != null) {
            Object subject = source.get("subject");
            Object body = source.get("body");
            if (subject != null && !subject.toString().isBlank()) {
                template.put("subject", subject.toString());
            }
            if (body != null && !body.toString().isBlank()) {
                String bodyStr = body.toString();
                if (requiresExpiry) {
                    bodyStr = ensureExpiryPlaceholder(bodyStr);
                }
                template.put("body", bodyStr);
            }
        }

        return template;
    }

    private String ensureExpiryPlaceholder(String body) {
        if (body.contains("{expiryDate}") || body.contains("{dataScadenza}")
                || body.contains("{data_scadenza}") || body.contains("{scadenza}")) {
            return body;
        }
        return body + "<br><br>Data di scadenza: <strong>{expiryDate}</strong>";
    }
}

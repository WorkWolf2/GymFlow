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
            if (pwd != null && !pwd.isBlank()) {
                smtpConfig.put("smtpPassword", "********");
            } else {
                smtpConfig.put("smtpPassword", "");
            }
        } else {
            smtpConfig.put("smtpHost", "");
            smtpConfig.put("smtpPort", 587);
            smtpConfig.put("smtpUsername", "");
            smtpConfig.put("smtpPassword", "");
            smtpConfig.put("smtpStarttls", true);
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
        if (newPassword != null && !newPassword.equals("********")) {
            currentSettings.put("smtpPassword", newPassword);
        }
        
        gym.setSettings(currentSettings);
        gymRepository.save(gym);
        realtimeEventService.publish(userDetails.getGymId(), "SETTINGS", "SMTP_UPDATED");
        return ResponseEntity.ok(gym.getSettings());
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
        
        if ("********".equals(password)) {
            Map<String, Object> current = gym.getSettings();
            if (current != null) {
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

    private List<Map<String, Object>> normalizeExpirationTemplates(List<Map<String, Object>> templates) {
        Map<String, Object> subscription = findExpirationTemplate(templates, SUBSCRIPTION_EXPIRATION_ID, "abbon");
        Map<String, Object> certificate = findExpirationTemplate(templates, CERTIFICATE_EXPIRATION_ID, "cert");

        return List.of(
            normalizeExpirationTemplate(subscription, SUBSCRIPTION_EXPIRATION_ID, "Abbonamento in scadenza", "Abbonamento", "dumbbell",
                "Avviso abbonamento in scadenza - GymSaaS",
                "Ciao {name},<br><br>ti ricordiamo che il tuo abbonamento scadra il <strong>{expiryDate}</strong>.<br><br>Passa in reception per il rinnovo.<br><br>Lo staff di GymSaaS"),
            normalizeExpirationTemplate(certificate, CERTIFICATE_EXPIRATION_ID, "Certificato in scadenza", "Certificato", "activity",
                "Scadenza certificato medico - GymSaaS",
                "Ciao {name},<br><br>ti ricordiamo che il tuo certificato medico scadra il <strong>{expiryDate}</strong>.<br><br>Consegna il certificato aggiornato in reception prima della scadenza.<br><br>Lo staff di GymSaaS")
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

    private Map<String, Object> normalizeExpirationTemplate(
            Map<String, Object> source,
            String id,
            String name,
            String category,
            String icon,
            String defaultSubject,
            String defaultBody) {
        Map<String, Object> template = new HashMap<>();
        template.put("id", id);
        template.put("name", name);
        template.put("category", category);
        template.put("colorClass", CERTIFICATE_EXPIRATION_ID.equals(id) ? "text-warning" : "text-accent");
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
                template.put("body", ensureExpiryPlaceholder(body.toString()));
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

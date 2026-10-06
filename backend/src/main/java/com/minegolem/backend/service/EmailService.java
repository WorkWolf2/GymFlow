package com.minegolem.backend.service;

import com.minegolem.backend.domain.entity.Gym;
import com.minegolem.backend.repository.GymRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final GymRepository gymRepository;

    private int parsePort(Object portObj) {
        if (portObj == null) {
            return 587;
        }
        if (portObj instanceof Number) {
            return ((Number) portObj).intValue();
        }
        try {
            return (int) Double.parseDouble(portObj.toString());
        } catch (Exception e) {
            return 587;
        }
    }

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}")
    private String defaultFromEmail;

    private void configureSender(JavaMailSenderImpl sender, String host, int port, String username, String password, boolean starttls) {
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");

        java.util.Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", port == 465 ? "smtps" : "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.smtp.connectiontimeout", "8000");
        props.put("mail.smtp.timeout", "8000");
        props.put("mail.smtp.writetimeout", "8000");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        if (username != null && !username.isBlank()) {
            props.put("mail.smtp.from", username.trim());
        }

        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", "465");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
            if (starttls) {
                props.put("mail.smtp.starttls.required", "false");
            }
        }
    }

    private String cleanPassword(String host, String password) {
        if (password == null) {
            return "";
        }
        password = password.trim();
        // If it's a 16-character Google App Password with space separators like "abcd efgh ijkl mnop"
        if (host != null && host.toLowerCase().contains("gmail") && password.length() == 19 && password.contains(" ")) {
            password = password.replace(" ", "");
        }
        return password;
    }

    public JavaMailSender getMailSender(UUID gymId) {
        if (gymId == null) {
            return mailSender;
        }

        Gym gym = gymRepository.findById(gymId).orElse(null);
        if (gym == null || gym.getSettings() == null) {
            return mailSender;
        }

        Map<String, Object> settings = gym.getSettings();
        String host = (String) settings.get("smtpHost");
        String username = (String) settings.get("smtpUsername");
        String password = (String) settings.get("smtpPassword");

        if (host != null && !host.isBlank() && username != null && !username.isBlank()) {
            try {
                int port = parsePort(settings.get("smtpPort"));
                boolean starttls = Boolean.parseBoolean(String.valueOf(settings.getOrDefault("smtpStarttls", true)));
                String cleanPwd = cleanPassword(host, password);

                JavaMailSenderImpl dynamicSender = new JavaMailSenderImpl();
                configureSender(dynamicSender, host.trim(), port, username.trim(), cleanPwd, starttls);

                log.info("Utilizzo del server SMTP personalizzato per la palestra: {}", gym.getName());
                return dynamicSender;
            } catch (Exception e) {
                log.error("Errore nella configurazione SMTP personalizzata per la palestra {}, utilizzo del default", gym.getName(), e);
            }
        }

        return mailSender;
    }

    public void sendEmail(String to, String subject, String body) {
        sendEmail(null, to, subject, body);
    }

    public void sendEmail(UUID gymId, String to, String subject, String body) {
        JavaMailSender sender = getMailSender(gymId);
        Gym gym = gymId != null ? gymRepository.findById(gymId).orElse(null) : null;

        String fromEmail = null;
        String fromPersonal = gym != null ? gym.getName() : "GymFlow";

        if (gym != null && gym.getSettings() != null) {
            Object smtpUser = gym.getSettings().get("smtpUsername");
            if (smtpUser != null && !smtpUser.toString().isBlank()) {
                fromEmail = smtpUser.toString().trim();
            }
            Object asdName = gym.getSettings().get("asdName");
            if (asdName != null && !asdName.toString().isBlank()) {
                fromPersonal = asdName.toString().trim();
            }
        }
        if ((fromEmail == null || fromEmail.isBlank()) && defaultFromEmail != null && !defaultFromEmail.isBlank()) {
            fromEmail = defaultFromEmail.trim();
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            if (fromEmail != null && !fromEmail.isBlank()) {
                if (fromPersonal != null && !fromPersonal.isBlank()) {
                    helper.setFrom(fromEmail, fromPersonal);
                } else {
                    helper.setFrom(fromEmail);
                }
                String replyTo = (gym != null && gym.getEmail() != null && !gym.getEmail().isBlank())
                        ? gym.getEmail().trim()
                        : fromEmail;
                helper.setReplyTo(replyTo);
            }

            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(body, true);

            sender.send(message);
            log.info("Email inviata con successo da <{}> a <{}> con oggetto: {}", fromEmail != null ? fromEmail : "default", to, subject);
        } catch (Exception e) {
            log.error("Errore durante l'invio della mail da <" + fromEmail + "> a <" + to + ">: " + e.getMessage(), e);
            throw new RuntimeException("Impossibile inviare la mail: " + e.getMessage(), e);
        }
    }

    public java.util.Map<String, Object> testSmtpConnection(String host, int port, String username, String password, boolean starttls) {
        try {
            if (host == null || host.isBlank()) {
                return java.util.Map.of("success", false, "error", "Host SMTP mancante");
            }
            if (username == null || username.isBlank()) {
                return java.util.Map.of("success", false, "error", "Username (email) SMTP mancante");
            }
            if (password == null || password.isBlank()) {
                return java.util.Map.of("success", false, "error", "Password SMTP non specificata né salvata in precedenza");
            }

            String cleanHost = host.trim();
            String cleanUsername = username.trim();
            String cleanPwd = cleanPassword(cleanHost, password);

            JavaMailSenderImpl testSender = new JavaMailSenderImpl();
            configureSender(testSender, cleanHost, port, cleanUsername, cleanPwd, starttls);
            
            jakarta.mail.Session session = testSender.getSession();
            String protocol = (port == 465) ? "smtps" : "smtp";
            jakarta.mail.Transport transport = session.getTransport(protocol);
            transport.connect(cleanHost, port, cleanUsername, cleanPwd);
            transport.close();
            return java.util.Map.of("success", true, "message", "Connessione SMTP stabilita con successo!");
        } catch (Exception e) {
            log.error("Errore durante il test di connessione SMTP: " + e.getMessage(), e);
            String errorMsg = e.getMessage();
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                errorMsg += " (" + e.getCause().getMessage() + ")";
            }
            return java.util.Map.of("success", false, "error", errorMsg);
        }
    }
}


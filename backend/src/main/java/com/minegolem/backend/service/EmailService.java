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

    public JavaMailSender getMailSender(UUID gymId) {
        if (gymId == null) {
            return mailSender;
        }

        Gym gym = gymRepository.findById(gymId).orElse(null);
        if (gym == null || gym.getSettings() == null) {
            return mailSender;
        }

        Map<String, Object> settings = gym.getSettings();
        if (settings.containsKey("smtpHost") && settings.containsKey("smtpUsername")) {
            try {
                JavaMailSenderImpl dynamicSender = new JavaMailSenderImpl();
                dynamicSender.setHost((String) settings.get("smtpHost"));
                dynamicSender.setPort(parsePort(settings.get("smtpPort")));
                dynamicSender.setUsername((String) settings.get("smtpUsername"));
                dynamicSender.setPassword((String) settings.get("smtpPassword"));

                java.util.Properties props = dynamicSender.getJavaMailProperties();
                props.put("mail.transport.protocol", "smtp");
                props.put("mail.smtp.auth", "true");
                boolean starttls = Boolean.parseBoolean(String.valueOf(settings.getOrDefault("smtpStarttls", true)));
                props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
                props.put("mail.smtp.ssl.trust", "*");
                props.put("mail.smtp.connectiontimeout", "5000");
                props.put("mail.smtp.timeout", "5000");
                props.put("mail.smtp.writetimeout", "5000");

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
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // Set to true to support rich HTML content
            
            sender.send(message);
            log.info("Email inviata con successo a: {}", to);
        } catch (MessagingException e) {
            log.error("Errore durante l'invio della mail a " + to, e);
            throw new RuntimeException("Impossibile inviare la mail: " + e.getMessage(), e);
        }
    }

    public java.util.Map<String, Object> testSmtpConnection(String host, int port, String username, String password, boolean starttls) {
        try {
            JavaMailSenderImpl testSender = new JavaMailSenderImpl();
            testSender.setHost(host);
            testSender.setPort(port);
            testSender.setUsername(username);
            testSender.setPassword(password);
            
            java.util.Properties props = testSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
            props.put("mail.smtp.ssl.trust", "*");
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");
            props.put("mail.smtp.writetimeout", "5000");
            
            jakarta.mail.Session session = testSender.getSession();
            jakarta.mail.Transport transport = session.getTransport("smtp");
            transport.connect(host, port, username, password);
            transport.close();
            return java.util.Map.of("success", true);
        } catch (Exception e) {
            log.error("Errore durante il test di connessione SMTP: " + e.getMessage(), e);
            return java.util.Map.of("success", false, "error", e.getMessage());
        }
    }
}


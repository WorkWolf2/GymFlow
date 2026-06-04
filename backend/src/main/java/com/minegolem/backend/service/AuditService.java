package com.minegolem.backend.service;


import com.minegolem.backend.domain.entity.AuditLog;
import com.minegolem.backend.repository.AuditLogRepository;
import com.minegolem.backend.security.StaffUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(String action, String entityType, String entityId) {
        log(action, entityType, entityId, null, null);
    }

    @Async
    public void log(String action, String entityType, String entityId,
                    Map<String, Object> oldValue, Map<String, Object> newValue) {
        try {
            UUID gymId = null;
            UUID staffUserId = null;
            String ip = null;

            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof StaffUserDetails details) {
                gymId = details.getGymId();
                staffUserId = details.getUserId();
            }

            var requestAttrs = RequestContextHolder.getRequestAttributes();
            if (requestAttrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                ip = request.getHeader("X-Forwarded-For");
                if (ip == null) ip = request.getRemoteAddr();
            }

            AuditLog auditLog = AuditLog.builder()
                .gymId(gymId)
                .staffUserId(staffUserId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .ipAddress(ip)
                .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }
}

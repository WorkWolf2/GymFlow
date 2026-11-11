package com.minegolem.backend.controller;

import com.minegolem.backend.domain.entity.StaffUser;
import com.minegolem.backend.dto.response.AuditLogResponse;
import com.minegolem.backend.repository.AuditLogRepository;
import com.minegolem.backend.repository.StaffUserRepository;
import com.minegolem.backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final StaffUserRepository staffUserRepository;

    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> list(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Pageable cleanPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Map<UUID, String> staffNames = staffNames(userDetails.getGymId());
        Page<AuditLogResponse> logs = auditLogRepository
            .findByGymId(userDetails.getGymId(), cleanPageable)
            .map(log -> toResponse(log, staffNames));
        return ResponseEntity.ok(logs);
    }

    private Map<UUID, String> staffNames(UUID gymId) {
        if (gymId == null) return Map.of();
        return staffUserRepository.findByGymId(gymId).stream()
            .filter(s -> s.getId() != null)
            .collect(Collectors.toMap(
                StaffUser::getId,
                s -> s.getFullName() != null ? s.getFullName() : "Staff",
                (existing, replacement) -> existing
            ));
    }

    private AuditLogResponse toResponse(com.minegolem.backend.domain.entity.AuditLog log,
                                        Map<UUID, String> staffNames) {
        String staffName = log.getStaffUserId() == null
            ? "Account non disponibile"
            : staffNames.getOrDefault(log.getStaffUserId(), "Account rimosso");
        return new AuditLogResponse(
            log.getId(), staffName, log.getAction(), log.getEntityType(), log.getEntityId(),
            log.getOldValue(), log.getNewValue(), log.getCreatedAt()
        );
    }
}

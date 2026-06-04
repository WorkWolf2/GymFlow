package com.minegolem.backend.controller;


import com.minegolem.backend.domain.entity.SubscriptionType;
import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;
import com.minegolem.backend.dto.request.SubscriptionTypeRequest;
import com.minegolem.backend.dto.response.SubscriptionTypeResponse;
import com.minegolem.backend.exception.ResourceNotFoundException;
import com.minegolem.backend.repository.GymRepository;
import com.minegolem.backend.repository.SubscriptionTypeRepository;
import com.minegolem.backend.security.StaffUserDetails;
import com.minegolem.backend.service.RealtimeEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscription-types")
@RequiredArgsConstructor
public class SubscriptionTypeController {

    private final SubscriptionTypeRepository subscriptionTypeRepository;
    private final GymRepository gymRepository;
    private final RealtimeEventService realtimeEventService;

    @GetMapping
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ')")
    public ResponseEntity<List<SubscriptionTypeResponse>> list(
        @AuthenticationPrincipal StaffUserDetails user,
        @RequestParam(required = false) SubscriptionTypeEnum type
    ) {
        List<SubscriptionTypeResponse> result = (type != null
            ? subscriptionTypeRepository.findByGymIdAndTypeAndActiveTrueOrderByNameAsc(user.getGymId(), type)
            : subscriptionTypeRepository.findByGymIdAndActiveTrueOrderByNameAsc(user.getGymId()))
            .stream()
            .map(SubscriptionTypeResponse::from)
            .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ')")
    public ResponseEntity<SubscriptionTypeResponse> getById(
        @AuthenticationPrincipal StaffUserDetails user,
        @PathVariable UUID id
    ) {
        return ResponseEntity.ok(SubscriptionTypeResponse.from(findOwned(id, user.getGymId())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUBSCRIPTION_WRITE')")
    public ResponseEntity<SubscriptionTypeResponse> create(
        @AuthenticationPrincipal StaffUserDetails user,
        @Valid @RequestBody SubscriptionTypeRequest request
    ) {
        SubscriptionType type = new SubscriptionType();
        type.setGym(gymRepository.findById(user.getGymId()).orElseThrow());
        apply(type, request);
        SubscriptionType saved = subscriptionTypeRepository.save(type);
        realtimeEventService.publish(user.getGymId(), "SUBSCRIPTION_TYPE", "CREATED", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(SubscriptionTypeResponse.from(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_WRITE')")
    public ResponseEntity<SubscriptionTypeResponse> update(
        @AuthenticationPrincipal StaffUserDetails user,
        @PathVariable UUID id,
        @Valid @RequestBody SubscriptionTypeRequest request
    ) {
        SubscriptionType type = findOwned(id, user.getGymId());
        apply(type, request);
        SubscriptionType saved = subscriptionTypeRepository.save(type);
        realtimeEventService.publish(user.getGymId(), "SUBSCRIPTION_TYPE", "UPDATED", saved.getId());
        return ResponseEntity.ok(SubscriptionTypeResponse.from(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_WRITE')")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal StaffUserDetails user,
        @PathVariable UUID id
    ) {
        SubscriptionType type = findOwned(id, user.getGymId());
        type.setActive(false);
        subscriptionTypeRepository.save(type);
        realtimeEventService.publish(user.getGymId(), "SUBSCRIPTION_TYPE", "DELETED", id);
        return ResponseEntity.noContent().build();
    }

    private SubscriptionType findOwned(UUID id, UUID gymId) {
        return subscriptionTypeRepository.findByIdAndGymId(id, gymId)
            .orElseThrow(() -> ResourceNotFoundException.of("SubscriptionType", id));
    }

    private void apply(SubscriptionType type, SubscriptionTypeRequest request) {
        type.setName(request.name());
        type.setType(request.type() != null ? request.type() : SubscriptionTypeEnum.ABBONAMENTO);
        type.setBasePrice(request.defaultPrice() != null ? request.defaultPrice() : BigDecimal.ZERO);
        type.setValidityDays(request.validityDays());
        type.setForcedExpiry(request.forcedExpiry());
        type.setDescription(request.description());
        type.setColor(request.color() != null && !request.color().isBlank() ? request.color() : "#6366f1");
        type.setActive(request.active() == null || request.active());
    }
}

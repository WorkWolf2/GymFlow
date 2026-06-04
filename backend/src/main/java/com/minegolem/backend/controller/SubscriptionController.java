package com.minegolem.backend.controller;


import com.minegolem.backend.domain.entity.Subscription;
import com.minegolem.backend.dto.request.SubscriptionRequest;
import com.minegolem.backend.dto.response.SubscriptionResponse;
import com.minegolem.backend.security.StaffUserDetails;
import com.minegolem.backend.service.RealtimeEventService;
import com.minegolem.backend.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final RealtimeEventService realtimeEventService;

    @PostMapping
    @PreAuthorize("hasAuthority('SUBSCRIPTION_WRITE')")
    public ResponseEntity<SubscriptionResponse> create(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @Valid @RequestBody SubscriptionRequest request
    ) {
        Subscription subscription = subscriptionService.create(request);
        realtimeEventService.publish(userDetails.getGymId(), "SUBSCRIPTION", "CREATED", subscription.getId());
        realtimeEventService.publish(userDetails.getGymId(), "DASHBOARD", "UPDATED", subscription.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(SubscriptionResponse.from(subscription));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ')")
    public ResponseEntity<List<SubscriptionResponse>> listByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(subscriptionService.listByUser(userId).stream().map(SubscriptionResponse::from).toList());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_WRITE')")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PathVariable UUID id
    ) {
        subscriptionService.delete(id);
        realtimeEventService.publish(userDetails.getGymId(), "SUBSCRIPTION", "DELETED", id);
        realtimeEventService.publish(userDetails.getGymId(), "DASHBOARD", "UPDATED", id);
        return ResponseEntity.noContent().build();
    }
}

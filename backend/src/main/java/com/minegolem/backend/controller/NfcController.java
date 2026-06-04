package com.minegolem.backend.controller;


import com.minegolem.backend.domain.entity.NfcTag;
import com.minegolem.backend.dto.response.NfcTagResponse;
import com.minegolem.backend.security.StaffUserDetails;
import com.minegolem.backend.service.NfcService;
import com.minegolem.backend.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/nfc")
@RequiredArgsConstructor
public class NfcController {

    private final NfcService nfcService;
    private final RealtimeEventService realtimeEventService;

    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<NfcTagResponse> assign(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @RequestParam String tagUid,
        @RequestParam UUID userId
    ) {
        NfcTagResponse response = NfcTagResponse.from(nfcService.assignTag(tagUid, userId));
        realtimeEventService.publish(userDetails.getGymId(), "NFC", "ASSIGNED", tagUid, java.util.Map.of("userId", userId.toString()));
        realtimeEventService.publish(userDetails.getGymId(), "USER", "NFC_UPDATED", userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{tagUid}/unassign")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<Void> unassign(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PathVariable String tagUid
    ) {
        nfcService.unassignTag(tagUid);
        realtimeEventService.publish(userDetails.getGymId(), "NFC", "UNASSIGNED", tagUid, java.util.Map.of());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{tagUid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PathVariable String tagUid
    ) {
        nfcService.deactivateTag(tagUid);
        realtimeEventService.publish(userDetails.getGymId(), "NFC", "DEACTIVATED", tagUid, java.util.Map.of());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unassigned")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<List<NfcTagResponse>> listUnassigned() {
        return ResponseEntity.ok(nfcService.listUnassigned().stream().map(NfcTagResponse::from).toList());
    }
}

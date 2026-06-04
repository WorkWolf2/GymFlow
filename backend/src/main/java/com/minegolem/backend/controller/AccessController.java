package com.minegolem.backend.controller;


import com.minegolem.backend.domain.entity.Access;
import com.minegolem.backend.dto.response.AccessResponse;
import com.minegolem.backend.service.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accesses")
@RequiredArgsConstructor
public class AccessController {

    private final AccessService accessService;

    @GetMapping
    @PreAuthorize("hasAuthority('ACCESS_READ')")
    public ResponseEntity<Page<AccessResponse>> list(
        @PageableDefault(size = 50) Pageable pageable
    ) {
        return ResponseEntity.ok(accessService.list(pageable).map(AccessResponse::from));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('ACCESS_READ')")
    public ResponseEntity<List<AccessResponse>> listByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(accessService.listByUser(userId).stream().map(AccessResponse::from).toList());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('ACCESS_READ')")
    public ResponseEntity<AccessService.RealtimeStats> stats() {
        return ResponseEntity.ok(accessService.getRealtimeStats());
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('ACCESS_READ')")
    public ResponseEntity<AccessResponse> validate(@RequestParam String accessId) {
        return ResponseEntity.ok(accessService.validateManual(accessId));
    }

    @PostMapping("/open-door")
    @PreAuthorize("hasAuthority('ACCESS_READ')")
    public ResponseEntity<java.util.Map<String, Boolean>> openDoor() {
        return ResponseEntity.ok(java.util.Map.of("sent", accessService.openDoor()));
    }
}

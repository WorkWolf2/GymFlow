package com.minegolem.backend.controller;

import com.minegolem.backend.dto.request.StaffUserRequest;
import com.minegolem.backend.dto.response.RoleResponse;
import com.minegolem.backend.dto.response.StaffUserResponse;
import com.minegolem.backend.security.StaffUserDetails;
import com.minegolem.backend.service.StaffUserService;
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
@RequestMapping("/api/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_ADMIN')")
public class StaffUserController {

    private final StaffUserService staffUserService;

    @GetMapping
    public ResponseEntity<List<StaffUserResponse>> list(@AuthenticationPrincipal StaffUserDetails userDetails) {
        return ResponseEntity.ok(staffUserService.listByGym(userDetails.getGymId()));
    }

    @GetMapping("/roles")
    public ResponseEntity<List<RoleResponse>> listRoles() {
        return ResponseEntity.ok(staffUserService.listRoles());
    }

    @PostMapping
    public ResponseEntity<StaffUserResponse> create(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @Valid @RequestBody StaffUserRequest request
    ) {
        StaffUserResponse response = staffUserService.create(userDetails.getGymId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StaffUserResponse> update(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PathVariable UUID id,
        @Valid @RequestBody StaffUserRequest request
    ) {
        return ResponseEntity.ok(staffUserService.update(id, userDetails.getGymId(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PathVariable UUID id
    ) {
        staffUserService.delete(id, userDetails.getGymId());
        return ResponseEntity.noContent().build();
    }
}

package com.minegolem.backend.controller;

import com.minegolem.backend.dto.request.VoucherRequest;
import com.minegolem.backend.dto.response.VoucherResponse;
import com.minegolem.backend.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class VoucherController {
    private final VoucherService voucherService;
    @GetMapping("/users/{userId}/vouchers") @PreAuthorize("hasAuthority('USER_READ')")
    public List<VoucherResponse> list(@PathVariable UUID userId) { return voucherService.list(userId); }
    @PostMapping("/users/{userId}/vouchers") @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<VoucherResponse> create(@PathVariable UUID userId, @RequestBody VoucherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherService.create(userId, request));
    }
    @DeleteMapping("/vouchers/{id}") @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        voucherService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

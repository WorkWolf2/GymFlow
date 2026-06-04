package com.minegolem.backend.controller;

import com.minegolem.backend.dto.request.UserRequest;
import com.minegolem.backend.dto.response.UserResponse;
import com.minegolem.backend.security.StaffUserDetails;
import com.minegolem.backend.service.ClientSheetPdfService;
import com.minegolem.backend.service.RealtimeEventService;
import com.minegolem.backend.service.UserService;
import com.lowagie.text.DocumentException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ClientSheetPdfService clientSheetPdfService;
    private final RealtimeEventService realtimeEventService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<Page<UserResponse>> list(
        @RequestParam(required = false) String search,
        @PageableDefault(size = 20, sort = "lastName") Pageable pageable
    ) {
        return ResponseEntity.ok(userService.list(search, pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<UserResponse> create(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @Valid @RequestBody UserRequest request
    ) {
        UserResponse response = userService.create(request);
        realtimeEventService.publish(userDetails.getGymId(), "USER", "CREATED", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping("/{id}/sheet/pdf")
    @PreAuthorize("hasAuthority('USER_READ')")
    public void downloadClientSheetPdf(@PathVariable UUID id, HttpServletResponse response)
        throws IOException, DocumentException {
        clientSheetPdfService.generatePdf(id, response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<UserResponse> update(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PathVariable UUID id,
        @Valid @RequestBody UserRequest request
    ) {
        UserResponse response = userService.update(id, request);
        realtimeEventService.publish(userDetails.getGymId(), "USER", "UPDATED", id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PathVariable UUID id
    ) {
        userService.delete(id);
        realtimeEventService.publish(userDetails.getGymId(), "USER", "DELETED", id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/avatar")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<UserResponse> uploadAvatar(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PathVariable UUID id,
        @RequestParam("file") MultipartFile file
    ) {
        UserResponse response = userService.uploadAvatar(id, file);
        realtimeEventService.publish(userDetails.getGymId(), "USER", "AVATAR_UPDATED", id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/signature")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<UserResponse> saveSignature(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PathVariable UUID id,
        @RequestParam("file") MultipartFile file
    ) {
        UserResponse response = userService.saveSignature(id, file);
        realtimeEventService.publish(userDetails.getGymId(), "USER", "SIGNATURE_UPDATED", id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/documents")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<UserResponse> uploadDocuments(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PathVariable UUID id,
        @RequestParam(value = "docFront", required = false) MultipartFile docFront,
        @RequestParam(value = "docBack", required = false) MultipartFile docBack
    ) {
        UserResponse response = userService.uploadDocuments(id, docFront, docBack);
        realtimeEventService.publish(userDetails.getGymId(), "USER", "DOCUMENTS_UPDATED", id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/certificate")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<UserResponse> saveCertificate(
        @AuthenticationPrincipal StaffUserDetails userDetails,
        @PathVariable UUID id,
        @RequestParam(value = "file", required = false) MultipartFile file,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
        @RequestParam(required = false) String notes
    ) {
        UserResponse response = userService.saveCertificate(id, file, issuedDate, expiryDate, notes);
        realtimeEventService.publish(userDetails.getGymId(), "USER", "CERTIFICATE_UPDATED", id);
        realtimeEventService.publish(userDetails.getGymId(), "DASHBOARD", "UPDATED", id);
        return ResponseEntity.ok(response);
    }
}

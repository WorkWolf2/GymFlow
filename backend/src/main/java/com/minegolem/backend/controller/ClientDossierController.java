package com.minegolem.backend.controller;

import com.lowagie.text.DocumentException;
import com.minegolem.backend.dto.request.*;
import com.minegolem.backend.dto.response.*;
import com.minegolem.backend.service.ClientDossierPdfService;
import com.minegolem.backend.service.ClientDossierService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/{userId}/dossier")
@RequiredArgsConstructor
public class ClientDossierController {

    private final ClientDossierService service;
    private final ClientDossierPdfService pdfService;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ClientDossierResponse dossier(@PathVariable UUID userId) {
        return service.dossier(userId);
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('USER_READ')")
    public DossierProfileResponse profile(@PathVariable UUID userId) {
        return service.profile(userId);
    }

    @PostMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<DossierProfileResponse> saveProfile(
        @PathVariable UUID userId,
        @ModelAttribute DossierProfileRequest request,
        @RequestParam(value = "biaFile", required = false) MultipartFile biaFile
    ) {
        return ResponseEntity.ok(service.saveProfile(userId, request, biaFile));
    }

    @GetMapping("/progress")
    @PreAuthorize("hasAuthority('USER_READ')")
    public List<DossierProgressResponse> progress(@PathVariable UUID userId) {
        return service.progress(userId);
    }

    @PostMapping(value = "/progress", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<DossierProgressResponse> addProgress(
        @PathVariable UUID userId,
        @ModelAttribute @Valid DossierProgressRequest request,
        @RequestParam(value = "biaFile", required = false) MultipartFile biaFile
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addProgress(userId, request, biaFile));
    }

    @PutMapping(value = "/progress/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<DossierProgressResponse> updateProgress(
        @PathVariable UUID userId,
        @PathVariable UUID id,
        @ModelAttribute @Valid DossierProgressRequest request,
        @RequestParam(value = "biaFile", required = false) MultipartFile biaFile
    ) {
        return ResponseEntity.ok(service.updateProgress(id, request, biaFile));
    }

    @DeleteMapping("/progress/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<Void> deleteProgress(@PathVariable UUID userId, @PathVariable UUID id) {
        service.deleteProgress(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/programs")
    @PreAuthorize("hasAuthority('USER_READ')")
    public List<DossierProgramResponse> programs(@PathVariable UUID userId) {
        return service.programs(userId);
    }

    @PostMapping(value = "/programs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<DossierProgramResponse> addProgram(
        @PathVariable UUID userId,
        @ModelAttribute DossierProgramRequest request,
        @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addProgram(userId, request, file));
    }

    @DeleteMapping("/programs/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<Void> deleteProgram(@PathVariable UUID userId, @PathVariable UUID id) {
        service.deleteProgram(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notes")
    @PreAuthorize("hasAuthority('USER_READ')")
    public List<DossierNoteResponse> notes(@PathVariable UUID userId) {
        return service.notes(userId);
    }

    @PostMapping("/notes")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<DossierNoteResponse> addNote(@PathVariable UUID userId, @Valid @RequestBody DossierNoteRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addNote(userId, r));
    }

    @DeleteMapping("/notes/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<Void> deleteNote(@PathVariable UUID userId, @PathVariable UUID id) {
        service.deleteNote(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents")
    @PreAuthorize("hasAuthority('USER_READ')")
    public List<DossierDocumentResponse> documents(@PathVariable UUID userId) {
        return service.documents(userId);
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<DossierDocumentResponse> addDocument(
        @PathVariable UUID userId,
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String description,
        @RequestParam MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addDocument(userId, name, description, file));
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID userId, @PathVariable UUID id) {
        service.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fields")
    @PreAuthorize("hasAuthority('USER_READ')")
    public List<DossierFieldResponse> fields(@PathVariable UUID userId) {
        return service.fields(userId);
    }

    @PostMapping("/fields")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<DossierFieldResponse> addField(@PathVariable UUID userId, @Valid @RequestBody DossierFieldRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addField(userId, r));
    }

    @DeleteMapping("/fields/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public ResponseEntity<Void> deleteField(@PathVariable UUID userId, @PathVariable UUID id) {
        service.deleteField(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pdf")
    @PreAuthorize("hasAuthority('USER_READ')")
    public void pdf(
        @PathVariable UUID userId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(required = false) String month, // format: YYYY-MM
        @RequestParam(defaultValue = "true") boolean includeNotes,
        @RequestParam(defaultValue = "true") boolean includeProgress,
        @RequestParam(defaultValue = "true") boolean includePrograms,
        @RequestParam(defaultValue = "true") boolean includeAssessment,
        HttpServletResponse response
    ) throws IOException, DocumentException {
        pdfService.generate(userId, fromDate, toDate, month, includeAssessment, includeProgress, includePrograms, includeNotes, response);
    }
}

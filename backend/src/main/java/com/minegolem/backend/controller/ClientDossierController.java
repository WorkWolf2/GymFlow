package com.minegolem.backend.controller;

import com.lowagie.text.DocumentException;
import com.minegolem.backend.dto.request.*;
import com.minegolem.backend.dto.response.*;
import com.minegolem.backend.service.ClientDossierPdfService;
import com.minegolem.backend.service.ClientDossierService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException; import java.util.*;

@RestController @RequestMapping("/api/users/{userId}/dossier") @RequiredArgsConstructor
public class ClientDossierController {
 private final ClientDossierService service; private final ClientDossierPdfService pdfService;
 @GetMapping @PreAuthorize("hasAuthority('USER_READ')") public ClientDossierResponse dossier(@PathVariable UUID userId){return service.dossier(userId);}
 @GetMapping("/notes") @PreAuthorize("hasAuthority('USER_READ')") public List<DossierNoteResponse> notes(@PathVariable UUID userId){return service.notes(userId);}
 @PostMapping("/notes") @PreAuthorize("hasAuthority('USER_WRITE')") public ResponseEntity<DossierNoteResponse> addNote(@PathVariable UUID userId,@Valid @RequestBody DossierNoteRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(service.addNote(userId,r));}
 @PutMapping("/notes/{id}") @PreAuthorize("hasAuthority('USER_WRITE')") public DossierNoteResponse updateNote(@PathVariable UUID userId,@PathVariable UUID id,@Valid @RequestBody DossierNoteRequest r){return service.updateNote(id,r);}
 @DeleteMapping("/notes/{id}") @PreAuthorize("hasAuthority('USER_WRITE')") public ResponseEntity<Void> deleteNote(@PathVariable UUID userId,@PathVariable UUID id){service.deleteNote(id);return ResponseEntity.noContent().build();}
 @GetMapping("/progress") @PreAuthorize("hasAuthority('USER_READ')") public List<DossierProgressResponse> progress(@PathVariable UUID userId){return service.progress(userId);}
 @PostMapping("/progress") @PreAuthorize("hasAuthority('USER_WRITE')") public ResponseEntity<DossierProgressResponse> addProgress(@PathVariable UUID userId,@Valid @RequestBody DossierProgressRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(service.addProgress(userId,r));}
 @PutMapping("/progress/{id}") @PreAuthorize("hasAuthority('USER_WRITE')") public DossierProgressResponse updateProgress(@PathVariable UUID userId,@PathVariable UUID id,@Valid @RequestBody DossierProgressRequest r){return service.updateProgress(id,r);}
 @DeleteMapping("/progress/{id}") @PreAuthorize("hasAuthority('USER_WRITE')") public ResponseEntity<Void> deleteProgress(@PathVariable UUID userId,@PathVariable UUID id){service.deleteProgress(id);return ResponseEntity.noContent().build();}
 @GetMapping("/documents") @PreAuthorize("hasAuthority('USER_READ')") public List<DossierDocumentResponse> documents(@PathVariable UUID userId){return service.documents(userId);}
 @PostMapping(value="/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAuthority('USER_WRITE')") public ResponseEntity<DossierDocumentResponse> addDocument(@PathVariable UUID userId,@RequestParam(required=false) String name,@RequestParam(required=false) String description,@RequestParam MultipartFile file){return ResponseEntity.status(HttpStatus.CREATED).body(service.addDocument(userId,name,description,file));}
 @DeleteMapping("/documents/{id}") @PreAuthorize("hasAuthority('USER_WRITE')") public ResponseEntity<Void> deleteDocument(@PathVariable UUID userId,@PathVariable UUID id){service.deleteDocument(id);return ResponseEntity.noContent().build();}
 @GetMapping("/fields") @PreAuthorize("hasAuthority('USER_READ')") public List<DossierFieldResponse> fields(@PathVariable UUID userId){return service.fields(userId);}
 @PostMapping("/fields") @PreAuthorize("hasAuthority('USER_WRITE')") public ResponseEntity<DossierFieldResponse> addField(@PathVariable UUID userId,@Valid @RequestBody DossierFieldRequest r){return ResponseEntity.status(HttpStatus.CREATED).body(service.addField(userId,r));}
 @PutMapping("/fields/{id}") @PreAuthorize("hasAuthority('USER_WRITE')") public DossierFieldResponse updateField(@PathVariable UUID userId,@PathVariable UUID id,@Valid @RequestBody DossierFieldRequest r){return service.updateField(id,r);}
 @DeleteMapping("/fields/{id}") @PreAuthorize("hasAuthority('USER_WRITE')") public ResponseEntity<Void> deleteField(@PathVariable UUID userId,@PathVariable UUID id){service.deleteField(id);return ResponseEntity.noContent().build();}
 @GetMapping("/pdf") @PreAuthorize("hasAuthority('USER_READ')") public void pdf(@PathVariable UUID userId,@RequestParam(defaultValue="true") boolean includeNotes,@RequestParam(defaultValue="true") boolean includeProgress,@RequestParam(defaultValue="true") boolean includeFields,HttpServletResponse response) throws IOException,DocumentException {pdfService.generate(userId,includeNotes,includeProgress,includeFields,response);}
}

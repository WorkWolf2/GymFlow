package com.minegolem.backend.dto.response;
import java.util.List; import java.util.UUID;
public record ClientDossierResponse(UUID userId, List<DossierNoteResponse> notes, List<DossierProgressResponse> progress, List<DossierDocumentResponse> documents, List<DossierFieldResponse> fields) {}

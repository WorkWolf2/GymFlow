package com.minegolem.backend.dto.response;

import java.util.List;
import java.util.UUID;

public record ClientDossierResponse(
    UUID userId,
    DossierProfileResponse profile,
    List<DossierProgressResponse> progress,
    List<DossierProgramResponse> programs,
    List<DossierNoteResponse> notes,
    List<DossierDocumentResponse> documents,
    List<DossierFieldResponse> fields
) {}

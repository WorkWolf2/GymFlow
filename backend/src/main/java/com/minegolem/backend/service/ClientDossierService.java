package com.minegolem.backend.service;

import com.minegolem.backend.domain.entity.*;
import com.minegolem.backend.dto.request.*;
import com.minegolem.backend.dto.response.*;
import com.minegolem.backend.exception.ResourceNotFoundException;
import com.minegolem.backend.repository.*;
import com.minegolem.backend.security.StaffUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ClientDossierService {

    private final UserRepository userRepository;
    private final ClientDossierProfileRepository profileRepository;
    private final ClientDossierProgressRepository progressRepository;
    private final ClientDossierProgramRepository programRepository;
    private final ClientDossierNoteRepository noteRepository;
    private final ClientDossierDocumentRepository documentRepository;
    private final ClientDossierFieldRepository fieldRepository;
    private final FileStorageService fileStorageService;
    private final StaffUserRepository staffUserRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public ClientDossierResponse dossier(UUID userId) {
        User u = user(userId);
        return new ClientDossierResponse(
            userId,
            profile(userId),
            progress(userId),
            programs(userId),
            notes(userId),
            documents(userId),
            fields(userId)
        );
    }

    @Transactional(readOnly = true)
    public DossierProfileResponse profile(UUID userId) {
        User u = user(userId);
        LocalDate firstRegDate = u.getCreatedAt() != null ? u.getCreatedAt().toLocalDate() : LocalDate.now();
        ClientDossierProfile p = profileRepository.findByUserId(userId).orElse(null);
        if (p == null) {
            return new DossierProfileResponse(
                null, firstRegDate, null, null, null, null, null, null, null, null, null, null
            );
        }
        return toProfileResponse(p, firstRegDate);
    }

    @Transactional
    public DossierProfileResponse saveProfile(UUID userId, DossierProfileRequest r, MultipartFile biaFile) {
        User u = user(userId);
        ClientDossierProfile p = profileRepository.findByUserId(userId)
            .orElse(ClientDossierProfile.builder().user(u).build());

        p.setMainGoal(clean(r.mainGoal()));
        p.setSecondaryGoals(clean(r.secondaryGoals()));
        p.setExperienceLevel(r.experienceLevel());
        p.setInitialWeight(r.initialWeight());
        p.setInitialHeight(r.initialHeight());
        p.setInitialMeasurements(clean(r.initialMeasurements()));
        p.setInitialAssessment(clean(r.initialAssessment()));
        p.setInitialLimitations(clean(r.initialLimitations()));

        if (biaFile != null && !biaFile.isEmpty()) {
            String path = fileStorageService.store(biaFile, "dossiers/" + userId + "/bia_initial");
            p.setInitialBiaFilePath(path);
        }

        ClientDossierProfile saved = profileRepository.save(p);
        audit("DOSSIER_PROFILE_SAVED", userId);
        LocalDate firstRegDate = u.getCreatedAt() != null ? u.getCreatedAt().toLocalDate() : LocalDate.now();
        return toProfileResponse(saved, firstRegDate);
    }

    @Transactional(readOnly = true)
    public List<DossierProgressResponse> progress(UUID userId) {
        user(userId);
        return progressRepository.findByUserIdAndDeletedAtIsNullOrderByRecordedDateDescCreatedAtDesc(userId)
            .stream().map(this::progressDto).toList();
    }

    @Transactional
    public DossierProgressResponse addProgress(UUID userId, DossierProgressRequest r, MultipartFile biaFile) {
        User u = user(userId);
        String biaPath = null;
        if (biaFile != null && !biaFile.isEmpty()) {
            biaPath = fileStorageService.store(biaFile, "dossiers/" + userId + "/bia");
        }

        ClientDossierProgress p = ClientDossierProgress.builder()
            .user(u)
            .recordedDate(r.recordedDate())
            .weight(r.weight())
            .height(r.height())
            .bodyFatPercentage(r.bodyFatPercentage())
            .muscleMass(r.muscleMass())
            .measurements(clean(r.measurements()))
            .observations(clean(r.observations()))
            .customParameters(clean(r.customParameters()))
            .biaFilePath(biaPath)
            .progressNotes(clean(r.progressNotes()))
            .performanceNotes(clean(r.performanceNotes()))
            .coachNotes(clean(r.coachNotes()))
            .criticalIssues(clean(r.criticalIssues()))
            .changesMade(clean(r.changesMade()))
            .nextCheckDate(r.nextCheckDate())
            .build();

        ClientDossierProgress saved = progressRepository.save(p);
        audit("DOSSIER_PROGRESS_CREATED", userId);
        return progressDto(saved);
    }

    @Transactional
    public DossierProgressResponse updateProgress(UUID id, DossierProgressRequest r, MultipartFile biaFile) {
        ClientDossierProgress p = progressRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id, gymId())
            .orElseThrow(() -> new ResourceNotFoundException("Rilevazione non trovata"));

        p.setRecordedDate(r.recordedDate());
        p.setWeight(r.weight());
        p.setHeight(r.height());
        p.setBodyFatPercentage(r.bodyFatPercentage());
        p.setMuscleMass(r.muscleMass());
        p.setMeasurements(clean(r.measurements()));
        p.setObservations(clean(r.observations()));
        p.setCustomParameters(clean(r.customParameters()));
        p.setProgressNotes(clean(r.progressNotes()));
        p.setPerformanceNotes(clean(r.performanceNotes()));
        p.setCoachNotes(clean(r.coachNotes()));
        p.setCriticalIssues(clean(r.criticalIssues()));
        p.setChangesMade(clean(r.changesMade()));
        p.setNextCheckDate(r.nextCheckDate());

        if (biaFile != null && !biaFile.isEmpty()) {
            String path = fileStorageService.store(biaFile, "dossiers/" + p.getUser().getId() + "/bia");
            p.setBiaFilePath(path);
        }

        ClientDossierProgress saved = progressRepository.save(p);
        audit("DOSSIER_PROGRESS_UPDATED", p.getUser().getId());
        return progressDto(saved);
    }

    @Transactional
    public void deleteProgress(UUID id) {
        ClientDossierProgress p = progressRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id, gymId())
            .orElseThrow(() -> new ResourceNotFoundException("Rilevazione non trovata"));
        p.setDeletedAt(LocalDateTime.now());
        progressRepository.save(p);
        audit("DOSSIER_PROGRESS_DELETED", p.getUser().getId());
    }

    @Transactional(readOnly = true)
    public List<DossierProgramResponse> programs(UUID userId) {
        user(userId);
        return programRepository.findByUserIdAndDeletedAtIsNullOrderByStartDateDescCreatedAtDesc(userId)
            .stream().map(this::programDto).toList();
    }

    @Transactional
    public DossierProgramResponse addProgram(UUID userId, DossierProgramRequest r, MultipartFile file) {
        User u = user(userId);
        String programPath = null;
        if (file != null && !file.isEmpty()) {
            programPath = fileStorageService.store(file, "dossiers/" + userId + "/programs");
        }

        ClientDossierProgram p = ClientDossierProgram.builder()
            .user(u)
            .programFilePath(programPath)
            .startDate(r.startDate())
            .reviewDate(r.reviewDate())
            .changesMade(clean(r.changesMade()))
            .coachName(clean(r.coachName()))
            .build();

        ClientDossierProgram saved = programRepository.save(p);
        audit("DOSSIER_PROGRAM_CREATED", userId);
        return programDto(saved);
    }

    @Transactional
    public void deleteProgram(UUID id) {
        ClientDossierProgram p = programRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id, gymId())
            .orElseThrow(() -> new ResourceNotFoundException("Scheda non trovata"));
        p.setDeletedAt(LocalDateTime.now());
        programRepository.save(p);
        audit("DOSSIER_PROGRAM_DELETED", p.getUser().getId());
    }

    @Transactional(readOnly = true)
    public List<DossierNoteResponse> notes(UUID userId) {
        user(userId);
        return noteRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
            .stream().map(this::note).toList();
    }

    @Transactional
    public DossierNoteResponse addNote(UUID userId, DossierNoteRequest r) {
        ClientDossierNote n = noteRepository.save(ClientDossierNote.builder()
            .user(user(userId))
            .author(currentStaff())
            .content(r.content().trim())
            .build());
        audit("DOSSIER_NOTE_CREATED", userId);
        return note(n);
    }

    @Transactional
    public DossierNoteResponse updateNote(UUID id, DossierNoteRequest r) {
        ClientDossierNote n = noteRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id, gymId())
            .orElseThrow(() -> new ResourceNotFoundException("Nota dossier non trovata"));
        n.setContent(r.content().trim());
        audit("DOSSIER_NOTE_UPDATED", n.getUser().getId());
        return note(n);
    }

    @Transactional
    public void deleteNote(UUID id) {
        ClientDossierNote n = noteRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id, gymId())
            .orElseThrow(() -> new ResourceNotFoundException("Nota dossier non trovata"));
        n.setDeletedAt(LocalDateTime.now());
        noteRepository.save(n);
        audit("DOSSIER_NOTE_DELETED", n.getUser().getId());
    }

    @Transactional(readOnly = true)
    public List<DossierDocumentResponse> documents(UUID userId) {
        user(userId);
        return documentRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
            .stream().map(this::document).toList();
    }

    @Transactional
    public DossierDocumentResponse addDocument(UUID userId, String name, String description, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Seleziona un documento da caricare");
        String resolvedName = (name == null || name.isBlank()) ? Objects.requireNonNullElse(file.getOriginalFilename(), "Documento") : name.trim();
        ClientDossierDocument d = documentRepository.save(ClientDossierDocument.builder()
            .user(user(userId))
            .name(resolvedName)
            .description(clean(description))
            .filePath(fileStorageService.store(file, "dossiers/" + userId))
            .build());
        audit("DOSSIER_DOCUMENT_CREATED", userId);
        return document(d);
    }

    @Transactional
    public void deleteDocument(UUID id) {
        ClientDossierDocument d = documentRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id, gymId())
            .orElseThrow(() -> new ResourceNotFoundException("Documento dossier non trovato"));
        d.setDeletedAt(LocalDateTime.now());
        documentRepository.save(d);
        fileStorageService.delete(d.getFilePath());
        audit("DOSSIER_DOCUMENT_DELETED", d.getUser().getId());
    }

    @Transactional(readOnly = true)
    public List<DossierFieldResponse> fields(UUID userId) {
        user(userId);
        return fieldRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId)
            .stream().map(this::field).toList();
    }

    @Transactional
    public DossierFieldResponse addField(UUID userId, DossierFieldRequest r) {
        ClientDossierField f = fieldRepository.save(ClientDossierField.builder()
            .user(user(userId))
            .fieldName(r.fieldName().trim())
            .fieldValue(clean(r.fieldValue()))
            .valueType(r.valueType())
            .build());
        audit("DOSSIER_FIELD_CREATED", userId);
        return field(f);
    }

    @Transactional
    public DossierFieldResponse updateField(UUID id, DossierFieldRequest r) {
        ClientDossierField f = fieldRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id, gymId())
            .orElseThrow(() -> new ResourceNotFoundException("Campo dossier non trovato"));
        f.setFieldName(r.fieldName().trim());
        f.setFieldValue(clean(r.fieldValue()));
        f.setValueType(r.valueType());
        audit("DOSSIER_FIELD_UPDATED", f.getUser().getId());
        return field(f);
    }

    @Transactional
    public void deleteField(UUID id) {
        ClientDossierField f = fieldRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id, gymId())
            .orElseThrow(() -> new ResourceNotFoundException("Campo dossier non trovato"));
        f.setDeletedAt(LocalDateTime.now());
        fieldRepository.save(f);
        audit("DOSSIER_FIELD_DELETED", f.getUser().getId());
    }

    private User user(UUID id) {
        return userRepository.findByIdAndGymIdAndDeletedAtIsNull(id, gymId())
            .orElseThrow(() -> new ResourceNotFoundException("Cliente non trovato"));
    }

    private UUID gymId() {
        return ((StaffUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getGymId();
    }

    private StaffUser currentStaff() {
        return staffUserRepository.findById(((StaffUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserId()).orElse(null);
    }

    private void audit(String a, UUID id) {
        auditService.log(a, "User", id.toString());
    }

    private String clean(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private DossierProfileResponse toProfileResponse(ClientDossierProfile p, LocalDate firstRegDate) {
        return new DossierProfileResponse(
            p.getId(),
            firstRegDate,
            p.getMainGoal(),
            p.getSecondaryGoals(),
            p.getExperienceLevel(),
            p.getInitialWeight(),
            p.getInitialHeight(),
            fileUrl(p.getInitialBiaFilePath()),
            p.getInitialMeasurements(),
            p.getInitialAssessment(),
            p.getInitialLimitations(),
            p.getUpdatedAt()
        );
    }

    private DossierProgressResponse progressDto(ClientDossierProgress p) {
        return new DossierProgressResponse(
            p.getId(),
            p.getRecordedDate(),
            p.getWeight(),
            p.getHeight(),
            p.getBodyFatPercentage(),
            p.getMuscleMass(),
            p.getMeasurements(),
            p.getObservations(),
            p.getCustomParameters(),
            fileUrl(p.getBiaFilePath()),
            p.getProgressNotes(),
            p.getPerformanceNotes(),
            p.getCoachNotes(),
            p.getCriticalIssues(),
            p.getChangesMade(),
            p.getNextCheckDate(),
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }

    private DossierProgramResponse programDto(ClientDossierProgram p) {
        return new DossierProgramResponse(
            p.getId(),
            fileUrl(p.getProgramFilePath()),
            p.getStartDate(),
            p.getReviewDate(),
            p.getChangesMade(),
            p.getCoachName(),
            p.getCreatedAt(),
            p.getUpdatedAt()
        );
    }

    private DossierNoteResponse note(ClientDossierNote n) {
        return new DossierNoteResponse(n.getId(), n.getContent(), n.getAuthor() == null ? null : n.getAuthor().getFullName(), n.getCreatedAt(), n.getUpdatedAt());
    }

    private DossierDocumentResponse document(ClientDossierDocument d) {
        return new DossierDocumentResponse(d.getId(), d.getName(), fileUrl(d.getFilePath()), d.getDescription(), d.getCreatedAt());
    }

    private DossierFieldResponse field(ClientDossierField f) {
        return new DossierFieldResponse(f.getId(), f.getFieldName(), f.getFieldValue(), f.getValueType(), f.getCreatedAt(), f.getUpdatedAt());
    }

    private String fileUrl(String path) {
        if (path == null || path.isBlank()) return null;
        String url = fileStorageService.getPresignedUrl(path);
        return url != null ? url : path;
    }
}

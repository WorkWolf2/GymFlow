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
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class ClientDossierService {
 private final UserRepository userRepository; private final ClientDossierNoteRepository noteRepository;
 private final ClientDossierProgressRepository progressRepository; private final ClientDossierDocumentRepository documentRepository;
 private final ClientDossierFieldRepository fieldRepository; private final FileStorageService fileStorageService;
 private final StaffUserRepository staffUserRepository; private final AuditService auditService;
 @Transactional(readOnly=true) public ClientDossierResponse dossier(UUID userId) { user(userId); return new ClientDossierResponse(userId, notes(userId), progress(userId), documents(userId), fields(userId)); }
 @Transactional(readOnly=true) public List<DossierNoteResponse> notes(UUID userId) { user(userId); return noteRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream().map(this::note).toList(); }
 @Transactional public DossierNoteResponse addNote(UUID userId,DossierNoteRequest r) { ClientDossierNote n=noteRepository.save(ClientDossierNote.builder().user(user(userId)).author(currentStaff()).content(r.content().trim()).build()); audit("DOSSIER_NOTE_CREATED",userId); return note(n); }
 @Transactional public DossierNoteResponse updateNote(UUID id,DossierNoteRequest r) { ClientDossierNote n=noteRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id,gymId()).orElseThrow(()->new ResourceNotFoundException("Nota dossier non trovata")); n.setContent(r.content().trim()); audit("DOSSIER_NOTE_UPDATED",n.getUser().getId()); return note(n); }
 @Transactional public void deleteNote(UUID id) { ClientDossierNote n=noteRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id,gymId()).orElseThrow(()->new ResourceNotFoundException("Nota dossier non trovata")); n.setDeletedAt(LocalDateTime.now()); audit("DOSSIER_NOTE_DELETED",n.getUser().getId()); }
 @Transactional(readOnly=true) public List<DossierProgressResponse> progress(UUID userId) { user(userId); return progressRepository.findByUserIdAndDeletedAtIsNullOrderByRecordedDateDescCreatedAtDesc(userId).stream().map(this::progress).toList(); }
 @Transactional public DossierProgressResponse addProgress(UUID userId,DossierProgressRequest r) { ClientDossierProgress p=progressRepository.save(progressEntity(user(userId),r)); audit("DOSSIER_PROGRESS_CREATED",userId); return progress(p); }
 @Transactional public DossierProgressResponse updateProgress(UUID id,DossierProgressRequest r) { ClientDossierProgress p=progressRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id,gymId()).orElseThrow(()->new ResourceNotFoundException("Rilevazione non trovata")); copy(p,r); audit("DOSSIER_PROGRESS_UPDATED",p.getUser().getId()); return progress(p); }
 @Transactional public void deleteProgress(UUID id) { ClientDossierProgress p=progressRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id,gymId()).orElseThrow(()->new ResourceNotFoundException("Rilevazione non trovata")); p.setDeletedAt(LocalDateTime.now()); audit("DOSSIER_PROGRESS_DELETED",p.getUser().getId()); }
 @Transactional(readOnly=true) public List<DossierDocumentResponse> documents(UUID userId) { user(userId); return documentRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream().map(this::document).toList(); }
 @Transactional public DossierDocumentResponse addDocument(UUID userId,String name,String description,MultipartFile file) { if(file==null||file.isEmpty()) throw new IllegalArgumentException("Seleziona un documento da caricare"); String resolvedName=(name==null||name.isBlank())?Objects.requireNonNullElse(file.getOriginalFilename(),"Documento"):name.trim(); ClientDossierDocument d=documentRepository.save(ClientDossierDocument.builder().user(user(userId)).name(resolvedName).description(clean(description)).filePath(fileStorageService.store(file,"dossiers/"+userId)).build()); audit("DOSSIER_DOCUMENT_CREATED",userId); return document(d); }
 @Transactional public void deleteDocument(UUID id) { ClientDossierDocument d=documentRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id,gymId()).orElseThrow(()->new ResourceNotFoundException("Documento dossier non trovato")); d.setDeletedAt(LocalDateTime.now()); fileStorageService.delete(d.getFilePath()); audit("DOSSIER_DOCUMENT_DELETED",d.getUser().getId()); }
 @Transactional(readOnly=true) public List<DossierFieldResponse> fields(UUID userId) { user(userId); return fieldRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId).stream().map(this::field).toList(); }
 @Transactional public DossierFieldResponse addField(UUID userId,DossierFieldRequest r) { ClientDossierField f=fieldRepository.save(ClientDossierField.builder().user(user(userId)).fieldName(r.fieldName().trim()).fieldValue(clean(r.fieldValue())).valueType(r.valueType()).build()); audit("DOSSIER_FIELD_CREATED",userId); return field(f); }
 @Transactional public DossierFieldResponse updateField(UUID id,DossierFieldRequest r) { ClientDossierField f=fieldRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id,gymId()).orElseThrow(()->new ResourceNotFoundException("Campo dossier non trovato")); f.setFieldName(r.fieldName().trim());f.setFieldValue(clean(r.fieldValue()));f.setValueType(r.valueType());audit("DOSSIER_FIELD_UPDATED",f.getUser().getId());return field(f); }
 @Transactional public void deleteField(UUID id) { ClientDossierField f=fieldRepository.findByIdAndUserGymIdAndDeletedAtIsNull(id,gymId()).orElseThrow(()->new ResourceNotFoundException("Campo dossier non trovato"));f.setDeletedAt(LocalDateTime.now());audit("DOSSIER_FIELD_DELETED",f.getUser().getId()); }
 private User user(UUID id){return userRepository.findByIdAndGymIdAndDeletedAtIsNull(id,gymId()).orElseThrow(()->new ResourceNotFoundException("Cliente non trovato"));}
 private UUID gymId(){return ((StaffUserDetails)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getGymId();}
 private StaffUser currentStaff(){return staffUserRepository.findById(((StaffUserDetails)SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserId()).orElse(null);}
 private void audit(String a,UUID id){auditService.log(a,"User",id.toString());}
 private ClientDossierProgress progressEntity(User u,DossierProgressRequest r){ClientDossierProgress p=ClientDossierProgress.builder().user(u).build();copy(p,r);return p;}
 private void copy(ClientDossierProgress p,DossierProgressRequest r){p.setRecordedDate(r.recordedDate());p.setWeight(r.weight());p.setHeight(r.height());p.setBodyFatPercentage(r.bodyFatPercentage());p.setMuscleMass(r.muscleMass());p.setMeasurements(clean(r.measurements()));p.setObservations(clean(r.observations()));p.setCustomParameters(clean(r.customParameters()));}
 private String clean(String s){return s==null||s.isBlank()?null:s.trim();}
 private DossierNoteResponse note(ClientDossierNote n){return new DossierNoteResponse(n.getId(),n.getContent(),n.getAuthor()==null?null:n.getAuthor().getFullName(),n.getCreatedAt(),n.getUpdatedAt());}
 private DossierProgressResponse progress(ClientDossierProgress p){return new DossierProgressResponse(p.getId(),p.getRecordedDate(),p.getWeight(),p.getHeight(),p.getBodyFatPercentage(),p.getMuscleMass(),p.getMeasurements(),p.getObservations(),p.getCustomParameters(),p.getCreatedAt(),p.getUpdatedAt());}
 private DossierDocumentResponse document(ClientDossierDocument d){return new DossierDocumentResponse(d.getId(),d.getName(),fileStorageService.getPresignedUrl(d.getFilePath()),d.getDescription(),d.getCreatedAt());}
 private DossierFieldResponse field(ClientDossierField f){return new DossierFieldResponse(f.getId(),f.getFieldName(),f.getFieldValue(),f.getValueType(),f.getCreatedAt(),f.getUpdatedAt());}
}

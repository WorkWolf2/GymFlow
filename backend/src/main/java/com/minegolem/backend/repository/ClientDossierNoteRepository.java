package com.minegolem.backend.repository;
import com.minegolem.backend.domain.entity.ClientDossierNote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ClientDossierNoteRepository extends JpaRepository<ClientDossierNote, UUID> {
 List<ClientDossierNote> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
 Optional<ClientDossierNote> findByIdAndUserGymIdAndDeletedAtIsNull(UUID id, UUID gymId);
}

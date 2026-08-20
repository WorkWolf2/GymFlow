package com.minegolem.backend.repository;
import com.minegolem.backend.domain.entity.ClientDossierDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ClientDossierDocumentRepository extends JpaRepository<ClientDossierDocument, UUID> {
 List<ClientDossierDocument> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
 Optional<ClientDossierDocument> findByIdAndUserGymIdAndDeletedAtIsNull(UUID id, UUID gymId);
}

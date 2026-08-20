package com.minegolem.backend.repository;
import com.minegolem.backend.domain.entity.ClientDossierField;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ClientDossierFieldRepository extends JpaRepository<ClientDossierField, UUID> {
 List<ClientDossierField> findByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID userId);
 Optional<ClientDossierField> findByIdAndUserGymIdAndDeletedAtIsNull(UUID id, UUID gymId);
}

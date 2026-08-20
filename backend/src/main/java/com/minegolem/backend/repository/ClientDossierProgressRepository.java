package com.minegolem.backend.repository;
import com.minegolem.backend.domain.entity.ClientDossierProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ClientDossierProgressRepository extends JpaRepository<ClientDossierProgress, UUID> {
 List<ClientDossierProgress> findByUserIdAndDeletedAtIsNullOrderByRecordedDateDescCreatedAtDesc(UUID userId);
 Optional<ClientDossierProgress> findByIdAndUserGymIdAndDeletedAtIsNull(UUID id, UUID gymId);
}

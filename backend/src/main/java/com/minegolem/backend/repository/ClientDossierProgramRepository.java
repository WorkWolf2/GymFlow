package com.minegolem.backend.repository;

import com.minegolem.backend.domain.entity.ClientDossierProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientDossierProgramRepository extends JpaRepository<ClientDossierProgram, UUID> {
    List<ClientDossierProgram> findByUserIdAndDeletedAtIsNullOrderByStartDateDescCreatedAtDesc(UUID userId);

    @Query("SELECT p FROM ClientDossierProgram p WHERE p.id = :id AND p.user.gym.id = :gymId AND p.deletedAt IS NULL")
    Optional<ClientDossierProgram> findByIdAndUserGymIdAndDeletedAtIsNull(@Param("id") UUID id, @Param("gymId") UUID gymId);
}

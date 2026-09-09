package com.minegolem.backend.repository;

import com.minegolem.backend.domain.entity.ClientDossierProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientDossierProfileRepository extends JpaRepository<ClientDossierProfile, UUID> {
    Optional<ClientDossierProfile> findByUserId(UUID userId);

    @Query("SELECT p FROM ClientDossierProfile p WHERE p.user.id = :userId AND p.user.gym.id = :gymId")
    Optional<ClientDossierProfile> findByUserIdAndGymId(@Param("userId") UUID userId, @Param("gymId") UUID gymId);
}

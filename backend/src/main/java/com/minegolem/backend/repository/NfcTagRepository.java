package com.minegolem.backend.repository;


import com.minegolem.backend.domain.entity.NfcTag;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NfcTagRepository extends JpaRepository<NfcTag, UUID> {
    Optional<NfcTag> findByTagUid(String tagUid);

    @EntityGraph(attributePaths = {"user", "gym"})
    Optional<NfcTag> findByTagUidAndActiveTrue(String tagUid);
    Optional<NfcTag> findByUserIdAndActiveTrue(UUID userId);
    List<NfcTag> findByGymIdAndUserIsNullAndActiveTrue(UUID gymId);
    boolean existsByTagUid(String tagUid);
}

package com.minegolem.backend.repository;


import com.minegolem.backend.domain.entity.SubscriptionType;
import com.minegolem.backend.domain.enums.SubscriptionTypeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionTypeRepository extends JpaRepository<SubscriptionType, UUID> {
    List<SubscriptionType> findByGymIdAndActiveTrueOrderByNameAsc(UUID gymId);
    List<SubscriptionType> findByGymIdAndTypeAndActiveTrueOrderByNameAsc(UUID gymId, SubscriptionTypeEnum type);
    Optional<SubscriptionType> findByIdAndGymId(UUID id, UUID gymId);
}

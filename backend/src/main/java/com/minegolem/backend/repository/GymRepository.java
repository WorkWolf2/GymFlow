package com.minegolem.backend.repository;


import com.minegolem.backend.domain.entity.Gym;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GymRepository extends JpaRepository<Gym, UUID> {
    boolean existsByEmail(String email);
}

package com.minegolem.backend.repository;


import com.minegolem.backend.domain.entity.StaffUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {
    Optional<StaffUser> findByEmailAndActiveTrue(String email);
    boolean existsByEmail(String email);
}

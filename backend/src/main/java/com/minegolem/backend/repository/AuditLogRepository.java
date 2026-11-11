package com.minegolem.backend.repository;

import com.minegolem.backend.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(value = "SELECT a FROM AuditLog a WHERE a.gymId = :gymId ORDER BY a.createdAt DESC, a.id DESC",
           countQuery = "SELECT COUNT(a) FROM AuditLog a WHERE a.gymId = :gymId")
    Page<AuditLog> findByGymId(@Param("gymId") UUID gymId, Pageable pageable);

    default Page<AuditLog> findByGymIdOrderByCreatedAtDesc(UUID gymId, Pageable pageable) {
        return findByGymId(gymId, pageable);
    }
}

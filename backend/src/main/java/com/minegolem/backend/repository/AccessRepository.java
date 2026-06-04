package com.minegolem.backend.repository;

import com.minegolem.backend.domain.entity.Access;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccessRepository extends JpaRepository<Access, UUID> {

    Page<Access> findByGymIdOrderByAccessTimeDesc(UUID gymId, Pageable pageable);

    @Query("""
        SELECT a FROM Access a
        WHERE a.gym.id = :gymId
          AND a.accessTime BETWEEN :from AND :to
        ORDER BY a.accessTime DESC
        """)
    List<Access> findByGymAndTimeRange(@Param("gymId") UUID gymId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    List<Access> findByUserIdOrderByAccessTimeDesc(UUID userId, Pageable pageable);

    long countByGymIdAndAccessTimeBetween(UUID gymId, LocalDateTime from, LocalDateTime to);

    long countByGymIdAndGrantedFalseAndAccessTimeBetween(UUID gymId, LocalDateTime from, LocalDateTime to);

    @Query("""
        SELECT COUNT(DISTINCT a.user.id)
        FROM Access a
        WHERE a.gym.id = :gymId
          AND a.granted = true
          AND a.accessTime >= :since
        """)
    long countDistinctUsersPresent(@Param("gymId") UUID gymId, @Param("since") LocalDateTime since);

    List<Access> findTop20ByGymIdOrderByAccessTimeDesc(UUID gymId);
}

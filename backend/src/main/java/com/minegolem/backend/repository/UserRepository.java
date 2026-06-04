package com.minegolem.backend.repository;


import com.minegolem.backend.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("""
        SELECT u FROM User u
        WHERE u.gym.id = :gymId
          AND u.deletedAt IS NULL
          AND (:search IS NULL OR :search = '' OR
               LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(u.phone) LIKE LOWER(CONCAT('%', :search, '%')) OR
               CAST(u.clientCode AS string) LIKE CONCAT('%', :search, '%'))
        """)
    Page<User> searchByGym(@Param("gymId") UUID gymId,
                           @Param("search") String search,
                           Pageable pageable);

    Optional<User> findByIdAndGymIdAndDeletedAtIsNull(UUID id, UUID gymId);

    Optional<User> findByClientCodeAndDeletedAtIsNull(Long clientCode);

    @Query("SELECT COALESCE(MAX(u.clientCode), 0) + 1 FROM User u")
    Long nextClientCode();

    boolean existsByEmailAndGymId(String email, UUID gymId);

    @Query("""
        SELECT u FROM User u
        JOIN NfcTag t ON t.user.id = u.id
        WHERE t.tagUid = :tagUid
          AND t.active = true
          AND u.deletedAt IS NULL
        """)
    Optional<User> findByNfcTagUid(@Param("tagUid") String tagUid);

    long countByGymIdAndActiveTrueAndDeletedAtIsNull(UUID gymId);

    @Query("""
        SELECT u FROM User u
        WHERE u.gym.id = :gymId
          AND u.deletedAt IS NULL
          AND u.active = true
          AND u.email IS NOT NULL
          AND u.email <> ''
        """)
    java.util.List<User> findAllWithValidEmailByGymId(@Param("gymId") UUID gymId);
}

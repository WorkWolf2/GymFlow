package com.minegolem.backend.repository;

import com.minegolem.backend.domain.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoucherRepository extends JpaRepository<Voucher, UUID> {
    List<Voucher> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<Voucher> findByIdAndUserGymId(UUID id, UUID gymId);
    @Query("SELECT v FROM Voucher v JOIN FETCH v.user u WHERE u.gym.id = :gymId AND u.deletedAt IS NULL ORDER BY v.endDate ASC, u.lastName ASC, u.firstName ASC")
    List<Voucher> findForReport(@Param("gymId") UUID gymId);
}

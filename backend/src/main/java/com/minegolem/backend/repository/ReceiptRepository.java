package com.minegolem.backend.repository;

import com.minegolem.backend.domain.entity.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    @Query("SELECT COALESCE(MAX(r.receiptNumber), 0) FROM Receipt r WHERE r.gym.id = :gymId AND r.receiptYear = :year")
    Integer findMaxReceiptNumberByGymIdAndYear(@Param("gymId") UUID gymId, @Param("year") Integer year);

    Optional<Receipt> findFirstBySubscriptionIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID subscriptionId);

    List<Receipt> findByUserIdAndDeletedAtIsNullOrderByIssueDateDescReceiptNumberDesc(UUID userId);

    Optional<Receipt> findByIdAndGymIdAndDeletedAtIsNull(UUID id, UUID gymId);

    List<Receipt> findByGymIdAndDeletedAtIsNullOrderByIssueDateDescReceiptNumberDesc(UUID gymId);
}

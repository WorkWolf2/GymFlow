package com.minegolem.backend.repository;


import com.minegolem.backend.domain.entity.Payment;
import com.minegolem.backend.domain.enums.PaymentMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Page<Payment> findByGymIdAndDeletedAtIsNullOrderByPaymentDateDesc(UUID gymId, Pageable pageable);

    @Query("""
        SELECT p FROM Payment p
        WHERE p.gym.id = :gymId
          AND p.deletedAt IS NULL
          AND p.paymentDate BETWEEN :from AND :to
        ORDER BY p.paymentDate DESC
        """)
    List<Payment> findByGymAndDateRange(@Param("gymId") UUID gymId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    List<Payment> findByUserIdAndDeletedAtIsNullOrderByPaymentDateDesc(UUID userId);

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM Payment p
        WHERE p.gym.id = :gymId
          AND p.deletedAt IS NULL
          AND p.paymentDate = :date
          AND p.type = com.minegolem.backend.domain.enums.PaymentType.INCOME
          AND (:method IS NULL OR p.method = :method)
        """)
    BigDecimal sumByGymAndDate(@Param("gymId") UUID gymId,
                               @Param("date") LocalDate date,
                               @Param("method") PaymentMethod method);

    @Query("""
        SELECT SUM(p.amount)
        FROM Payment p
        WHERE p.gym.id = :gymId
          AND p.deletedAt IS NULL
          AND p.paymentDate = :date
          AND p.type = com.minegolem.backend.domain.enums.PaymentType.INCOME
        """)
    BigDecimal sumByGymAndDateOnly(@Param("gymId") UUID gymId,
                                   @Param("date") LocalDate date);

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM Payment p
        WHERE p.gym.id = :gymId
          AND p.deletedAt IS NULL
          AND p.paymentDate = :date
          AND p.type = com.minegolem.backend.domain.enums.PaymentType.EXPENSE
        """)
    BigDecimal sumExpensesByGymAndDate(@Param("gymId") UUID gymId,
                                       @Param("date") LocalDate date);

    @Query("SELECT DISTINCT p.paymentDate FROM Payment p WHERE p.gym.id = :gymId AND p.deletedAt IS NULL")
    List<LocalDate> findDatesWithPayments(@Param("gymId") UUID gymId);
}

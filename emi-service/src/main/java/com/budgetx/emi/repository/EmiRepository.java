package com.budgetx.emi.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.budgetx.emi.entity.Emi;
import com.budgetx.emi.entity.EmiStatus;

public interface EmiRepository extends JpaRepository<Emi, Long> {

    List<Emi> findByUserIdOrderByStartDateDesc(Long userId);

    List<Emi> findByUserIdAndStatusOrderByStartDateDesc(Long userId, EmiStatus status);

    Optional<Emi> findByEmiIdAndUserId(Long emiId, Long userId);

    /** Sum of instalments due in the month containing {@code monthStart}..{@code monthEnd}. */
    @Query("""
            select coalesce(sum(e.emiAmount), 0) from Emi e
            where e.userId = :userId
              and e.status = com.budgetx.emi.entity.EmiStatus.ACTIVE
              and e.startDate <= :monthEnd
              and e.endDate >= :monthStart
            """)
    BigDecimal sumDueInPeriod(@Param("userId") Long userId,
                              @Param("monthStart") LocalDate monthStart,
                              @Param("monthEnd") LocalDate monthEnd);
}

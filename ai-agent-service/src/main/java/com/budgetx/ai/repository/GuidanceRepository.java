package com.budgetx.ai.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.budgetx.ai.entity.GuidanceRecord;

public interface GuidanceRepository extends JpaRepository<GuidanceRecord, Long> {

    Optional<GuidanceRecord> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUserIdAndAiCalledTrueAndCreatedAtAfter(Long userId, LocalDateTime after);
}

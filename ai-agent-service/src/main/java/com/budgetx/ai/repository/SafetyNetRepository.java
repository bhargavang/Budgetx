package com.budgetx.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.budgetx.ai.entity.SafetyNet;

public interface SafetyNetRepository extends JpaRepository<SafetyNet, Long> {
}

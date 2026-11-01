package com.budgetx.ai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The user's own answers about savings, dependents and insurance. One row per user. */
@Entity
@Table(name = "safety_nets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafetyNet {

    @Id
    private Long userId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal emergencySavings;

    @Column(nullable = false)
    private int dependents;

    @Column(nullable = false)
    private boolean hasTermInsurance;

    @Column(nullable = false)
    private boolean hasHealthInsurance;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

package com.budgetx.budget.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Records that a month's overspend alert was sent, so each user gets at most one per month. */
@Entity
@Table(name = "overspend_alerts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "alertYear", "alertMonth"}))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverspendAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int alertYear;

    @Column(nullable = false)
    private int alertMonth;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal overBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

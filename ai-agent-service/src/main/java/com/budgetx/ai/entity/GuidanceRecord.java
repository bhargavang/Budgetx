package com.budgetx.ai.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Every guidance shown to a user is kept, so what we told them can be reviewed later. */
@Entity
@Table(name = "guidance", indexes = @Index(columnList = "userId,createdAt"))
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuidanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** True when the AI was called for this guidance (counts toward the daily limit). */
    @Column(nullable = false)
    private boolean aiCalled;

    @Column(nullable = false, length = 20)
    private String mode;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;
}

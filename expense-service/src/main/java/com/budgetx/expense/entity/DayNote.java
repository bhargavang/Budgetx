package com.budgetx.expense.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A free-text journal note for one day of spending, one per user per day. */
@Entity
@Table(name = "day_notes", uniqueConstraints = @UniqueConstraint(name = "uk_day_notes_user_date",
        columnNames = {"userId", "noteDate"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dayNoteId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate noteDate;

    @Column(nullable = false, length = 2000)
    private String note;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}

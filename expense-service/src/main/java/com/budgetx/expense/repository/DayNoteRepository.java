package com.budgetx.expense.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.budgetx.expense.entity.DayNote;

public interface DayNoteRepository extends JpaRepository<DayNote, Long> {

    Optional<DayNote> findByUserIdAndNoteDate(Long userId, LocalDate noteDate);
}

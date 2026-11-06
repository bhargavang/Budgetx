package com.budgetx.expense.dto;

import jakarta.validation.constraints.Size;

/** A blank or null note removes the day's note. */
public record DayNoteRequest(@Size(max = 2000) String note) {
}

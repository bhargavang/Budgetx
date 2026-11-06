package com.budgetx.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** {@code saved} is false until the user has answered the questions once. */
public record SafetyProfileResponse(boolean saved, BigDecimal emergencySavings, int dependents,
                                    boolean hasTermInsurance, boolean hasHealthInsurance, LocalDateTime updatedAt) {
}

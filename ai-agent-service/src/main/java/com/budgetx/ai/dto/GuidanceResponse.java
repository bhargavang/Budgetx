package com.budgetx.ai.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Educational guidance for one user and month.
 *
 * @param mode     AI_CHECKED (AI proposed the split and it passed every safety rule) or RULES_ONLY
 * @param modeNote plain-language reason for the mode, shown to the user
 */
public record GuidanceResponse(
        Long id,
        LocalDateTime generatedAt,
        String mode,
        String modeNote,
        String riskProfile,
        int equityCapPercent,
        BudgetSnapshot budget,
        boolean safetyProfileMissing,
        List<Insight> warnings,
        List<ActionItem> actions,
        List<Allocation> allocations,
        String summary,
        List<String> principles,
        String disclaimer) {
}

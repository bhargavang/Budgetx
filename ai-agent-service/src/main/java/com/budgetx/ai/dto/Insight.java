package com.budgetx.ai.dto;

/** A warning or note from the rule engine. Severity: CRITICAL, WARNING or INFO. */
public record Insight(String severity, String title, String detail) {
}

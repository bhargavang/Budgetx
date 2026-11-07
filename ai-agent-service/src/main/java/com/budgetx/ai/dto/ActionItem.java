package com.budgetx.ai.dto;

import java.math.BigDecimal;

/**
 * A safety step that comes before investing: emergency fund, health cover, term cover, debt.
 * {@code monthlyAmount} is set only when part of the monthly savings goes to it.
 */
public record ActionItem(String type, String title, String detail, BigDecimal monthlyAmount) {
}

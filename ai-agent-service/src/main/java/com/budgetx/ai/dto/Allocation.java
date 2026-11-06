package com.budgetx.ai.dto;

import java.math.BigDecimal;

import com.budgetx.ai.rules.AssetClass;
import com.budgetx.ai.rules.InvestmentType;

/** One slice of the monthly amount to invest. Title, risk and description always come from {@link InvestmentType}. */
public record Allocation(InvestmentType type, String title, AssetClass assetClass, String riskLevel, String horizon,
                         int percent, BigDecimal monthlyAmount, String about, String reason) {
}

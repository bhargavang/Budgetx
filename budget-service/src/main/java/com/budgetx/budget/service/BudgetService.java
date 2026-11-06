package com.budgetx.budget.service;

import java.time.Clock;
import java.time.YearMonth;

import org.springframework.stereotype.Service;

import com.budgetx.budget.dto.BudgetSummary;
import com.budgetx.budget.feign.EmiClient;
import com.budgetx.budget.feign.ExpenseClient;
import com.budgetx.budget.feign.UserClient;
import com.budgetx.budget.feign.UserProfile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final UserClient userClient;
    private final EmiClient emiClient;
    private final ExpenseClient expenseClient;
    private final BudgetCalculator calculator;
    private final Clock clock;

    public BudgetSummary current(Long userId) {
        return forMonth(userId, YearMonth.now(clock));
    }

    public BudgetSummary forMonth(Long userId, YearMonth period) {
        UserProfile profile = userClient.getProfile(userId);
        YearMonth previous = period.minusMonths(1);
        return calculator.calculate(userId, period.getMonthValue(), period.getYear(),
                profile.monthlySalary(), profile.riskProfile(),
                emiClient.getTotalEmi(userId, period.getMonthValue(), period.getYear()),
                expenseClient.getTotalExpenses(userId, period.getMonthValue(), period.getYear()),
                expenseClient.getTotalExpenses(userId, previous.getMonthValue(), previous.getYear()));
    }
}

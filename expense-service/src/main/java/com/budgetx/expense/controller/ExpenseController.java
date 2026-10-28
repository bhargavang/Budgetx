package com.budgetx.expense.controller;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.expense.dto.ExpenseRequest;
import com.budgetx.expense.dto.ExpenseResponse;
import com.budgetx.expense.dto.MonthSummaryResponse;
import com.budgetx.expense.dto.MonthlySummary;
import com.budgetx.expense.service.ExpenseService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The caller's identity comes from the X-User-Id header set by the API gateway after JWT validation. */
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    static final String USER_ID = "X-User-Id";

    private final ExpenseService expenseService;
    private final Clock clock;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@RequestHeader(USER_ID) Long userId, @Valid @RequestBody ExpenseRequest request) {
        return expenseService.create(userId, request);
    }

    /** Defaults to the current month when no date range is given. */
    @GetMapping
    public Page<ExpenseResponse> list(
            @RequestHeader(USER_ID) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @PageableDefault(size = 20, sort = "expenseDate", direction = Sort.Direction.DESC) Pageable pageable) {
        YearMonth now = YearMonth.now(clock);
        return expenseService.search(userId,
                from != null ? from : now.atDay(1),
                to != null ? to : now.atEndOfMonth(),
                categoryId, pageable);
    }

    /** Totals for one month, grouped by category. Defaults to the current month. */
    @GetMapping("/summary/month")
    public MonthSummaryResponse monthSummary(@RequestHeader(USER_ID) Long userId,
                                             @RequestParam(required = false) Integer month,
                                             @RequestParam(required = false) Integer year) {
        return expenseService.monthSummary(userId, resolve(month, year));
    }

    /** Monthly totals for a year (months with no spending are omitted). Defaults to the current year. */
    @GetMapping("/summary/year")
    public List<MonthlySummary> yearSummary(@RequestHeader(USER_ID) Long userId,
                                            @RequestParam(required = false) Integer year) {
        return expenseService.yearSummary(userId, year != null ? year : Year.now(clock).getValue());
    }

    @GetMapping("/{expenseId}")
    public ExpenseResponse get(@RequestHeader(USER_ID) Long userId, @PathVariable Long expenseId) {
        return expenseService.get(userId, expenseId);
    }

    @PutMapping("/{expenseId}")
    public ExpenseResponse update(@RequestHeader(USER_ID) Long userId, @PathVariable Long expenseId,
                                  @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(userId, expenseId, request);
    }

    @DeleteMapping("/{expenseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(USER_ID) Long userId, @PathVariable Long expenseId) {
        expenseService.delete(userId, expenseId);
    }

    private YearMonth resolve(Integer month, Integer year) {
        YearMonth now = YearMonth.now(clock);
        return YearMonth.of(year != null ? year : now.getYear(), month != null ? month : now.getMonthValue());
    }
}

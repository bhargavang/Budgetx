package com.budgetx.emi.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.budgetx.emi.dto.EmiRequest;
import com.budgetx.emi.dto.EmiResponse;
import com.budgetx.emi.dto.EmiSummaryResponse;
import com.budgetx.emi.entity.Emi;
import com.budgetx.emi.entity.EmiStatus;
import com.budgetx.emi.exception.ResourceNotFoundException;
import com.budgetx.emi.repository.EmiRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmiService {

    private final EmiRepository emiRepository;
    private final Clock clock;

    @Transactional
    public EmiResponse create(Long userId, EmiRequest request) {
        Emi emi = Emi.builder().userId(userId).build();
        apply(emi, request);
        return EmiResponse.from(emiRepository.save(emi), today());
    }

    @Transactional(readOnly = true)
    public List<EmiResponse> list(Long userId, EmiStatus status) {
        List<Emi> emis = status == null
                ? emiRepository.findByUserIdOrderByStartDateDesc(userId)
                : emiRepository.findByUserIdAndStatusOrderByStartDateDesc(userId, status);
        LocalDate today = today();
        return emis.stream().map(e -> EmiResponse.from(e, today)).toList();
    }

    @Transactional(readOnly = true)
    public EmiResponse get(Long userId, Long emiId) {
        return EmiResponse.from(find(userId, emiId), today());
    }

    @Transactional
    public EmiResponse update(Long userId, Long emiId, EmiRequest request) {
        Emi emi = find(userId, emiId);
        apply(emi, request);
        return EmiResponse.from(emi, today());
    }

    @Transactional
    public EmiResponse close(Long userId, Long emiId) {
        Emi emi = find(userId, emiId);
        emi.setStatus(EmiStatus.CLOSED);
        return EmiResponse.from(emi, today());
    }

    @Transactional
    public void delete(Long userId, Long emiId) {
        emiRepository.delete(find(userId, emiId));
    }

    @Transactional(readOnly = true)
    public EmiSummaryResponse summary(Long userId) {
        List<EmiResponse> active = list(userId, EmiStatus.ACTIVE);
        BigDecimal outstanding = active.stream()
                .map(EmiResponse::outstandingPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new EmiSummaryResponse(active.size(), totalForMonth(userId, YearMonth.now(clock)), outstanding);
    }

    /** Total instalments the user owes in the given month: what budget-service subtracts from salary. */
    @Transactional(readOnly = true)
    public BigDecimal totalForMonth(Long userId, YearMonth month) {
        return emiRepository.sumDueInPeriod(userId, month.atDay(1), month.atEndOfMonth());
    }

    private void apply(Emi emi, EmiRequest request) {
        BigDecimal emiAmount = request.emiAmount() != null
                ? request.emiAmount()
                : EmiCalculator.monthlyInstalment(
                        request.principalAmount(), request.interestRate(), request.tenureMonths());
        emi.setLoanName(request.loanName().trim());
        emi.setLenderName(request.lenderName());
        emi.setLoanType(request.loanType());
        emi.setPrincipalAmount(request.principalAmount());
        emi.setInterestRate(request.interestRate());
        emi.setTenureMonths(request.tenureMonths());
        emi.setEmiAmount(emiAmount);
        emi.setStartDate(request.startDate());
        emi.setEndDate(request.startDate().plusMonths(request.tenureMonths() - 1L));
    }

    private Emi find(Long userId, Long emiId) {
        return emiRepository.findByEmiIdAndUserId(emiId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("EMI " + emiId + " not found"));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}

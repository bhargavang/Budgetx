package com.budgetx.ai.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.budgetx.ai.agent.AiPlan;
import com.budgetx.ai.agent.AiPlanValidator;
import com.budgetx.ai.agent.AiPlanner;
import com.budgetx.ai.agent.AiUnavailableException;
import com.budgetx.ai.agent.PlanContext;
import com.budgetx.ai.dto.Allocation;
import com.budgetx.ai.dto.BudgetSnapshot;
import com.budgetx.ai.dto.GuidanceResponse;
import com.budgetx.ai.entity.GuidanceRecord;
import com.budgetx.ai.entity.SafetyNet;
import com.budgetx.ai.exception.ResourceNotFoundException;
import com.budgetx.ai.feign.BudgetClient;
import com.budgetx.ai.feign.BudgetSummary;
import com.budgetx.ai.feign.RiskProfile;
import com.budgetx.ai.feign.UserClient;
import com.budgetx.ai.feign.UserProfile;
import com.budgetx.ai.repository.GuidanceRepository;
import com.budgetx.ai.rules.AllocationBuilder;
import com.budgetx.ai.rules.DefaultAllocator;
import com.budgetx.ai.rules.FinancialRuleEngine;
import com.budgetx.ai.rules.RuleOutcome;
import com.budgetx.ai.rules.Share;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/** Rules first, then (optionally) AI within the rules' limits, then a final safety check on what the AI said. */
@Slf4j
@Service
public class GuidanceService {

    public static final String MODE_AI = "AI_CHECKED";
    public static final String MODE_RULES = "RULES_ONLY";

    static final String DISCLAIMER = "This is free general education, not investment advice. BudgetX is not a "
            + "SEBI-registered investment adviser and earns nothing from any product. Investments in mutual funds, "
            + "gold and other market-linked products can lose value, and past performance does not guarantee future "
            + "results. Read each product's official documents, and for advice on your exact situation talk to a "
            + "SEBI-registered investment adviser.";

    static final List<String> PRINCIPLES = List.of(
            "Free, with no commissions, referral fees or ads. We never earn from what you choose.",
            "We name categories, never specific funds, banks, insurers or apps.",
            "Safety first: no spending gap, an emergency fund and insurance come before investing.",
            "Equity is limited by your risk profile and age, and the AI can't go past those limits.",
            "We never suggest crypto, F&O, intraday trading, penny stocks, ULIPs or chit funds, and never promise returns.",
            "Only anonymous numbers go to the AI, never your name, email or phone.");

    private final UserClient userClient;
    private final BudgetClient budgetClient;
    private final SafetyProfileService safetyProfiles;
    private final FinancialRuleEngine ruleEngine;
    private final DefaultAllocator defaultAllocator;
    private final AllocationBuilder allocationBuilder;
    private final AiPlanner aiPlanner;
    private final AiPlanValidator validator;
    private final GuidanceRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int aiDailyLimit;

    public GuidanceService(UserClient userClient, BudgetClient budgetClient, SafetyProfileService safetyProfiles,
                           FinancialRuleEngine ruleEngine, DefaultAllocator defaultAllocator,
                           AllocationBuilder allocationBuilder, AiPlanner aiPlanner, AiPlanValidator validator,
                           GuidanceRepository repository, ObjectMapper objectMapper, Clock clock,
                           @Value("${budgetx.ai.daily-limit-per-user:5}") int aiDailyLimit) {
        this.userClient = userClient;
        this.budgetClient = budgetClient;
        this.safetyProfiles = safetyProfiles;
        this.ruleEngine = ruleEngine;
        this.defaultAllocator = defaultAllocator;
        this.allocationBuilder = allocationBuilder;
        this.aiPlanner = aiPlanner;
        this.validator = validator;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.aiDailyLimit = aiDailyLimit;
    }

    public GuidanceResponse generate(Long userId) {
        UserProfile profile = userClient.getProfile(userId);
        BudgetSummary budget = budgetClient.getSummary(userId);
        SafetyNet safety = safetyProfiles.find(userId).orElse(null);
        RiskProfile risk = profile.riskProfile() == null ? RiskProfile.MODERATE : profile.riskProfile();

        RuleOutcome rules = ruleEngine.apply(profile, budget, safety);

        List<Share> shares = List.of();
        String mode = MODE_RULES;
        String note;
        String summary = null;
        boolean aiCalled = false;

        if (!rules.canInvest()) {
            note = "Nothing to invest yet, so the guidance focuses on the safety steps above.";
        } else {
            shares = defaultAllocator.allocate(risk, rules.equityCapPercent());
            if (!aiPlanner.isEnabled()) {
                note = "Based on BudgetX's built-in rules.";
            } else if (aiUsedToday(userId) >= aiDailyLimit) {
                note = "You've reached today's limit for AI explanations, so this uses BudgetX's built-in rules.";
            } else {
                aiCalled = true;
                try {
                    AiPlan plan = aiPlanner.propose(context(profile, risk, budget, safety, rules));
                    AiPlanValidator.Result checked = validator.validate(plan, rules.equityCapPercent());
                    if (checked.usable()) {
                        shares = checked.shares();
                        summary = checked.summary();
                        mode = MODE_AI;
                        note = "Personalised by AI, then checked against BudgetX's safety rules.";
                    } else {
                        log.warn("AI plan for user {} rejected: {}", userId, checked.violations());
                        note = "The AI's suggestion didn't pass our safety checks, so this uses BudgetX's built-in rules.";
                    }
                    if (checked.usable() && !checked.violations().isEmpty()) {
                        log.info("AI plan for user {} accepted after removing: {}", userId, checked.violations());
                    }
                } catch (AiUnavailableException e) {
                    log.warn("AI unavailable for user {}: {}", userId, e.getMessage());
                    note = "The AI couldn't be reached, so this uses BudgetX's built-in rules.";
                }
            }
        }

        List<Allocation> allocations = allocationBuilder.build(shares, rules.canInvest() ? rules.amountToInvest() : BigDecimal.ZERO);
        BudgetSnapshot snapshot = new BudgetSnapshot(budget.month(), budget.year(), budget.monthlySalary(),
                budget.totalEmi(), budget.spendingEstimate(), budget.disposableIncome(), budget.investableAmount(),
                rules.emergencyFundContribution(), rules.canInvest() ? rules.amountToInvest() : BigDecimal.ZERO);

        LocalDateTime now = LocalDateTime.now(clock);
        GuidanceResponse draft = new GuidanceResponse(null, now, mode, note, risk.name(), rules.equityCapPercent(),
                snapshot, safety == null, rules.warnings(), rules.actions(), allocations, summary, PRINCIPLES, DISCLAIMER);
        GuidanceRecord saved = repository.save(GuidanceRecord.builder().userId(userId).createdAt(now)
                .aiCalled(aiCalled).mode(mode).payload(toJson(draft)).build());
        return withId(draft, saved.getId());
    }

    public GuidanceResponse latest(Long userId) {
        GuidanceRecord record = repository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No guidance yet"));
        try {
            return withId(objectMapper.readValue(record.getPayload(), GuidanceResponse.class), record.getId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored guidance is unreadable", e);
        }
    }

    public Optional<Integer> remainingAiToday(Long userId) {
        return aiPlanner.isEnabled() ? Optional.of(Math.max(0, aiDailyLimit - (int) aiUsedToday(userId))) : Optional.empty();
    }

    private long aiUsedToday(Long userId) {
        return repository.countByUserIdAndAiCalledTrueAndCreatedAtAfter(userId, LocalDateTime.now(clock).minusHours(24));
    }

    private static PlanContext context(UserProfile p, RiskProfile risk, BudgetSummary b, SafetyNet s, RuleOutcome r) {
        boolean emergencyDone = s != null && s.getEmergencySavings().compareTo(r.emergencyFundTarget()) >= 0;
        return new PlanContext(p.age(), risk, nz(b.monthlySalary()), nz(b.totalEmi()), nz(b.spendingEstimate()),
                r.amountToInvest(), r.equityCapPercent(), FinancialRuleEngine.MAX_GOLD_PERCENT,
                s == null ? 0 : s.getDependents(), emergencyDone, s != null && s.isHasHealthInsurance(),
                s != null && s.isHasTermInsurance());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String toJson(GuidanceResponse r) {
        try {
            return objectMapper.writeValueAsString(r);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static GuidanceResponse withId(GuidanceResponse r, Long id) {
        return new GuidanceResponse(id, r.generatedAt(), r.mode(), r.modeNote(), r.riskProfile(), r.equityCapPercent(),
                r.budget(), r.safetyProfileMissing(), r.warnings(), r.actions(), r.allocations(), r.summary(),
                r.principles(), r.disclaimer());
    }
}

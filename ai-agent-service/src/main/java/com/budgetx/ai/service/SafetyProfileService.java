package com.budgetx.ai.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.budgetx.ai.dto.SafetyProfileRequest;
import com.budgetx.ai.dto.SafetyProfileResponse;
import com.budgetx.ai.entity.SafetyNet;
import com.budgetx.ai.repository.SafetyNetRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SafetyProfileService {

    private final SafetyNetRepository repository;
    private final Clock clock;

    public Optional<SafetyNet> find(Long userId) {
        return repository.findById(userId);
    }

    public SafetyProfileResponse get(Long userId) {
        return repository.findById(userId).map(SafetyProfileService::toResponse)
                .orElse(new SafetyProfileResponse(false, BigDecimal.ZERO, 0, false, false, null));
    }

    @Transactional
    public SafetyProfileResponse save(Long userId, SafetyProfileRequest request) {
        SafetyNet net = repository.findById(userId).orElseGet(() -> SafetyNet.builder().userId(userId).build());
        net.setEmergencySavings(request.emergencySavings());
        net.setDependents(request.dependents());
        net.setHasTermInsurance(request.hasTermInsurance());
        net.setHasHealthInsurance(request.hasHealthInsurance());
        net.setUpdatedAt(LocalDateTime.now(clock));
        return toResponse(repository.save(net));
    }

    private static SafetyProfileResponse toResponse(SafetyNet n) {
        return new SafetyProfileResponse(true, n.getEmergencySavings(), n.getDependents(), n.isHasTermInsurance(),
                n.isHasHealthInsurance(), n.getUpdatedAt());
    }
}

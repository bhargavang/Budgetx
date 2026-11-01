package com.budgetx.ai.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.ai.dto.GuidanceResponse;
import com.budgetx.ai.dto.SafetyProfileRequest;
import com.budgetx.ai.dto.SafetyProfileResponse;
import com.budgetx.ai.service.GuidanceService;
import com.budgetx.ai.service.SafetyProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class GuidanceController {

    private final GuidanceService guidanceService;
    private final SafetyProfileService safetyProfiles;

    /** Builds fresh guidance from the user's current budget and safety net. */
    @PostMapping("/api/recommendations/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public GuidanceResponse generate(@RequestHeader("X-User-Id") Long userId) {
        return guidanceService.generate(userId);
    }

    /** The most recent guidance; 404 if none yet. */
    @GetMapping("/api/recommendations/latest")
    public GuidanceResponse latest(@RequestHeader("X-User-Id") Long userId) {
        return guidanceService.latest(userId);
    }

    @GetMapping("/api/ai/safety-profile")
    public SafetyProfileResponse getSafetyProfile(@RequestHeader("X-User-Id") Long userId) {
        return safetyProfiles.get(userId);
    }

    @PutMapping("/api/ai/safety-profile")
    public SafetyProfileResponse saveSafetyProfile(@RequestHeader("X-User-Id") Long userId,
                                                   @Valid @RequestBody SafetyProfileRequest request) {
        return safetyProfiles.save(userId, request);
    }

    /** Whether AI explanations are on, and how many are left today. */
    @GetMapping("/api/ai/status")
    public Map<String, Object> status(@RequestHeader("X-User-Id") Long userId) {
        Map<String, Object> status = new LinkedHashMap<>();
        var remaining = guidanceService.remainingAiToday(userId);
        status.put("aiEnabled", remaining.isPresent());
        status.put("aiRemainingToday", remaining.orElse(0));
        return status;
    }
}

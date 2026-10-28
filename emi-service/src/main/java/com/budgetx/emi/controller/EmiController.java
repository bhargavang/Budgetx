package com.budgetx.emi.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.emi.dto.EmiRequest;
import com.budgetx.emi.dto.EmiResponse;
import com.budgetx.emi.dto.EmiSummaryResponse;
import com.budgetx.emi.entity.EmiStatus;
import com.budgetx.emi.service.EmiService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The caller's identity comes from the X-User-Id header set by the API gateway after JWT validation. */
@RestController
@RequestMapping("/api/emis")
@RequiredArgsConstructor
public class EmiController {

    static final String USER_ID = "X-User-Id";

    private final EmiService emiService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmiResponse create(@RequestHeader(USER_ID) Long userId, @Valid @RequestBody EmiRequest request) {
        return emiService.create(userId, request);
    }

    @GetMapping
    public List<EmiResponse> list(@RequestHeader(USER_ID) Long userId,
                                  @RequestParam(required = false) EmiStatus status) {
        return emiService.list(userId, status);
    }

    @GetMapping("/summary")
    public EmiSummaryResponse summary(@RequestHeader(USER_ID) Long userId) {
        return emiService.summary(userId);
    }

    @GetMapping("/{emiId}")
    public EmiResponse get(@RequestHeader(USER_ID) Long userId, @PathVariable Long emiId) {
        return emiService.get(userId, emiId);
    }

    @PutMapping("/{emiId}")
    public EmiResponse update(@RequestHeader(USER_ID) Long userId, @PathVariable Long emiId,
                              @Valid @RequestBody EmiRequest request) {
        return emiService.update(userId, emiId, request);
    }

    @PatchMapping("/{emiId}/close")
    public EmiResponse close(@RequestHeader(USER_ID) Long userId, @PathVariable Long emiId) {
        return emiService.close(userId, emiId);
    }

    @DeleteMapping("/{emiId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(USER_ID) Long userId, @PathVariable Long emiId) {
        emiService.delete(userId, emiId);
    }
}

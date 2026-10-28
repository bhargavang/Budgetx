package com.budgetx.user.controller;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.user.dto.UserProfileResponse;
import com.budgetx.user.service.UserService;

import lombok.RequiredArgsConstructor;

/**
 * Service-to-service endpoints used by budget-service and ai-agent-service via Feign.
 * The API gateway blocks these paths for external clients.
 */
@RestController
@RequestMapping("/api/users/{id}")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/salary")
    public BigDecimal salary(@PathVariable Long id) {
        return userService.getSalary(id);
    }

    @GetMapping("/profile")
    public UserProfileResponse profile(@PathVariable Long id) {
        return UserProfileResponse.from(userService.getById(id));
    }
}

package com.budgetx.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.user.dto.ChangePasswordRequest;
import com.budgetx.user.dto.UpdateUserRequest;
import com.budgetx.user.dto.UserResponse;
import com.budgetx.user.security.AuthenticatedUser;
import com.budgetx.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Endpoints for the signed-in user's own account. */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.from(userService.getById(principal.userId()));
    }

    @PatchMapping
    public UserResponse update(@AuthenticationPrincipal AuthenticatedUser principal,
                               @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(userService.update(principal.userId(), request));
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.userId(), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser principal) {
        userService.delete(principal.userId());
    }
}

package com.budgetx.user.security;

/** Principal stored in the SecurityContext once a request's JWT has been validated. */
public record AuthenticatedUser(Long userId, String email) {
}

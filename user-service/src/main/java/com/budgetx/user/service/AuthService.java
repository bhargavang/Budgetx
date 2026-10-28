package com.budgetx.user.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.budgetx.user.dto.AuthResponse;
import com.budgetx.user.dto.LoginRequest;
import com.budgetx.user.dto.RegisterRequest;
import com.budgetx.user.dto.UserResponse;
import com.budgetx.user.entity.RiskProfile;
import com.budgetx.user.entity.User;
import com.budgetx.user.exception.ConflictException;
import com.budgetx.user.repository.UserRepository;
import com.budgetx.user.security.JwtUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        if (request.phone() != null && userRepository.existsByPhone(request.phone())) {
            throw new ConflictException("An account with this phone number already exists");
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .phone(request.phone())
                .dateOfBirth(request.dateOfBirth())
                .gender(request.gender())
                .occupation(request.occupation())
                .monthlySalary(request.monthlySalary())
                .riskProfile(request.riskProfile() != null ? request.riskProfile() : RiskProfile.MODERATE)
                .build();
        return issueTokens(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        var claims = jwtUtil.validate(refreshToken, JwtUtil.TYPE_REFRESH)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));
        User user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        return new AuthResponse(
                jwtUtil.generateAccessToken(user),
                jwtUtil.generateRefreshToken(user),
                "Bearer",
                jwtUtil.accessTokenTtlSeconds(),
                UserResponse.from(user));
    }
}

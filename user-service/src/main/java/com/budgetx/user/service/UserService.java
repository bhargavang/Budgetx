package com.budgetx.user.service;

import java.math.BigDecimal;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.budgetx.user.dto.ChangePasswordRequest;
import com.budgetx.user.dto.UpdateUserRequest;
import com.budgetx.user.entity.User;
import com.budgetx.user.exception.ConflictException;
import com.budgetx.user.exception.ResourceNotFoundException;
import com.budgetx.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));
    }

    @Transactional(readOnly = true)
    public BigDecimal getSalary(Long userId) {
        return getById(userId).getMonthlySalary();
    }

    @Transactional
    public User update(Long userId, UpdateUserRequest request) {
        User user = getById(userId);
        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null && !request.phone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(request.phone())) {
                throw new ConflictException("An account with this phone number already exists");
            }
            user.setPhone(request.phone());
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        if (request.occupation() != null) {
            user.setOccupation(request.occupation());
        }
        if (request.monthlySalary() != null) {
            user.setMonthlySalary(request.monthlySalary());
        }
        if (request.riskProfile() != null) {
            user.setRiskProfile(request.riskProfile());
        }
        return user;
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getById(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public void delete(Long userId) {
        userRepository.delete(getById(userId));
    }
}

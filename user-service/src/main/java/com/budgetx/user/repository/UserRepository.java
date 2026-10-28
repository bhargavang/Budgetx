package com.budgetx.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.budgetx.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);
}

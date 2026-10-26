package com.example.expensetracker.repository;

import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);
    java.util.List<User> findByAuthProviderAndEmailVerifiedFalseAndCreatedAtBefore(AuthProvider authProvider, java.time.LocalDateTime cutoff);
}
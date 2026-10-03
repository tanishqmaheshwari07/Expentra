package com.example.expensetracker.repository;

import com.example.expensetracker.entity.EmailVerificationToken;
import com.example.expensetracker.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM EmailVerificationToken t WHERE t.tokenHash = :tokenHash")
    Optional<EmailVerificationToken> findByTokenHashWithLock(@Param("tokenHash") String tokenHash);

    Optional<EmailVerificationToken> findTopByUserOrderByCreatedAtDesc(User user);

    Optional<EmailVerificationToken> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    List<EmailVerificationToken> findByUserAndUsedFalse(User user);

    List<EmailVerificationToken> findByUserIdAndUsedFalse(Long userId);

    void deleteByUser(User user);
}

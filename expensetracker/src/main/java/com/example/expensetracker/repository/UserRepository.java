package com.example.expensetracker.repository;

import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);

    /**
     * Finds unverified users for a given provider created before a cutoff timestamp,
     * strictly excluding any user who owns or has ever owned an expense (including soft-deleted expenses).
     * Uses a native query directly querying the expenses table so that Hibernate's
     * @SQLRestriction("is_deleted = false") does not hide soft-deleted expenses.
     */
    @Query(value = """
        SELECT * FROM users u
        WHERE u.auth_provider = :#{#provider.name()}
          AND u.email_verified = false
          AND u.created_at < :cutoff
          AND NOT EXISTS (SELECT 1 FROM expenses e WHERE e.user_id = u.id)
        """, nativeQuery = true)
    List<User> findStaleUnverifiedWithoutExpenses(@Param("provider") AuthProvider provider,
                                                 @Param("cutoff") LocalDateTime cutoff);
}
package com.example.expensetracker.service;

import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.Category;
import com.example.expensetracker.entity.EmailVerificationToken;
import com.example.expensetracker.entity.Expense;
import com.example.expensetracker.entity.RefreshToken;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.repository.EmailVerificationTokenRepository;
import com.example.expensetracker.repository.ExpenseRepository;
import com.example.expensetracker.repository.OAuthExchangeCodeRepository;
import com.example.expensetracker.repository.RefreshTokenRepository;
import com.example.expensetracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 3: Real-database integration test suite for the safe unverified-user cleanup job.
 * Executes on the active test database profile (H2 in PostgreSQL mode / Testcontainers).
 *
 * Verifies all 7 safety scenarios:
 * 1. Unverified LOCAL user older than cutoff WITH active expense: NOT deleted.
 * 2. Unverified LOCAL user older than cutoff with ONLY soft-deleted expense: NOT deleted.
 * 3. Unverified LOCAL user older than cutoff with NO expenses: deleted, along with verification & refresh tokens.
 * 4. Unverified LOCAL user newer than cutoff: NOT deleted.
 * 5. Verified LOCAL user: NOT deleted.
 * 6. GOOGLE user: NOT deleted.
 * 7. Kill switch & dry-run behavior:
 *    - enabled=false: nothing deleted.
 *    - enabled=true, dry-run=true: nothing deleted.
 *    - enabled=true, dry-run=false: eligible users deleted.
 */
@SpringBootTest
@ActiveProfiles("test")
class UnverifiedUserCleanupDatabaseTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OAuthExchangeCodeRepository exchangeCodeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        exchangeCodeRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        // Delete all expenses via native query to wipe both active and soft-deleted rows
        jdbcTemplate.execute("DELETE FROM expenses");
        userRepository.deleteAll();

        // Configure default test settings: 7 days retention
        userService.setUnverifiedCleanupDays(7);
        userService.setUnverifiedCleanupEnabled(true);
        userService.setUnverifiedCleanupDryRun(false);
    }

    private User createUser(String name, String email, AuthProvider provider, boolean emailVerified, int ageDays) {
        User user = userRepository.save(User.builder()
                .name(name)
                .email(email)
                .authProvider(provider)
                .emailVerified(emailVerified)
                .role(Role.ROLE_USER)
                .isActive(true)
                .build());

        // Update created_at in the database to simulate age
        LocalDateTime pastCreatedAt = LocalDateTime.now().minusDays(ageDays);
        jdbcTemplate.update("UPDATE users SET created_at = ? WHERE id = ?", pastCreatedAt, user.getId());
        return user;
    }

    private Expense createExpense(User user, BigDecimal amount, boolean softDeleted) {
        Expense expense = expenseRepository.save(Expense.builder()
                .user(user)
                .amount(amount)
                .description("Test Expense")
                .category(Category.FOOD)
                .date(LocalDate.now())
                .isDeleted(false)
                .build());

        if (softDeleted) {
            jdbcTemplate.update("UPDATE expenses SET is_deleted = true WHERE id = ?", expense.getId());
        }
        return expense;
    }

    @Test
    @DisplayName("1. Unverified LOCAL user older than cutoff WITH active expense: NOT deleted")
    void cleanup_UnverifiedLocalUser_WithActiveExpense_NotDeleted() {
        User user = createUser("Old Unverified With Expense", "has-expense@example.com", AuthProvider.LOCAL, false, 14);
        createExpense(user, new BigDecimal("49.99"), false);

        int deletedCount = userService.cleanupUnverifiedUsers();

        assertThat(deletedCount).isEqualTo(0);
        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    @Test
    @DisplayName("2. Unverified LOCAL user older than cutoff with ONLY soft-deleted expense: NOT deleted")
    void cleanup_UnverifiedLocalUser_WithSoftDeletedExpense_NotDeleted() {
        User user = createUser("Old Unverified Soft Deleted", "soft-deleted-expense@example.com", AuthProvider.LOCAL, false, 14);
        createExpense(user, new BigDecimal("19.99"), true);

        // Verify native table has the soft-deleted expense
        Integer expenseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE user_id = ? AND is_deleted = true",
                Integer.class,
                user.getId()
        );
        assertThat(expenseCount).isEqualTo(1);

        int deletedCount = userService.cleanupUnverifiedUsers();

        assertThat(deletedCount).isEqualTo(0);
        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    @Test
    @DisplayName("3. Unverified LOCAL user older than cutoff with NO expenses: deleted along with verification and refresh tokens")
    void cleanup_UnverifiedLocalUser_NoExpenses_DeletedWithTokens() {
        User user = createUser("Stale Cleanable User", "stale@example.com", AuthProvider.LOCAL, false, 14);

        // Associate verification token
        EmailVerificationToken token = emailVerificationTokenRepository.save(EmailVerificationToken.builder()
                .user(user)
                .tokenHash("test-token-hash-12345")
                .expiresAt(Instant.now().plusSeconds(3600))
                .createdAt(Instant.now())
                .used(false)
                .build());

        // Associate refresh token
        RefreshToken refreshToken = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token("test-refresh-token-67890")
                .expiryDate(Instant.now().plusSeconds(86400))
                .revoked(false)
                .build());

        int deletedCount = userService.cleanupUnverifiedUsers();

        assertThat(deletedCount).isEqualTo(1);
        assertThat(userRepository.findById(user.getId())).isEmpty();
        assertThat(emailVerificationTokenRepository.findByTokenHash("test-token-hash-12345")).isEmpty();
        assertThat(refreshTokenRepository.findByToken("test-refresh-token-67890")).isEmpty();
    }

    @Test
    @DisplayName("4. Unverified LOCAL user newer than cutoff: NOT deleted")
    void cleanup_UnverifiedLocalUser_NewerThanCutoff_NotDeleted() {
        User recentUser = createUser("Recent Unverified", "recent@example.com", AuthProvider.LOCAL, false, 2);

        int deletedCount = userService.cleanupUnverifiedUsers();

        assertThat(deletedCount).isEqualTo(0);
        assertThat(userRepository.findById(recentUser.getId())).isPresent();
    }

    @Test
    @DisplayName("5. Verified LOCAL user older than cutoff: NOT deleted")
    void cleanup_VerifiedLocalUser_OlderThanCutoff_NotDeleted() {
        User verifiedUser = createUser("Verified Local", "verified.local@example.com", AuthProvider.LOCAL, true, 20);

        int deletedCount = userService.cleanupUnverifiedUsers();

        assertThat(deletedCount).isEqualTo(0);
        assertThat(userRepository.findById(verifiedUser.getId())).isPresent();
    }

    @Test
    @DisplayName("6. GOOGLE user older than cutoff without expenses: NOT deleted")
    void cleanup_GoogleUser_OlderThanCutoff_NotDeleted() {
        User googleUser = createUser("Google User", "google.user@example.com", AuthProvider.GOOGLE, false, 20);

        int deletedCount = userService.cleanupUnverifiedUsers();

        assertThat(deletedCount).isEqualTo(0);
        assertThat(userRepository.findById(googleUser.getId())).isPresent();
    }

    @Test
    @DisplayName("7a. enabled=false: nothing deleted")
    void cleanup_WhenDisabled_DeletesNothing() {
        User user = createUser("Stale User Disabled Mode", "disabled-mode@example.com", AuthProvider.LOCAL, false, 14);

        userService.setUnverifiedCleanupEnabled(false);
        userService.setUnverifiedCleanupDryRun(false);

        int deletedCount = userService.cleanupUnverifiedUsers();

        assertThat(deletedCount).isEqualTo(0);
        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    @Test
    @DisplayName("7b. enabled=true with dry-run=true: nothing deleted")
    void cleanup_WhenDryRun_DeletesNothing() {
        User user = createUser("Stale User Dry Run Mode", "dryrun-mode@example.com", AuthProvider.LOCAL, false, 14);

        userService.setUnverifiedCleanupEnabled(true);
        userService.setUnverifiedCleanupDryRun(true);

        int deletedCount = userService.cleanupUnverifiedUsers();

        assertThat(deletedCount).isEqualTo(0);
        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    @Test
    @DisplayName("7c. enabled=true with dry-run=false: eligible users deleted")
    void cleanup_WhenEnabledAndNotDryRun_DeletesEligibleUsers() {
        User user = createUser("Stale User Active Mode", "active-mode@example.com", AuthProvider.LOCAL, false, 14);

        userService.setUnverifiedCleanupEnabled(true);
        userService.setUnverifiedCleanupDryRun(false);

        int deletedCount = userService.cleanupUnverifiedUsers();

        assertThat(deletedCount).isEqualTo(1);
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }
}

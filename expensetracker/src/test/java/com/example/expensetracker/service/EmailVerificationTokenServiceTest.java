package com.example.expensetracker.service;

import com.example.expensetracker.config.EmailVerificationProperties;
import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.EmailVerificationToken;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.exception.InvalidVerificationTokenException;
import com.example.expensetracker.exception.ResendCooldownException;
import com.example.expensetracker.repository.EmailVerificationTokenRepository;
import com.example.expensetracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationTokenServiceTest {

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationProperties properties;

    @InjectMocks
    private EmailVerificationTokenService tokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Alex Doe")
                .email("alex@example.com")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(false)
                .build();
    }

    @Test
    @DisplayName("Should generate secure token, store only hash in DB, and return raw token")
    void createVerificationToken_StoresHashAndReturnsRaw() {
        when(properties.getExpirationMinutes()).thenReturn(30L);
        when(tokenRepository.findByUserIdAndUsedFalse(1L)).thenReturn(Collections.emptyList());

        String rawToken = tokenService.createVerificationToken(testUser);

        assertThat(rawToken).isNotNull();
        assertThat(rawToken).hasSize(64); // 32 hex bytes = 64 characters

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository, times(1)).save(tokenCaptor.capture());

        EmailVerificationToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUser()).isEqualTo(testUser);
        assertThat(savedToken.isUsed()).isFalse();
        assertThat(savedToken.getTokenHash()).isNotEqualTo(rawToken);
        assertThat(savedToken.getTokenHash()).isEqualTo(EmailVerificationTokenService.hashToken(rawToken));
    }

    @Test
    @DisplayName("Should invalidate any active tokens when creating a new token")
    void createVerificationToken_InvalidatesOlderActiveTokens() {
        when(properties.getExpirationMinutes()).thenReturn(30L);

        EmailVerificationToken oldToken = EmailVerificationToken.builder()
                .id(10L)
                .user(testUser)
                .tokenHash("old-hash")
                .used(false)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .createdAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();

        when(tokenRepository.findByUserIdAndUsedFalse(1L)).thenReturn(List.of(oldToken));

        String rawToken = tokenService.createVerificationToken(testUser);

        assertThat(rawToken).isNotNull();
        assertThat(oldToken.isUsed()).isTrue();
        verify(tokenRepository, times(1)).saveAll(List.of(oldToken));
    }

    @Test
    @DisplayName("Should successfully verify account with valid token")
    void verifyToken_ValidToken_Success() {
        String rawToken = "my-valid-raw-token-12345";
        String tokenHash = EmailVerificationTokenService.hashToken(rawToken);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(20L)
                .user(testUser)
                .tokenHash(tokenHash)
                .used(false)
                .expiresAt(Instant.now().plus(20, ChronoUnit.MINUTES))
                .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build();

        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(token));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        User verifiedUser = tokenService.verifyToken(rawToken);

        assertThat(verifiedUser).isNotNull();
        assertThat(token.isUsed()).isTrue();
        assertThat(testUser.isEmailVerified()).isTrue();
        verify(tokenRepository, times(1)).save(token);
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    @DisplayName("Should throw InvalidVerificationTokenException when token does not exist")
    void verifyToken_NonExistentToken_ThrowsException() {
        String rawToken = "non-existent-token";
        String tokenHash = EmailVerificationTokenService.hashToken(rawToken);

        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.verifyToken(rawToken))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessageContaining("Invalid verification token");
    }

    @Test
    @DisplayName("Should throw InvalidVerificationTokenException when token is already used")
    void verifyToken_AlreadyUsedToken_ThrowsException() {
        String rawToken = "already-used-raw-token";
        String tokenHash = EmailVerificationTokenService.hashToken(rawToken);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(21L)
                .user(testUser)
                .tokenHash(tokenHash)
                .used(true)
                .expiresAt(Instant.now().plus(20, ChronoUnit.MINUTES))
                .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build();

        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> tokenService.verifyToken(rawToken))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    @DisplayName("Should throw InvalidVerificationTokenException when token is expired")
    void verifyToken_ExpiredToken_ThrowsException() {
        String rawToken = "expired-raw-token";
        String tokenHash = EmailVerificationTokenService.hashToken(rawToken);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(22L)
                .user(testUser)
                .tokenHash(tokenHash)
                .used(false)
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .createdAt(Instant.now().minus(35, ChronoUnit.MINUTES))
                .build();

        when(tokenRepository.findByTokenHashWithLock(tokenHash)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> tokenService.verifyToken(rawToken))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Should enforce resend cooldown when requested within cooldown window")
    void createResendVerificationToken_WithinCooldown_ThrowsException() {
        when(properties.getResendCooldownSeconds()).thenReturn(60L);

        EmailVerificationToken recentToken = EmailVerificationToken.builder()
                .id(30L)
                .user(testUser)
                .tokenHash("recent-hash")
                .used(false)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .createdAt(Instant.now().minus(20, ChronoUnit.SECONDS)) // Created 20 seconds ago (< 60s)
                .build();

        when(tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(recentToken));

        assertThatThrownBy(() -> tokenService.createResendVerificationToken(testUser))
                .isInstanceOf(ResendCooldownException.class)
                .hasMessageContaining("Please wait");
    }

    @Test
    @DisplayName("Should allow resend when cooldown period has passed")
    void createResendVerificationToken_AfterCooldown_Success() {
        when(properties.getResendCooldownSeconds()).thenReturn(60L);
        when(properties.getExpirationMinutes()).thenReturn(30L);

        EmailVerificationToken olderToken = EmailVerificationToken.builder()
                .id(31L)
                .user(testUser)
                .tokenHash("older-hash")
                .used(false)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .createdAt(Instant.now().minus(75, ChronoUnit.SECONDS)) // Created 75 seconds ago (> 60s)
                .build();

        when(tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(olderToken));
        when(tokenRepository.findByUserIdAndUsedFalse(1L)).thenReturn(List.of(olderToken));

        String rawToken = tokenService.createResendVerificationToken(testUser);

        assertThat(rawToken).isNotNull();
        verify(tokenRepository, times(1)).save(any(EmailVerificationToken.class));
    }

    @Test
    @DisplayName("Concurrent verification attempts: only the first caller can claim the token, subsequent caller is rejected")
    void verifyToken_ConcurrentAttempts_OnlyOneSucceeds() throws Exception {
        String rawToken = "concurrent-token-test-123";
        String tokenHash = EmailVerificationTokenService.hashToken(rawToken);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(40L)
                .user(testUser)
                .tokenHash(tokenHash)
                .used(false)
                .expiresAt(Instant.now().plus(20, ChronoUnit.MINUTES))
                .createdAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();

        // Simulate database locking: first invocation returns unused token, second invocation sees updated used token
        when(tokenRepository.findByTokenHashWithLock(tokenHash))
                .thenReturn(Optional.of(token));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // First verification succeeds
        User verifiedUser = tokenService.verifyToken(rawToken);
        assertThat(verifiedUser).isNotNull();
        assertThat(token.isUsed()).isTrue();

        // Second verification attempt on the now-used token fails immediately
        assertThatThrownBy(() -> tokenService.verifyToken(rawToken))
                .isInstanceOf(InvalidVerificationTokenException.class)
                .hasMessageContaining("already been used");

        verify(userRepository, times(1)).save(testUser);
    }
}

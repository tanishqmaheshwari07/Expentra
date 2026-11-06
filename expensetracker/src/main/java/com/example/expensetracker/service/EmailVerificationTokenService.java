package com.example.expensetracker.service;

import com.example.expensetracker.config.EmailVerificationProperties;
import com.example.expensetracker.entity.EmailVerificationToken;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.exception.InvalidVerificationTokenException;
import com.example.expensetracker.exception.ResendCooldownException;
import com.example.expensetracker.repository.EmailVerificationTokenRepository;
import com.example.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationTokenService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailVerificationProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically secure token, stores only its SHA-256 hash in DB,
     * and returns the raw unhashed token for email delivery.
     */
    @Transactional
    public String createVerificationToken(User user) {
        // Invalidate any previously active tokens for this user
        invalidateActiveTokensForUser(user);

        String rawToken = generateSecureRandomToken();
        String tokenHash = hashToken(rawToken);

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(properties.getExpirationMinutes(), ChronoUnit.MINUTES))
                .used(false)
                .createdAt(Instant.now())
                .build();

        tokenRepository.save(verificationToken);
        log.info("Created verification token for user ID: {}", user.getId());

        return rawToken;
    }

    /**
     * Checks cooldown and creates a new verification token for resending.
     */
    @Transactional
    public String createResendVerificationToken(User user) {
        tokenRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId()).ifPresent(latestToken -> {
            long secondsSinceCreation = Duration.between(latestToken.getCreatedAt(), Instant.now()).getSeconds();
            if (secondsSinceCreation < properties.getResendCooldownSeconds()) {
                long waitSeconds = properties.getResendCooldownSeconds() - secondsSinceCreation;
                log.warn("Resend cooldown active for user ID: {}. Seconds remaining: {}", user.getId(), waitSeconds);
                throw new ResendCooldownException("Please wait " + waitSeconds + " seconds before requesting another verification email.");
            }
        });

        return createVerificationToken(user);
    }

    /**
     * Atomically validates token hash, marks token used, and sets user emailVerified = true.
     */
    @Transactional
    public User verifyToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidVerificationTokenException("Verification token is required");
        }

        String tokenHash = hashToken(rawToken.trim());

        EmailVerificationToken token = tokenRepository.findByTokenHashWithLock(tokenHash)
                .orElseThrow(() -> new InvalidVerificationTokenException("Invalid verification token"));

        if (token.isUsed()) {
            throw new InvalidVerificationTokenException("Verification token has already been used");
        }

        if (token.isExpired()) {
            throw new InvalidVerificationTokenException("Verification token has expired. Please request a new one.");
        }

        token.setUsed(true);
        tokenRepository.save(token);

        User user = token.getUser();
        user.setEmailVerified(true);
        User savedUser = userRepository.save(user);

        log.info("Successfully verified email for user ID: {}", savedUser.getId());
        return savedUser;
    }

    @Transactional
    public void invalidateActiveTokensForUser(User user) {
        List<EmailVerificationToken> activeTokens = tokenRepository.findByUserIdAndUsedFalse(user.getId());
        for (EmailVerificationToken token : activeTokens) {
            token.setUsed(true);
        }
        if (!activeTokens.isEmpty()) {
            tokenRepository.saveAll(activeTokens);
            log.info("Invalidated {} prior verification tokens for user ID: {}", activeTokens.size(), user.getId());
        }
    }

    public String generateSecureRandomToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}

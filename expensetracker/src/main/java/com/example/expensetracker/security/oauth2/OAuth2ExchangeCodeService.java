package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.config.OAuth2Properties;
import com.example.expensetracker.dto.ConsumedOAuthCode;
import com.example.expensetracker.entity.OAuthExchangeCode;
import com.example.expensetracker.repository.OAuthExchangeCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2ExchangeCodeService {

    private final OAuth2Properties oAuth2Properties;
    private final OAuthExchangeCodeRepository exchangeCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically random, 256-bit URL-safe, single-use exchange code,
     * stores its SHA-256 hash in the database bound to user ID and browser binder hash,
     * and returns the raw code once.
     */
    @Transactional
    public String createExchangeCode(Long userId, String binder) {
        byte[] randomBytes = new byte[32]; // 256-bit random
        secureRandom.nextBytes(randomBytes);
        String rawCode = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        String codeHash = sha256Hex(rawCode);
        String binderHash = sha256Hex(binder != null ? binder : "");

        Instant expiresAt = Instant.now().plusSeconds(oAuth2Properties.getExchangeCodeExpirationSeconds());

        OAuthExchangeCode exchangeCode = OAuthExchangeCode.builder()
                .codeHash(codeHash)
                .userId(userId)
                .binderHash(binderHash)
                .expiresAt(expiresAt)
                .build();

        exchangeCodeRepository.save(exchangeCode);
        log.debug("Persisted one-time OAuth exchange code hash for user ID: {}, expires at: {}", userId, expiresAt);

        return rawCode;
    }

    /**
     * Overload for backwards compatibility and tests that do not supply a browser binder.
     */
    @Transactional
    public String createExchangeCode(Long userId, String email, String binder) {
        return createExchangeCode(userId, binder);
    }

    /**
     * Atomically consumes a one-time exchange code and validates browser binder binding.
     * Uses a single race-free atomic query (DELETE ... RETURNING).
     */
    @Transactional
    public Optional<Long> consumeExchangeCode(String rawCode, String binder) {
        if (rawCode == null || rawCode.isBlank()) {
            return Optional.empty();
        }

        String codeHash = sha256Hex(rawCode.trim());

        // Atomically remove and return entry in a single statement
        Optional<ConsumedOAuthCode> consumedOpt = exchangeCodeRepository.consumeCodeAtomically(codeHash);
        if (consumedOpt.isEmpty()) {
            log.warn("OAuth exchange code not found, already consumed, or expired");
            return Optional.empty();
        }

        ConsumedOAuthCode consumed = consumedOpt.get();

        // If a binder is stored, verify using constant-time comparison
        String expectedBinderHash = sha256Hex(binder != null ? binder : "");
        byte[] expectedBytes = expectedBinderHash.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = consumed.binderHash().getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            log.warn("OAuth exchange code binder mismatch for user ID {}", consumed.userId());
            return Optional.empty();
        }

        log.info("Successfully consumed one-time OAuth exchange code for user ID: {}", consumed.userId());
        return Optional.of(consumed.userId());
    }

    /**
     * Overload for code-only consumption (defaults binder to empty).
     */
    @Transactional
    public Optional<Long> consumeExchangeCode(String rawCode) {
        return consumeExchangeCode(rawCode, null);
    }

    /**
     * Periodically cleans up expired exchange code rows from the database.
     */
    @Scheduled(fixedDelayString = "${app.oauth2.cleanup-delay:300000}")
    @Transactional
    public void cleanupExpiredCodes() {
        int deleted = exchangeCodeRepository.deleteExpiredCodes(Instant.now());
        if (deleted > 0) {
            log.debug("Cleaned up {} expired OAuth exchange code rows", deleted);
        }
    }

    /**
     * Computes SHA-256 hex digest of input string.
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}

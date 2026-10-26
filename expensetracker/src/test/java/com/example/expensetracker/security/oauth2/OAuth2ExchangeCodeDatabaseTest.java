package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.config.OAuth2Properties;
import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.repository.OAuthExchangeCodeRepository;
import com.example.expensetracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OAuth2ExchangeCodeDatabaseTest {

    @Autowired
    private OAuth2ExchangeCodeService exchangeCodeService;

    @Autowired
    private OAuthExchangeCodeRepository exchangeCodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OAuth2Properties oAuth2Properties;

    private User testUser;

    @BeforeEach
    void setUp() {
        exchangeCodeRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .name("Test OAuth User")
                .email("test.oauth@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-test-id")
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(true)
                .build());
    }

    @Test
    @DisplayName("Code works once and single-use constraint prevents second use")
    void code_WorksOnce_SecondUseFails() {
        String binder = "test-browser-binder-123";
        String rawCode = exchangeCodeService.createExchangeCode(testUser.getId(), binder);

        assertThat(rawCode).isNotBlank();

        // 1. First consume succeeds
        Optional<Long> firstResult = exchangeCodeService.consumeExchangeCode(rawCode, binder);
        assertThat(firstResult).isPresent();
        assertThat(firstResult.get()).isEqualTo(testUser.getId());

        // 2. Second consume fails
        Optional<Long> secondResult = exchangeCodeService.consumeExchangeCode(rawCode, binder);
        assertThat(secondResult).isEmpty();
    }

    @Test
    @DisplayName("Expired exchange code fails to consume")
    void expiredCode_FailsToConsume() {
        OAuth2Properties expiredProps = new OAuth2Properties();
        expiredProps.setExchangeCodeExpirationSeconds(-5L); // Expired 5 seconds ago

        OAuth2ExchangeCodeService expiredService = new OAuth2ExchangeCodeService(expiredProps, exchangeCodeRepository);
        String rawCode = expiredService.createExchangeCode(testUser.getId(), "test-binder");

        Optional<Long> result = expiredService.consumeExchangeCode(rawCode, "test-binder");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Code survives app restart (service recreation)")
    void code_SurvivesAppRestart() {
        String binder = "persistent-binder";
        String rawCode = exchangeCodeService.createExchangeCode(testUser.getId(), binder);

        // Recreate the service instance to simulate application restart
        OAuth2ExchangeCodeService restartedService = new OAuth2ExchangeCodeService(oAuth2Properties, exchangeCodeRepository);

        Optional<Long> result = restartedService.consumeExchangeCode(rawCode, binder);
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("Two simultaneous exchanges of the same code produce exactly one success")
    void concurrentExchange_ProducesExactlyOneSuccess() throws Exception {
        String binder = "concurrent-binder";
        String rawCode = exchangeCodeService.createExchangeCode(testUser.getId(), binder);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Optional<Long>>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return exchangeCodeService.consumeExchangeCode(rawCode, binder);
            }));
        }

        // Fire both threads simultaneously
        startLatch.countDown();

        List<Optional<Long>> results = new ArrayList<>();
        for (Future<Optional<Long>> future : futures) {
            results.add(future.get());
        }

        executor.shutdown();

        long successCount = results.stream().filter(Optional::isPresent).count();
        long failureCount = results.stream().filter(Optional::isEmpty).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Scheduled cleanup deletes expired rows")
    void scheduledCleanup_DeletesExpiredRows() {
        OAuth2Properties expiredProps = new OAuth2Properties();
        expiredProps.setExchangeCodeExpirationSeconds(-10L);

        OAuth2ExchangeCodeService expiredService = new OAuth2ExchangeCodeService(expiredProps, exchangeCodeRepository);
        expiredService.createExchangeCode(testUser.getId(), "binder-1");
        expiredService.createExchangeCode(testUser.getId(), "binder-2");

        assertThat(exchangeCodeRepository.count()).isEqualTo(2);

        exchangeCodeService.cleanupExpiredCodes();

        assertThat(exchangeCodeRepository.count()).isEqualTo(0);
    }
}

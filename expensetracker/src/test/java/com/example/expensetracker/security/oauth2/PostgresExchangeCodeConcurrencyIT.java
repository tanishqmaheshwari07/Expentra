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
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 7 & Step 2 Acceptance:
 * Testcontainers PostgreSQL integration test verifying:
 * 1. Flyway cleanly applies all migrations (V1 through V5) on real PostgreSQL.
 * 2. Native single-statement DELETE ... RETURNING atomic consumption on PostgreSQL.
 * 3. Two concurrent exchanges of the same code produce exactly one success under PostgreSQL.
 */
@Testcontainers
@SpringBootTest
@EnabledIf("isDockerAvailable")
class PostgresExchangeCodeConcurrencyIT {

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("test_oauth")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (isDockerAvailable() && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        }
    }

    @Autowired
    private OAuth2ExchangeCodeService exchangeCodeService;

    @Autowired
    private OAuthExchangeCodeRepository exchangeCodeRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        exchangeCodeRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .name("Postgres Test User")
                .email("postgres.user@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-pg-sub")
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(true)
                .build());
    }

    @Test
    @DisplayName("Real PostgreSQL: Two concurrent exchanges of one code give exactly one success via DELETE ... RETURNING")
    void concurrentExchanges_OnRealPostgres_ExactlyOneSuccess() throws Exception {
        String binder = "pg-browser-binder-123";
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
}

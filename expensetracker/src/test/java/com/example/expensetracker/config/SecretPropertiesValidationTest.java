package com.example.expensetracker.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SecretPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ValidationAutoConfiguration.class,
                    ConfigurationPropertiesAutoConfiguration.class
            ));

    @Test
    @DisplayName("Context fails to start when JWT secret is missing or blank")
    void contextFailsWhenJwtSecretIsMissing() {
        contextRunner
                .withUserConfiguration(JwtProperties.class)
                .withPropertyValues("application.security.jwt.secret-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("application.security.jwt.secret-key")
                            .hasStackTraceContaining("must not be blank");
                });
    }

    @Test
    @DisplayName("Context fails to start when JWT secret is too short (< 32 chars)")
    void contextFailsWhenJwtSecretIsTooShort() {
        contextRunner
                .withUserConfiguration(JwtProperties.class)
                .withPropertyValues("application.security.jwt.secret-key=short-secret-key-less-than-32")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("application.security.jwt.secret-key")
                            .hasStackTraceContaining("must be at least 32 characters");
                });
    }

    @Test
    @DisplayName("Context starts successfully when valid JWT secret is present (>= 32 chars)")
    void contextStartsWhenValidJwtSecretIsPresent() {
        contextRunner
                .withUserConfiguration(JwtProperties.class)
                .withPropertyValues("application.security.jwt.secret-key=test-fake-jwt-secret-with-at-least-32-chars-long")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtProperties.class);
                    JwtProperties properties = context.getBean(JwtProperties.class);
                    assertThat(properties.getSecretKey()).isEqualTo("test-fake-jwt-secret-with-at-least-32-chars-long");
                });
    }

    @Test
    @DisplayName("Context fails to start when OAuth2 cookie secret is missing or blank")
    void contextFailsWhenOAuth2CookieSecretIsMissing() {
        contextRunner
                .withUserConfiguration(OAuth2Properties.class)
                .withPropertyValues("application.oauth2.cookie-secret=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("application.oauth2.cookie-secret");
                });
    }

    @Test
    @DisplayName("Context fails to start when OAuth2 cookie secret is too short (< 32 chars)")
    void contextFailsWhenOAuth2CookieSecretIsTooShort() {
        contextRunner
                .withUserConfiguration(OAuth2Properties.class)
                .withPropertyValues("application.oauth2.cookie-secret=short-cookie-secret")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("application.oauth2.cookie-secret");
                });
    }

    @Test
    @DisplayName("Context starts successfully when valid OAuth2 cookie secret is present (>= 32 chars)")
    void contextStartsWhenValidOAuth2CookieSecretIsPresent() {
        contextRunner
                .withUserConfiguration(OAuth2Properties.class)
                .withPropertyValues("application.oauth2.cookie-secret=test-fake-cookie-secret-with-at-least-32-chars")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OAuth2Properties.class);
                    OAuth2Properties properties = context.getBean(OAuth2Properties.class);
                    assertThat(properties.getCookieSecret()).isEqualTo("test-fake-cookie-secret-with-at-least-32-chars");
                });
    }
}

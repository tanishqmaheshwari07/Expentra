package com.example.expensetracker.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "application.oauth2")
@Validated
@Getter
@Setter
public class OAuth2Properties {

    /**
     * Frontend redirect URL for successful OAuth2 authorization with one-time exchange code.
     */
    private String authorizedRedirectUri = "http://localhost:3000/oauth2/callback";

    /**
     * Frontend redirect URL when OAuth2 authorization fails.
     */
    private String failureRedirectUri = "http://localhost:3000/login?error=oauth_failed";

    /**
     * Lifespan of one-time exchange code in seconds (default: 300s / 5 minutes).
     */
    private long exchangeCodeExpirationSeconds = 300L;

    /**
     * Secret key for HMAC signing of OAuth2 authorization request cookies.
     * Must have NO default value in production and at least 32 characters.
     */
    @NotBlank(message = "application.oauth2.cookie-secret (OAUTH2_COOKIE_SECRET) must not be blank")
    @Size(min = 32, message = "application.oauth2.cookie-secret (OAUTH2_COOKIE_SECRET) must be at least 32 characters")
    private String cookieSecret;

    @PostConstruct
    public void validateProperties() {
        if (cookieSecret == null || cookieSecret.isBlank()) {
            throw new IllegalStateException("OAuth2 cookieSecret must be configured (has NO default value)");
        }
        if (cookieSecret.length() < 32) {
            throw new IllegalStateException("OAuth2 cookieSecret must be at least 32 characters");
        }
    }
}

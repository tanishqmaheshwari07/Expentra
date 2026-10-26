package com.example.expensetracker.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.oauth2")
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
     * Must have NO default value in production.
     */
    private String cookieSecret;

    @PostConstruct
    public void validateProperties() {
        if (cookieSecret == null || cookieSecret.isBlank()) {
            throw new IllegalStateException("OAuth2 cookieSecret must be configured (has NO default value)");
        }
    }
}

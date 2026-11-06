package com.example.expensetracker.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "application.security.jwt")
@Validated
@Getter
@Setter
public class JwtProperties {

    /**
     * Secret key for signing JWT tokens (HMAC-SHA256, minimum 256 bits / 32 chars).
     * Must be provided via JWT_SECRET environment variable; no default value.
     */
    @NotBlank(message = "application.security.jwt.secret-key (JWT_SECRET) must not be blank")
    @Size(min = 32, message = "application.security.jwt.secret-key (JWT_SECRET) must be at least 32 characters")
    private String secretKey;

    /**
     * Access token validity in milliseconds (default: 15 minutes).
     */
    private long accessTokenExpirationMs = 15 * 60 * 1000L;

    /**
     * Refresh token validity in milliseconds (default: 7 days).
     */
    private long refreshTokenExpirationMs = 7 * 24 * 60 * 60 * 1000L;

    /**
     * Whether refresh token cookie requires HTTPS (Secure flag). Default false for local dev.
     */
    private boolean cookieSecure = false;

    /**
     * SameSite attribute for the refresh token cookie (Lax, Strict, None). Default: Lax.
     */
    private String cookieSameSite = "Lax";
}

package com.example.expensetracker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.mail")
@Getter
@Setter
public class EmailVerificationProperties {

    /**
     * Frontend base URL for verification links (e.g. http://localhost:3000).
     */
    private String frontendUrl = "http://localhost:3000";

    /**
     * From email address for system emails.
     */
    private String from = "noreply@expensetracker.com";

    /**
     * Lifespan of email verification token in minutes (default: 30 minutes).
     */
    private long expirationMinutes = 30L;

    /**
     * Minimum cooldown between resend requests in seconds (default: 60 seconds).
     */
    private long resendCooldownSeconds = 60L;

    public long getVerificationExpirationMinutes() {
        return expirationMinutes;
    }

    public void setVerificationExpirationMinutes(long verificationExpirationMinutes) {
        this.expirationMinutes = verificationExpirationMinutes;
    }
}

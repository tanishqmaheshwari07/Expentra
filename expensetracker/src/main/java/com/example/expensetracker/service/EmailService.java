package com.example.expensetracker.service;

import com.example.expensetracker.config.EmailVerificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final EmailVerificationProperties properties;

    public void sendVerificationEmail(String toEmail, String userName, String rawToken) {
        try {
            String verificationUrl = properties.getFrontendUrl().replaceAll("/+$", "") + "/verify-email?token=" + rawToken;

            Context context = new Context();
            context.setVariable("userName", userName != null ? userName : "User");
            context.setVariable("verificationUrl", verificationUrl);
            context.setVariable("expirationMinutes", properties.getExpirationMinutes());

            String htmlContent = templateEngine.process("verification-email", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setTo(toEmail);
            helper.setFrom(properties.getFrom());
            helper.setSubject("Verify your ExpenseTracker account");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Verification email successfully sent to '{}'", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to compose or send verification email to '{}'", toEmail, e);
            throw new RuntimeException("Failed to send verification email. Please try again later.", e);
        } catch (Exception e) {
            log.error("Unexpected error sending verification email to '{}'", toEmail, e);
            throw new RuntimeException("Failed to send verification email. Please try again later.", e);
        }
    }
}

package com.example.expensetracker.service;

import com.example.expensetracker.config.JwtProperties;
import com.example.expensetracker.dto.AuthResponse;
import com.example.expensetracker.dto.LoginRequest;
import com.example.expensetracker.dto.OAuth2ExchangeRequest;
import com.example.expensetracker.dto.PasswordUpdateRequest;
import com.example.expensetracker.dto.ProfileUpdateRequest;
import com.example.expensetracker.dto.RefreshTokenRequest;
import com.example.expensetracker.dto.UserRequest;
import com.example.expensetracker.dto.UserResponse;
import com.example.expensetracker.dto.ResendVerificationRequest;
import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.RefreshToken;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.exception.EmailNotVerifiedException;
import com.example.expensetracker.exception.UnauthorizedAccessException;
import com.example.expensetracker.exception.UserAlreadyExistsException;
import com.example.expensetracker.exception.UserNotFoundException;
import com.example.expensetracker.mapper.UserMapper;
import com.example.expensetracker.repository.EmailVerificationTokenRepository;
import com.example.expensetracker.repository.RefreshTokenRepository;
import com.example.expensetracker.repository.UserRepository;
import com.example.expensetracker.security.oauth2.OAuth2ExchangeCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.expensetracker.dto.AdminUserResponse;
import com.example.expensetracker.entity.Expense;
import com.example.expensetracker.repository.ExpenseRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final ExpenseRepository expenseRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final OAuth2ExchangeCodeService exchangeCodeService;
    private final EmailVerificationTokenService emailVerificationTokenService;
    private final EmailService emailService;

    @Value("${application.users.unverified-cleanup-days:7}")
    private int unverifiedCleanupDays = 7;

    public void setUnverifiedCleanupDays(int unverifiedCleanupDays) {
        this.unverifiedCleanupDays = unverifiedCleanupDays;
    }

    public UserResponse createUser(UserRequest userRequest) {
        String normalizedEmail = userRequest.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException("User with email " + userRequest.getEmail() + " already exists");
        }

        User user = User.builder()
                .name(userRequest.getName().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(userRequest.getPassword()))
                .authProvider(AuthProvider.LOCAL)
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Registered new local user with ID: {}", savedUser.getId());

        // Generate verification token and send verification email
        String rawToken = emailVerificationTokenService.createVerificationToken(savedUser);
        emailService.sendVerificationEmail(savedUser.getEmail(), savedUser.getName(), rawToken);

        return UserMapper.toResponse(savedUser);
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.isActive()) {
            throw new UnauthorizedAccessException("Account is disabled. Please contact support.");
        }

        if (user.getAuthProvider() == AuthProvider.LOCAL && !user.isEmailVerified()) {
            throw new EmailNotVerifiedException("EMAIL_NOT_VERIFIED: Please verify your email before logging in.");
        }

        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole());
        RefreshToken refreshToken = createRefreshToken(user);

        log.info("User '{}' logged in successfully", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresInMs(jwtProperties.getAccessTokenExpirationMs())
                .user(UserMapper.toResponse(user))
                .build();
    }

    public void verifyEmail(String rawToken) {
        emailVerificationTokenService.verifyToken(rawToken);
    }

    public void resendVerificationEmail(ResendVerificationRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            return;
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);

        if (userOptional.isEmpty()) {
            log.info("Resend verification requested for non-existent email '{}'. Silently ignoring to prevent enumeration.", normalizedEmail);
            return;
        }

        User user = userOptional.get();

        if (user.getAuthProvider() != AuthProvider.LOCAL) {
            log.info("Resend verification requested for non-local OAuth user '{}'. Ignoring.", normalizedEmail);
            return;
        }

        if (user.isEmailVerified()) {
            log.info("Resend verification requested for already-verified user '{}'. Ignoring.", normalizedEmail);
            return;
        }

        String rawToken = emailVerificationTokenService.createResendVerificationToken(user);
        emailService.sendVerificationEmail(user.getEmail(), user.getName(), rawToken);
        log.info("Resent verification email to user ID: {}", user.getId());
    }

    public AuthResponse exchangeOAuth2Code(OAuth2ExchangeRequest request) {
        return exchangeOAuth2Code(request, null);
    }

    public AuthResponse exchangeOAuth2Code(OAuth2ExchangeRequest request, String binderCookie) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw new UnauthorizedAccessException("Exchange code is required");
        }

        if (binderCookie == null || binderCookie.isBlank()) {
            log.warn("OAuth2 exchange rejected: Missing oauth_binder cookie");
            throw new UnauthorizedAccessException("Invalid or expired OAuth2 exchange code");
        }

        Long userId = exchangeCodeService.consumeExchangeCode(request.getCode(), binderCookie)
                .orElseThrow(() -> new UnauthorizedAccessException("Invalid or expired OAuth2 exchange code"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found for OAuth2 exchange code"));

        if (!user.isActive()) {
            throw new UnauthorizedAccessException("Account is disabled. Please contact support.");
        }

        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole());
        RefreshToken refreshToken = createRefreshToken(user);

        log.info("OAuth2 exchange code consumed successfully for user '{}'", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresInMs(jwtProperties.getAccessTokenExpirationMs())
                .user(UserMapper.toResponse(user))
                .build();
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new UnauthorizedAccessException("Refresh token is required");
        }
        return refreshTokenByTokenString(request.getRefreshToken());
    }

    public AuthResponse refreshTokenByTokenString(String tokenString) {
        RefreshToken token = refreshTokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new UnauthorizedAccessException("Invalid or revoked refresh token"));

        if (token.isRevoked() || token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new UnauthorizedAccessException("Refresh token has expired or was revoked. Please sign in again.");
        }

        User user = token.getUser();
        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole());

        // Rotate Refresh Token
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpirationMs()));
        RefreshToken rotatedToken = refreshTokenRepository.save(token);

        log.info("Rotated refresh token for user '{}'", user.getEmail());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(rotatedToken.getToken())
                .tokenType("Bearer")
                .expiresInMs(jwtProperties.getAccessTokenExpirationMs())
                .user(UserMapper.toResponse(user))
                .build();
    }

    public void logoutByRefreshToken(String tokenString) {
        if (tokenString != null && !tokenString.isBlank()) {
            refreshTokenRepository.findByToken(tokenString).ifPresent(token -> {
                refreshTokenRepository.delete(token);
                log.info("Revoked refresh token for user '{}'", token.getUser().getEmail());
            });
        }
    }

    private RefreshToken createRefreshToken(User user) {
        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpirationMs()))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User currentUser = getCurrentUser();
        if (!currentUser.getId().equals(id) && currentUser.getRole() != Role.ROLE_ADMIN) {
            throw new UnauthorizedAccessException("You are not authorized to view another user's profile");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User with id " + id + " not found"));

        return UserMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUserProfile() {
        return UserMapper.toResponse(getCurrentUser());
    }

    public UserResponse updateUser(Long id, ProfileUpdateRequest request) {
        User currentUser = getCurrentUser();
        if (!currentUser.getId().equals(id) && currentUser.getRole() != Role.ROLE_ADMIN) {
            throw new UnauthorizedAccessException("You are not authorized to update another user's profile");
        }

        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User with id " + id + " not found"));

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // If email changed, check for uniqueness
        if (!existingUser.getEmail().equalsIgnoreCase(normalizedEmail)
                && userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException("Email " + request.getEmail() + " is already taken");
        }

        existingUser.setName(request.getName().trim());
        existingUser.setEmail(normalizedEmail);

        User updatedUser = userRepository.save(existingUser);
        log.info("Updated profile for user ID: {}", id);
        return UserMapper.toResponse(updatedUser);
    }

    public void changePassword(Long id, PasswordUpdateRequest request) {
        User currentUser = getCurrentUser();
        if (!currentUser.getId().equals(id) && currentUser.getRole() != Role.ROLE_ADMIN) {
            throw new UnauthorizedAccessException("You are not authorized to update another user's password");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User with id " + id + " not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password successfully updated for user ID: {}", id);
    }

    public void deleteUser(Long id) {
        User currentUser = getCurrentUser();
        if (!currentUser.getId().equals(id) && currentUser.getRole() != Role.ROLE_ADMIN) {
            throw new UnauthorizedAccessException("You are not authorized to delete another user's profile");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User with id " + id + " not found"));

        refreshTokenRepository.deleteByUser(user);
        userRepository.delete(user);
        log.info("Deleted user with ID: {}", id);
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(user -> {
            List<Expense> userExpenses = expenseRepository.findByUserId(user.getId());
            BigDecimal totalSpend = userExpenses.stream()
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int count = userExpenses.size();
            BigDecimal avgTicket = count > 0
                    ? totalSpend.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            int activeCategories = (int) userExpenses.stream()
                    .map(Expense::getCategory)
                    .distinct()
                    .count();

            return AdminUserResponse.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .isActive(user.isActive())
                    .createdAt(user.getCreatedAt())
                    .updatedAt(user.getUpdatedAt())
                    .totalSpend(totalSpend)
                    .transactionsCount(count)
                    .avgTicket(avgTicket)
                    .activeCategories(activeCategories)
                    .build();
        }).toList();
    }

    public UserResponse updateUserRole(Long id, Role newRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User with id " + id + " not found"));

        user.setRole(newRole);
        User savedUser = userRepository.save(user);
        log.info("Updated role for user ID {} to {}", id, newRole);
        return UserMapper.toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new UnauthorizedAccessException("User is not authenticated");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User with email " + email + " not found"));
    }

    /**
     * Step 6: Scheduled cleanup of unverified LOCAL user accounts older than configurable days (default 7).
     * Deletes orphaned email verification tokens and refresh tokens prior to removing the user.
     */
    @Scheduled(cron = "${application.users.unverified-cleanup-cron:0 0 2 * * *}")
    public int cleanupUnverifiedUsers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(unverifiedCleanupDays);
        List<User> expiredUsers = userRepository.findByAuthProviderAndEmailVerifiedFalseAndCreatedAtBefore(AuthProvider.LOCAL, cutoff);
        if (expiredUsers.isEmpty()) {
            return 0;
        }
        log.info("Found {} unverified local user accounts older than {} days (cutoff: {}). Cleaning up...",
                expiredUsers.size(), unverifiedCleanupDays, cutoff);
        for (User user : expiredUsers) {
            emailVerificationTokenRepository.deleteByUser(user);
            refreshTokenRepository.deleteByUser(user);
            userRepository.delete(user);
        }
        userRepository.flush();
        log.info("Finished cleaning up {} unverified local user accounts", expiredUsers.size());
        return expiredUsers.size();
    }
}

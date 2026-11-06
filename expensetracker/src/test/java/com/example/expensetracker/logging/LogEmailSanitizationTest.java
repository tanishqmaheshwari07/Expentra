package com.example.expensetracker.logging;

import com.example.expensetracker.config.JwtProperties;
import com.example.expensetracker.config.OAuth2Properties;
import com.example.expensetracker.dto.ExpenseRequest;
import com.example.expensetracker.dto.LoginRequest;
import com.example.expensetracker.dto.OAuth2ExchangeRequest;
import com.example.expensetracker.dto.ResendVerificationRequest;
import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.Category;
import com.example.expensetracker.entity.EmailVerificationToken;
import com.example.expensetracker.entity.Expense;
import com.example.expensetracker.entity.RefreshToken;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.repository.EmailVerificationTokenRepository;
import com.example.expensetracker.repository.ExpenseRepository;
import com.example.expensetracker.repository.RefreshTokenRepository;
import com.example.expensetracker.repository.UserRepository;
import com.example.expensetracker.security.oauth2.CustomOAuth2User;
import com.example.expensetracker.security.oauth2.OAuth2AuthenticationFailureHandler;
import com.example.expensetracker.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.example.expensetracker.security.oauth2.OAuth2ExchangeCodeService;
import com.example.expensetracker.service.EmailService;
import com.example.expensetracker.service.EmailVerificationTokenService;
import com.example.expensetracker.service.ExpenseService;
import com.example.expensetracker.service.JwtService;
import com.example.expensetracker.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class LogEmailSanitizationTest {

    private static final String LEAK_EMAIL = "leaktest@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private EmailVerificationTokenService emailVerificationTokenService;

    @Mock
    private OAuth2ExchangeCodeService exchangeCodeService;

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private UserService userService;

    private ExpenseService expenseService;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Leak Tester")
                .email(LEAK_EMAIL)
                .password("encoded_pass")
                .role(Role.ROLE_USER)
                .authProvider(AuthProvider.LOCAL)
                .isActive(true)
                .emailVerified(true)
                .build();

        expenseService = new ExpenseService(expenseRepository, userService);
    }

    @Test
    @DisplayName("Login path must not log full email address")
    void loginPath_DoesNotLogFullEmail(CapturedOutput output) {
        LoginRequest request = new LoginRequest();
        request.setEmail(LEAK_EMAIL);
        request.setPassword("Secret123!");

        when(userRepository.findByEmail(LEAK_EMAIL)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Secret123!", "encoded_pass")).thenReturn(true);
        when(jwtService.generateAccessToken(eq(LEAK_EMAIL), any())).thenReturn("mock-access-token");
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.login(request);

        assertThat(output.getAll()).doesNotContain(LEAK_EMAIL);
        assertThat(output.getAll()).contains("User ID: 1 logged in successfully");
    }

    @Test
    @DisplayName("OAuth exchange path must not log full email address")
    void oauthExchangePath_DoesNotLogFullEmail(CapturedOutput output) {
        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest();
        request.setCode("valid-exchange-code");

        when(exchangeCodeService.consumeExchangeCode("valid-exchange-code", "binder-cookie-val"))
                .thenReturn(Optional.of(1L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(eq(LEAK_EMAIL), any())).thenReturn("mock-access-token");
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.exchangeOAuth2Code(request, "binder-cookie-val");

        assertThat(output.getAll()).doesNotContain(LEAK_EMAIL);
        assertThat(output.getAll()).contains("OAuth2 exchange code consumed successfully for user ID: 1");
    }

    @Test
    @DisplayName("Refresh token path must not log full email address")
    void refreshTokenPath_DoesNotLogFullEmail(CapturedOutput output) {
        RefreshToken token = RefreshToken.builder()
                .id(10L)
                .token("current-refresh-token")
                .user(testUser)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("current-refresh-token")).thenReturn(Optional.of(token));
        when(jwtService.generateAccessToken(eq(LEAK_EMAIL), any())).thenReturn("new-access-token");
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.refreshTokenByTokenString("current-refresh-token");

        assertThat(output.getAll()).doesNotContain(LEAK_EMAIL);
        assertThat(output.getAll()).contains("Rotated refresh token for user ID: 1");
    }

    @Test
    @DisplayName("Logout path must not log full email address")
    void logoutPath_DoesNotLogFullEmail(CapturedOutput output) {
        RefreshToken token = RefreshToken.builder()
                .id(10L)
                .token("logout-token")
                .user(testUser)
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("logout-token")).thenReturn(Optional.of(token));

        userService.logoutByRefreshToken("logout-token");

        assertThat(output.getAll()).doesNotContain(LEAK_EMAIL);
        assertThat(output.getAll()).contains("Revoked refresh token for user ID: 1");
    }

    @Test
    @DisplayName("Resend verification for non-existent email must not log full email address")
    void resendVerification_NonExistentEmail_DoesNotLogFullEmail(CapturedOutput output) {
        when(userRepository.findByEmail(LEAK_EMAIL)).thenReturn(Optional.empty());

        userService.resendVerificationEmail(new ResendVerificationRequest(LEAK_EMAIL));

        assertThat(output.getAll()).doesNotContain(LEAK_EMAIL);
        assertThat(output.getAll()).contains("l***@example.com");
    }

    @Test
    @DisplayName("Resend verification for non-local OAuth user must not log full email address")
    void resendVerification_NonLocalUser_DoesNotLogFullEmail(CapturedOutput output) {
        User googleUser = User.builder()
                .id(2L)
                .email(LEAK_EMAIL)
                .authProvider(AuthProvider.GOOGLE)
                .build();
        when(userRepository.findByEmail(LEAK_EMAIL)).thenReturn(Optional.of(googleUser));

        userService.resendVerificationEmail(new ResendVerificationRequest(LEAK_EMAIL));

        assertThat(output.getAll()).doesNotContain(LEAK_EMAIL);
        assertThat(output.getAll()).contains("Resend verification requested for non-local OAuth user ID: 2. Ignoring.");
    }

    @Test
    @DisplayName("Resend verification for already-verified user must not log full email address")
    void resendVerification_AlreadyVerifiedUser_DoesNotLogFullEmail(CapturedOutput output) {
        when(userRepository.findByEmail(LEAK_EMAIL)).thenReturn(Optional.of(testUser));

        userService.resendVerificationEmail(new ResendVerificationRequest(LEAK_EMAIL));

        assertThat(output.getAll()).doesNotContain(LEAK_EMAIL);
        assertThat(output.getAll()).contains("Resend verification requested for already-verified user ID: 1. Ignoring.");
    }

    @Test
    @DisplayName("Expense delete path must not log full email address")
    void expenseDeletePath_DoesNotLogFullEmail(CapturedOutput output) {
        Expense expense = Expense.builder()
                .id(99L)
                .user(testUser)
                .amount(new BigDecimal("42.00"))
                .category(Category.FOOD)
                .description("Lunch")
                .date(LocalDate.now())
                .isDeleted(false)
                .build();

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.getName()).thenReturn(LEAK_EMAIL);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        when(expenseRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.of(expense));

        expenseService.deleteExpense(99L);

        assertThat(output.getAll()).doesNotContain(LEAK_EMAIL);
        assertThat(output.getAll()).contains("User ID: 1 soft-deleted expense ID: 99");
    }

    @Test
    @DisplayName("OAuth2AuthenticationSuccessHandler must not log full email address")
    void oAuth2SuccessHandler_DoesNotLogFullEmail(CapturedOutput output) throws Exception {
        OAuth2Properties props = new OAuth2Properties();
        props.setAuthorizedRedirectUri("http://localhost:3000/oauth2/redirect");
        props.setFailureRedirectUri("http://localhost:3000/login");

        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(props, exchangeCodeService);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Authentication auth = mock(Authentication.class);
        CustomOAuth2User customUser = new CustomOAuth2User(testUser, Map.of("email", LEAK_EMAIL, "sub", "sub-123"));

        Cookie binderCookie = new Cookie("oauth_binder", "test-binder-123");
        when(request.getCookies()).thenReturn(new Cookie[]{binderCookie});
        when(auth.getPrincipal()).thenReturn(customUser);
        when(exchangeCodeService.createExchangeCode(1L, "test-binder-123")).thenReturn("code-xyz");

        handler.onAuthenticationSuccess(request, response, auth);

        assertThat(output.getAll()).doesNotContain(LEAK_EMAIL);
        assertThat(output.getAll()).contains("OAuth2/OIDC authentication successful for user ID: 1");
    }

    @Test
    @DisplayName("OAuth2AuthenticationFailureHandler must not log full email address even if in exception message")
    void oAuth2FailureHandler_DoesNotLogFullEmail(CapturedOutput output) throws Exception {
        OAuth2Properties props = new OAuth2Properties();
        props.setFailureRedirectUri("http://localhost:3000/login");

        OAuth2AuthenticationFailureHandler handler = new OAuth2AuthenticationFailureHandler(props);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
                new OAuth2Error("account_conflict"),
                "Conflict with email " + LEAK_EMAIL
        );

        handler.onAuthenticationFailure(request, response, exception);

        assertThat(output.getAll()).doesNotContain(LEAK_EMAIL);
        assertThat(output.getAll()).contains("OAuth2 authentication failure: error_code=account_conflict");
    }
}

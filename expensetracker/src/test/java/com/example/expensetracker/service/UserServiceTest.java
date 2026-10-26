package com.example.expensetracker.service;

import com.example.expensetracker.config.JwtProperties;
import com.example.expensetracker.dto.AuthResponse;
import com.example.expensetracker.dto.LoginRequest;
import com.example.expensetracker.dto.PasswordUpdateRequest;
import com.example.expensetracker.dto.ProfileUpdateRequest;
import com.example.expensetracker.dto.UserRequest;
import com.example.expensetracker.dto.UserResponse;
import com.example.expensetracker.entity.RefreshToken;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.exception.UserAlreadyExistsException;
import com.example.expensetracker.repository.EmailVerificationTokenRepository;
import com.example.expensetracker.repository.RefreshTokenRepository;
import com.example.expensetracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.expensetracker.dto.OAuth2ExchangeRequest;
import com.example.expensetracker.dto.ResendVerificationRequest;
import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.exception.EmailNotVerifiedException;
import com.example.expensetracker.exception.UnauthorizedAccessException;
import com.example.expensetracker.security.oauth2.OAuth2ExchangeCodeService;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

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
    private OAuth2ExchangeCodeService exchangeCodeService;

    @Mock
    private EmailVerificationTokenService emailVerificationTokenService;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UserRequest userRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Alex Doe")
                .email("alex@example.com")
                .password("encodedPassword123")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(true)
                .build();

        userRequest = new UserRequest();
        userRequest.setName("Alex Doe");
        userRequest.setEmail("alex@example.com");
        userRequest.setPassword("rawPassword123");
    }

    @Test
    @DisplayName("Should successfully register a new user with unverified email and send verification email")
    void createUser_Success() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("rawPassword123")).thenReturn("encodedPassword123");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(emailVerificationTokenService.createVerificationToken(any(User.class))).thenReturn("raw-test-token-123");

        UserResponse response = userService.createUser(userRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("alex@example.com");
        assertThat(response.getRole()).isEqualTo(Role.ROLE_USER);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User captured = userCaptor.getValue();
        assertThat(captured.isEmailVerified()).isFalse();
        assertThat(captured.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);

        verify(emailVerificationTokenService, times(1)).createVerificationToken(testUser);
        verify(emailService, times(1)).sendVerificationEmail("alex@example.com", "Alex Doe", "raw-test-token-123");
    }

    @Test
    @DisplayName("Should throw UserAlreadyExistsException when email already exists")
    void createUser_DuplicateEmail_ThrowsException() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.createUser(userRequest))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should successfully login user and return access & refresh tokens")
    void login_Success() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("alex@example.com");
        loginRequest.setPassword("rawPassword123");

        RefreshToken refreshToken = RefreshToken.builder()
                .id(10L)
                .token("mock-refresh-token")
                .expiryDate(Instant.now().plusSeconds(3600))
                .user(testUser)
                .build();

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("rawPassword123", "encodedPassword123")).thenReturn(true);
        when(jwtService.generateAccessToken(eq("alex@example.com"), eq(Role.ROLE_USER))).thenReturn("mock-access-token");
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        AuthResponse authResponse = userService.login(loginRequest);

        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(authResponse.getRefreshToken()).isEqualTo("mock-refresh-token");
        assertThat(authResponse.getUser().getEmail()).isEqualTo("alex@example.com");
    }

    @Test
    @DisplayName("Should throw BadCredentialsException when password is wrong")
    void login_InvalidPassword_ThrowsBadCredentials() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("alex@example.com");
        loginRequest.setPassword("wrongPassword");

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword123")).thenReturn(false);

        assertThatThrownBy(() -> userService.login(loginRequest))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Should successfully update user profile name and email")
    void updateUser_Success() {
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("alex@example.com", null, java.util.Collections.emptyList());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        ProfileUpdateRequest updateRequest = ProfileUpdateRequest.builder()
                .name("Alex Updated")
                .email("alexupdated@example.com")
                .build();

        User updatedUser = User.builder()
                .id(1L)
                .name("Alex Updated")
                .email("alexupdated@example.com")
                .password("encodedPassword123")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmail("alexupdated@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(updatedUser);

        UserResponse response = userService.updateUser(1L, updateRequest);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Alex Updated");
        assertThat(response.getEmail()).isEqualTo("alexupdated@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should successfully change password when current password matches")
    void changePassword_Success() {
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("alex@example.com", null, java.util.Collections.emptyList());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        PasswordUpdateRequest request = PasswordUpdateRequest.builder()
                .currentPassword("rawPassword123")
                .newPassword("newSecurePassword456")
                .build();

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("rawPassword123", "encodedPassword123")).thenReturn(true);
        when(passwordEncoder.encode("newSecurePassword456")).thenReturn("encodedNewPassword456");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        userService.changePassword(1L, request);

        verify(passwordEncoder, times(1)).matches("rawPassword123", "encodedPassword123");
        verify(passwordEncoder, times(1)).encode("newSecurePassword456");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when current password is wrong in changePassword")
    void changePassword_WrongCurrentPassword_ThrowsException() {
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("alex@example.com", null, java.util.Collections.emptyList());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        PasswordUpdateRequest request = PasswordUpdateRequest.builder()
                .currentPassword("wrongCurrentPassword")
                .newPassword("newSecurePassword456")
                .build();

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongCurrentPassword", "encodedPassword123")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("9 & 10. Exchange OAuth2 code successfully returns application JWT and Refresh Token")
    void exchangeOAuth2Code_Success() {
        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest("valid-exchange-code");

        RefreshToken refreshToken = RefreshToken.builder()
                .id(10L)
                .token("mock-oauth2-refresh-token")
                .expiryDate(Instant.now().plusSeconds(3600))
                .user(testUser)
                .build();

        when(exchangeCodeService.consumeExchangeCode("valid-exchange-code", "valid-binder")).thenReturn(Optional.of(1L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(jwtService.generateAccessToken(eq("alex@example.com"), eq(Role.ROLE_USER))).thenReturn("mock-oauth2-jwt");
        when(jwtProperties.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtProperties.getAccessTokenExpirationMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        AuthResponse response = userService.exchangeOAuth2Code(request, "valid-binder");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-oauth2-jwt");
        assertThat(response.getRefreshToken()).isEqualTo("mock-oauth2-refresh-token");
        assertThat(response.getUser().getEmail()).isEqualTo("alex@example.com");
        verify(exchangeCodeService, times(1)).consumeExchangeCode("valid-exchange-code", "valid-binder");
    }

    @Test
    @DisplayName("Exchange OAuth2 code fails when binder cookie is missing")
    void exchangeOAuth2Code_MissingBinder_ThrowsUnauthorized() {
        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest("valid-code");

        assertThatThrownBy(() -> userService.exchangeOAuth2Code(request, null))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Invalid or expired OAuth2 exchange code");
    }

    @Test
    @DisplayName("Exchange OAuth2 code fails when code is invalid or expired")
    void exchangeOAuth2Code_InvalidOrExpired_ThrowsUnauthorized() {
        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest("invalid-or-expired-code");

        when(exchangeCodeService.consumeExchangeCode("invalid-or-expired-code", "some-binder")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.exchangeOAuth2Code(request, "some-binder"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Invalid or expired OAuth2 exchange code");
    }

    @Test
    @DisplayName("Exchange OAuth2 code fails when user account is deactivated")
    void exchangeOAuth2Code_DisabledAccount_ThrowsUnauthorized() {
        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest("valid-code-inactive-user");

        User inactiveUser = User.builder()
                .id(2L)
                .name("Inactive User")
                .email("inactive@example.com")
                .role(Role.ROLE_USER)
                .isActive(false)
                .build();

        when(exchangeCodeService.consumeExchangeCode("valid-code-inactive-user", "valid-binder")).thenReturn(Optional.of(2L));
        when(userRepository.findById(2L)).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> userService.exchangeOAuth2Code(request, "valid-binder"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Account is disabled");
    }

    @Test
    @DisplayName("Local user with unverified email cannot login - throws EmailNotVerifiedException")
    void login_UnverifiedLocalUser_ThrowsEmailNotVerifiedException() {
        User unverifiedUser = User.builder()
                .id(3L)
                .name("Unverified Local User")
                .email("unverified@example.com")
                .password("encodedPassword123")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(false)
                .build();

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("unverified@example.com");
        loginRequest.setPassword("rawPassword123");

        when(userRepository.findByEmail("unverified@example.com")).thenReturn(Optional.of(unverifiedUser));
        when(passwordEncoder.matches("rawPassword123", "encodedPassword123")).thenReturn(true);

        assertThatThrownBy(() -> userService.login(loginRequest))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessageContaining("EMAIL_NOT_VERIFIED");
    }

    @Test
    @DisplayName("verifyEmail calls EmailVerificationTokenService.verifyToken")
    void verifyEmail_CallsTokenService() {
        userService.verifyEmail("raw-token-abc-123");
        verify(emailVerificationTokenService, times(1)).verifyToken("raw-token-abc-123");
    }

    @Test
    @DisplayName("resendVerificationEmail for eligible unverified local user creates token and sends email")
    void resendVerificationEmail_EligibleUser_Success() {
        User unverifiedUser = User.builder()
                .id(4L)
                .name("Unverified User")
                .email("pending@example.com")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(false)
                .build();

        when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(unverifiedUser));
        when(emailVerificationTokenService.createResendVerificationToken(unverifiedUser)).thenReturn("new-raw-token-456");

        ResendVerificationRequest request = ResendVerificationRequest.builder()
                .email("pending@example.com")
                .build();

        userService.resendVerificationEmail(request);

        verify(emailVerificationTokenService, times(1)).createResendVerificationToken(unverifiedUser);
        verify(emailService, times(1)).sendVerificationEmail("pending@example.com", "Unverified User", "new-raw-token-456");
    }

    @Test
    @DisplayName("resendVerificationEmail for already verified user does not generate token or send email")
    void resendVerificationEmail_AlreadyVerifiedUser_Ignored() {
        User verifiedUser = User.builder()
                .id(5L)
                .name("Verified User")
                .email("verified@example.com")
                .authProvider(AuthProvider.LOCAL)
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(true)
                .build();

        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(verifiedUser));

        ResendVerificationRequest request = ResendVerificationRequest.builder()
                .email("verified@example.com")
                .build();

        userService.resendVerificationEmail(request);

        verify(emailVerificationTokenService, never()).createResendVerificationToken(any(User.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("resendVerificationEmail for Google OAuth user does not generate token or send email")
    void resendVerificationEmail_GoogleUser_Ignored() {
        User googleUser = User.builder()
                .id(6L)
                .name("Google User")
                .email("google@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-id-6")
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(true)
                .build();

        when(userRepository.findByEmail("google@example.com")).thenReturn(Optional.of(googleUser));

        ResendVerificationRequest request = ResendVerificationRequest.builder()
                .email("google@example.com")
                .build();

        userService.resendVerificationEmail(request);

        verify(emailVerificationTokenService, never()).createResendVerificationToken(any(User.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("resendVerificationEmail for non-existent email ignores silently to prevent enumeration")
    void resendVerificationEmail_NonExistentUser_IgnoredSilently() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ResendVerificationRequest request = ResendVerificationRequest.builder()
                .email("ghost@example.com")
                .build();

        userService.resendVerificationEmail(request);

        verify(emailVerificationTokenService, never()).createResendVerificationToken(any(User.class));
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("cleanupUnverifiedUsers deletes unverified local accounts and associated tokens older than retention period")
    void cleanupUnverifiedUsers_DeletesExpiredUnverifiedLocalUsers() {
        User unverifiedUser = User.builder()
                .id(99L)
                .name("Unverified Old")
                .email("unverified@example.com")
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .build();

        when(userRepository.findByAuthProviderAndEmailVerifiedFalseAndCreatedAtBefore(eq(AuthProvider.LOCAL), any(java.time.LocalDateTime.class)))
                .thenReturn(List.of(unverifiedUser));

        int deletedCount = userService.cleanupUnverifiedUsers();

        assertThat(deletedCount).isEqualTo(1);
        verify(emailVerificationTokenRepository, times(1)).deleteByUser(unverifiedUser);
        verify(refreshTokenRepository, times(1)).deleteByUser(unverifiedUser);
        verify(userRepository, times(1)).delete(unverifiedUser);
        verify(userRepository, times(1)).flush();
    }
}

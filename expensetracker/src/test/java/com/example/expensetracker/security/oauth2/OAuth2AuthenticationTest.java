package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.config.OAuth2Properties;
import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.repository.EmailVerificationTokenRepository;
import com.example.expensetracker.repository.OAuthExchangeCodeRepository;
import com.example.expensetracker.repository.RefreshTokenRepository;
import com.example.expensetracker.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private OAuth2Properties oAuth2Properties;

    @Mock
    private OAuth2ExchangeCodeService exchangeCodeService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    private OAuth2Properties realOAuth2Properties;
    private OAuth2ExchangeCodeService realExchangeCodeService;

    private final java.util.Map<String, com.example.expensetracker.entity.OAuthExchangeCode> mockCodeTable = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        mockCodeTable.clear();
        realOAuth2Properties = new OAuth2Properties();
        realOAuth2Properties.setAuthorizedRedirectUri("http://localhost:3000/oauth2/callback");
        realOAuth2Properties.setFailureRedirectUri("http://localhost:3000/login?error=oauth_failed");
        realOAuth2Properties.setExchangeCodeExpirationSeconds(300L);
        realOAuth2Properties.setCookieSecret("test-cookie-secret-for-oauth2-32-bytes");

        OAuthExchangeCodeRepository mockRepo = mock(OAuthExchangeCodeRepository.class);
        lenient().when(mockRepo.save(any(com.example.expensetracker.entity.OAuthExchangeCode.class))).thenAnswer(invocation -> {
            com.example.expensetracker.entity.OAuthExchangeCode code = invocation.getArgument(0);
            mockCodeTable.put(code.getCodeHash(), code);
            return code;
        });
        lenient().when(mockRepo.consumeCodeAtomically(anyString())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(0);
            com.example.expensetracker.entity.OAuthExchangeCode removed = mockCodeTable.remove(hash);
            if (removed != null && removed.getExpiresAt().isAfter(Instant.now())) {
                return Optional.of(new com.example.expensetracker.dto.ConsumedOAuthCode(removed.getUserId(), removed.getBinderHash()));
            }
            return Optional.empty();
        });

        realExchangeCodeService = new OAuth2ExchangeCodeService(realOAuth2Properties, mockRepo);
    }

    @Test
    @DisplayName("2. New Google user provisioning - creates new User with ROLE_USER, GOOGLE provider, and null password")
    void processOAuth2User_NewGoogleUser_CreatesUserWithRoleUser() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-sub-12345");
        attributes.put("email", "newuser@gmail.com");
        attributes.put("name", "Google New User");

        OAuth2User oAuth2User = new DefaultOAuth2User(
                Collections.emptyList(),
                attributes,
                "email"
        );

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-12345"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("newuser@gmail.com"))
                .thenReturn(Optional.empty());

        User savedUser = User.builder()
                .id(10L)
                .name("Google New User")
                .email("newuser@gmail.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-12345")
                .role(Role.ROLE_USER)
                .isActive(true)
                .password(null)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        CustomOAuth2User result = customOAuth2UserService.processOAuth2User(oAuth2User);

        assertThat(result).isNotNull();
        assertThat(result.getUser().getId()).isEqualTo(10L);
        assertThat(result.getUser().getEmail()).isEqualTo("newuser@gmail.com");
        assertThat(result.getUser().getName()).isEqualTo("Google New User");
        assertThat(result.getUser().getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(result.getUser().getProviderId()).isEqualTo("google-sub-12345");
        assertThat(result.getUser().getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(result.getUser().getPassword()).isNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User captured = userCaptor.getValue();
        assertThat(captured.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(captured.getProviderId()).isEqualTo("google-sub-12345");
        assertThat(captured.isEmailVerified()).isTrue();
        assertThat(captured.getPassword()).isNull();
    }

    @Test
    @DisplayName("1. Existing Google user login - finds by providerId and does not create duplicate user")
    void processOAuth2User_ExistingGoogleUser_ReturnsExistingWithoutDuplicate() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-sub-12345");
        attributes.put("email", "existinggoogle@gmail.com");
        attributes.put("name", "Google Existing User");

        OAuth2User oAuth2User = new DefaultOAuth2User(
                Collections.emptyList(),
                attributes,
                "email"
        );

        User existingUser = User.builder()
                .id(5L)
                .name("Google Existing User")
                .email("existinggoogle@gmail.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-12345")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-12345"))
                .thenReturn(Optional.of(existingUser));

        CustomOAuth2User result = customOAuth2UserService.processOAuth2User(oAuth2User);

        assertThat(result).isNotNull();
        assertThat(result.getUser().getId()).isEqualTo(5L);
        assertThat(result.getUser().getEmail()).isEqualTo("existinggoogle@gmail.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("3. Existing verified LOCAL account conflict - throws OAuth2AuthenticationException")
    void processOAuth2User_ExistingLocalAccount_ThrowsAccountConflict() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-sub-99999");
        attributes.put("email", "localuser@example.com");
        attributes.put("name", "Local Account Holder");
        attributes.put("email_verified", true);

        OAuth2User oAuth2User = new DefaultOAuth2User(
                Collections.emptyList(),
                attributes,
                "email"
        );

        User localUser = User.builder()
                .id(2L)
                .name("Local Account Holder")
                .email("localuser@example.com")
                .password("bcryptEncodedPassword")
                .authProvider(AuthProvider.LOCAL)
                .providerId(null)
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(true)
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-99999"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("localuser@example.com"))
                .thenReturn(Optional.of(localUser));

        assertThatThrownBy(() -> customOAuth2UserService.processOAuth2User(oAuth2User))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("3b. Existing unverified LOCAL account and Google verified - deletes unverified account and provisions Google user")
    void processOAuth2User_ExistingUnverifiedLocalAccountAndGoogleVerified_DeletesUnverifiedAndCreatesGoogleUser() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-sub-77777");
        attributes.put("email", "unverified-local@example.com");
        attributes.put("name", "Google Real User");
        attributes.put("email_verified", true);

        OAuth2User oAuth2User = new DefaultOAuth2User(
                Collections.emptyList(),
                attributes,
                "email"
        );

        User unverifiedLocalUser = User.builder()
                .id(22L)
                .name("Old Unverified")
                .email("unverified-local@example.com")
                .password("somePasswordHash")
                .authProvider(AuthProvider.LOCAL)
                .providerId(null)
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(false)
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-77777"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("unverified-local@example.com"))
                .thenReturn(Optional.of(unverifiedLocalUser));

        User savedGoogleUser = User.builder()
                .id(33L)
                .name("Google Real User")
                .email("unverified-local@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-77777")
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(true)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedGoogleUser);

        CustomOAuth2User result = customOAuth2UserService.processOAuth2User(oAuth2User);

        assertThat(result).isNotNull();
        assertThat(result.getUser().getId()).isEqualTo(33L);
        assertThat(result.getUser().getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);

        verify(emailVerificationTokenRepository, times(1)).deleteByUser(unverifiedLocalUser);
        verify(refreshTokenRepository, times(1)).deleteByUser(unverifiedLocalUser);
        verify(userRepository, times(1)).delete(unverifiedLocalUser);
        verify(userRepository, times(1)).flush();
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("4. Disabled account rejection in success handler redirects with account_disabled error")
    void successHandler_DisabledAccount_RedirectsWithError() throws Exception {
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                realOAuth2Properties,
                exchangeCodeService
        );

        User disabledUser = User.builder()
                .id(30L)
                .name("Disabled User")
                .email("disabled@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-30")
                .role(Role.ROLE_USER)
                .isActive(false)
                .build();

        CustomOAuth2User principal = new CustomOAuth2User(disabledUser, Map.of("email", "disabled@example.com"));
        when(authentication.getPrincipal()).thenReturn(principal);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(exchangeCodeService, never()).createExchangeCode(anyLong(), anyString());

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, times(1)).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).isEqualTo("http://localhost:3000/login?error=account_disabled");
    }

    @Test
    @DisplayName("5. OIDC principal correctly exposes the application user, ID token, claims, and authorities")
    void oidcPrincipal_CorrectlyExposesApplicationUserAndClaims() {
        Instant now = Instant.now();
        Map<String, Object> idClaims = Map.of(
                "sub", "google-sub-44",
                "email", "oidcuser@example.com"
        );
        OidcIdToken idToken = new OidcIdToken("token-xyz", now, now.plusSeconds(3600), idClaims);

        Map<String, Object> userClaims = Map.of(
                "sub", "google-sub-44",
                "email", "oidcuser@example.com",
                "name", "OIDC User"
        );
        OidcUserInfo userInfo = new OidcUserInfo(userClaims);

        User user = User.builder()
                .id(44L)
                .name("OIDC User")
                .email("oidcuser@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-44")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        CustomOidcUser customOidcUser = new CustomOidcUser(authorities, idToken, userInfo, "sub", user);

        assertThat(customOidcUser).isInstanceOf(OAuth2UserPrincipal.class);
        assertThat(customOidcUser.getUser()).isEqualTo(user);
        assertThat(customOidcUser.getUser().getId()).isEqualTo(44L);
        assertThat(customOidcUser.getIdToken()).isEqualTo(idToken);
        assertThat(customOidcUser.getUserInfo()).isEqualTo(userInfo);
        assertThat(customOidcUser.getClaims()).containsEntry("email", "oidcuser@example.com");
        assertThat(customOidcUser.getAuthorities()).extracting("authority").contains("ROLE_USER");
        assertThat(customOidcUser.getName()).isEqualTo("google-sub-44");
    }

    @Test
    @DisplayName("6. Success handler with CustomOidcUser generates one-time exchange code and redirects to frontend")
    void successHandler_WithCustomOidcUser_GeneratesExchangeCodeAndRedirects() throws Exception {
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                realOAuth2Properties,
                exchangeCodeService
        );

        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("id-tok", now, now.plusSeconds(3600), Map.of("sub", "sub-55"));
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("sub", "sub-55", "email", "oidc@gmail.com"));

        User user = User.builder()
                .id(55L)
                .name("OIDC User")
                .email("oidc@gmail.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("sub-55")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();

        CustomOidcUser customOidcUser = new CustomOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken,
                userInfo,
                "sub",
                user
        );

        when(request.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[]{
                new jakarta.servlet.http.Cookie("oauth_binder", "mock-binder-123")
        });
        when(authentication.getPrincipal()).thenReturn(customOidcUser);
        when(exchangeCodeService.createExchangeCode(55L, "mock-binder-123")).thenReturn("code-oidc-123");

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, times(1)).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).isEqualTo("http://localhost:3000/oauth2/callback?code=code-oidc-123");
        assertThat(redirectUrl).doesNotContain("Bearer");
        assertThat(redirectUrl).doesNotContain("eyJ");
    }

    @Test
    @DisplayName("7. Unexpected principal handling redirects safely to failure redirect URL")
    void successHandler_UnexpectedPrincipal_RedirectsToFailureUrl() throws Exception {
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                realOAuth2Properties,
                exchangeCodeService
        );

        // Standard DefaultOAuth2User which lacks application User entity
        OAuth2User unexpectedPrincipal = new DefaultOAuth2User(
                Collections.emptyList(),
                Map.of("sub", "unknown-123"),
                "sub"
        );

        when(authentication.getPrincipal()).thenReturn(unexpectedPrincipal);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(exchangeCodeService, never()).createExchangeCode(anyLong(), anyString());

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, times(1)).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).isEqualTo("http://localhost:3000/login?error=oauth_failed");
    }

    @Test
    @DisplayName("8a. Exchange code works once and redirects with short-lived code")
    void exchangeCode_WorksOnce() {
        String code = realExchangeCodeService.createExchangeCode(10L, "");
        assertThat(code).isNotBlank();

        Optional<Long> userIdOpt = realExchangeCodeService.consumeExchangeCode(code);
        assertThat(userIdOpt).isPresent();
        assertThat(userIdOpt.get()).isEqualTo(10L);
    }

    @Test
    @DisplayName("8b. Exchange code cannot be reused (single-use constraint)")
    void exchangeCode_CannotBeReused() {
        String code = realExchangeCodeService.createExchangeCode(10L, "");

        Optional<Long> firstConsume = realExchangeCodeService.consumeExchangeCode(code);
        assertThat(firstConsume).isPresent();

        // Second consume attempt with the exact same code
        Optional<Long> secondConsume = realExchangeCodeService.consumeExchangeCode(code);
        assertThat(secondConsume).isEmpty();
    }

    @Test
    @DisplayName("8c. Expired exchange code fails")
    void exchangeCode_ExpiredCode_Fails() {
        OAuth2Properties shortLivedProps = new OAuth2Properties();
        shortLivedProps.setExchangeCodeExpirationSeconds(-10L);

        OAuthExchangeCodeRepository shortLivedRepo = mock(OAuthExchangeCodeRepository.class);
        java.util.Map<String, com.example.expensetracker.entity.OAuthExchangeCode> table = new java.util.HashMap<>();
        lenient().when(shortLivedRepo.save(any(com.example.expensetracker.entity.OAuthExchangeCode.class))).thenAnswer(inv -> {
            com.example.expensetracker.entity.OAuthExchangeCode c = inv.getArgument(0);
            table.put(c.getCodeHash(), c);
            return c;
        });
        lenient().when(shortLivedRepo.consumeCodeAtomically(anyString())).thenAnswer(inv -> {
            String h = inv.getArgument(0);
            com.example.expensetracker.entity.OAuthExchangeCode c = table.remove(h);
            if (c != null && c.getExpiresAt().isAfter(java.time.Instant.now())) {
                return Optional.of(new com.example.expensetracker.dto.ConsumedOAuthCode(c.getUserId(), c.getBinderHash()));
            }
            return Optional.empty();
        });

        OAuth2ExchangeCodeService shortLivedService = new OAuth2ExchangeCodeService(shortLivedProps, shortLivedRepo);
        String code = shortLivedService.createExchangeCode(10L, "");

        Optional<Long> consumeResult = shortLivedService.consumeExchangeCode(code);
        assertThat(consumeResult).isEmpty();
    }

    @Test
    @DisplayName("Success Handler with CustomOAuth2User redirects to frontend with one-time exchange code")
    void successHandler_GeneratesExchangeCodeAndRedirects() throws Exception {
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                realOAuth2Properties,
                exchangeCodeService
        );

        User user = User.builder()
                .id(20L)
                .name("Test User")
                .email("testuser@gmail.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-20")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();

        CustomOAuth2User customOAuth2User = new CustomOAuth2User(user, Map.of("email", "testuser@gmail.com"));
        when(request.getCookies()).thenReturn(new jakarta.servlet.http.Cookie[]{
                new jakarta.servlet.http.Cookie("oauth_binder", "mock-binder-456")
        });
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(exchangeCodeService.createExchangeCode(20L, "mock-binder-456")).thenReturn("mock-exchange-code-abc");

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, times(1)).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).startsWith("http://localhost:3000/oauth2/callback?code=mock-exchange-code-abc");
        assertThat(redirectUrl).doesNotContain("Bearer");
        assertThat(redirectUrl).doesNotContain("eyJ"); // Does not contain raw JWT
    }

    @Test
    @DisplayName("Success handler missing oauth_binder redirects to failure redirect URL")
    void successHandler_MissingBinderCookie_RedirectsToFailureUrl() throws Exception {
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                realOAuth2Properties,
                exchangeCodeService
        );

        User user = User.builder()
                .id(20L)
                .name("Test User")
                .email("testuser@gmail.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-20")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();

        CustomOAuth2User customOAuth2User = new CustomOAuth2User(user, Map.of("email", "testuser@gmail.com"));
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(request.getCookies()).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(exchangeCodeService, never()).createExchangeCode(anyLong(), anyString());
        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, times(1)).sendRedirect(redirectCaptor.capture());

        assertThat(redirectCaptor.getValue()).isEqualTo("http://localhost:3000/login?error=oauth_failed");
    }

    @Test
    @DisplayName("Failure Handler redirects to frontend with sanitized error query param")
    void failureHandler_RedirectsWithSanitizedError() throws Exception {
        OAuth2AuthenticationFailureHandler failureHandler = new OAuth2AuthenticationFailureHandler(realOAuth2Properties);

        OAuth2AuthenticationException ex = new OAuth2AuthenticationException(
                new OAuth2Error("account_conflict"),
                "Account conflict detected"
        );

        failureHandler.onAuthenticationFailure(request, response, ex);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, times(1)).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).isEqualTo("http://localhost:3000/login?error=account_conflict");
    }

    @Test
    @DisplayName("Failure Handler redirects to frontend with error=email_not_verified when email_not_verified occurs")
    void failureHandler_EmailNotVerified_RedirectsWithMappedError() throws Exception {
        OAuth2AuthenticationFailureHandler failureHandler = new OAuth2AuthenticationFailureHandler(realOAuth2Properties);

        OAuth2AuthenticationException ex = new OAuth2AuthenticationException(
                new OAuth2Error("email_not_verified"),
                "Email is not verified by Google"
        );

        failureHandler.onAuthenticationFailure(request, response, ex);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, times(1)).sendRedirect(redirectCaptor.capture());

        assertThat(redirectCaptor.getValue()).isEqualTo("http://localhost:3000/login?error=email_not_verified");
    }
}

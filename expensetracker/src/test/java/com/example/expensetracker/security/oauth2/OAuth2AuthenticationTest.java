package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.config.OAuth2Properties;
import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.entity.User;
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
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
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

    @BeforeEach
    void setUp() {
        realOAuth2Properties = new OAuth2Properties();
        realOAuth2Properties.setAuthorizedRedirectUri("http://localhost:3000/oauth2/callback");
        realOAuth2Properties.setFailureRedirectUri("http://localhost:3000/login?error=oauth_failed");
        realOAuth2Properties.setExchangeCodeExpirationSeconds(300L);

        realExchangeCodeService = new OAuth2ExchangeCodeService(realOAuth2Properties);
    }

    @Test
    @DisplayName("3 & 8. Google user creation - creates new User with ROLE_USER, GOOGLE provider, and null password")
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
    @DisplayName("4, 5 & 6. Existing Google user login - finds by providerId and does not create duplicate user")
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
    @DisplayName("7. Existing LOCAL account is not silently merged - throws OAuth2AuthenticationException")
    void processOAuth2User_ExistingLocalAccount_ThrowsAccountConflict() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-sub-99999");
        attributes.put("email", "localuser@example.com");
        attributes.put("name", "Local Account Holder");

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
                .build();

        when(userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-99999"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("localuser@example.com"))
                .thenReturn(Optional.of(localUser));

        assertThatThrownBy(() -> customOAuth2UserService.processOAuth2User(oAuth2User))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("11. Exchange code works once and redirects with short-lived code")
    void exchangeCode_WorksOnce() {
        String code = realExchangeCodeService.createExchangeCode(10L, "user@example.com");
        assertThat(code).isNotBlank();

        Optional<Long> userIdOpt = realExchangeCodeService.consumeExchangeCode(code);
        assertThat(userIdOpt).isPresent();
        assertThat(userIdOpt.get()).isEqualTo(10L);
    }

    @Test
    @DisplayName("12. Exchange code cannot be reused (single-use constraint)")
    void exchangeCode_CannotBeReused() {
        String code = realExchangeCodeService.createExchangeCode(10L, "user@example.com");

        Optional<Long> firstConsume = realExchangeCodeService.consumeExchangeCode(code);
        assertThat(firstConsume).isPresent();

        // Second consume attempt with the exact same code
        Optional<Long> secondConsume = realExchangeCodeService.consumeExchangeCode(code);
        assertThat(secondConsume).isEmpty();
    }

    @Test
    @DisplayName("13. Expired exchange code fails")
    void exchangeCode_ExpiredCode_Fails() {
        OAuth2Properties shortLivedProps = new OAuth2Properties();
        shortLivedProps.setExchangeCodeExpirationSeconds(-10L); // Already expired

        OAuth2ExchangeCodeService shortLivedService = new OAuth2ExchangeCodeService(shortLivedProps);
        String code = shortLivedService.createExchangeCode(10L, "user@example.com");

        Optional<Long> consumeResult = shortLivedService.consumeExchangeCode(code);
        assertThat(consumeResult).isEmpty();
    }

    @Test
    @DisplayName("Success Handler redirects to frontend with one-time exchange code (no JWT in URL)")
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
        when(authentication.getPrincipal()).thenReturn(customOAuth2User);
        when(exchangeCodeService.createExchangeCode(20L, "testuser@gmail.com")).thenReturn("mock-exchange-code-abc");

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response, times(1)).sendRedirect(redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).startsWith("http://localhost:3000/oauth2/callback?code=mock-exchange-code-abc");
        assertThat(redirectUrl).doesNotContain("Bearer");
        assertThat(redirectUrl).doesNotContain("eyJ"); // Does not contain raw JWT
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
}

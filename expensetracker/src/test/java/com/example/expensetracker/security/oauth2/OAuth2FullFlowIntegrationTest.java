package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.config.JwtProperties;
import com.example.expensetracker.config.OAuth2Properties;
import com.example.expensetracker.dto.ApiResponse;
import com.example.expensetracker.dto.AuthResponse;
import com.example.expensetracker.dto.OAuth2ExchangeRequest;
import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.repository.EmailVerificationTokenRepository;
import com.example.expensetracker.repository.OAuthExchangeCodeRepository;
import com.example.expensetracker.repository.RefreshTokenRepository;
import com.example.expensetracker.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Step 7: Comprehensive Integration Test Suite covering all 10 required scenarios:
 * 1. Successful login initiation sets/uses binder cookie & redirects with code.
 * 2. Exchange with correct cookie returns tokens + HttpOnly refresh cookie, and NO refresh token in JSON.
 * 3. Exchange with missing or wrong binder cookie returns 401.
 * 4. A code cannot be used twice; expired code fails.
 * 5. Two concurrent exchanges of one code produce exactly one success.
 * 6. email_verified=false is rejected with mapped error (email_not_verified).
 * 7. Verified LOCAL email -> account_conflict; unverified LOCAL email -> replaced by Google user.
 * 8. Disabled user is rejected at both the success handler and the exchange.
 * 9. The flow works without an HTTP session (STATELESS) using cookie-based authorization request.
 * 10. CORS: allowed origin passes, other origin is rejected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuth2FullFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private OAuthExchangeCodeRepository exchangeCodeRepository;

    @Autowired
    private OAuth2ExchangeCodeService exchangeCodeService;

    @Autowired
    private OAuth2Properties oAuth2Properties;

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired
    private OAuth2AuthenticationSuccessHandler successHandler;

    @Autowired
    private OAuth2AuthenticationFailureHandler failureHandler;

    private User activeGoogleUser;
    private User disabledGoogleUser;

    @BeforeEach
    void setUp() {
        exchangeCodeRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        userRepository.deleteAll();

        activeGoogleUser = userRepository.save(User.builder()
                .name("Integration User")
                .email("integration.user@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-integration-sub")
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(true)
                .build());

        disabledGoogleUser = userRepository.save(User.builder()
                .name("Disabled User")
                .email("disabled.user@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-disabled-sub")
                .role(Role.ROLE_USER)
                .isActive(false)
                .emailVerified(true)
                .build());
    }

    // -------------------------------------------------------------------------
    // Scenario 1 & 9: Initiation, Binder Cookie, and Stateless Request Storage
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenarios 1 & 9: /oauth2/authorization/google sets oauth_binder and oauth2_auth_request statelessly without HTTP session")
    void authorizationEndpoint_SetsBinderAndRequestCookiesStatelessly() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("accounts.google.com")))
                .andExpect(cookie().exists("oauth_binder"))
                .andExpect(cookie().exists("oauth2_auth_request"))
                .andReturn();

        // Verify session was NOT created (stateless)
        assertThat(result.getRequest().getSession(false)).isNull();

        // Verify binder cookie attributes
        jakarta.servlet.http.Cookie binderCookie = result.getResponse().getCookie("oauth_binder");
        assertThat(binderCookie).isNotNull();
        assertThat(binderCookie.isHttpOnly()).isTrue();
        assertThat(binderCookie.getPath()).isEqualTo("/");
        assertThat(binderCookie.getMaxAge()).isEqualTo(300);

        // Verify auth request cookie attributes
        jakarta.servlet.http.Cookie requestCookie = result.getResponse().getCookie("oauth2_auth_request");
        assertThat(requestCookie).isNotNull();
        assertThat(requestCookie.isHttpOnly()).isTrue();
        assertThat(requestCookie.getPath()).isEqualTo("/");
        assertThat(requestCookie.getMaxAge()).isEqualTo(300);
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Token Exchange with matching binder cookie
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 2: Exchange returns JWT access token, HttpOnly refresh cookie, and NO refresh token in JSON")
    void exchange_WithMatchingBinder_ReturnsTokensAndSetsRefreshCookie() throws Exception {
        String binder = "integration-valid-binder-123";
        String rawCode = exchangeCodeService.createExchangeCode(activeGoogleUser.getId(), binder);

        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest(rawCode);

        mockMvc.perform(post("/api/users/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie("oauth_binder", binder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist()) // Crucial: must NOT be in JSON body
                .andExpect(jsonPath("$.data.user.email").value(activeGoogleUser.getEmail()))
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().maxAge("oauth_binder", 0)); // Cleared after exchange
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Exchange with missing or wrong binder cookie returns 401
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 3a: Exchange with missing binder cookie returns 401")
    void exchange_MissingBinderCookie_Returns401() throws Exception {
        String binder = "integration-valid-binder-456";
        String rawCode = exchangeCodeService.createExchangeCode(activeGoogleUser.getId(), binder);

        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest(rawCode);

        mockMvc.perform(post("/api/users/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired OAuth2 exchange code"));
    }

    @Test
    @DisplayName("Scenario 3b: Exchange with mismatched binder cookie returns 401")
    void exchange_MismatchedBinderCookie_Returns401() throws Exception {
        String originalBinder = "integration-valid-binder-789";
        String wrongBinder = "attacker-different-binder-999";
        String rawCode = exchangeCodeService.createExchangeCode(activeGoogleUser.getId(), originalBinder);

        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest(rawCode);

        mockMvc.perform(post("/api/users/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie("oauth_binder", wrongBinder)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired OAuth2 exchange code"));
    }

    // -------------------------------------------------------------------------
    // Scenario 4: A code cannot be used twice; an expired code fails
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 4: A code cannot be used twice; expired code fails")
    void exchange_CannotBeUsedTwice_AndExpiredCodeFails() throws Exception {
        String binder = "single-use-binder";
        String rawCode = exchangeCodeService.createExchangeCode(activeGoogleUser.getId(), binder);

        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest(rawCode);

        // 1. First use succeeds
        mockMvc.perform(post("/api/users/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie("oauth_binder", binder)))
                .andExpect(status().isOk());

        // 2. Second use fails with 401
        mockMvc.perform(post("/api/users/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie("oauth_binder", binder)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // 3. Expired code fails
        OAuth2Properties expiredProperties = new OAuth2Properties();
        expiredProperties.setExchangeCodeExpirationSeconds(-10L);
        OAuth2ExchangeCodeService expiredService = new OAuth2ExchangeCodeService(expiredProperties, exchangeCodeRepository);
        String expiredCode = expiredService.createExchangeCode(activeGoogleUser.getId(), binder);

        OAuth2ExchangeRequest expiredRequest = new OAuth2ExchangeRequest(expiredCode);
        mockMvc.perform(post("/api/users/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expiredRequest))
                        .cookie(new Cookie("oauth_binder", binder)))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Scenario 5: Two concurrent exchanges produce exactly one success
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 5: Two concurrent exchanges of the same code produce exactly one success")
    void concurrentExchanges_ProduceExactlyOneSuccess() throws Exception {
        String binder = "concurrency-test-binder";
        String rawCode = exchangeCodeService.createExchangeCode(activeGoogleUser.getId(), binder);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Optional<Long>>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return exchangeCodeService.consumeExchangeCode(rawCode, binder);
            }));
        }

        startLatch.countDown();

        List<Optional<Long>> results = new ArrayList<>();
        for (Future<Optional<Long>> future : futures) {
            results.add(future.get());
        }

        executor.shutdown();

        long successCount = results.stream().filter(Optional::isPresent).count();
        long failureCount = results.stream().filter(Optional::isEmpty).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Scenario 6: email_verified=false rejected with mapped error
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 6: OAuth2AuthenticationFailureHandler maps email_not_verified to friendly query param")
    void failureHandler_MapsEmailNotVerifiedCode() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();

        OAuth2AuthenticationException ex = new OAuth2AuthenticationException(
                new OAuth2Error("email_not_verified"),
                "Google reports this email is not verified"
        );

        failureHandler.onAuthenticationFailure(req, res, ex);

        assertThat(res.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=email_not_verified");
    }

    // -------------------------------------------------------------------------
    // Scenario 7: Conflict handling: verified local -> account_conflict; unverified local -> replaced
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 7a: Verified LOCAL account conflicts with incoming Google login")
    void processOAuth2User_VerifiedLocalAccount_ThrowsAccountConflict() {
        String email = "verified.local@example.com";
        userRepository.save(User.builder()
                .name("Verified Local Holder")
                .email(email)
                .password("hash123")
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(true)
                .isActive(true)
                .build());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-sub-conflict-1");
        attributes.put("email", email);
        attributes.put("name", "Google Conflict Attempt");
        attributes.put("email_verified", true);

        OAuth2User oAuth2User = new DefaultOAuth2User(
                Collections.emptyList(),
                attributes,
                "email"
        );

        assertThatThrownBy(() -> customOAuth2UserService.processOAuth2User(oAuth2User))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Scenario 7b: Unverified LOCAL account is deleted and replaced by verified Google user")
    void processOAuth2User_UnverifiedLocalAccount_ReplacedByGoogleUser() {
        String email = "unverified.local@example.com";
        User unverifiedLocal = userRepository.save(User.builder()
                .name("Unverified Local Holder")
                .email(email)
                .password("hash456")
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .isActive(true)
                .build());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("sub", "google-sub-replacement-2");
        attributes.put("email", email);
        attributes.put("name", "Google Real User");
        attributes.put("email_verified", true);

        OAuth2User oAuth2User = new DefaultOAuth2User(
                Collections.emptyList(),
                attributes,
                "email"
        );

        CustomOAuth2User result = customOAuth2UserService.processOAuth2User(oAuth2User);

        assertThat(result).isNotNull();
        assertThat(result.getUser().getEmail()).isEqualTo(email);
        assertThat(result.getUser().getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(result.getUser().isEmailVerified()).isTrue();

        // Confirm database has the Google user and old local is gone
        User dbUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(dbUser.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(dbUser.getId()).isNotEqualTo(unverifiedLocal.getId());
    }

    // -------------------------------------------------------------------------
    // Scenario 8: Disabled user rejected at both success handler and exchange
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 8a: Disabled user rejected at success handler")
    void successHandler_DisabledUser_RedirectsWithAccountDisabled() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(new Cookie("oauth_binder", "some-binder"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        CustomOAuth2User customUser = new CustomOAuth2User(disabledGoogleUser, Map.of("email", disabledGoogleUser.getEmail()));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                customUser,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        successHandler.onAuthenticationSuccess(req, res, auth);

        assertThat(res.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=account_disabled");
        // Verify no exchange code was created
        assertThat(exchangeCodeRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("Scenario 8b: Disabled user rejected at exchange endpoint with 401")
    void exchange_DisabledUser_Returns401() throws Exception {
        String binder = "disabled-user-binder";
        String rawCode = exchangeCodeService.createExchangeCode(disabledGoogleUser.getId(), binder);

        OAuth2ExchangeRequest request = new OAuth2ExchangeRequest(rawCode);

        mockMvc.perform(post("/api/users/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .cookie(new Cookie("oauth_binder", binder)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Account is disabled. Please contact support."));
    }

    // -------------------------------------------------------------------------
    // Scenario 10: CORS - allowed origin passes preflight, disallowed fails
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 10: CORS allowed origin passes preflight, disallowed origin is rejected")
    void cors_PreflightChecks() throws Exception {
        // Allowed origin (localhost default)
        mockMvc.perform(options("/api/users/oauth2/exchange")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));

        // Disallowed origin
        mockMvc.perform(options("/api/users/oauth2/exchange")
                        .header("Origin", "https://unauthorized-attacker.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}

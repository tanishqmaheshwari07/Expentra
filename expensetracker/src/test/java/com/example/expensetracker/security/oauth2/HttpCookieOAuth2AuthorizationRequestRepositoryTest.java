package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.config.JwtProperties;
import com.example.expensetracker.config.OAuth2Properties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    private HttpCookieOAuth2AuthorizationRequestRepository repository;
    private OAuth2Properties oAuth2Properties;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        oAuth2Properties = new OAuth2Properties();
        oAuth2Properties.setCookieSecret("test-secret-key-for-oauth2-hmac-sha256-signature-123");

        jwtProperties = new JwtProperties();
        jwtProperties.setCookieSecure(false);
        jwtProperties.setCookieSameSite("Lax");

        repository = new HttpCookieOAuth2AuthorizationRequestRepository(oAuth2Properties, jwtProperties);
    }

    @Test
    @DisplayName("Save and load authorization request works statelessly via signed cookie")
    void saveAndLoad_WorksStatelesslyWithSignedCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
                .clientId("test-google-client-id")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .state("secure-state-12345")
                .build();

        // Save
        repository.saveAuthorizationRequest(authRequest, request, response);

        Cookie cookie = response.getCookie(HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo(300);
        assertThat(cookie.getValue()).contains("."); // payload.signature format

        // Load
        request.setCookies(cookie);
        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(request);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getClientId()).isEqualTo("test-google-client-id");
        assertThat(loaded.getState()).isEqualTo("secure-state-12345");
        assertThat(loaded.getRedirectUri()).isEqualTo("http://localhost:8080/login/oauth2/code/google");

        // Verify no session was created
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("Tampered cookie payload fails HMAC verification and returns null")
    void load_TamperedPayload_ReturnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
                .clientId("test-google-client-id")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .redirectUri("http://localhost:8080/login/oauth2/code/google")
                .state("original-state")
                .build();

        repository.saveAuthorizationRequest(authRequest, request, response);

        Cookie originalCookie = response.getCookie(HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        assertThat(originalCookie).isNotNull();

        String[] parts = originalCookie.getValue().split("\\.");
        String payload = parts[0];
        String signature = parts[1];

        // Tamper with payload
        byte[] payloadBytes = Base64.getUrlDecoder().decode(payload);
        payloadBytes[0] ^= 0xFF; // Flip bits
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);

        Cookie tamperedCookie = new Cookie(HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, tamperedPayload + "." + signature);
        request.setCookies(tamperedCookie);

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(request);
        assertThat(loaded).isNull();
    }

    @Test
    @DisplayName("Unsigned cookie fails verification and returns null")
    void load_UnsignedCookie_ReturnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Cookie unsignedCookie = new Cookie(HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, "arbitrary-unsigned-content-without-dot");
        request.setCookies(unsignedCookie);

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(request);
        assertThat(loaded).isNull();
    }

    @Test
    @DisplayName("Remove authorization request deletes cookie with maxAge 0")
    void remove_DeletesCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthorizationRequest authRequest = OAuth2AuthorizationRequest.authorizationCode()
                .clientId("test-google-client-id")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .state("state-to-remove")
                .build();

        repository.saveAuthorizationRequest(authRequest, request, response);

        Cookie savedCookie = response.getCookie(HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        request.setCookies(savedCookie);

        MockHttpServletResponse removeResponse = new MockHttpServletResponse();
        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(request, removeResponse);

        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo("state-to-remove");

        Cookie deleteCookie = removeResponse.getCookie(HttpCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        assertThat(deleteCookie).isNotNull();
        assertThat(deleteCookie.getMaxAge()).isEqualTo(0);
    }
}

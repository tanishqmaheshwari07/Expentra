package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.config.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CustomOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String OAUTH_BINDER_COOKIE_NAME = "oauth_binder";
    public static final int BINDER_COOKIE_MAX_AGE_SECONDS = 300;

    private final DefaultOAuth2AuthorizationRequestResolver defaultResolver;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public CustomOAuth2AuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository,
            JwtProperties jwtProperties) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                "/oauth2/authorization"
        );
        this.jwtProperties = jwtProperties;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authorizationRequest = defaultResolver.resolve(request);
        if (authorizationRequest != null) {
            attachBinderCookie(request);
        }
        return authorizationRequest;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authorizationRequest = defaultResolver.resolve(request, clientRegistrationId);
        if (authorizationRequest != null) {
            attachBinderCookie(request);
        }
        return authorizationRequest;
    }

    public String generateBinderToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public ResponseCookie createBinderCookie(String token) {
        return ResponseCookie.from(OAUTH_BINDER_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite(jwtProperties.getCookieSameSite())
                .path("/")
                .maxAge(BINDER_COOKIE_MAX_AGE_SECONDS)
                .build();
    }

    private void attachBinderCookie(HttpServletRequest request) {
        String token = generateBinderToken();
        ResponseCookie cookie = createBinderCookie(token);

        request.setAttribute(OAUTH_BINDER_COOKIE_NAME, cookie.toString());
        request.setAttribute(OAUTH_BINDER_COOKIE_NAME + "_token", token);

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null && attrs.getResponse() != null) {
            attrs.getResponse().addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
    }
}

package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.config.JwtProperties;
import com.example.expensetracker.config.OAuth2Properties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class HttpCookieOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    public static final int COOKIE_EXPIRE_SECONDS = 300;

    private final OAuth2Properties oAuth2Properties;
    private final JwtProperties jwtProperties;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        Cookie cookie = getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return null;
        }

        return deserializeAndVerify(cookie.getValue());
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (response == null) {
            return;
        }

        if (authorizationRequest == null) {
            removeAuthorizationRequestCookies(request, response);
            return;
        }

        String serialized = serializeAndSign(authorizationRequest);

        ResponseCookie cookie = ResponseCookie.from(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, serialized)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite("Lax") // Lax required for cross-site top-level GET redirect from Google
                .path("/")
                .maxAge(COOKIE_EXPIRE_SECONDS)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Also ensure any binder cookie set in request attribute is added to response
        if (request != null) {
            Object binderCookieHeader = request.getAttribute(CustomOAuth2AuthorizationRequestResolver.OAUTH_BINDER_COOKIE_NAME);
            if (binderCookieHeader instanceof String s) {
                response.addHeader(HttpHeaders.SET_COOKIE, s);
            }
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response) {
        OAuth2AuthorizationRequest authRequest = this.loadAuthorizationRequest(request);
        removeAuthorizationRequestCookies(request, response);
        return authRequest;
    }

    public void removeAuthorizationRequestCookies(HttpServletRequest request, HttpServletResponse response) {
        if (response == null) {
            return;
        }

        ResponseCookie deleteCookie = ResponseCookie.from(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());
    }

    private String serializeAndSign(OAuth2AuthorizationRequest authorizationRequest) {
        try {
            byte[] rawBytes;
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(authorizationRequest);
                oos.flush();
                rawBytes = baos.toByteArray();
            }

            byte[] hmacBytes = computeHmac(rawBytes);

            String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes);
            String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);

            return encodedPayload + "." + encodedSignature;
        } catch (Exception e) {
            log.error("Failed to serialize and sign OAuth2AuthorizationRequest", e);
            throw new IllegalStateException("Could not serialize OAuth2 authorization request", e);
        }
    }

    private OAuth2AuthorizationRequest deserializeAndVerify(String cookieValue) {
        try {
            int dotIdx = cookieValue.lastIndexOf('.');
            if (dotIdx <= 0 || dotIdx == cookieValue.length() - 1) {
                log.warn("Invalid format for OAuth2 authorization request cookie");
                return null;
            }

            String payloadB64 = cookieValue.substring(0, dotIdx);
            String signatureB64 = cookieValue.substring(dotIdx + 1);

            byte[] payloadBytes = Base64.getUrlDecoder().decode(payloadB64);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureB64);

            byte[] expectedSignatureBytes = computeHmac(payloadBytes);

            // Constant-time signature comparison to prevent timing attacks
            if (!MessageDigest.isEqual(signatureBytes, expectedSignatureBytes)) {
                log.warn("OAuth2 authorization request cookie HMAC signature verification failed");
                return null;
            }

            // Deserialization only runs AFTER signature integrity is cryptographically verified
            try (ByteArrayInputStream bais = new ByteArrayInputStream(payloadBytes);
                 ObjectInputStream ois = new ObjectInputStream(bais)) {
                return (OAuth2AuthorizationRequest) ois.readObject();
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize verified OAuth2AuthorizationRequest: {}", e.getMessage());
            return null;
        }
    }

    private byte[] computeHmac(byte[] data) {
        try {
            String secret = oAuth2Properties.getCookieSecret();
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException("OAuth2 cookieSecret is not configured (has NO default value)");
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute HMAC-SHA256 signature", e);
        }
    }

    private Cookie getCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie;
            }
        }
        return null;
    }
}

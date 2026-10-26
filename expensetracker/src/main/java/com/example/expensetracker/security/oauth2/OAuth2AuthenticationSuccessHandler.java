package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.config.OAuth2Properties;
import com.example.expensetracker.entity.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@Slf4j
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuth2Properties oAuth2Properties;
    private final OAuth2ExchangeCodeService exchangeCodeService;
    private final HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    public OAuth2AuthenticationSuccessHandler(
            OAuth2Properties oAuth2Properties,
            OAuth2ExchangeCodeService exchangeCodeService) {
        this(oAuth2Properties, exchangeCodeService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OAuth2AuthenticationSuccessHandler(
            OAuth2Properties oAuth2Properties,
            OAuth2ExchangeCodeService exchangeCodeService,
            @org.springframework.lang.Nullable HttpCookieOAuth2AuthorizationRequestRepository authorizationRequestRepository) {
        this.oAuth2Properties = oAuth2Properties;
        this.exchangeCodeService = exchangeCodeService;
        this.authorizationRequestRepository = authorizationRequestRepository;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        User user = null;
        Object principal = (authentication != null) ? authentication.getPrincipal() : null;

        if (principal instanceof OAuth2UserPrincipal oAuth2UserPrincipal) {
            user = oAuth2UserPrincipal.getUser();
        } else if (principal instanceof CustomOAuth2User customOAuth2User) {
            user = customOAuth2User.getUser();
        } else if (principal instanceof CustomOidcUser customOidcUser) {
            user = customOidcUser.getUser();
        }

        // Step 5: Clean up short-lived authorization request cookie
        clearAuthorizationRequestCookies(request, response);

        if (user != null) {
            if (!user.isActive()) {
                log.warn("OAuth login rejected: Account is disabled for email '{}'", user.getEmail());
                String redirectUrl = UriComponentsBuilder
                        .fromUriString(oAuth2Properties.getFailureRedirectUri())
                        .replaceQueryParam("error", "account_disabled")
                        .build()
                        .toUriString();
                response.sendRedirect(redirectUrl);
                return;
            }

            // Step 3: Validate browser binding cookie
            String binderCookie = null;
            if (request.getCookies() != null) {
                for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                    if (CustomOAuth2AuthorizationRequestResolver.OAUTH_BINDER_COOKIE_NAME.equals(c.getName())) {
                        binderCookie = c.getValue();
                        break;
                    }
                }
            }

            if (binderCookie == null || binderCookie.isBlank()) {
                log.warn("OAuth login rejected: Missing oauth_binder cookie");
                String redirectUrl = UriComponentsBuilder
                        .fromUriString(oAuth2Properties.getFailureRedirectUri())
                        .replaceQueryParam("error", "oauth_failed")
                        .build()
                        .toUriString();
                response.sendRedirect(redirectUrl);
                return;
            }

            // Generate short-lived single-use exchange code bound to browser binder (DO NOT put JWT in URL)
            String exchangeCode = exchangeCodeService.createExchangeCode(user.getId(), binderCookie);

            String targetUrl = UriComponentsBuilder
                    .fromUriString(oAuth2Properties.getAuthorizedRedirectUri())
                    .queryParam("code", exchangeCode)
                    .build()
                    .toUriString();

            log.info("OAuth2/OIDC authentication successful for user '{}'. Redirecting with one-time exchange code.", user.getEmail());
            response.sendRedirect(targetUrl);
        } else {
            log.error("Unexpected principal type in OAuth2 authentication success: {}",
                    principal != null ? principal.getClass().getName() : "null");
            response.sendRedirect(oAuth2Properties.getFailureRedirectUri());
        }
    }

    protected void clearAuthorizationRequestCookies(HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequestRepository != null) {
            authorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
        }
    }
}

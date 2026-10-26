package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOidcUserService extends OidcUserService {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // Delegate to standard OidcUserService to validate ID token and fetch UserInfo
        OidcUser oidcUser = super.loadUser(userRequest);

        // Reject if email_verified claim is not exactly true
        Object emailVerifiedClaim = (oidcUser.getAttributes() != null)
                ? oidcUser.getAttributes().get("email_verified")
                : null;
        boolean isEmailVerified = Boolean.TRUE.equals(emailVerifiedClaim)
                || (emailVerifiedClaim instanceof String s && "true".equalsIgnoreCase(s.trim()));

        if (!isEmailVerified) {
            log.warn("OAuth2/OIDC user rejected: Google email is not verified");
            throw new OAuth2AuthenticationException(
                    new org.springframework.security.oauth2.core.OAuth2Error("email_not_verified"),
                    "Your Google email address is not verified. Please verify your email with Google and try again."
            );
        }

        // Reuse existing account lookup and provisioning logic
        CustomOAuth2User customOAuth2User = customOAuth2UserService.processOAuth2User(oidcUser);
        User user = customOAuth2User.getUser();

        // Preserve OIDC authorities/scopes while guaranteeing the application role is included
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (oidcUser.getAuthorities() != null) {
            authorities.addAll(oidcUser.getAuthorities());
        }
        if (user.getRole() != null) {
            authorities.add(new SimpleGrantedAuthority(user.getRole().name()));
        }

        // Determine the principal name attribute key (default to 'sub')
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();

        if (userNameAttributeName == null || userNameAttributeName.isBlank()) {
            userNameAttributeName = IdTokenClaimNames.SUB;
        }

        return new CustomOidcUser(
                authorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                userNameAttributeName,
                user
        );
    }
}

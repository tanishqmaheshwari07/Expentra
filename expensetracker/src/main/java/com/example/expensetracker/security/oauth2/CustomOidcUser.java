package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.Collection;

/**
 * Custom OIDC user principal that wraps Spring Security's DefaultOidcUser,
 * preserving all standard OpenID Connect ID token and UserInfo claims and authorities,
 * while exposing the underlying application User entity via OAuth2UserPrincipal.
 */
@Getter
public class CustomOidcUser extends DefaultOidcUser implements OAuth2UserPrincipal {

    private final User user;

    public CustomOidcUser(Collection<? extends GrantedAuthority> authorities,
                          OidcIdToken idToken,
                          OidcUserInfo userInfo,
                          String nameAttributeKey,
                          User user) {
        super(authorities, idToken, userInfo, (nameAttributeKey != null && !nameAttributeKey.isBlank()) ? nameAttributeKey : "sub");
        this.user = user;
    }

    public CustomOidcUser(Collection<? extends GrantedAuthority> authorities,
                          OidcIdToken idToken,
                          OidcUserInfo userInfo,
                          User user) {
        super(authorities, idToken, userInfo);
        this.user = user;
    }

    @Override
    public User getUser() {
        return user;
    }
}

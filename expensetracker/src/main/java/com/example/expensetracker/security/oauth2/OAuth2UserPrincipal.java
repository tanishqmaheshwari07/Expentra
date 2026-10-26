package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.entity.User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Common interface implemented by application OAuth2/OIDC user principals
 * allowing unified access to the underlying application User entity.
 */
public interface OAuth2UserPrincipal extends OAuth2User {

    /**
     * Returns the application User entity associated with this principal.
     */
    User getUser();
}

package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        return processOAuth2User(oAuth2User);
    }

    public CustomOAuth2User processOAuth2User(OAuth2User oAuth2User) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        Object subObj = attributes != null ? (attributes.get("sub") != null ? attributes.get("sub") : attributes.get("id")) : null;
        String sub = subObj != null ? String.valueOf(subObj).trim() : null;
        Object emailObj = attributes != null ? attributes.get("email") : null;
        String email = emailObj != null ? String.valueOf(emailObj).trim() : null;
        Object nameObj = attributes != null ? attributes.get("name") : null;
        String name = nameObj != null ? String.valueOf(nameObj).trim() : null;

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_email"),
                    "Email not found from OAuth2 provider payload"
            );
        }

        if (sub == null || sub.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_sub"),
                    "Provider subject identifier (sub) not found in OAuth2 payload"
            );
        }

        String normalizedEmail = email.trim().toLowerCase();

        // 1. Check if user already exists with Google provider & sub ID
        Optional<User> existingGoogleUser = userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, sub);
        if (existingGoogleUser.isPresent()) {
            User user = existingGoogleUser.get();
            log.info("Found existing Google user with ID: {} for email: {}", user.getId(), normalizedEmail);
            return new CustomOAuth2User(user, attributes);
        }

        // 2. Check if an account already exists with this email (e.g. LOCAL email/password account)
        Optional<User> existingUserByEmail = userRepository.findByEmail(normalizedEmail);
        if (existingUserByEmail.isPresent()) {
            log.warn("Account conflict detected for email '{}'. Account exists with provider: {}",
                    normalizedEmail, existingUserByEmail.get().getAuthProvider());
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_conflict"),
                    "An account with email " + normalizedEmail + " already exists. Please sign in with your email and password."
            );
        }

        // 3. Auto-provision new Google User
        User newUser = User.builder()
                .name((name != null && !name.isBlank()) ? name.trim() : normalizedEmail.split("@")[0])
                .email(normalizedEmail)
                .authProvider(AuthProvider.GOOGLE)
                .providerId(sub)
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(true)
                .password(null)
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("Successfully registered new Google OAuth user with ID: {} and email: {}", savedUser.getId(), normalizedEmail);
        return new CustomOAuth2User(savedUser, attributes);
    }
}

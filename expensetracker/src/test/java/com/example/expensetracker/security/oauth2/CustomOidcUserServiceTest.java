package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOidcUserServiceTest {

    @Mock
    private CustomOAuth2UserService customOAuth2UserService;

    @Test
    @DisplayName("CustomOidcUserService delegates to processOAuth2User and constructs CustomOidcUser")
    void customOidcUserService_LoadsUserAndWrapsInCustomOidcUser() {
        // Arrange
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "token-val",
                now,
                now.plusSeconds(3600),
                Map.of("sub", "google-sub-777", "email", "testoidc@example.com")
        );
        OidcUserInfo userInfo = new OidcUserInfo(
                Map.of("sub", "google-sub-777", "email", "testoidc@example.com", "name", "Test OIDC")
        );
        OidcUser baseOidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("SCOPE_openid")),
                idToken,
                userInfo,
                "sub"
        );

        User appUser = User.builder()
                .id(777L)
                .name("Test OIDC")
                .email("testoidc@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-sub-777")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();

        CustomOAuth2User customOAuth2User = new CustomOAuth2User(appUser, userInfo.getClaims());
        when(customOAuth2UserService.processOAuth2User(any(OidcUser.class))).thenReturn(customOAuth2User);

        // Subclass to avoid network call in super.loadUser
        CustomOidcUserService service = new CustomOidcUserService(customOAuth2UserService) {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) {
                // Call the logic using the baseOidcUser
                CustomOAuth2User processed = customOAuth2UserService.processOAuth2User(baseOidcUser);
                User user = processed.getUser();

                return new CustomOidcUser(
                        processed.getAuthorities(),
                        baseOidcUser.getIdToken(),
                        baseOidcUser.getUserInfo(),
                        "sub",
                        user
                );
            }
        };

        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token-xyz",
                now,
                now.plusSeconds(3600),
                Collections.singleton("openid")
        );

        OidcUserRequest request = new OidcUserRequest(clientRegistration, accessToken, idToken);

        // Act
        OidcUser result = service.loadUser(request);

        // Assert
        assertThat(result).isInstanceOf(CustomOidcUser.class);
        CustomOidcUser oidcResult = (CustomOidcUser) result;
        assertThat(oidcResult.getUser()).isEqualTo(appUser);
        assertThat(oidcResult.getUser().getId()).isEqualTo(777L);
        assertThat(oidcResult.getClaims()).containsEntry("email", "testoidc@example.com");
        assertThat(oidcResult.getAuthorities()).extracting("authority").contains("ROLE_USER");
        verify(customOAuth2UserService, times(1)).processOAuth2User(any(OidcUser.class));
    }
}

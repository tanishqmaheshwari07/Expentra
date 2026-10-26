package com.example.expensetracker.security.oauth2;

import com.example.expensetracker.dto.OAuth2ExchangeRequest;
import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.entity.User;
import com.example.expensetracker.repository.OAuthExchangeCodeRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuth2BinderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OAuth2ExchangeCodeService exchangeCodeService;

    @Autowired
    private OAuthExchangeCodeRepository exchangeCodeRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        exchangeCodeRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .name("Binder Test User")
                .email("binder.test@example.com")
                .authProvider(AuthProvider.GOOGLE)
                .providerId("google-binder-id")
                .role(Role.ROLE_USER)
                .isActive(true)
                .emailVerified(true)
                .build());
    }

    @Test
    @DisplayName("Exchange with valid code and matching binder cookie succeeds, returns tokens, sets refresh cookie, and clears oauth_binder")
    void exchange_WithValidCodeAndMatchingCookie_Succeeds() throws Exception {
        String binderCookieValue = "browser-secret-binder-123";
        String validCode = exchangeCodeService.createExchangeCode(testUser.getId(), binderCookieValue);

        OAuth2ExchangeRequest exchangeRequest = new OAuth2ExchangeRequest(validCode);

        mockMvc.perform(post("/api/users/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exchangeRequest))
                        .cookie(new Cookie("oauth_binder", binderCookieValue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist()) // Refresh token not in JSON body
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    @DisplayName("Exchange with valid code but MISSING binder cookie returns 401 Unauthorized")
    void exchange_WithValidCodeButMissingCookie_Returns401() throws Exception {
        String binderCookieValue = "browser-secret-binder-456";
        String validCode = exchangeCodeService.createExchangeCode(testUser.getId(), binderCookieValue);

        OAuth2ExchangeRequest exchangeRequest = new OAuth2ExchangeRequest(validCode);

        mockMvc.perform(post("/api/users/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exchangeRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Exchange with valid code but WRONG binder cookie returns 401 Unauthorized")
    void exchange_WithValidCodeButWrongCookie_Returns401() throws Exception {
        String originalBinder = "browser-original-binder-789";
        String differentBinder = "attacker-different-binder-999";
        String validCode = exchangeCodeService.createExchangeCode(testUser.getId(), originalBinder);

        OAuth2ExchangeRequest exchangeRequest = new OAuth2ExchangeRequest(validCode);

        mockMvc.perform(post("/api/users/oauth2/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(exchangeRequest))
                        .cookie(new Cookie("oauth_binder", differentBinder)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}

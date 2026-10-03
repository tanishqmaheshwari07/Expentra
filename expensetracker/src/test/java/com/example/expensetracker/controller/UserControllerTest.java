package com.example.expensetracker.controller;

import com.example.expensetracker.config.JwtProperties;
import com.example.expensetracker.dto.ApiResponse;
import com.example.expensetracker.dto.AuthResponse;
import com.example.expensetracker.dto.LoginRequest;
import com.example.expensetracker.dto.ResendVerificationRequest;
import com.example.expensetracker.dto.UserRequest;
import com.example.expensetracker.dto.UserResponse;
import com.example.expensetracker.entity.AuthProvider;
import com.example.expensetracker.entity.Role;
import com.example.expensetracker.exception.EmailNotVerifiedException;
import com.example.expensetracker.exception.GlobalExceptionHandler;
import com.example.expensetracker.exception.InvalidVerificationTokenException;
import com.example.expensetracker.exception.ResendCooldownException;
import com.example.expensetracker.security.JwtAuthenticationFilter;
import com.example.expensetracker.service.JwtService;
import com.example.expensetracker.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtProperties jwtProperties;

    @Test
    @DisplayName("POST /api/users/register - Should return 201 with verify instructions")
    void registerUser_Success() throws Exception {
        UserRequest request = new UserRequest();
        request.setName("John Doe");
        request.setEmail("john@example.com");
        request.setPassword("securePassword123");

        UserResponse userResponse = UserResponse.builder()
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .role(Role.ROLE_USER)
                .authProvider(AuthProvider.LOCAL)
                .emailVerified(false)
                .build();

        when(userService.createUser(any(UserRequest.class))).thenReturn(userResponse);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully. Please check your email to verify your account."))
                .andExpect(jsonPath("$.data.email").value("john@example.com"))
                .andExpect(jsonPath("$.data.emailVerified").value(false));
    }

    @Test
    @DisplayName("POST /api/users/login - When unverified, should return 403 Forbidden with EMAIL_NOT_VERIFIED")
    void login_UnverifiedUser_Returns403() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@example.com");
        request.setPassword("securePassword123");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new EmailNotVerifiedException("EMAIL_NOT_VERIFIED: Please verify your email before logging in."));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("EMAIL_NOT_VERIFIED: Please verify your email before logging in."));
    }

    @Test
    @DisplayName("GET /api/users/verify-email?token=... - Valid token returns 200 OK")
    void verifyEmail_Success() throws Exception {
        doNothing().when(userService).verifyEmail("valid-token-123");

        mockMvc.perform(get("/api/users/verify-email")
                        .param("token", "valid-token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email verified successfully. You can now log in."));

        verify(userService, times(1)).verifyEmail("valid-token-123");
    }

    @Test
    @DisplayName("GET /api/users/verify-email?token=... - Expired or invalid token returns 400 Bad Request")
    void verifyEmail_InvalidToken_Returns400() throws Exception {
        doThrow(new InvalidVerificationTokenException("Verification token has expired. Please request a new one."))
                .when(userService).verifyEmail("expired-token-123");

        mockMvc.perform(get("/api/users/verify-email")
                        .param("token", "expired-token-123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Verification token has expired. Please request a new one."));
    }

    @Test
    @DisplayName("POST /api/users/resend-verification - Valid email sends email and returns 200 OK")
    void resendVerification_Success() throws Exception {
        ResendVerificationRequest request = ResendVerificationRequest.builder()
                .email("john@example.com")
                .build();

        doNothing().when(userService).resendVerificationEmail(any(ResendVerificationRequest.class));

        mockMvc.perform(post("/api/users/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("If an unverified account with this email exists, a verification email has been sent."));

        verify(userService, times(1)).resendVerificationEmail(any(ResendVerificationRequest.class));
    }

    @Test
    @DisplayName("POST /api/users/resend-verification - When in cooldown period, returns 429 Too Many Requests")
    void resendVerification_Cooldown_Returns429() throws Exception {
        ResendVerificationRequest request = ResendVerificationRequest.builder()
                .email("john@example.com")
                .build();

        doThrow(new ResendCooldownException("Please wait 45 seconds before requesting another verification email."))
                .when(userService).resendVerificationEmail(any(ResendVerificationRequest.class));

        mockMvc.perform(post("/api/users/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Please wait 45 seconds before requesting another verification email."));
    }
}

package com.example.expensetracker.controller;

import com.example.expensetracker.dto.AdminUserResponse;
import com.example.expensetracker.dto.ApiResponse;
import com.example.expensetracker.dto.AuthResponse;
import com.example.expensetracker.dto.LoginRequest;
import com.example.expensetracker.dto.OAuth2ExchangeRequest;
import com.example.expensetracker.dto.PasswordUpdateRequest;
import com.example.expensetracker.dto.ProfileUpdateRequest;
import com.example.expensetracker.dto.RefreshTokenRequest;
import com.example.expensetracker.dto.ResendVerificationRequest;
import com.example.expensetracker.dto.RoleUpdateRequest;
import com.example.expensetracker.dto.UserRequest;
import com.example.expensetracker.dto.UserResponse;
import com.example.expensetracker.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import com.example.expensetracker.config.JwtProperties;
import com.example.expensetracker.exception.UnauthorizedAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User & Authentication", description = "Endpoints for user registration, authentication, token rotation, and profile management")
public class UserController {

    private final UserService userService;
    private final JwtProperties jwtProperties;

    @Operation(summary = "Register a new user", description = "Creates a new user account with default ROLE_USER")
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody UserRequest userRequest) {
        UserResponse response = userService.createUser(userRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "User registered successfully"));
    }

    @Operation(summary = "Register user (alias)", description = "Alias endpoint for registering a new user")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> registerUser(
            @Valid @RequestBody UserRequest userRequest) {
        UserResponse response = userService.createUser(userRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "User registered successfully. Please check your email to verify your account."));
    }

    @Operation(summary = "Verify email address", description = "Verifies user account using the single-use token sent via email")
    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @RequestParam("token") String token) {
        userService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.ok(null, "Email verified successfully. You can now log in."));
    }

    @Operation(summary = "Resend verification email", description = "Sends a new verification email if account is unverified and cooldown has elapsed")
    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        userService.resendVerificationEmail(request);
        return ResponseEntity.ok(ApiResponse.ok(null, "If an unverified account with this email exists, a verification email has been sent."));
    }

    @Operation(summary = "Authenticate user", description = "Authenticates user credentials, sets HttpOnly refresh cookie, and returns access token")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse response = userService.login(loginRequest);
        String rawRefreshToken = response.getRefreshToken();

        ResponseCookie cookie = createRefreshTokenCookie(
                rawRefreshToken,
                jwtProperties.getRefreshTokenExpirationMs() / 1000
        );

        // Sanitize response to never expose refresh token to JavaScript
        AuthResponse sanitizedResponse = AuthResponse.builder()
                .accessToken(response.getAccessToken())
                .tokenType(response.getTokenType())
                .expiresInMs(response.getExpiresInMs())
                .user(response.getUser())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(sanitizedResponse, "Login successful"));
    }

    @Operation(summary = "Exchange OAuth2 code", description = "Consumes a one-time OAuth2 exchange code, issues JWT access token, and sets refresh token cookie")
    @PostMapping("/oauth2/exchange")
    public ResponseEntity<ApiResponse<AuthResponse>> exchangeOAuth2Code(
            @Valid @RequestBody OAuth2ExchangeRequest exchangeRequest,
            @CookieValue(name = "oauth_binder", required = false) String binderCookie) {
        AuthResponse response = userService.exchangeOAuth2Code(exchangeRequest, binderCookie);
        String rawRefreshToken = response.getRefreshToken();

        ResponseCookie cookie = createRefreshTokenCookie(
                rawRefreshToken,
                jwtProperties.getRefreshTokenExpirationMs() / 1000
        );

        // Clear the oauth_binder cookie after successful exchange
        ResponseCookie clearBinderCookie = ResponseCookie.from("oauth_binder", "")
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite(jwtProperties.getCookieSameSite())
                .path("/")
                .maxAge(0)
                .build();

        // Sanitize response to never expose refresh token to JavaScript
        AuthResponse sanitizedResponse = AuthResponse.builder()
                .accessToken(response.getAccessToken())
                .tokenType(response.getTokenType())
                .expiresInMs(response.getExpiresInMs())
                .user(response.getUser())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.SET_COOKIE, clearBinderCookie.toString())
                .body(ApiResponse.ok(sanitizedResponse, "OAuth2 authentication successful"));
    }

    @Operation(summary = "Refresh access token", description = "Generates a new access token and rotates the refresh token using HttpOnly cookie")
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestBody(required = false) RefreshTokenRequest request) {

        String tokenStr = (cookieRefreshToken != null && !cookieRefreshToken.isBlank())
                ? cookieRefreshToken
                : (request != null ? request.getRefreshToken() : null);

        if (tokenStr == null || tokenStr.isBlank()) {
            throw new UnauthorizedAccessException("No refresh token provided in cookie or request body");
        }

        AuthResponse response = userService.refreshTokenByTokenString(tokenStr);
        String newRotatedRefreshToken = response.getRefreshToken();

        ResponseCookie cookie = createRefreshTokenCookie(
                newRotatedRefreshToken,
                jwtProperties.getRefreshTokenExpirationMs() / 1000
        );

        // Sanitize response to never expose refresh token to JavaScript
        AuthResponse sanitizedResponse = AuthResponse.builder()
                .accessToken(response.getAccessToken())
                .tokenType(response.getTokenType())
                .expiresInMs(response.getExpiresInMs())
                .user(response.getUser())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(sanitizedResponse, "Token refreshed successfully"));
    }

    @Operation(summary = "Logout user", description = "Revokes refresh token in database and clears HttpOnly refresh cookie")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken) {

        if (cookieRefreshToken != null && !cookieRefreshToken.isBlank()) {
            userService.logoutByRefreshToken(cookieRefreshToken);
        }

        ResponseCookie cleanCookie = createRefreshTokenCookie("", 0);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
                .body(ApiResponse.ok(null, "Logged out successfully"));
    }

    private ResponseCookie createRefreshTokenCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite(jwtProperties.getCookieSameSite())
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    @Operation(summary = "Get all users (Admin only)", description = "Retrieves directory of all registered users with financial statistics", security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> getAllUsers() {
        List<AdminUserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.ok(users, "Users retrieved successfully"));
    }

    @Operation(summary = "Get current user profile", description = "Retrieves profile of currently authenticated user", security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UserResponse response = userService.getCurrentUserProfile();
        return ResponseEntity.ok(ApiResponse.ok(response, "Profile retrieved successfully"));
    }

    @Operation(summary = "Get user by ID", description = "Retrieves user details by ID (own account or admin)", security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable Long id) {
        UserResponse response = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.ok(response, "User retrieved successfully"));
    }

    @Operation(summary = "Update user profile", description = "Updates user name and email", security = @SecurityRequirement(name = "BearerAuth"))
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody ProfileUpdateRequest request) {
        UserResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Profile updated successfully"));
    }

    @Operation(summary = "Change user password", description = "Updates user password after verifying current password", security = @SecurityRequirement(name = "BearerAuth"))
    @PutMapping("/{id}/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable Long id,
            @Valid @RequestBody PasswordUpdateRequest request) {
        userService.changePassword(id, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password updated successfully"));
    }

    @Operation(summary = "Update user role (Admin only)", description = "Updates a user's role in the system", security = @SecurityRequirement(name = "BearerAuth"))
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request) {
        UserResponse response = userService.updateUserRole(id, request.getRole());
        return ResponseEntity.ok(ApiResponse.ok(response, "User role updated successfully"));
    }

    @Operation(summary = "Delete user", description = "Deletes user account and associated expenses", security = @SecurityRequirement(name = "BearerAuth"))
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "User deleted successfully"));
    }
}
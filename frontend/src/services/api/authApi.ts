import apiClient from './client';
import {
  ApiResponse,
  AuthResponse,
  LoginRequest,
  User,
  AdminUser,
  UserRequest,
  ProfileUpdateRequest,
  PasswordUpdateRequest,
  Role,
} from '../../types';

export const authApi = {
  /**
   * Register a new user account
   * POST /api/users/register (or /api/users)
   */
  register: async (payload: UserRequest): Promise<ApiResponse<User>> => {
    const res = await apiClient.post<ApiResponse<User>>('/users/register', payload);
    return res.data;
  },

  /**
   * Authenticate user credentials and retrieve JWT tokens
   * POST /api/users/login (sets HttpOnly refresh cookie)
   */
  login: async (payload: LoginRequest): Promise<ApiResponse<AuthResponse>> => {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/users/login', payload);
    return res.data;
  },

  /**
   * Exchange one-time OAuth2 code for application JWT and HttpOnly refresh cookie
   * POST /api/users/oauth2/exchange
   */
  exchangeOAuth2Code: async (code: string): Promise<ApiResponse<AuthResponse>> => {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/users/oauth2/exchange', { code });
    return res.data;
  },

  /**
   * Refresh JWT access token with HttpOnly refresh cookie
   * POST /api/users/refresh-token
   */
  refreshToken: async (): Promise<ApiResponse<AuthResponse>> => {
    const res = await apiClient.post<ApiResponse<AuthResponse>>('/users/refresh-token', {});
    return res.data;
  },

  /**
   * Logout current user and clear HttpOnly refresh cookie on server
   * POST /api/users/logout
   */
  logout: async (): Promise<ApiResponse<void>> => {
    const res = await apiClient.post<ApiResponse<void>>('/users/logout', {});
    return res.data;
  },

  /**
   * Get current authenticated user profile
   * GET /api/users/me
   */
  getMe: async (): Promise<ApiResponse<User>> => {
    const res = await apiClient.get<ApiResponse<User>>('/users/me');
    return res.data;
  },

  /**
   * Get all registered users with summary statistics (Admin only)
   * GET /api/users
   */
  getUsers: async (): Promise<ApiResponse<AdminUser[]>> => {
    const res = await apiClient.get<ApiResponse<AdminUser[]>>('/users');
    return res.data;
  },

  /**
   * Update user profile (name, email)
   * PUT /api/users/{id}
   */
  updateProfile: async (id: number, payload: ProfileUpdateRequest): Promise<ApiResponse<User>> => {
    const res = await apiClient.put<ApiResponse<User>>(`/users/${id}`, payload);
    return res.data;
  },

  /**
   * Update user password
   * PUT /api/users/{id}/password
   */
  updatePassword: async (id: number, payload: PasswordUpdateRequest): Promise<ApiResponse<void>> => {
    const res = await apiClient.put<ApiResponse<void>>(`/users/${id}/password`, payload);
    return res.data;
  },

  /**
   * Update user details (backward compatible alias)
   * PUT /api/users/{id}
   */
  updateUser: async (id: number, payload: ProfileUpdateRequest | UserRequest): Promise<ApiResponse<User>> => {
    const res = await apiClient.put<ApiResponse<User>>(`/users/${id}`, payload);
    return res.data;
  },

  /**
   * Update user role (Admin only)
   * PATCH /api/users/{id}/role
   */
  updateUserRole: async (id: number, role: Role): Promise<ApiResponse<User>> => {
    const res = await apiClient.patch<ApiResponse<User>>(`/users/${id}/role`, { role });
    return res.data;
  },

  /**
   * Delete user account by ID
   * DELETE /api/users/{id}
   */
  deleteUser: async (id: number): Promise<ApiResponse<void>> => {
    const res = await apiClient.delete<ApiResponse<void>>(`/users/${id}`);
    return res.data;
  },

  /**
   * Verify email address with single-use token
   * GET /api/users/verify-email?token=...
   */
  verifyEmail: async (token: string): Promise<ApiResponse<void>> => {
    const res = await apiClient.get<ApiResponse<void>>('/users/verify-email', {
      params: { token },
    });
    return res.data;
  },

  /**
   * Resend verification email
   * POST /api/users/resend-verification
   */
  resendVerification: async (email: string): Promise<ApiResponse<void>> => {
    const res = await apiClient.post<ApiResponse<void>>('/users/resend-verification', { email });
    return res.data;
  },
};

/**
 * Returns the full backend OAuth2 authorization URL for Google
 * derived from the existing API base URL configuration.
 */
export const getGoogleAuthUrl = (): string => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
  // Remove trailing /api or /api/ to get backend root URL
  const hostUrl = baseUrl.replace(/\/api\/?$/, '');
  return `${hostUrl}/oauth2/authorization/google`;
};

export default authApi;

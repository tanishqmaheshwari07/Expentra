import React, { useState, useEffect } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { Eye, EyeOff, AlertCircle } from 'lucide-react';
import { Card, Button, Input } from '../components/ui';
import { authApi, getGoogleAuthUrl } from '../services/api/authApi';
import { useAuthStore } from '../store/authStore';
import { useToast } from '../components/ui/Toast';
import { LoginRequest } from '../types';
import { parseApiError, applyServerFieldErrors } from '../utils/errorHandling';

const GoogleIcon: React.FC<{ className?: string }> = ({ className = 'w-4 h-4' }) => (
  <svg className={className} viewBox="0 0 24 24">
    <path
      fill="#4285F4"
      d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
    />
    <path
      fill="#34A853"
      d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
    />
    <path
      fill="#FBBC05"
      d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
    />
    <path
      fill="#EA4335"
      d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
    />
  </svg>
);

const loginSchema = z.object({
  email: z
    .string()
    .min(1, 'Email is required')
    .email('Please enter a valid email address'),
  password: z
    .string()
    .min(1, 'Password is required'),
});

type LoginFormData = z.infer<typeof loginSchema>;

export const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuthStore();
  const { showToast } = useToast();

  const [showPassword, setShowPassword] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isUnverified, setIsUnverified] = useState(false);
  const [unverifiedEmail, setUnverifiedEmail] = useState('');
  const [isResending, setIsResending] = useState(false);
  const [resendSuccessMsg, setResendSuccessMsg] = useState<string | null>(null);

  // Check and display error query parameters returned from OAuth redirect
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const error = params.get('error');

    if (error) {
      if (error === 'account_conflict') {
        setErrorMessage(
          'This Google account is already associated with a different login method. Please sign in with your existing account.'
        );
      } else if (error === 'email_not_verified') {
        setErrorMessage(
          'Your Google account email address is not verified. Please verify your email with Google and try again.'
        );
      } else if (error === 'account_disabled') {
        setErrorMessage('This account is disabled. Please contact support.');
      } else if (error === 'oauth_failed') {
        setErrorMessage('Google sign-in was cancelled or could not be completed. Please try again.');
      } else {
        setErrorMessage('Google sign-in could not be completed. Please try again.');
      }

      // Clean the error parameter from browser URL without reloading
      window.history.replaceState({}, document.title, location.pathname);
    }
  }, [location.search, location.pathname]);

  const {
    register,
    handleSubmit,
    setValue,
    getValues,
    setError,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const loginMutation = useMutation({
    mutationFn: async (credentials: LoginRequest) => {
      const response = await authApi.login(credentials);
      return response.data;
    },
    onSuccess: (data) => {
      if (data) {
        login(data);
        showToast('success', 'Logged in successfully', `Welcome back${data.user?.name ? `, ${data.user.name}` : ''}!`);
        
        // Redirect to originating route or /dashboard
        const destination = (location.state as { from?: { pathname?: string } })?.from?.pathname || '/dashboard';
        navigate(destination, { replace: true });
      }
    },
    onError: (error: unknown) => {
      const parsed = parseApiError(error, 'Sign in failed. Please check your credentials and try again.');
      if (parsed.message.includes('EMAIL_NOT_VERIFIED')) {
        setIsUnverified(true);
        setUnverifiedEmail(getValues('email'));
        setErrorMessage('Please verify your email before logging in.');
      } else {
        setIsUnverified(false);
        setErrorMessage(parsed.message);
      }
      applyServerFieldErrors(error, setError);
    },
  });

  const handleResendFromLogin = async () => {
    const emailToUse = unverifiedEmail || getValues('email');
    if (!emailToUse || !emailToUse.includes('@')) {
      showToast('error', 'Error', 'Please enter your email address to resend verification.');
      return;
    }

    setIsResending(true);
    setResendSuccessMsg(null);

    try {
      const res = await authApi.resendVerification(emailToUse.trim());
      setResendSuccessMsg(res.message || 'Verification email sent. Please check your inbox.');
      showToast('success', 'Email Sent', 'A fresh verification link has been sent to your inbox.');
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to resend verification email.');
      showToast('error', 'Resend Failed', parsed.message);
    } finally {
      setIsResending(false);
    }
  };

  const onSubmit = (formData: LoginFormData) => {
    setErrorMessage(null);
    setIsUnverified(false);
    setResendSuccessMsg(null);
    loginMutation.mutate(formData);
  };

  const handleDemoFill = () => {
    setValue('email', 'alex.morgan@example.com');
    setValue('password', 'Password123!');
    setErrorMessage(null);
    setIsUnverified(false);
  };

  const handleGoogleLogin = () => {
    setErrorMessage(null);
    setIsUnverified(false);
    window.location.href = getGoogleAuthUrl();
  };

  return (
    <div className="min-h-screen bg-white flex items-center justify-center p-4">
      <div className="w-full max-w-[400px]">
        {/* Centered white Card */}
        <Card className="bg-white border-border shadow-card p-6 sm:p-8">
          <div className="text-center mb-6">
            <h1 className="text-2xl font-bold tracking-tight text-ink">Sign in</h1>
            <p className="text-sm text-muted mt-1">Access your expense tracker account</p>
          </div>

          {/* Error Banner */}
          {errorMessage && (
            <div className="mb-4 p-3.5 rounded-lg bg-red-50 border border-danger/20 text-danger text-sm font-medium animate-fadeIn">
              <div className="flex items-start gap-2.5">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <div className="flex-1">
                  <span>{errorMessage}</span>
                  {isUnverified && (
                    <div className="mt-2.5 pt-2 border-t border-danger/10">
                      <button
                        type="button"
                        onClick={handleResendFromLogin}
                        disabled={isResending}
                        className="text-xs font-semibold text-accent-end hover:underline focus:outline-none flex items-center gap-1.5"
                      >
                        {isResending ? 'Sending email...' : 'Resend verification email'}
                      </button>
                      {resendSuccessMsg && (
                        <p className="text-xs text-emerald-700 mt-1 font-normal">
                          {resendSuccessMsg}
                        </p>
                      )}
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Google Sign In Button */}
          <Button
            type="button"
            variant="secondary"
            onClick={handleGoogleLogin}
            leftIcon={<GoogleIcon className="w-4 h-4" />}
            className="w-full h-11 text-sm font-medium"
          >
            Continue with Google
          </Button>

          {/* Divider */}
          <div className="relative my-5">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-border" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-white px-2 text-muted">Or continue with email</span>
            </div>
          </div>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            {/* Email Field */}
            <div>
              <Input
                label="Email"
                type="email"
                placeholder="name@example.com"
                {...register('email')}
                error={errors.email?.message}
                disabled={loginMutation.isPending}
              />
            </div>

            {/* Password Field with Show/Hide Toggle */}
            <div>
              <Input
                label="Password"
                type={showPassword ? 'text' : 'password'}
                placeholder="Enter your password"
                {...register('password')}
                error={errors.password?.message}
                disabled={loginMutation.isPending}
                rightIcon={
                  <button
                    type="button"
                    onClick={() => setShowPassword((prev) => !prev)}
                    className="p-1 text-muted hover:text-ink focus:outline-none transition-colors"
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                }
              />
            </div>

            {/* Primary Black Pill Submit Button */}
            <div className="pt-2">
              <Button
                type="submit"
                variant="primary"
                className="w-full h-11 text-sm font-semibold"
                isLoading={loginMutation.isPending}
              >
                Sign In
              </Button>
            </div>
          </form>

          {/* Quick Demo Autofill Helper */}
          <div className="mt-4 pt-4 border-t border-border text-center">
            <button
              type="button"
              onClick={handleDemoFill}
              className="text-xs text-muted hover:text-ink transition-colors underline"
            >
              Autofill test credentials
            </button>
          </div>

          {/* Link Below in accent-end */}
          <div className="mt-6 text-center text-sm text-muted">
            Don't have an account?{' '}
            <Link to="/register" className="text-accent-end font-semibold hover:underline">
              Register
            </Link>
          </div>
        </Card>
      </div>
    </div>
  );
};

export default LoginPage;

import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { Eye, EyeOff, AlertCircle, Mail, ArrowRight } from 'lucide-react';
import { Card, Button, Input } from '../components/ui';
import { authApi, getGoogleAuthUrl } from '../services/api/authApi';
import { useToast } from '../components/ui/Toast';
import { UserRequest } from '../types';
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

const registerSchema = z
  .object({
    name: z
      .string()
      .min(2, 'Name must be at least 2 characters')
      .max(50, 'Name cannot exceed 50 characters'),
    email: z
      .string()
      .min(1, 'Email is required')
      .email('Please enter a valid email address'),
    password: z
      .string()
      .min(8, 'Password must be at least 8 characters'),
    confirmPassword: z
      .string()
      .min(1, 'Please confirm your password'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type RegisterFormData = z.infer<typeof registerSchema>;

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);
  const [isResending, setIsResending] = useState(false);
  const [resendStatusMsg, setResendStatusMsg] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      name: '',
      email: '',
      password: '',
      confirmPassword: '',
    },
  });

  const registerMutation = useMutation({
    mutationFn: async (payload: UserRequest) => {
      const response = await authApi.register(payload);
      return response.data;
    },
    onSuccess: (_, variables) => {
      setRegisteredEmail(variables.email);
      showToast('success', 'Account created!', 'Please check your email to verify your account.');
    },
    onError: (error: unknown) => {
      const parsed = parseApiError(error, 'Registration failed. Please try again.');
      setErrorMessage(parsed.message);
      applyServerFieldErrors(error, setError);
    },
  });

  const onSubmit = (data: RegisterFormData) => {
    setErrorMessage(null);
    setResendStatusMsg(null);
    registerMutation.mutate({
      name: data.name,
      email: data.email,
      password: data.password,
    });
  };

  const handleResend = async () => {
    if (!registeredEmail) return;
    setIsResending(true);
    setResendStatusMsg(null);
    try {
      const res = await authApi.resendVerification(registeredEmail);
      setResendStatusMsg(res.message || 'Verification email resent! Please check your inbox.');
      showToast('success', 'Email Sent', 'A fresh verification link has been sent.');
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to resend verification email.');
      setResendStatusMsg(parsed.message);
      showToast('error', 'Resend Failed', parsed.message);
    } finally {
      setIsResending(false);
    }
  };

  const handleGoogleLogin = () => {
    setErrorMessage(null);
    window.location.href = getGoogleAuthUrl();
  };

  if (registeredEmail) {
    return (
      <div className="min-h-screen bg-white flex items-center justify-center p-4">
        <div className="w-full max-w-[420px]">
          <Card className="bg-white border-border shadow-card p-6 sm:p-8 text-center space-y-6">
            <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-emerald-50 text-emerald-600 mx-auto">
              <Mail className="w-7 h-7 text-emerald-600" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-ink">Check your email</h1>
              <p className="text-sm text-muted mt-2 leading-relaxed">
                We've sent a verification link to <strong className="text-ink font-semibold">{registeredEmail}</strong>. Please click the link to activate your account.
              </p>
            </div>

            {resendStatusMsg && (
              <div className="p-3 rounded-lg bg-surface border border-border text-xs text-muted">
                {resendStatusMsg}
              </div>
            )}

            <div className="space-y-3 pt-2">
              <Button
                type="button"
                variant="primary"
                className="w-full h-11 text-sm font-semibold flex items-center justify-center gap-2"
                onClick={() => navigate('/login')}
              >
                <span>Go to Login</span>
                <ArrowRight className="w-4 h-4" />
              </Button>

              <button
                type="button"
                onClick={handleResend}
                disabled={isResending}
                className="text-xs font-semibold text-accent-end hover:underline focus:outline-none transition-colors"
              >
                {isResending ? 'Resending...' : "Didn't receive the email? Resend"}
              </button>
            </div>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-white flex items-center justify-center p-4">
      <div className="w-full max-w-[400px]">
        {/* Centered white Card */}
        <Card className="bg-white border-border shadow-card p-6 sm:p-8">
          <div className="text-center mb-6">
            <h1 className="text-2xl font-bold tracking-tight text-ink">Create an account</h1>
            <p className="text-sm text-muted mt-1">Start tracking your personal finances</p>
          </div>

          {/* Error Banner */}
          {errorMessage && (
            <div className="mb-4 p-3 rounded-lg bg-red-50 border border-danger/20 flex items-center gap-2.5 text-danger text-sm font-medium animate-fadeIn">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{errorMessage}</span>
            </div>
          )}

          {/* Google Sign In/Register Button */}
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
              <span className="bg-white px-2 text-muted">Or register with email</span>
            </div>
          </div>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            {/* Name Field */}
            <div>
              <Input
                label="Full Name"
                placeholder="Alex Morgan"
                {...register('name')}
                error={errors.name?.message}
                disabled={registerMutation.isPending}
              />
            </div>

            {/* Email Field */}
            <div>
              <Input
                label="Email"
                type="email"
                placeholder="name@example.com"
                {...register('email')}
                error={errors.email?.message}
                disabled={registerMutation.isPending}
              />
            </div>

            {/* Password Field */}
            <div>
              <Input
                label="Password"
                type={showPassword ? 'text' : 'password'}
                placeholder="Min. 8 characters"
                {...register('password')}
                error={errors.password?.message}
                disabled={registerMutation.isPending}
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

            {/* Confirm Password Field */}
            <div>
              <Input
                label="Confirm Password"
                type={showConfirmPassword ? 'text' : 'password'}
                placeholder="Re-enter your password"
                {...register('confirmPassword')}
                error={errors.confirmPassword?.message}
                disabled={registerMutation.isPending}
                rightIcon={
                  <button
                    type="button"
                    onClick={() => setShowConfirmPassword((prev) => !prev)}
                    className="p-1 text-muted hover:text-ink focus:outline-none transition-colors"
                    aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                  >
                    {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
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
                isLoading={registerMutation.isPending}
              >
                Create Account
              </Button>
            </div>
          </form>

          {/* Link Below in accent-end */}
          <div className="mt-6 text-center text-sm text-muted">
            Already have an account?{' '}
            <Link to="/login" className="text-accent-end font-semibold hover:underline">
              Log in
            </Link>
          </div>
        </Card>
      </div>
    </div>
  );
};

export default RegisterPage;

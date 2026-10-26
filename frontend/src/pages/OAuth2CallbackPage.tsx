import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { AlertCircle, ArrowLeft, Loader2 } from 'lucide-react';
import { Card, Button } from '../components/ui';
import { authApi } from '../services/api/authApi';
import { useAuthStore } from '../store/authStore';
import { useToast } from '../components/ui/Toast';
import { parseApiError } from '../utils/errorHandling';

export const OAuth2CallbackPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { login } = useAuthStore();
  const { showToast } = useToast();

  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState(true);

  // Prevent double execution in React StrictMode or re-renders
  const hasExchangedRef = useRef(false);

  useEffect(() => {
    // If already executed or processing completed, don't re-run
    if (hasExchangedRef.current) {
      return;
    }

    const code = searchParams.get('code');
    const error = searchParams.get('error');

    // Handle OAuth error returned directly from Spring Security / Google
    if (error) {
      hasExchangedRef.current = true;
      setIsProcessing(false);
      navigate(`/login?error=${encodeURIComponent(error)}`, { replace: true });
      return;
    }

    // Handle missing code
    if (!code) {
      hasExchangedRef.current = true;
      setIsProcessing(false);
      setErrorMessage('No authorization code was provided. Please try signing in again.');
      return;
    }

    // Mark as processed to prevent race conditions or duplicate exchange
    hasExchangedRef.current = true;

    const exchangeCode = async () => {
      try {
        const response = await authApi.exchangeOAuth2Code(code);
        const authData = response.data;

        if (authData && authData.accessToken) {
          // Store access token in-memory and user profile in Zustand auth store
          login(authData);

          // Clean up the one-time code from browser URL/history
          window.history.replaceState({}, document.title, '/oauth2/callback');

          showToast(
            'success',
            'Signed in with Google',
            `Welcome back${authData.user?.name ? `, ${authData.user.name}` : ''}!`
          );

          // Redirect to authenticated dashboard route
          navigate('/dashboard', { replace: true });
        } else {
          throw new Error('Incomplete authentication response received from server.');
        }
      } catch (err: unknown) {
        setIsProcessing(false);
        // Clean up the one-time code from URL on failure to avoid repeated submission
        window.history.replaceState({}, document.title, '/oauth2/callback');

        const parsed = parseApiError(err);
        const errorMsg = parsed.message || '';
        const lowerMsg = errorMsg.toLowerCase();

        // Check for account conflict or disabled account
        if (lowerMsg.includes('account_conflict') || lowerMsg.includes('already exists')) {
          navigate('/login?error=account_conflict', { replace: true });
          return;
        }

        if (lowerMsg.includes('disabled') || lowerMsg.includes('account_disabled')) {
          navigate('/login?error=account_disabled', { replace: true });
          return;
        }

        setErrorMessage(
          errorMsg || 'Google sign-in could not be completed or the session has expired. Please try signing in again.'
        );
      }
    };

    exchangeCode();
  }, [searchParams, navigate, login, showToast]);

  return (
    <div className="min-h-screen bg-white flex items-center justify-center p-4">
      <div className="w-full max-w-[420px]">
        <Card className="bg-white border-border shadow-card p-6 sm:p-8 text-center">
          {isProcessing ? (
            <div className="py-6 flex flex-col items-center justify-center space-y-4">
              <div className="w-12 h-12 rounded-full bg-surface flex items-center justify-center text-accent-start animate-spin">
                <Loader2 className="w-6 h-6 text-ink" />
              </div>
              <div>
                <h2 className="text-xl font-bold tracking-tight text-ink">Signing you in with Google...</h2>
                <p className="text-sm text-muted mt-1">Please wait while we set up your secure session.</p>
              </div>
            </div>
          ) : (
            <div className="py-4 space-y-5">
              <div className="w-12 h-12 rounded-full bg-red-50 text-danger mx-auto flex items-center justify-center">
                <AlertCircle className="w-6 h-6" />
              </div>
              <div>
                <h2 className="text-xl font-bold tracking-tight text-ink">Authentication Failed</h2>
                <p className="text-sm text-muted mt-1.5">{errorMessage}</p>
              </div>
              <div className="pt-2">
                <Link to="/login" replace>
                  <Button variant="primary" className="w-full h-11 text-sm font-semibold" leftIcon={<ArrowLeft className="w-4 h-4" />}>
                    Back to Sign In
                  </Button>
                </Link>
              </div>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
};

export default OAuth2CallbackPage;

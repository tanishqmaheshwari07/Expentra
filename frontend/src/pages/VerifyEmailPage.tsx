import React, { useState, useEffect } from 'react';
import { useSearchParams, Link, useNavigate } from 'react-router-dom';
import { CheckCircle2, AlertCircle, Loader2, Mail, ArrowRight } from 'lucide-react';
import { Card, Button, Input } from '../components/ui';
import { authApi } from '../services/api/authApi';
import { parseApiError } from '../utils/errorHandling';

export const VerifyEmailPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();

  const [status, setStatus] = useState<'loading' | 'success' | 'error' | 'no-token'>('loading');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Resend flow state
  const [resendEmail, setResendEmail] = useState('');
  const [isResending, setIsResending] = useState(false);
  const [resendStatus, setResendStatus] = useState<'idle' | 'success' | 'error'>('idle');
  const [resendMessage, setResendMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!token || token.trim() === '') {
      setStatus('no-token');
      return;
    }

    let isMounted = true;
    setStatus('loading');

    authApi
      .verifyEmail(token.trim())
      .then(() => {
        if (isMounted) {
          setStatus('success');
        }
      })
      .catch((err) => {
        if (isMounted) {
          const parsed = parseApiError(err, 'Failed to verify email. The link may be expired or invalid.');
          setErrorMessage(parsed.message);
          setStatus('error');
        }
      });

    return () => {
      isMounted = false;
    };
  }, [token]);

  const handleResend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!resendEmail || !resendEmail.includes('@')) {
      setResendStatus('error');
      setResendMessage('Please enter a valid email address.');
      return;
    }

    setIsResending(true);
    setResendStatus('idle');
    setResendMessage(null);

    try {
      const res = await authApi.resendVerification(resendEmail.trim());
      setResendStatus('success');
      setResendMessage(res.message || 'Verification email sent! Please check your inbox.');
    } catch (err) {
      const parsed = parseApiError(err, 'Could not send verification email. Please try again later.');
      setResendStatus('error');
      setResendMessage(parsed.message);
    } finally {
      setIsResending(false);
    }
  };

  return (
    <div className="min-h-screen bg-white flex items-center justify-center p-4">
      <div className="w-full max-w-[440px]">
        <Card className="bg-white border-border shadow-card p-6 sm:p-8">
          {/* Header Branding */}
          <div className="text-center mb-6">
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-full bg-primary/5 text-primary mb-3">
              <Mail className="w-6 h-6 text-accent-start" />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-ink">Email Verification</h1>
            <p className="text-sm text-muted mt-1">ExpenseTracker Account Confirmation</p>
          </div>

          {/* 1. Loading State */}
          {status === 'loading' && (
            <div className="py-8 text-center space-y-4 animate-fadeIn">
              <Loader2 className="w-10 h-10 text-primary animate-spin mx-auto" />
              <div>
                <h2 className="text-base font-semibold text-ink">Verifying your email...</h2>
                <p className="text-sm text-muted mt-1">Please wait while we confirm your verification token.</p>
              </div>
            </div>
          )}

          {/* 2. Success State */}
          {status === 'success' && (
            <div className="py-6 text-center space-y-5 animate-fadeIn">
              <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-emerald-50 text-emerald-600 mx-auto">
                <CheckCircle2 className="w-8 h-8" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-ink">Email Verified Successfully!</h2>
                <p className="text-sm text-muted mt-1.5 leading-relaxed">
                  Your email address has been confirmed. You now have full access to your ExpenseTracker account.
                </p>
              </div>
              <div className="pt-2">
                <Button
                  type="button"
                  variant="primary"
                  className="w-full h-11 text-sm font-semibold flex items-center justify-center gap-2"
                  onClick={() => navigate('/login')}
                >
                  <span>Proceed to Login</span>
                  <ArrowRight className="w-4 h-4" />
                </Button>
              </div>
            </div>
          )}

          {/* 3. Error State (Expired / Used / Invalid) */}
          {status === 'error' && (
            <div className="space-y-5 animate-fadeIn">
              <div className="text-center">
                <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-red-50 text-danger mx-auto mb-3">
                  <AlertCircle className="w-8 h-8" />
                </div>
                <h2 className="text-lg font-bold text-ink">Verification Failed</h2>
                <p className="text-sm text-danger mt-1 font-medium">
                  {errorMessage || 'The verification link is invalid or has expired.'}
                </p>
              </div>

              {/* Resend box */}
              <div className="mt-4 p-4 rounded-xl bg-surface border border-border space-y-3">
                <h3 className="text-xs font-semibold uppercase tracking-wider text-muted">
                  Request a New Verification Link
                </h3>
                <form onSubmit={handleResend} className="space-y-3">
                  <Input
                    type="email"
                    placeholder="Enter your email address"
                    value={resendEmail}
                    onChange={(e) => setResendEmail(e.target.value)}
                    disabled={isResending}
                    className="h-10 text-sm"
                  />
                  {resendMessage && (
                    <div
                      className={`p-2.5 rounded-lg text-xs font-medium ${
                        resendStatus === 'success'
                          ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                          : 'bg-red-50 text-danger border border-danger/20'
                      }`}
                    >
                      {resendMessage}
                    </div>
                  )}
                  <Button
                    type="submit"
                    variant="secondary"
                    className="w-full h-10 text-xs font-semibold"
                    isLoading={isResending}
                  >
                    Resend Verification Email
                  </Button>
                </form>
              </div>

              <div className="pt-2 text-center">
                <Link to="/login" className="text-sm text-accent-end font-semibold hover:underline">
                  Return to Login
                </Link>
              </div>
            </div>
          )}

          {/* 4. No Token Provided */}
          {status === 'no-token' && (
            <div className="py-6 text-center space-y-4 animate-fadeIn">
              <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-amber-50 text-amber-600 mx-auto">
                <AlertCircle className="w-8 h-8" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-ink">Invalid Verification Link</h2>
                <p className="text-sm text-muted mt-1 leading-relaxed">
                  No verification token was found. Please make sure you clicked the full link from your email.
                </p>
              </div>
              <div className="pt-2">
                <Button
                  type="button"
                  variant="primary"
                  className="w-full h-11 text-sm font-semibold"
                  onClick={() => navigate('/login')}
                >
                  Go to Login
                </Button>
              </div>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
};

export default VerifyEmailPage;

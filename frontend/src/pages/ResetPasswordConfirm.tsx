import { FormEvent, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { KeyRound } from 'lucide-react';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/hooks/useAuth';
import { getCombinedSearchParams } from '@/lib/utils/url';

export default function ResetPasswordConfirm() {
  const { confirmPasswordReset } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const params = getCombinedSearchParams(location);
  const accessToken = (params.get('access_token') ?? '').trim();
  const [password, setPassword] = useState('');
  const [confirmValue, setConfirmValue] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const redirectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (redirectTimer.current) {
        clearTimeout(redirectTimer.current);
      }
    };
  }, []);

  if (!accessToken) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary/10 via-background to-secondary/10 p-4">
        <Card className="w-full max-w-md card-gradient shadow-elegant">
          <CardHeader className="text-center space-y-3">
            <div className="flex justify-center">
              <div className="p-3 rounded-full bg-primary/10">
                <KeyRound className="h-8 w-8 text-primary" />
              </div>
            </div>
            <CardTitle className="text-2xl font-bold text-gradient">Reset link expired</CardTitle>
            <CardDescription>
              The reset link is invalid or has expired. Request a new password reset email to continue.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 text-center">
            <Button variant="link" asChild>
              <Link to="/auth/reset">Request a new reset email</Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setFormError(null);

    if (password.length < 6) {
      setFormError('Password must be at least 6 characters long.');
      return;
    }

    if (password !== confirmValue) {
      setFormError('Passwords do not match.');
      return;
    }

    setIsSubmitting(true);
    try {
      const { error } = await confirmPasswordReset(accessToken, password);
      if (error) {
        setFormError(error.message);
        return;
      }

      setSuccess(true);
      setPassword('');
      setConfirmValue('');
      redirectTimer.current = setTimeout(() => {
        navigate('/auth');
      }, 2000);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary/10 via-background to-secondary/10 p-4">
      <Card className="w-full max-w-md card-gradient shadow-elegant">
        <CardHeader className="text-center space-y-3">
          <div className="flex justify-center">
            <div className="p-3 rounded-full bg-primary/10">
              <KeyRound className="h-8 w-8 text-primary" />
            </div>
          </div>
          <CardTitle className="text-2xl font-bold text-gradient">
            {success ? 'Password updated' : 'Choose a new password'}
          </CardTitle>
          <CardDescription>
            {success
              ? 'Your password has been changed. Redirecting you to the sign-in page…'
              : 'Create a new password to secure your account.'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="space-y-2 text-left">
              <Label htmlFor="password">New password</Label>
              <Input
                id="password"
                type="password"
                required
                minLength={6}
                value={password}
                onChange={event => {
                  setPassword(event.target.value);
                  setFormError(null);
                }}
                disabled={isSubmitting || success}
              />
            </div>
            <div className="space-y-2 text-left">
              <Label htmlFor="confirmPassword">Confirm password</Label>
              <Input
                id="confirmPassword"
                type="password"
                required
                minLength={6}
                value={confirmValue}
                onChange={event => {
                  setConfirmValue(event.target.value);
                  setFormError(null);
                }}
                disabled={isSubmitting || success}
              />
            </div>
            {formError && <p className="text-sm text-destructive text-center">{formError}</p>}
            <Button type="submit" className="w-full" disabled={isSubmitting || success}>
              {success ? 'Redirecting…' : isSubmitting ? 'Updating…' : 'Update password'}
            </Button>
            {!success && (
              <p className="text-sm text-muted-foreground text-center">
                Changed your mind?{' '}
                <Button variant="link" size="sm" asChild className="p-0 h-auto">
                  <Link to="/auth">Back to sign in</Link>
                </Button>
              </p>
            )}
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

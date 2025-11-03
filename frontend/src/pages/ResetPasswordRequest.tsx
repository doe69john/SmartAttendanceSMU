import { FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { Mail } from 'lucide-react';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/hooks/useAuth';

export default function ResetPasswordRequest() {
  const { requestPasswordReset } = useAuth();
  const [email, setEmail] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const normalizedEmail = email.trim();
    if (!normalizedEmail) {
      return;
    }

    setIsSubmitting(true);
    try {
      const { error } = await requestPasswordReset(normalizedEmail);
      if (!error) {
        setSubmitted(true);
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const buttonLabel = submitted ? 'Email Sent' : isSubmitting ? 'Sending…' : 'Send reset link';

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary/10 via-background to-secondary/10 p-4">
      <Card className="w-full max-w-md card-gradient shadow-elegant">
        <CardHeader className="text-center space-y-3">
          <div className="flex justify-center">
            <div className="p-3 rounded-full bg-primary/10">
              <Mail className="h-8 w-8 text-primary" />
            </div>
          </div>
          <CardTitle className="text-2xl font-bold text-gradient">Reset your password</CardTitle>
          <CardDescription>
            Enter the email associated with your account and we&apos;ll send a link to reset your password.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="space-y-2 text-left">
              <Label htmlFor="email">Email address</Label>
              <Input
                id="email"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={event => {
                  setEmail(event.target.value);
                  setSubmitted(false);
                }}
                disabled={isSubmitting || submitted}
                placeholder="you@example.com"
              />
            </div>
            <Button type="submit" className="w-full" disabled={isSubmitting || submitted || !email.trim()}>
              {buttonLabel}
            </Button>
            {submitted ? (
              <p className="text-sm text-muted-foreground text-center">
                If an account exists for <span className="font-medium text-foreground">{email.trim()}</span>, you&apos;ll receive a
                reset email shortly.
              </p>
            ) : (
              <p className="text-sm text-muted-foreground text-center">
                Remembered your password?{' '}
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

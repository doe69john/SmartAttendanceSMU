import { useState, useEffect, useCallback, createContext, useContext, ReactNode, useMemo, useRef } from 'react';
import { useToast } from '@/hooks/use-toast';
import {
  ApiError,
  fetchCurrentUser,
  signIn as apiSignIn,
  signOut as apiSignOut,
  signUp as apiSignUp,
  requestPasswordReset as apiRequestPasswordReset,
  confirmPasswordReset as apiConfirmPasswordReset,
  SignInResponse,
} from '@/lib/api';
import { getAccessToken, setAccessToken as storeAccessToken, setUnauthorizedHandler } from '@/lib/authTokenStore';

interface AuthSession {
  accessToken: string | null;
  expiresIn?: number | null;
  expiresAt?: number | null;
  tokenType?: string | null;
}

interface AuthUser {
  id: string;
  email?: string;
  user_metadata?: Record<string, unknown>;
}

interface Profile {
  id: string;
  userId: string;
  email: string;
  fullName: string;
  role: 'student' | 'professor' | 'admin';
  staffId?: string;
  studentId?: string;
  avatarUrl?: string;
}

interface AuthContextType {
  session: AuthSession | null;
  user: AuthUser | null;
  profile: Profile | null;
  accessToken: string | null;
  loading: boolean;
  signUp: (email: string, password: string, userData: Record<string, unknown>) => Promise<{ error: Error | null }>;
  signIn: (email: string, password: string) => Promise<{ error: Error | null }>;
  signOut: () => Promise<void>;
  requestPasswordReset: (email: string) => Promise<{ error: Error | null }>;
  confirmPasswordReset: (accessToken: string, password: string) => Promise<{ error: Error | null }>;
  isStudent: boolean;
  isProfessor: boolean;
  isAdmin: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

type CurrentUser = Awaited<ReturnType<typeof fetchCurrentUser>>;
type ProfilePayload = NonNullable<CurrentUser>['profile'];

function mapProfile(payload: ProfilePayload | Record<string, unknown>): Profile {
  const source = (payload ?? {}) as Record<string, unknown>;
  const getString = (key: string, fallback = ''): string => {
    const value = source[key];
    return typeof value === 'string' ? value : fallback;
  };
  const getOptionalString = (...keys: string[]): string | undefined => {
    for (const key of keys) {
      const value = source[key];
      if (typeof value === 'string') {
        return value;
      }
    }
    return undefined;
  };
  return {
    id: getString('id'),
    userId: getString('userId', getString('user_id')),
    email: getString('email'),
    fullName: getString('fullName', getString('full_name', getString('email'))),
    role: (getString('role', 'student') as Profile['role']),
    staffId: getOptionalString('staffId', 'staff_id'),
    studentId: getOptionalString('studentId', 'student_id'),
    avatarUrl: getOptionalString('avatarUrl', 'avatar_url'),
  };
}

function deriveUserFromProfile(profile: Profile): AuthUser {
  return {
    id: profile.userId,
    email: profile.email,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const initialStoredToken = useRef<string | null>(getAccessToken());
  const [accessToken, setAccessTokenState] = useState<string | null>(initialStoredToken.current);
  const [loading, setLoading] = useState(true);
  const { toast } = useToast();
  const hasAuthenticatedRef = useRef(Boolean(initialStoredToken.current));

  const clearSession = useCallback(() => {
    setSession(null);
    setUser(null);
    setProfile(null);
    setAccessTokenState(null);
    storeAccessToken(null);
    hasAuthenticatedRef.current = false;
  }, []);

  const syncSessionState = useCallback((value: SignInResponse['session'] | null | undefined) => {
    if (!value) {
      clearSession();
      return;
    }
    const normalized: AuthSession = {
      accessToken: value.accessToken ?? null,
      expiresIn: value.expiresIn ?? null,
      expiresAt: value.expiresAt ?? null,
      tokenType: value.tokenType ?? null,
    };
    setSession(normalized);
    setAccessTokenState(normalized.accessToken ?? null);
    storeAccessToken(normalized.accessToken ?? null);
    if (value.user) {
      setUser({
        id: value.user.id,
        email: value.user.email,
        user_metadata: value.user.userMetadata ?? undefined,
      });
    }
    if (normalized.accessToken) {
      hasAuthenticatedRef.current = true;
    }
  }, [clearSession]);

  const loadProfile = useCallback(async () => {
    const token = accessToken ?? getAccessToken();
    if (!token) {
      return false;
    }
    try {
      const current = await fetchCurrentUser();
      if (current?.profile) {
        const mapped = mapProfile(current.profile);
        setProfile(mapped);
        setUser(existing => existing ?? deriveUserFromProfile(mapped));
        setSession(existing => existing ?? { accessToken: token });
        hasAuthenticatedRef.current = true;
        return true;
      }
      setProfile(null);
      return false;
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        return false;
      }
      console.error('Failed to load profile', error);
      setProfile(null);
      return false;
    }
  }, [accessToken]);

  const handleUnauthorized = useCallback(async () => {
    if (hasAuthenticatedRef.current) {
      toast({
        title: 'Session Expired',
        description: 'Please sign in again.',
        variant: 'destructive',
      });
    }
    clearSession();
    setLoading(false);
  }, [clearSession, toast]);

  useEffect(() => {
    setUnauthorizedHandler(handleUnauthorized);
    let cancelled = false;

    (async () => {
      setLoading(true);
      try {
        await loadProfile();
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();

    return () => {
      cancelled = true;
      setUnauthorizedHandler(null);
    };
  }, [handleUnauthorized, loadProfile]);

  const signUp = useCallback(async (email: string, password: string, userData: Record<string, unknown>) => {
    try {
      await apiSignUp(email, password, userData);
      toast({
        title: 'Sign Up Successful',
        description: 'Please check your email to confirm your account.',
      });
      return { error: null as Error | null };
    } catch (error) {
      const message = error instanceof ApiError ? error.message : error instanceof Error ? error.message : 'Sign up failed';
      const err = new Error(message);
      toast({
        title: 'Sign Up Error',
        description: err.message,
        variant: 'destructive',
      });
      return { error: err };
    }
  }, [toast]);

  const signIn = useCallback(async (email: string, password: string) => {
    setLoading(true);
    try {
      const response = await apiSignIn(email, password);
      syncSessionState(response.session);

      const profileErrorMessage = 'Sign-in failed-unable to load your profile';
      let profileLoaded: boolean;
      try {
        profileLoaded = await loadProfile();
      } catch (profileError) {
        console.error('Failed to load profile after sign-in', profileError);
        clearSession();
        const err = new Error(profileErrorMessage);
        toast({
          title: 'Sign In Error',
          description: err.message,
          variant: 'destructive',
        });
        return { error: err };
      }

      if (!profileLoaded) {
        clearSession();
        const err = new Error(profileErrorMessage);
        toast({
          title: 'Sign In Error',
          description: err.message,
          variant: 'destructive',
        });
        return { error: err };
      }

      toast({
        title: 'Welcome back!',
        description: 'You have successfully signed in.',
      });
      return { error: null as Error | null };
    } catch (error) {
      const message = error instanceof ApiError ? error.message : error instanceof Error ? error.message : 'Sign in failed';
      const err = new Error(message);
      toast({
        title: 'Sign In Error',
        description: err.message,
        variant: 'destructive',
      });
      clearSession();
      return { error: err };
    } finally {
      setLoading(false);
    }
  }, [clearSession, loadProfile, syncSessionState, toast]);

  const signOut = useCallback(async () => {
    setLoading(true);
    try {
      hasAuthenticatedRef.current = false;
      await apiSignOut();
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401) {
        console.warn('Sign out request failed', error);
      }
    } finally {
      clearSession();
      setLoading(false);
      toast({
        title: 'Signed Out',
        description: 'You have been successfully signed out.',
      });
    }
  }, [clearSession, toast]);

  const requestPasswordReset = useCallback(async (email: string) => {
    try {
      await apiRequestPasswordReset(email);
      toast({
        title: 'Reset Email Sent',
        description: 'Check your inbox for instructions to update your password.',
      });
      return { error: null as Error | null };
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.message
          : error instanceof Error
            ? error.message
            : 'Unable to process password reset';
      const err = new Error(message);
      toast({
        title: 'Password Reset Error',
        description: err.message,
        variant: 'destructive',
      });
      return { error: err };
    }
  }, [toast]);

  const confirmPasswordReset = useCallback(async (accessToken: string, password: string) => {
    try {
      await apiConfirmPasswordReset(accessToken, password);
      clearSession();
      toast({
        title: 'Password Updated',
        description: 'You can now sign in with your new password.',
      });
      return { error: null as Error | null };
    } catch (error) {
      const message =
        error instanceof ApiError
          ? error.message
          : error instanceof Error
            ? error.message
            : 'Unable to update password';
      const err = new Error(message);
      toast({
        title: 'Password Update Error',
        description: err.message,
        variant: 'destructive',
      });
      return { error: err };
    }
  }, [clearSession, toast]);

  const contextValue: AuthContextType = useMemo(() => ({
    session,
    user,
    profile,
    accessToken,
    loading,
    signUp,
    signIn,
    signOut,
    requestPasswordReset,
    confirmPasswordReset,
    isStudent: profile?.role === 'student',
    isProfessor: profile?.role === 'professor',
    isAdmin: profile?.role === 'admin',
  }), [session, user, profile, accessToken, loading, signUp, signIn, signOut, requestPasswordReset, confirmPasswordReset]);

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

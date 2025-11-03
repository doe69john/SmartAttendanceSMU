import { useAuth } from '@/hooks/useAuth';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

export default function AuthDebug() {
  const { session, user, profile, loading } = useAuth();

  // Only show in development
  if (process.env.NODE_ENV !== 'development') {
    return null;
  }

  return (
    <Card className="fixed bottom-4 right-4 max-w-sm opacity-80">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm">Auth Debug</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2 text-xs">
        <div className="flex items-center gap-2">
          <span>Loading:</span>
          <Badge variant={loading ? "destructive" : "secondary"}>
            {loading ? "true" : "false"}
          </Badge>
        </div>
        
        <div className="flex items-center gap-2">
          <span>Session:</span>
          <Badge variant={session ? "default" : "secondary"}>
            {session ? "authenticated" : "none"}
          </Badge>
        </div>
        
        <div className="flex items-center gap-2">
          <span>User:</span>
          <Badge variant={user ? "default" : "secondary"}>
            {user ? user.email : "none"}
          </Badge>
        </div>
        
        <div className="flex items-center gap-2">
          <span>Profile:</span>
          <Badge variant={profile ? "default" : "secondary"}>
            {profile ? `${profile.role} - ${profile.fullName}` : "none"}
          </Badge>
        </div>
        
        {profile && (
          <div className="pt-2 border-t text-xs text-muted-foreground">
            ID: {profile.studentId || profile.staffId || 'N/A'}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
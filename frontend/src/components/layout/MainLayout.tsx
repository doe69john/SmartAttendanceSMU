import { useState } from 'react';
import { Outlet, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import Sidebar from './Sidebar';
import LoadingSpinner from '@/components/LoadingSpinner';

export default function MainLayout() {
  const { session, profile, loading } = useAuth();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const location = useLocation();
  const isFullBleedRoute = location.pathname.startsWith('/face-setup');

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <LoadingSpinner size="lg" text="Loading your dashboard..." />
      </div>
    );
  }

  if (!session) {
    return <Navigate to="/auth" replace />;
  }

  // If session exists but no profile, show error state
  if (session && !profile) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="text-center space-y-4">
          <h2 className="text-xl font-semibold">Profile Loading Error</h2>
          <p className="text-muted-foreground">
            Unable to load your profile. Please try refreshing the page.
          </p>
          <button 
            onClick={() => window.location.reload()} 
            className="px-4 py-2 bg-primary text-primary-foreground rounded-md"
          >
            Refresh Page
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen flex-col bg-background">
      <Sidebar
        menuOpen={mobileMenuOpen}
        onToggle={() => setMobileMenuOpen(prev => !prev)}
      />

      <main className="flex-1 overflow-auto">
        {isFullBleedRoute ? (
          <div className="h-full">
            <Outlet />
          </div>
        ) : (
          <div className="container mx-auto h-full p-6">
            <Outlet />
          </div>
        )}
      </main>
    </div>
  );
}

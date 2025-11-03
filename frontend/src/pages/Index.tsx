import { useAuth } from '@/hooks/useAuth';
import { Navigate, useLocation } from 'react-router-dom';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import LoadingSpinner from '@/components/LoadingSpinner';
import { 
  GraduationCap, 
  Users, 
  Shield, 
  UserCheck, 
  Camera,
  BarChart3,
  BookOpen,
  CheckCircle,
  Zap,
  Globe,
  Clock
} from 'lucide-react';

const Index = () => {
  const { session, profile, loading } = useAuth();
  const location = useLocation();

  // Show loading state while checking authentication
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary/10 via-background to-secondary/10">
        <LoadingSpinner size="lg" text="Initializing Smart Attendance System..." />
      </div>
    );
  }

  // If user is authenticated and has a profile, redirect to appropriate dashboard
  if (session && profile) {
    // Only redirect if we're on the root path to avoid redirect loops
    if (location.pathname === '/') {
      if (profile.role === 'student') {
        return <Navigate to="/face-setup" replace />;
      } else if (profile.role === 'professor') {
        return <Navigate to="/sections" replace />;
      } else if (profile.role === 'admin') {
        return <Navigate to="/admin-courses" replace />;
      }
      return <Navigate to="/" replace />;
    }
  }

  // If user is authenticated but no profile yet (still loading), show loading
  if (session && !profile) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary/10 via-background to-secondary/10">
        <LoadingSpinner size="lg" text="Loading your profile..." />
      </div>
    );
  }

  // Landing page for unauthenticated users
  return (
    <div className="min-h-screen bg-gradient-to-br from-primary/10 via-background to-secondary/10">
      {/* Header */}
      <header className="border-b border-border/50 bg-card/50 backdrop-blur-sm">
        <div className="container mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-primary/10">
              <UserCheck className="h-6 w-6 text-primary" />
            </div>
            <h1 className="text-xl font-bold text-gradient">Smart Attendance System</h1>
          </div>
          <Button asChild className="btn-gradient">
            <a href="/auth">Get Started</a>
          </Button>
        </div>
      </header>

      {/* Hero Section */}
      <section className="py-20 px-4">
        <div className="container mx-auto text-center">
          <div className="max-w-4xl mx-auto">
            <h2 className="text-4xl md:text-6xl font-bold mb-6">
              Revolutionize <span className="text-gradient">Attendance Tracking</span> with AI
            </h2>
            <p className="text-xl text-muted-foreground mb-8 max-w-2xl mx-auto">
              Experience the future of educational technology with our intelligent face recognition system. 
              Accurate, secure, and effortless attendance management for modern institutions.
            </p>
            <div className="flex flex-col sm:flex-row gap-4 justify-center">
              <Button size="lg" className="btn-gradient" asChild>
                <a href="/auth">
                  <GraduationCap className="h-5 w-5 mr-2" />
                  Start as Student
                </a>
              </Button>
              <Button size="lg" variant="outline" asChild>
                <a href="/auth">
                  <Users className="h-5 w-5 mr-2" />
                  Join as Professor
                </a>
              </Button>
            </div>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section className="py-20 px-4 bg-card/30">
        <div className="container mx-auto">
          <div className="text-center mb-16">
            <h3 className="text-3xl font-bold mb-4">
              Why Choose <span className="text-gradient">Smart Attendance?</span>
            </h3>
            <p className="text-muted-foreground text-lg max-w-2xl mx-auto">
              Our cutting-edge system combines advanced AI with intuitive design to deliver unparalleled attendance management.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            <Card className="card-gradient shadow-elegant">
              <CardHeader>
                <div className="p-3 rounded-full bg-primary/10 w-fit">
                  <Camera className="h-6 w-6 text-primary" />
                </div>
                <CardTitle>AI-Powered Recognition</CardTitle>
                <CardDescription>
                  Advanced facial recognition technology ensures accurate and instant attendance marking.
                </CardDescription>
              </CardHeader>
            </Card>

            <Card className="card-gradient shadow-elegant">
              <CardHeader>
                <div className="p-3 rounded-full bg-success/10 w-fit">
                  <BarChart3 className="h-6 w-6 text-success" />
                </div>
                <CardTitle>Real-time Analytics</CardTitle>
                <CardDescription>
                  Comprehensive dashboards and reports provide insights into attendance patterns and trends.
                </CardDescription>
              </CardHeader>
            </Card>

            <Card className="card-gradient shadow-elegant">
              <CardHeader>
                <div className="p-3 rounded-full bg-warning/10 w-fit">
                  <Shield className="h-6 w-6 text-warning" />
                </div>
                <CardTitle>Secure & Private</CardTitle>
                <CardDescription>
                  Bank-level security with encrypted data storage and privacy-first design principles.
                </CardDescription>
              </CardHeader>
            </Card>

            <Card className="card-gradient shadow-elegant">
              <CardHeader>
                <div className="p-3 rounded-full bg-academic-blue/10 w-fit">
                  <BookOpen className="h-6 w-6 text-academic-blue" />
                </div>
                <CardTitle>Multi-Course Support</CardTitle>
                <CardDescription>
                  Manage multiple courses, sections, and schedules from a single intuitive dashboard.
                </CardDescription>
              </CardHeader>
            </Card>

            <Card className="card-gradient shadow-elegant">
              <CardHeader>
                <div className="p-3 rounded-full bg-academic-green/10 w-fit">
                  <CheckCircle className="h-6 w-6 text-academic-green" />
                </div>
                <CardTitle>Automated Workflow</CardTitle>
                <CardDescription>
                  Streamlined processes reduce manual work and eliminate attendance tracking errors.
                </CardDescription>
              </CardHeader>
            </Card>

            <Card className="card-gradient shadow-elegant">
              <CardHeader>
                <div className="p-3 rounded-full bg-primary/10 w-fit">
                  <Clock className="h-6 w-6 text-primary" />
                </div>
                <CardTitle>24/7 Accessibility</CardTitle>
                <CardDescription>
                  Cloud-based system accessible anytime, anywhere with real-time synchronization.
                </CardDescription>
              </CardHeader>
            </Card>
          </div>
        </div>
      </section>

      {/* Stats Section */}
      <section className="py-20 px-4">
        <div className="container mx-auto">
          <div className="grid grid-cols-2 md:grid-cols-4 gap-8 text-center">
            <div>
              <div className="text-4xl font-bold text-gradient mb-2">99.9%</div>
              <div className="text-muted-foreground">Accuracy Rate</div>
            </div>
            <div>
              <div className="text-4xl font-bold text-gradient mb-2">&lt;2s</div>
              <div className="text-muted-foreground">Recognition Time</div>
            </div>
            <div>
              <div className="text-4xl font-bold text-gradient mb-2">500+</div>
              <div className="text-muted-foreground">Institutions</div>
            </div>
            <div>
              <div className="text-4xl font-bold text-gradient mb-2">100K+</div>
              <div className="text-muted-foreground">Students</div>
            </div>
          </div>
        </div>
      </section>

      {/* User Types Section */}
      <section className="py-20 px-4 bg-card/30">
        <div className="container mx-auto">
          <div className="text-center mb-16">
            <h3 className="text-3xl font-bold mb-4">
              Built for <span className="text-gradient">Everyone</span>
            </h3>
            <p className="text-muted-foreground text-lg">
              Tailored experiences for students, professors, and administrators.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            <Card className="card-gradient shadow-elegant hover:shadow-glow transition-all duration-300">
              <CardHeader className="text-center">
                <div className="p-4 rounded-full bg-primary/10 w-fit mx-auto mb-4">
                  <GraduationCap className="h-8 w-8 text-primary" />
                </div>
                <CardTitle className="text-xl">Students</CardTitle>
                <CardDescription>
                  Simple face registration and attendance tracking
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex items-center gap-2 text-sm">
                  <CheckCircle className="h-4 w-4 text-success" />
                  Easy face enrollment
                </div>
                <div className="flex items-center gap-2 text-sm">
                  <CheckCircle className="h-4 w-4 text-success" />
                  View personal attendance
                </div>
                <div className="flex items-center gap-2 text-sm">
                  <CheckCircle className="h-4 w-4 text-success" />
                  Course enrollment
                </div>
                <Button className="w-full mt-4 btn-gradient" asChild>
                  <a href="/auth">Join as Student</a>
                </Button>
              </CardContent>
            </Card>

            <Card className="card-gradient shadow-elegant hover:shadow-glow transition-all duration-300">
              <CardHeader className="text-center">
                <div className="p-4 rounded-full bg-success/10 w-fit mx-auto mb-4">
                  <Users className="h-8 w-8 text-success" />
                </div>
                <CardTitle className="text-xl">Professors</CardTitle>
                <CardDescription>
                  Manage classes and conduct live attendance sessions
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex items-center gap-2 text-sm">
                  <CheckCircle className="h-4 w-4 text-success" />
                  Live attendance sessions
                </div>
                <div className="flex items-center gap-2 text-sm">
                  <CheckCircle className="h-4 w-4 text-success" />
                  Student management
                </div>
                <div className="flex items-center gap-2 text-sm">
                  <CheckCircle className="h-4 w-4 text-success" />
                  Analytics & reports
                </div>
                <Button className="w-full mt-4 btn-gradient" asChild>
                  <a href="/auth">Join as Professor</a>
                </Button>
              </CardContent>
            </Card>

            <Card className="card-gradient shadow-elegant hover:shadow-glow transition-all duration-300">
              <CardHeader className="text-center">
                <div className="p-4 rounded-full bg-warning/10 w-fit mx-auto mb-4">
                  <Shield className="h-8 w-8 text-warning" />
                </div>
                <CardTitle className="text-xl">Administrators</CardTitle>
                <CardDescription>
                  Full system control and institutional oversight
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex items-center gap-2 text-sm">
                  <CheckCircle className="h-4 w-4 text-success" />
                  System administration
                </div>
                <div className="flex items-center gap-2 text-sm">
                  <CheckCircle className="h-4 w-4 text-success" />
                  User management
                </div>
                <div className="flex items-center gap-2 text-sm">
                  <CheckCircle className="h-4 w-4 text-success" />
                  Institution analytics
                </div>
                <Button className="w-full mt-4 btn-gradient" asChild>
                  <a href="/auth">Join as Admin</a>
                </Button>
              </CardContent>
            </Card>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-20 px-4">
        <div className="container mx-auto text-center">
          <div className="max-w-2xl mx-auto">
            <h3 className="text-3xl font-bold mb-4">
              Ready to Transform Your Institution?
            </h3>
            <p className="text-muted-foreground text-lg mb-8">
              Join thousands of educational institutions already using Smart Attendance System.
            </p>
            <Button size="lg" className="btn-gradient" asChild>
              <a href="/auth">
                <Zap className="h-5 w-5 mr-2" />
                Get Started Today
              </a>
            </Button>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-border/50 bg-card/50 py-8 px-4">
        <div className="container mx-auto text-center">
          <div className="flex items-center justify-center gap-3 mb-4">
            <div className="p-2 rounded-lg bg-primary/10">
              <UserCheck className="h-5 w-5 text-primary" />
            </div>
            <span className="font-semibold">Smart Attendance System</span>
          </div>
          <p className="text-sm text-muted-foreground">
            (c) 2024 Smart Attendance System. Built with modern technology for educational excellence.
          </p>
        </div>
      </footer>
    </div>
  );
};

export default Index;
